package app.recall.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * A small reader for Hugging Face `tokenizer.json` files with a BPE model, enough for Gemma-family tokenizers.
 *
 * Supports added/special token splitting, the common normalizers (Replace, Prepend, Sequence, NFC/NFKC/NFD/NFKD,
 * Lowercase), Split/Metaspace pre-tokenizers, BPE with byte fallback, and a TemplateProcessing post-processor.
 * Anything else throws, so an unsupported tokenizer fails loudly instead of producing wrong ids.
 */
class HfTokenizer private constructor(
    private val vocab: HashMap<String, Int>,
    private val idToToken: Array<String?>,
    private val merges: LongIntMap,
    private val byteFallback: Boolean,
    private val ignoreMerges: Boolean,
    private val unkId: Int?,
    private val added: List<AddedToken>,
    private val normalizer: JsonObject?,
    private val preTokenizer: JsonObject?,
    private val postProcessor: JsonObject?,
) {
    data class AddedToken(val content: String, val id: Int, val lstrip: Boolean, val rstrip: Boolean)

    private val addedTrie = Trie().also { t -> added.forEach { t.insert(it.content, it) } }
    private val byteIds = IntArray(256) { b -> vocab[String.format("<0x%02X>", b)] ?: -1 }

    fun tokenId(token: String): Int = vocab[token] ?: added.firstOrNull { it.content == token }?.id
        ?: error("Token $token is not in the vocabulary")

    fun token(id: Int): String? = idToToken.getOrNull(id)

    /** Encodes [text] with the tokenizer's post-processor (for Gemma, adds `<bos>` and possibly `<eos>`). */
    fun encode(text: String, addSpecialTokens: Boolean = true): IntArray {
        val ids = ArrayList<Int>()
        for (segment in splitOnAdded(text)) {
            if (segment.added != null) ids.add(segment.added.id)
            else if (segment.text.isNotEmpty()) encodeSegment(segment.text, ids)
        }
        return if (addSpecialTokens) postProcess(ids).toIntArray() else ids.toIntArray()
    }

    // ---------------------------------------------------------------- added tokens

    private class Segment(val text: String, val added: AddedToken?)

    private fun splitOnAdded(text: String): List<Segment> {
        val out = ArrayList<Segment>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val match = addedTrie.longestMatch(text, i)
            if (match == null) { i++; continue }
            var before = text.substring(start, i)
            if (match.second.lstrip) before = before.trimEnd()
            if (before.isNotEmpty()) out.add(Segment(before, null))
            out.add(Segment(match.second.content, match.second))
            i += match.first
            if (match.second.rstrip) while (i < text.length && text[i].isWhitespace()) i++
            start = i
        }
        if (start < text.length) out.add(Segment(text.substring(start), null))
        return out
    }

    // ---------------------------------------------------------------- pipeline

    private fun encodeSegment(text: String, out: MutableList<Int>) {
        val normalized = normalize(normalizer, text)
        for (word in preTokenize(preTokenizer, listOf(normalized))) {
            if (word.isNotEmpty()) bpe(word, out)
        }
    }

    private fun normalize(cfg: JsonObject?, s: String): String {
        if (cfg == null) return s
        return when (val type = cfg.str("type")) {
            "Sequence" -> cfg.getAsJsonArray("normalizers").fold(s) { acc, n -> normalize(n.asJsonObject, acc) }
            "Replace" -> replacePattern(cfg.getAsJsonObject("pattern"), s, cfg.str("content") ?: "")
            "Prepend" -> if (s.isEmpty()) s else (cfg.str("prepend") ?: "") + s
            "NFC" -> Normalizer.normalize(s, Normalizer.Form.NFC)
            "NFKC" -> Normalizer.normalize(s, Normalizer.Form.NFKC)
            "NFD" -> Normalizer.normalize(s, Normalizer.Form.NFD)
            "NFKD" -> Normalizer.normalize(s, Normalizer.Form.NFKD)
            "Lowercase" -> s.lowercase()
            "Strip" -> {
                var r = s
                if (cfg.bool("strip_left", true)) r = r.trimStart()
                if (cfg.bool("strip_right", true)) r = r.trimEnd()
                r
            }
            else -> error("Unsupported normalizer: $type")
        }
    }

    private fun replacePattern(pattern: JsonObject, s: String, content: String): String = when {
        pattern.has("String") -> s.replace(pattern.str("String")!!, content)
        pattern.has("Regex") -> Regex(pattern.str("Regex")!!).replace(s, Regex.escapeReplacement(content))
        else -> error("Unsupported pattern: $pattern")
    }

    private fun preTokenize(cfg: JsonObject?, pieces: List<String>): List<String> {
        if (cfg == null) return pieces
        return when (val type = cfg.str("type")) {
            "Sequence" -> cfg.getAsJsonArray("pretokenizers").fold(pieces) { acc, p -> preTokenize(p.asJsonObject, acc) }
            "Split" -> pieces.flatMap { split(it, cfg) }
            "Metaspace" -> pieces.flatMapIndexed { index, p -> metaspace(p, cfg, index == 0) }
            "WhitespaceSplit" -> pieces.flatMap { it.split(Regex("\\s+")).filter(String::isNotEmpty) }
            else -> error("Unsupported pre-tokenizer: $type")
        }
    }

    private fun metaspace(s: String, cfg: JsonObject, first: Boolean): List<String> {
        val repl = cfg.str("replacement") ?: "▁"
        var r = s.replace(" ", repl)
        val scheme = cfg.str("prepend_scheme") ?: if (cfg.bool("add_prefix_space", true)) "always" else "never"
        if ((scheme == "always" || (scheme == "first" && first)) && !r.startsWith(repl)) r = repl + r
        if (!cfg.bool("split", true)) return listOf(r)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in r) {
            if (ch.toString() == repl && sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() }
            sb.append(ch)
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    private fun split(s: String, cfg: JsonObject): List<String> {
        val pattern = cfg.getAsJsonObject("pattern")
        val regex = when {
            pattern.has("String") -> Regex(Regex.escape(pattern.str("String")!!))
            pattern.has("Regex") -> Regex(pattern.str("Regex")!!)
            else -> error("Unsupported split pattern")
        }
        val invert = cfg.bool("invert", false)
        // (range, isDelimiter)
        val spans = ArrayList<Pair<IntRange, Boolean>>()
        var last = 0
        for (m in regex.findAll(s)) {
            if (m.range.isEmpty()) continue
            if (m.range.first > last) spans.add((last until m.range.first) to false)
            spans.add(m.range to true)
            last = m.range.last + 1
        }
        if (last < s.length) spans.add((last until s.length) to false)
        val marked = spans.map { (r, isMatch) -> s.substring(r.first, r.last + 1) to (if (invert) !isMatch else isMatch) }

        val out = ArrayList<String>()
        when (val behavior = cfg.str("behavior") ?: "Isolated") {
            "Removed" -> marked.filter { !it.second }.forEach { out.add(it.first) }
            "Isolated" -> marked.forEach { out.add(it.first) }
            "MergedWithPrevious" -> marked.forEach { (t, d) ->
                if (d && out.isNotEmpty()) out[out.size - 1] = out.last() + t else out.add(t)
            }
            "MergedWithNext" -> {
                var pending = ""
                marked.forEach { (t, d) -> if (d) pending += t else { out.add(pending + t); pending = "" } }
                if (pending.isNotEmpty()) out.add(pending)
            }
            "Contiguous" -> {
                var prevDelim = false
                marked.forEach { (t, d) ->
                    if (d && prevDelim) out[out.size - 1] = out.last() + t else out.add(t)
                    prevDelim = d
                }
            }
            else -> error("Unsupported split behavior: $behavior")
        }
        return out.filter { it.isNotEmpty() }
    }

    // ---------------------------------------------------------------- BPE

    private fun bpe(word: String, out: MutableList<Int>) {
        if (ignoreMerges) vocab[word]?.let { out.add(it); return }
        val syms = ArrayList<Int>(word.length)
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val id = vocab[ch]
            when {
                id != null -> syms.add(id)
                byteFallback -> ch.toByteArray(Charsets.UTF_8).forEach { b ->
                    val bid = byteIds[b.toInt() and 0xFF]
                    if (bid >= 0) syms.add(bid) else unkId?.let(syms::add)
                }
                unkId != null -> syms.add(unkId)
            }
            i += Character.charCount(cp)
        }
        // Repeatedly merge the adjacent pair with the lowest rank (leftmost on ties)
        while (syms.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIdx = -1
            for (k in 0 until syms.size - 1) {
                val rank = merges.get(pairKey(syms[k], syms[k + 1]))
                if (rank in 0 until bestRank) { bestRank = rank; bestIdx = k }
            }
            if (bestIdx < 0) break
            val merged = vocab[idToToken[syms[bestIdx]] + idToToken[syms[bestIdx + 1]]] ?: break
            syms[bestIdx] = merged
            syms.removeAt(bestIdx + 1)
        }
        out.addAll(syms)
    }

    // ---------------------------------------------------------------- post-processing

    private fun postProcess(ids: List<Int>): List<Int> {
        val cfg = postProcessor ?: return ids
        return when (val type = cfg.str("type")) {
            "TemplateProcessing" -> {
                val special = cfg.getAsJsonObject("special_tokens")
                val out = ArrayList<Int>()
                for (piece in cfg.getAsJsonArray("single")) {
                    val o = piece.asJsonObject
                    when {
                        o.has("Sequence") -> out.addAll(ids)
                        o.has("SpecialToken") -> {
                            val name = o.getAsJsonObject("SpecialToken").str("id")!!
                            special.getAsJsonObject(name).getAsJsonArray("ids").forEach { out.add(it.asInt) }
                        }
                    }
                }
                out
            }
            "Sequence" -> cfg.getAsJsonArray("processors").fold(ids) { acc, p -> copyWithPost(p.asJsonObject).postProcess(acc) }
            "ByteLevel" -> ids
            else -> error("Unsupported post-processor: $type")
        }
    }

    private fun copyWithPost(p: JsonObject) = HfTokenizer(
        vocab, idToToken, merges, byteFallback, ignoreMerges, unkId, added, normalizer, preTokenizer, p,
    )

    /** Describes the pipeline, for logs and tests. */
    fun describe(): String = buildString {
        append("normalizer=").append(normalizer).append('\n')
        append("pre_tokenizer=").append(preTokenizer).append('\n')
        append("post_processor=").append(postProcessor).append('\n')
        append("byte_fallback=$byteFallback ignore_merges=$ignoreMerges unk=$unkId vocab=${vocab.size} added=${added.size}")
    }

    companion object {
        private fun pairKey(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

        /** Streams [file] so the 30+ MB vocabulary never sits in memory as a JSON tree. */
        fun load(file: File): HfTokenizer {
            var normalizer: JsonObject? = null
            var preTokenizer: JsonObject? = null
            var postProcessor: JsonObject? = null
            val added = ArrayList<AddedToken>()
            val vocab = HashMap<String, Int>(300_000)
            val mergePairs = ArrayList<Pair<String, String>>(600_000)
            var byteFallback = false
            var ignoreMerges = false
            var unkToken: String? = null

            JsonReader(InputStreamReader(file.inputStream().buffered(1 shl 16), Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "normalizer" -> normalizer = readObjOrNull(r)
                        "pre_tokenizer" -> preTokenizer = readObjOrNull(r)
                        "post_processor" -> postProcessor = readObjOrNull(r)
                        "added_tokens" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                val o = JsonParser.parseReader(r).asJsonObject
                                added.add(AddedToken(o.str("content")!!, o.get("id").asInt, o.bool("lstrip", false), o.bool("rstrip", false)))
                            }
                            r.endArray()
                        }
                        "model" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "type" -> { val t = r.nextString(); require(t == "BPE") { "Only BPE tokenizers are supported, got $t" } }
                                    "byte_fallback" -> byteFallback = readBool(r)
                                    "ignore_merges" -> ignoreMerges = readBool(r)
                                    "unk_token" -> unkToken = if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextString()
                                    "vocab" -> {
                                        r.beginObject()
                                        while (r.hasNext()) vocab[r.nextName()] = r.nextInt()
                                        r.endObject()
                                    }
                                    "merges" -> {
                                        r.beginArray()
                                        while (r.hasNext()) {
                                            if (r.peek() == JsonToken.STRING) {
                                                val m = r.nextString()
                                                val sp = m.indexOf(' ', 1)
                                                mergePairs.add(m.substring(0, sp) to m.substring(sp + 1))
                                            } else {
                                                r.beginArray(); val a = r.nextString(); val b = r.nextString(); r.endArray()
                                                mergePairs.add(a to b)
                                            }
                                        }
                                        r.endArray()
                                    }
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                        }
                        else -> r.skipValue()
                    }
                }
            }
            for (a in added) vocab.putIfAbsent(a.content, a.id)
            val maxId = maxOf(vocab.values.maxOrNull() ?: 0, added.maxOfOrNull { it.id } ?: 0)
            val idToToken = arrayOfNulls<String>(maxId + 1)
            for ((t, id) in vocab) idToToken[id] = t
            val merges = LongIntMap(mergePairs.size * 2)
            mergePairs.forEachIndexed { rank, (a, b) ->
                val ia = vocab[a]; val ib = vocab[b]
                if (ia != null && ib != null) {
                    val k = pairKey(ia, ib)
                    if (merges.get(k) < 0) merges.put(k, rank)
                }
            }
            return HfTokenizer(
                vocab, idToToken, merges, byteFallback, ignoreMerges, unkToken?.let { vocab[it] },
                added.sortedBy { it.id }, normalizer, preTokenizer, postProcessor,
            )
        }

        private fun readObjOrNull(r: JsonReader): JsonObject? =
            if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else JsonParser.parseReader(r).asJsonObject

        private fun readBool(r: JsonReader): Boolean =
            if (r.peek() == JsonToken.NULL) { r.nextNull(); false } else r.nextBoolean()
    }
}

internal fun JsonObject.str(key: String): String? = get(key)?.takeUnless(JsonElement::isJsonNull)?.asString
internal fun JsonObject.bool(key: String, default: Boolean): Boolean =
    get(key)?.takeUnless(JsonElement::isJsonNull)?.asBoolean ?: default

/** Character trie for longest-match lookup of added tokens. */
internal class Trie {
    private class Node { val next = HashMap<Char, Node>(); var value: HfTokenizer.AddedToken? = null }
    private val root = Node()

    fun insert(key: String, value: HfTokenizer.AddedToken) {
        var n = root
        for (c in key) n = n.next.getOrPut(c) { Node() }
        n.value = value
    }

    /** Returns (length, token) of the longest added token starting at [start]. */
    fun longestMatch(s: String, start: Int): Pair<Int, HfTokenizer.AddedToken>? {
        var n = root
        var best: Pair<Int, HfTokenizer.AddedToken>? = null
        var i = start
        while (i < s.length) {
            n = n.next[s[i]] ?: break
            i++
            n.value?.let { best = (i - start) to it }
        }
        return best
    }
}

/** Open-addressing Long -> Int map (values must be >= 0; missing keys return -1). */
internal class LongIntMap(expected: Int) {
    private var cap = Integer.highestOneBit(maxOf(16, expected * 2 - 1)) shl 1
    private var keys = LongArray(cap)
    private var vals = IntArray(cap) { -1 }
    private var size = 0

    private fun mix(k: Long): Int {
        var h = k * -0x61c8864680b583ebL
        h = h xor (h ushr 32)
        return h.toInt()
    }

    fun get(k: Long): Int {
        var i = mix(k) and (cap - 1)
        while (true) {
            val v = vals[i]
            if (v < 0) return -1
            if (keys[i] == k) return v
            i = (i + 1) and (cap - 1)
        }
    }

    fun put(k: Long, v: Int) {
        if ((size + 1) * 2 > cap) grow()
        var i = mix(k) and (cap - 1)
        while (vals[i] >= 0 && keys[i] != k) i = (i + 1) and (cap - 1)
        if (vals[i] < 0) size++
        keys[i] = k; vals[i] = v
    }

    private fun grow() {
        val ok = keys; val ov = vals
        cap = cap shl 1; keys = LongArray(cap); vals = IntArray(cap) { -1 }; size = 0
        for (i in ok.indices) if (ov[i] >= 0) put(ok[i], ov[i])
    }
}

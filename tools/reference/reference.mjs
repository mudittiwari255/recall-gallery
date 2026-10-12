// Computes reference outputs with the official Transformers.js implementation of EmbeddingGemma 2,
// so CI can check the Kotlin port (tokenizer, image preprocessing, ONNX inference) against it.
//
// Usage: node reference.mjs <models-root> <out.json> <image>...
//   <models-root> holds onnx-community/embeddinggemma-2-ONNX/...
import { AutoConfig, AutoModel, AutoProcessor, RawImage, env } from "@huggingface/transformers";
import { readFileSync, writeFileSync } from "node:fs";

const [root, outPath, ...images] = process.argv.slice(2);
env.allowRemoteModels = false;
env.localModelPath = root.endsWith("/") ? root : root + "/";
const MODEL = "onnx-community/embeddinggemma-2-ONNX";

const tok = JSON.parse(readFileSync(`${env.localModelPath}${MODEL}/tokenizer.json`, "utf8"));
console.log("normalizer:", JSON.stringify(tok.normalizer));
console.log("pre_tokenizer:", JSON.stringify(tok.pre_tokenizer));
console.log("post_processor:", JSON.stringify(tok.post_processor));
const { vocab, merges, ...modelRest } = tok.model;
console.log("model:", JSON.stringify(modelRest), "merges:", merges.length, "first merge:", JSON.stringify(merges[0]));

const config = await AutoConfig.from_pretrained(MODEL);
config.audio_config = null;
const processor = await AutoProcessor.from_pretrained(MODEL);
const model = await AutoModel.from_pretrained(MODEL, { config, device: "cpu", dtype: "q4" });

const out = { texts: [], images: [] };
const queries = [
  "task: search result | query: cats sleeping on a couch",
  "task: search result | query: a turtle swimming in the ocean",
  "task: search result | query: रेस्टोरेंट का बिल 2025 ✨ receipt",
  "title: none | text: Hello   world!\nNew line\ttab  — naïve café",
  "task: search result | query: ",
];
for (const text of queries) {
  const inputs = await processor([text]);
  const { sentence_embedding } = await model(inputs);
  out.texts.push({ text, ids: Array.from(inputs.input_ids.data, Number), embedding: Array.from(sentence_embedding.data) });
}

for (const path of images) {
  for (const budget of [70, 280]) {
    processor.image_processor.max_soft_tokens = budget;
    const image = await RawImage.read(path);
    const inputs = await processor(null, image);
    const n = inputs.num_soft_tokens_per_image[0];
    // Mean of the real (non-padded) patch values, a cheap fingerprint of resize + rescale
    const pv = inputs.pixel_values.data;
    const pos = inputs.image_position_ids.data;
    const patchDim = inputs.pixel_values.dims[2];
    let sum = 0, count = 0, realPatches = 0;
    for (let p = 0; p < inputs.pixel_values.dims[1]; p++) {
      if (Number(pos[p * 2]) < 0) continue;
      realPatches++;
      for (let k = 0; k < patchDim; k++) { sum += pv[p * patchDim + k]; count++; }
    }
    const { sentence_embedding } = await model(inputs);
    out.images.push({
      path, budget, width: image.width, height: image.height, numSoftTokens: n, realPatches,
      meanPixel: sum / count, ids: Array.from(inputs.input_ids.data, Number),
      embedding: Array.from(sentence_embedding.data),
    });
    console.log(`${path} budget=${budget}: ${n} soft tokens, ${realPatches} patches, mean=${(sum / count).toFixed(4)}`);
  }
}
writeFileSync(outPath, JSON.stringify(out));
console.log("wrote", outPath);

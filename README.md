# Recall Gallery

An Android gallery app that lets you search your photos by describing them: "receipt from a restaurant", "dog on the beach", "whiteboard notes". Search runs entirely on your phone with Google's [EmbeddingGemma 2](https://huggingface.co/google/embeddinggemma-2) model. No photo ever leaves the device.

## Install

1. On your Android phone, open the [latest release](../../releases/latest).
2. Tap **recall-gallery.apk** to download it, then open it. Allow your browser to install apps when Android asks.
3. Open Recall, allow photo access, and tap **Download model** (about 285 MB, once; Wi-Fi recommended).
4. Indexing starts on its own and continues in the background. Search works on whatever is already indexed.

Updates install over the previous version, because every build is signed with the same key.

## How it works

- **Gallery:** reads your photo library through Android's MediaStore.
- **Indexing:** a background job (WorkManager) embeds each photo with the vision encoder and stores one 768-number vector per photo in a local SQLite database. New photos are picked up while the phone charges.
- **Search:** your text is embedded with the text model and compared to every photo vector (cosine similarity). Tap a photo and **Find similar** to search by example.
- **Model:** [onnx-community/embeddinggemma-2-ONNX](https://huggingface.co/onnx-community/embeddinggemma-2-ONNX), 4-bit, run with ONNX Runtime.

## Project layout

- `core/` – pure Kotlin search engine: tokenizer, image preprocessing, ONNX inference. Tested on the desktop JVM.
- `app/` – the Android app (Jetpack Compose).
- `tools/reference/` – produces reference outputs with the official Transformers.js implementation.
- `.github/workflows/build.yml` – on every push: checks `core/` against the reference using the real model, builds the APK, and publishes a release.

## Notes

- The signing key in `keystore/` is committed so CI builds can update each other. That's fine for a personal app; use a private key stored as a GitHub secret before publishing anywhere.
- Photo detail (Settings) trades indexing speed for finding small details.

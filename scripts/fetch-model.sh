#!/bin/bash
# Fetch the local embedding model (all-MiniLM-L6-v2 ONNX, ~87 MB) for a quick start.
#
# This file is intentionally NOT committed to git (too large for a shareable repo), so a
# fresh clone downloads it once here. Public source, no auth. Override MODEL_URL to use a
# mirror. Idempotent: skips if the model is already present (use FORCE=1 to re-download).
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Destination directory. Defaults to the classpath location (baked into the jar at build).
# Override to download the model anywhere, then point the app at it with
#   EMBEDDING_MODEL_URI=file:/your/path/model.onnx  (and EMBEDDING_TOKENIZER_URI=...).
DIR="${MODEL_DIR:-$REPO/src/main/resources/onnx/all-MiniLM-L6-v2}"

MODEL_URL="${MODEL_URL:-https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx}"
TOKENIZER_URL="${TOKENIZER_URL:-https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json}"

mkdir -p "$DIR"

if [ -s "$DIR/model.onnx" ] && [ "${FORCE:-0}" != "1" ]; then
  echo "model.onnx already present ($(du -h "$DIR/model.onnx" | cut -f1)) — skipping (set FORCE=1 to re-download)."
else
  echo "Downloading model.onnx (~87 MB) from $MODEL_URL"
  curl -fSL --retry 3 -o "$DIR/model.onnx" "$MODEL_URL"
fi

# tokenizer.json is small and normally committed; fetch it only if missing.
if [ ! -s "$DIR/tokenizer.json" ]; then
  echo "Downloading tokenizer.json from $TOKENIZER_URL"
  curl -fSL --retry 3 -o "$DIR/tokenizer.json" "$TOKENIZER_URL"
fi

echo "Embedding model ready in $DIR"

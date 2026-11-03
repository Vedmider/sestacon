#!/bin/bash
# Populate docker/djl-cache (baked into the image for offline embeddings).
# Reuses the local ~/.djl.ai cache if present, else triggers a one-off download
# by running the embedding test, then copies the result.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$REPO/docker/djl-cache"

if [ ! -d ~/.djl.ai/pytorch ]; then
  echo "No ~/.djl.ai cache yet — running embedding test to populate it..."
  ( source "$REPO/env.sh" && cd "$REPO" && mvn -B test -Dtest=EmbeddingModelTest )
fi

rm -rf "$DEST"
mkdir -p "$DEST"
cp -r ~/.djl.ai/. "$DEST"/
chmod -R u+rwX,go+rX "$DEST"
du -sh "$DEST"
echo "DJL cache staged at $DEST"
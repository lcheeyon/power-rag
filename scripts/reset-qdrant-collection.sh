#!/usr/bin/env bash
# Drop the knowledge-base Qdrant collection so the backend can recreate it with powerrag.embedding.dimensions (default 768).
# Use after dimension mismatches (e.g. "expected dim: 3072, got 768") or corrupt indexes.
# Then restart the backend and re-upload documents.

set -euo pipefail
HOST="${QDRANT_HTTP_HOST:-127.0.0.1}"
PORT="${QDRANT_HTTP_PORT:-6333}"
NAME="${QDRANT_COLLECTION:-power_rag_docs}"

url="http://${HOST}:${PORT}/collections/${NAME}"
echo "DELETE ${url}"
code=$(curl -sS -o /tmp/qdrant-del.body -w '%{http_code}' -X DELETE "$url" || true)
echo "HTTP ${code}"
if [[ "$code" == "200" || "$code" == "404" ]]; then
  echo "Done. Restart the backend, then re-ingest documents."
  exit 0
fi
cat /tmp/qdrant-del.body 2>/dev/null || true
exit 1

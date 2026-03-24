#!/usr/bin/env bash
# restart.sh — Kill local dev services and restart them (with HTTPS via nginx)
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_LOG="${PROJECT_DIR}/logs/backend.log"
FRONTEND_LOG="${PROJECT_DIR}/logs/frontend.log"
NGINX_CONF="${PROJECT_DIR}/nginx/nginx.conf"

mkdir -p "${PROJECT_DIR}/logs"

# ── colours ────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }

# ── 1. Load API keys from .env.example ─────────────────────────────────────
ENV_FILE="${PROJECT_DIR}/.env.example"
if [[ -f "${ENV_FILE}" ]]; then
  set -o allexport
  source <(grep -E '^[A-Z_]+=.+' "${ENV_FILE}")
  set +o allexport
  info "Loaded env from ${ENV_FILE}"
fi

if [[ -z "${ANTHROPIC_API_KEY:-}" ]]; then
  echo -e "${RED}[ERROR]${NC} ANTHROPIC_API_KEY is not set and not found in .env.example"
  exit 1
fi
success "API keys loaded"

# ── 2. Kill existing processes ─────────────────────────────────────────────
info "Stopping existing processes …"
pkill -f 'spring-boot:run' 2>/dev/null && warn "  Killed spring-boot:run" || true
pkill -f 'vite'            2>/dev/null && warn "  Killed vite"            || true
nginx -c "${NGINX_CONF}" -s stop 2>/dev/null && warn "  Stopped nginx"    || true
sleep 2

# ── 3. Start Docker services ───────────────────────────────────────────────
info "Starting Docker services (Postgres / Redis Stack / Qdrant) …"
docker compose -f "${PROJECT_DIR}/docker-compose.yml" up -d postgres redis-stack qdrant
sleep 3

# ── 4. Guard: stop local Homebrew Redis (conflicts with Docker Redis Stack) ─
if brew services list 2>/dev/null | grep -q "^redis.*started"; then
  warn "Local Homebrew Redis running — stopping to avoid port 6379 conflict"
  brew services stop redis
fi

# ── 5. Start Ollama (guardrails + embeddings) ──────────────────────────────
if ! curl -sf http://localhost:11434/api/version > /dev/null 2>&1; then
  info "Starting Ollama …"
  nohup ollama serve > /tmp/ollama.log 2>&1 &
  sleep 3
  curl -sf http://localhost:11434/api/version > /dev/null && success "Ollama started" || warn "Ollama failed to start — guardrails will be skipped"
else
  success "Ollama already running"
fi

# ── 7. Start nginx (HTTPS reverse proxy) ───────────────────────────────────
info "Starting nginx …"
nginx -c "${NGINX_CONF}"
success "nginx started — HTTPS endpoints:"
echo "         Frontend: https://localhost:3443"
echo "         Backend:  https://localhost:8443"

# ── 8. Start Spring Boot backend ───────────────────────────────────────────
info "Starting Spring Boot backend → ${BACKEND_LOG}"
nohup env \
  ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
  GOOGLE_API_KEY="${GOOGLE_API_KEY:-}" \
  JWT_SECRET="${JWT_SECRET:-change-me-in-production-min-32-chars!!}" \
  mvn -f "${PROJECT_DIR}/backend/pom.xml" spring-boot:run \
  > "${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!
success "Backend started (PID ${BACKEND_PID})"

# ── 9. Wait for backend health ─────────────────────────────────────────────
info "Waiting for backend to be healthy …"
ATTEMPTS=0
until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
  ATTEMPTS=$((ATTEMPTS + 1))
  if [[ $ATTEMPTS -ge 60 ]]; then
    echo -e "${RED}[ERROR]${NC} Backend did not become healthy after 120s"
    echo "        Check logs: tail -50 ${BACKEND_LOG}"
    exit 1
  fi
  sleep 2
done
success "Backend is healthy"

# ── 10. Start Vite frontend ────────────────────────────────────────────────
info "Starting React/Vite frontend → ${FRONTEND_LOG}"
nohup bash -c "cd '${PROJECT_DIR}/frontend' && npm run dev" \
  > "${FRONTEND_LOG}" 2>&1 &
FRONTEND_PID=$!
success "Frontend started (PID ${FRONTEND_PID})"

# ── 11. Summary ────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}All services started successfully.${NC}"
echo ""
echo "  HTTPS (use these):   https://localhost:3443  (app)"
echo "                       https://localhost:8443  (API direct)"
echo "  HTTP  (internal):    http://localhost:3000   (Vite)"
echo "                       http://localhost:8080   (Spring Boot)"
echo ""
echo "  Backend log:  tail -f ${BACKEND_LOG}"
echo "  Frontend log: tail -f ${FRONTEND_LOG}"
echo "  nginx log:    tail -f /opt/homebrew/var/log/nginx/power-rag-error.log"
echo ""
echo "  To trust the SSL cert (run once, requires password):"
echo "    mkcert -install"

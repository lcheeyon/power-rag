#!/usr/bin/env sh
# One-time: download boutquin/mcp-server-email v1.0.2 release binary into mcp/bin/ for local dev
# (Dockerfile installs the same for container images).
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
BIN_DIR="$ROOT/bin"
BIN="$BIN_DIR/mcp-server-email"
VERSION="1.0.2"
TAG="v${VERSION}"
mkdir -p "$BIN_DIR"
case "$(uname -s)/$(uname -m)" in
  Darwin/arm64)  SUF="darwin_arm64.tar.gz"  ; TAR=1 ;;
  Darwin/x86_64) SUF="darwin_amd64.tar.gz"  ; TAR=1 ;;
  Linux/aarch64) SUF="linux_arm64.tar.gz"   ; TAR=1 ;;
  Linux/arm64)   SUF="linux_arm64.tar.gz"   ; TAR=1 ;;
  Linux/x86_64)  SUF="linux_amd64.tar.gz"  ; TAR=1 ;;
  *) echo "Unsupported OS/arch: $(uname -s) $(uname -m)" >&2; exit 1 ;;
esac
URL="https://github.com/boutquin/mcp-server-email/releases/download/${TAG}/mcp-server-email_${VERSION}_${SUF}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo "Downloading $URL ..."
curl -fsSL "$URL" -o "$TMP/archive.tar.gz"
tar -xzf "$TMP/archive.tar.gz" -C "$TMP"
# GoReleaser layout: single binary at top level of tarball
if [ -f "$TMP/mcp-server-email" ]; then
  install -m 0755 "$TMP/mcp-server-email" "$BIN"
else
  echo "Unexpected archive layout under $TMP" >&2
  ls -la "$TMP" >&2
  exit 1
fi
echo "Installed $BIN"

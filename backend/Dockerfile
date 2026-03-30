# ── boutquin/mcp-server-email (static Go binary for MCP stdio) ───────────────
FROM golang:1.24-alpine AS mcp-email-build
RUN apk add --no-cache git ca-certificates && \
    CGO_ENABLED=0 go install -ldflags="-s -w" github.com/boutquin/mcp-server-email/cmd/mcp-server-email@v1.0.2

# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Cache apk packages across builds (never re-downloads JDK tooling)
RUN --mount=type=cache,target=/var/cache/apk \
    apk add --update maven

COPY pom.xml .

# Cache Maven local repo across builds (never re-downloads dependencies unless pom.xml changes)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -q

COPY src ./src

# Build — reuses cached .m2 repo
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S powerrag && adduser -S powerrag -G powerrag && \
    mkdir -p /app/uploads && chown powerrag:powerrag /app/uploads

# MCP stdio: Power RAG Python tools + boutquin/mcp-server-email (IMAP/SMTP).
COPY mcp ./mcp
COPY --from=mcp-email-build /go/bin/mcp-server-email /app/mcp/bin/mcp-server-email
RUN cp mcp/email-accounts.docker-stub.json mcp/email-accounts.json && \
    chmod +x /app/mcp/bin/mcp-server-email && \
    apk add --no-cache python3 py3-pip && \
    pip3 install --break-system-packages --no-cache-dir -r mcp/requirements.txt && \
    chown -R powerrag:powerrag /app/mcp

USER powerrag

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

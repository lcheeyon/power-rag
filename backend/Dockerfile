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
USER powerrag

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

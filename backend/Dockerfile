# syntax=docker/dockerfile:1.7

# Tags completas e versionadas: atualizar é uma mudança explícita e revisável.
ARG TEMURIN_VERSION=25.0.3_9
ARG ALPINE_VERSION=3.23

FROM eclipse-temurin:${TEMURIN_VERSION}-jdk-alpine-${ALPINE_VERSION} AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# O cache fica no builder/BuildKit, nunca na imagem final.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && \
    ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests package && \
    java -Djarmode=tools -jar target/crm-0.0.1-SNAPSHOT.jar \
      extract --launcher --layers --destination target/extracted

FROM eclipse-temurin:${TEMURIN_VERSION}-jre-alpine-${ALPINE_VERSION} AS runtime

ARG APP_VERSION=0.0.1-dev
ARG VCS_REF=working-tree

LABEL org.opencontainers.image.title="CRM PNP Backend" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="https://github.com/PNPeixoto/CRM"

RUN addgroup -S -g 10001 app && \
    adduser -S -D -H -u 10001 -G app app

WORKDIR /app

# Dependências mudam menos que a aplicação e entram primeiro para preservar o
# cache entre builds. A imagem final recebe apenas runtime e camadas executáveis.
COPY --from=build --chown=10001:10001 /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/target/extracted/application/ ./

USER 10001:10001

EXPOSE 8080
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=4 \
  CMD wget -q -T 2 -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:InitialRAMPercentage=25.0", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "org.springframework.boot.loader.launch.JarLauncher"]

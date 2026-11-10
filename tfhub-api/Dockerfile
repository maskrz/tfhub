FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build
COPY pom.xml .
COPY src/main src/main
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

ARG RCLONE_VERSION=1.76.0
ARG RCLONE_SHA256_AMD64=b00f00b3266c3764413afe599d3ec316f6f43b3e0ea8a0bc1d186d2133e46df7
ARG RCLONE_SHA256_ARM64=ee169016675e23a69d77240565133ff7dbc9b33d16fa8f7053d4af39f1eaeb78
ARG RCLONE_SHA256_ARM_V7=090e1e2ce0f2a27c6c5ad5b2a2f7bbf7570933f3523e8bf83cb25ee50115f885

RUN apk add --no-cache ca-certificates unzip \
    && case "$(apk --print-arch)" in \
        x86_64) rclone_arch=amd64; rclone_sha256="$RCLONE_SHA256_AMD64" ;; \
        aarch64) rclone_arch=arm64; rclone_sha256="$RCLONE_SHA256_ARM64" ;; \
        armhf) rclone_arch=arm-v7; rclone_sha256="$RCLONE_SHA256_ARM_V7" ;; \
        *) echo "Unsupported architecture for rclone" >&2; exit 1 ;; \
    esac \
    && rclone_archive="rclone-v${RCLONE_VERSION}-linux-${rclone_arch}.zip" \
    && wget -q "https://github.com/rclone/rclone/releases/download/v${RCLONE_VERSION}/${rclone_archive}" -O /tmp/rclone.zip \
    && echo "${rclone_sha256}  /tmp/rclone.zip" | sha256sum -c - \
    && unzip -q /tmp/rclone.zip -d /tmp/rclone \
    && install -m 0755 "/tmp/rclone/rclone-v${RCLONE_VERSION}-linux-${rclone_arch}/rclone" /usr/local/bin/rclone \
    && rm -rf /tmp/rclone /tmp/rclone.zip \
    && apk del unzip \
    && addgroup -S -g 10001 spring \
    && adduser -S -u 10001 -G spring spring
WORKDIR /app
COPY --from=build --chown=spring:spring /build/target/tfhub-api-*.jar /app/app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

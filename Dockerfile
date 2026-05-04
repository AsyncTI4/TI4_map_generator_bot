# ---- Build stage ----
FROM maven:3.9.15-eclipse-temurin-26-alpine AS build
WORKDIR /opt/app

# cache dependencies
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline

# add sources late for better layer reuse
# Only package classpath config; runtime assets are mounted from the host repo.
COPY src/main/resources/config ./src/main/resources/config
COPY src/main/resources/logback.xml ./src/main/resources/logback.xml
COPY src/main/java ./src/main/java

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dspotless.skip=true -Dmaven.test.skip=true clean package

# ---- Runtime stage ----
# Use the glibc-based (Amazon Linux 2023) image so the async-profiler linux-x64 native
# library can be loaded; Alpine uses musl libc which is binary-incompatible with it.
FROM amazoncorretto:26
WORKDIR /app

# needed to handle fonts
RUN dnf install -y fontconfig dejavu-fonts-all && dnf clean all

# async-profiler: enables remote CPU/allocation profiling via IntelliJ Profiler
ARG ASYNC_PROFILER_VERSION=4.4
RUN curl -fsSL "https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64.tar.gz" \
        -O /tmp/async-profiler.tar.gz \
    && mkdir -p /app/async-profiler \
    && tar -xzf /tmp/async-profiler.tar.gz -C /app/async-profiler --strip-components=1 \
    && rm /tmp/async-profiler.tar.gz

COPY --from=build /opt/app/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT.jar tibot.jar

ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources

ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=70.0", \
            "-XX:InitialRAMPercentage=20.0", \
            "-XX:+UseStringDeduplication", \
            "-jar", "tibot.jar"]

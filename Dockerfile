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
FROM amazoncorretto:26-alpine3.23
WORKDIR /app

# needed to handle fonts; libc6-compat provides glibc shims required by async-profiler
RUN apk add --no-cache fontconfig ttf-dejavu libc6-compat

COPY --from=build /opt/app/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT.jar tibot.jar

# Install async-profiler (linux-x64 build) to /opt/async-profiler
ARG ASYNC_PROFILER_VERSION=4.4
RUN wget -qO /tmp/async-profiler.tar.gz \
      "https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/async-profiler-${ASYNC_PROFILER_VERSION}-linux-x64.tar.gz" \
    && mkdir -p /opt/async-profiler \
    && tar -xzf /tmp/async-profiler.tar.gz --strip-components=1 -C /opt/async-profiler \
    && rm /tmp/async-profiler.tar.gz

ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources

ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=70.0", \
            "-XX:InitialRAMPercentage=20.0", \
            "-XX:+UseStringDeduplication", \
            "-XX:+UnlockDiagnosticVMOptions", \
            "-XX:+DebugNonSafepoints", \
            "-jar", "tibot.jar"]

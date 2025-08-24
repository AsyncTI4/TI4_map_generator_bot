# ---- Build stage ----
FROM maven:3.9.8-amazoncorretto-21 AS build
WORKDIR /opt/app

# cache dependencies
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline

# add sources late for better layer reuse
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package

# ---- Runtime stage ----
FROM amazoncorretto:21-alpine
WORKDIR /app

# needed to handle fonts
RUN apk add --no-cache fontconfig ttf-dejavu

COPY --from=build /opt/app/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT.jar tibot.jar
COPY --from=build /opt/app/src/main/resources /opt/resources

ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources

ENTRYPOINT ["java", "-jar", "-XX:MaxRAMPercentage=90.0", "-XX:InitialRAMPercentage=30.0", "-XX:+UseStringDeduplication", "tibot.jar"]

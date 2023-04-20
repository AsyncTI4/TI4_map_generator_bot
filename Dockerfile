FROM amazoncorretto:18
WORKDIR /opt
RUN df -h && \
    yum install -y maven && \
    mvn --version
COPY ./src ./src
COPY pom.xml pom.xml
COPY .classpath .classpath
COPY .project .project
RUN mvn --batch-mode clean compile assembly:single && \
    mvn --batch-mode package && \
    cp $(pwd)/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar tibot.jar
COPY ./src/main/resources /opt/resources
ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources
ARG DISCORD_BOT_KEY
ARG DISCORD_USER
ARG DISCORD_SERVER
ARG DISCORD_SERVER2

ARG AWS_KEY
ARG AWS_SECRET

ENV AWS_ACCESS_KEY_ID=$AWS_KEY
ENV AWS_SECRET_ACCESS_KEY=$AWS_SECRET
ENV BOT_KEY=$DISCORD_BOT_KEY
ENV USER=$DISCORD_USER
ENV SERVER=$DISCORD_SERVER
ENV SERVER2=$DISCORD_SERVER2
# ENTRYPOINT java -Xmx1400m -jar tibot.jar $DISCORD_BOT_KEY $DISCORD_USER $DISCORD_SERVER
ENTRYPOINT ["java", "-Xmx3400m", "-jar", "tibot.jar"]

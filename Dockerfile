FROM amazoncorretto:18
WORKDIR /opt
RUN df -h
RUN yum install -y maven
COPY ./src ./src
COPY pom.xml pom.xml
COPY .classpath .classpath
COPY .project .project
RUN mvn clean compile assembly:single
RUN mvn package
RUN cp $(pwd)/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar tibot.jar
COPY ./src/main/resources /opt/resources
ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources
ENTRYPOINT java -jar tibot.jar $DISCORD_SECRET $DISCORD_SERVER $DISCORD_USER

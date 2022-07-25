FROM amazoncorretto:18
WORKDIR /opt
RUN df -h && \
    yum install -y maven
COPY ./src ./src
COPY pom.xml pom.xml
COPY .classpath .classpath
COPY .project .project
RUN mvn clean compile assembly:single && \
    mvn package && \
    cp $(pwd)/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar tibot.jar
COPY ./src/main/resources /opt/resources
ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources
# ENTRYPOINT java -Xmx1400m -jar tibot.jar
ENTRYPOINT ["tibot.jar"]
CMD ["java", "-Xmx1400m", "-jar", "tibot.jar"]

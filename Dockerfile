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
ENTRYPOINT java -Xmx1400m -jar tibot.jar "OTc4Mjk1ODgzMTUyMTA1NTUz.GyLGNo.H-cLoVqlfqHuGe4_r6mqWJ7lyFgI8SGky1Sjo4" "978295883152105553" "950413954327396362"

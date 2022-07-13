FROM amazoncorretto:18
WORKDIR /opt
ADD tibot.jar .
COPY ./src/main/resources /opt/resources
ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources
RUN ls /opt

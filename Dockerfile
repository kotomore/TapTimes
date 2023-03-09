FROM openjdk:19
ARG JAR_FILE=/target/*.jar
COPY ${JAR_FILE} 4Clients.jar
ENTRYPOINT ["nohup", "java","-jar","/4Clients.jar"]
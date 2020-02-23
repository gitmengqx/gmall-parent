FROM openjdk:8-jdk-alpine
VOLUME /tmp
COPY ./target/service-user.jar service-user.jar
ENTRYPOINT ["java","-jar","/service-user.jar", "&"]
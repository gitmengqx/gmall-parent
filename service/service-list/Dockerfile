FROM openjdk:8-jdk-alpine
VOLUME /tmp
COPY ./target/service-list.jar service-list.jar
ENTRYPOINT ["java","-jar","/service-list.jar", "&"]
FROM openjdk:8-jdk-alpine
VOLUME /tmp
COPY ./target/service-item.jar service-item.jar
ENTRYPOINT ["java","-jar","/service-item.jar", "&"]
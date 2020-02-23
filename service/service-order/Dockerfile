FROM openjdk:8-jdk-alpine
VOLUME /tmp
COPY ./target/service-order.jar service-order.jar
ENTRYPOINT ["java","-jar","/service-order.jar", "&"]
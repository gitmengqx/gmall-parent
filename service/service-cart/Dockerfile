FROM openjdk:8-jdk-alpine
VOLUME /tmp
COPY ./target/service-cart.jar service-cart.jar
ENTRYPOINT ["java","-jar","/service-cart.jar", "&"]
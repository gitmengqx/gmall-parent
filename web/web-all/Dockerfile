FROM openjdk:8-jdk-alpine
VOLUME /tmp
COPY ./target/web-all.jar web-all.jar
ENTRYPOINT ["java","-jar","/web-all.jar", "&"]
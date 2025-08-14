# syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/ip-geo-reactive-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

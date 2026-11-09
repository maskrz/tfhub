FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build
COPY pom.xml .
COPY src/main src/main
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build --chown=spring:spring /build/target/tfhub-api-*.jar /app/app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

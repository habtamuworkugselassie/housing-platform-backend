FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/target/*.jar app.jar

# Prod default upload dir: writable by spring so app.upload-dir=/var/lib/housing-platform/uploads works
RUN mkdir -p /var/lib/housing-platform/uploads && chown -R spring:spring /var/lib/housing-platform

USER spring:spring

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

# Use PORT environment variable if set (for Render.com), otherwise use default 8080
ENTRYPOINT java -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -jar app.jar

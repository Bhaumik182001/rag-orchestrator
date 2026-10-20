# Stage 1: Build the artifact
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies in a separate layer for faster rebuilds
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose standard Spring Boot port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
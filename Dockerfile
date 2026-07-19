# ---- Stage 1: build the executable jar with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first (only re-downloads when pom.xml changes)
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Then build the app
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Stage 2: small runtime image with just the JRE + the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# The host sets $PORT; the app reads it (see application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

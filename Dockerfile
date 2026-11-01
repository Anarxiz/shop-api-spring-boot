# ---- сборка ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# сначала зависимости — этот слой кэшируется, пока не изменится pom.xml
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- запуск ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

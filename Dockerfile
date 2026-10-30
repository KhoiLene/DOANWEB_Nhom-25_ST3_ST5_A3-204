FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && chown app:app /app
COPY --from=build --chown=app:app /workspace/target/DoAnWeb-0.0.1-SNAPSHOT.jar /app/app.jar
# Render Free provides 512 MB total process memory. Reserve room for native JVM,
# Tomcat, Hibernate and gRPC/Qdrant buffers instead of letting non-heap memory grow unchecked.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=42.0 -XX:InitialRAMPercentage=10.0 -XX:+UseSerialGC -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=64m -Xss512k"
ENV SPRING_DOCKER_COMPOSE_ENABLED=false
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8080
EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY libs libs
COPY services services
COPY workers/sample-catalog-worker workers/sample-catalog-worker
RUN mvn -B -pl services/control-plane -am package -DskipTests
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /workspace/services/control-plane/target/control-plane-*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]

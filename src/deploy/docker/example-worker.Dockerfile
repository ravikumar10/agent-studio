FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY workers/example-worker/Main.java dev/agentstudio/worker/Main.java
RUN javac --release 21 -d /out dev/agentstudio/worker/Main.java
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /out /app
EXPOSE 8090
ENTRYPOINT ["java","-cp","/app","dev.agentstudio.worker.Main"]

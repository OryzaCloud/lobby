FROM gradle:jdk25 AS builder
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle dependencies --no-daemon || true
COPY src ./src
COPY testdata ./testdata
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd -r oryza && useradd -r -g oryza oryza
COPY --from=builder /app/build/libs/*-all.jar /app/lobby.jar
USER oryza
EXPOSE 25580
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/lobby.jar"]

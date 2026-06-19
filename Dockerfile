FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY horarios-domain/pom.xml horarios-domain/pom.xml
COPY horarios-solver/pom.xml horarios-solver/pom.xml
COPY horarios-testkit/pom.xml horarios-testkit/pom.xml
COPY horarios-api/pom.xml horarios-api/pom.xml

COPY horarios-domain/src horarios-domain/src
COPY horarios-solver/src horarios-solver/src
COPY horarios-testkit/src horarios-testkit/src
COPY horarios-api/src horarios-api/src
RUN mvn -q -pl horarios-api -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/horarios-api/target/horarios-api-*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

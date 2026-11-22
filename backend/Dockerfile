FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
#layer caching
COPY pom.xml .
COPY --from=api openapi.yaml /api/openapi.yaml
COPY src ./src
# Persistent Maven local repository across builds (Avoid re-downloading the dependency tree every time)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp package
#lighter runtime 
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /work/target/hms-community-0.1.0-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

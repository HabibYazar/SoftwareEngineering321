FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /work
# Katman önbelleği: bağımlılıklar yalnızca pom.xml değişince yeniden indirilir.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# Testler CI'da (mvn clean verify) çalışır. İmaj derlemesi test hatalarını gizlemez:
# hatalar yok sayılmaz, testler burada hiç çalıştırılmaz.
RUN mvn -B -ntp -DskipTests package

# Daha hafif çalışma imajı; uygulama root olmayan kullanıcıyla çalışır (NFR-04).
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /work/target/hms-community-0.1.0-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

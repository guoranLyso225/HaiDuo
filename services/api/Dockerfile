FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /work
COPY services/api/pom.xml pom.xml
COPY services/api/src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package && jar --list --file target/api-0.1.0.jar > /dev/null

FROM eclipse-temurin:21.0.13_9-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269
RUN groupadd --gid 10001 haiduo && useradd --uid 10001 --gid 10001 --no-create-home haiduo && mkdir -p /app/health/cn/haiduo /data/files && chown -R 10001:10001 /app /data
WORKDIR /app
COPY --from=build /work/target/api-0.1.0.jar app.jar
COPY --from=build /work/target/classes/cn/haiduo/HealthProbe.class /app/health/cn/haiduo/HealthProbe.class
RUN test -s /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 CMD ["java","-cp","/app/health","cn.haiduo.HealthProbe"]
ENTRYPOINT ["java","-jar","/app/app.jar"]

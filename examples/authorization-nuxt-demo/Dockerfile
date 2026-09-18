# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress \
      --projects examples/authorization-nuxt-demo \
      --also-make \
      package \
      -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build --chown=10001:10001 \
  /workspace/examples/authorization-nuxt-demo/target/authorization-nuxt-demo-*.jar \
  /app/application.jar

USER 10001:10001
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/application.jar"]

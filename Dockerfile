# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

COPY --chmod=0755 gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon \
    && cp build/libs/*.jar app.jar

FROM eclipse-temurin:25-jre-noble

WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=builder --chmod=0444 /workspace/app.jar /app/app.jar

USER 10001:10001

ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

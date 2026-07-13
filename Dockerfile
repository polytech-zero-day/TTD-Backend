# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# 의존성 레이어를 소스 변경과 분리해 캐시 적중률을 높인다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test -x securityCheck

RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination extracted

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring:spring
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8080/v3/api-docs || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# Build stage
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app

# 캐시 최적화: 설정/의존성 파일 먼저 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 소스 복사
COPY src ./src

# 빌드
RUN gradle clean bootJar -x test

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
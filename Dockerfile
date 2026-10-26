FROM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205 AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS backend
WORKDIR /build/backend
COPY backend/ ./
COPY --from=frontend /build/frontend/dist/ ./src/main/resources/static/
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5
WORKDIR /app
COPY --from=backend --chown=10001:10001 /build/backend/target/pay-period-planner-api-0.0.1-SNAPSHOT.jar /app/application.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    FINANCIALS_SESSION_COOKIE_SECURE=true \
    SERVER_FORWARD_HEADERS_STRATEGY=none
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]

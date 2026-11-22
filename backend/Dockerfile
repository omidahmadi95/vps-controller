# Build from the repository root so the image contains both the React UI and Spring Boot API:
#   docker build --platform linux/amd64 -f backend/Dockerfile -t vps-controller:latest .
FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn/ .mvn/
RUN ./mvnw -B -q -DskipTests dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend /frontend/dist/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests package && mv target/*.jar app.jar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /build/app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC"
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

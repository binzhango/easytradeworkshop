FROM eclipse-temurin:21.0.8_9-jdk-alpine-3.22@sha256:89517925fa675c6c4b770bee7c44d38a7763212741b0d6fca5a5103caab21a97 AS build

WORKDIR /app

# Download Gradle dependencies first for better caching
COPY ["build.gradle", "settings.gradle", "gradlew", "./"]
COPY ["gradle", "gradle"]
RUN ./gradlew dependencies

# Copy source and build
COPY ["src", "./src"]
RUN ./gradlew test bootJar

# Runtime stage
FROM eclipse-temurin:21.0.8_9-jre-alpine-3.22@sha256:990397e0495ac088ab6ee3d949a2e97b715a134d8b96c561c5d130b3786a489d

ARG GIT_COMMIT=unspecified
ENV DT_LIVEDEBUGGER_COMMIT=$GIT_COMMIT

ARG GIT_ORIGIN=unspecified
ENV DT_LIVEDEBUGGER_REMOTE_ORIGIN=$GIT_ORIGIN

# Create non-root user
RUN addgroup --system --gid 3369 easytrade \
    && adduser --system --ingroup easytrade --uid 3369 easytrade

USER easytrade:easytrade
WORKDIR /home/easytrade

# Copy JAR from build stage
COPY --from=build --chown=easytrade:easytrade ["/app/build/libs/*.jar", "app.jar"]

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", "app.jar"]

# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom and source
COPY pom.xml .
COPY src ./src

# Build directly (no go-offline step)
RUN mvn clean package -DskipTests

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S finflow && adduser -S finflow -G finflow

# Copy built jar
COPY --from=builder /app/target/admin-service-*.jar app.jar

RUN chown finflow:finflow app.jar
USER finflow

EXPOSE 7007

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
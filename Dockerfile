# syntax=docker/dockerfile:1

############################
# Stage 1: build the JAR   #
############################
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy only the POM first and resolve dependencies.
# This layer is cached and reused as long as pom.xml does not change,
# so code-only changes do not re-download every dependency.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Now copy the sources and build (tests already ran in the Jenkins Test stage)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

############################
# Stage 2: slim runtime    #
############################
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="calculator" \
      org.opencontainers.image.description="Calculator REST API (Spring Boot)" \
      org.opencontainers.image.source="https://github.com/maxinh00000/calculator-devops"

# Run as an unprivileged user, never as root
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
# Copy ONLY the executable JAR from the build stage (no Maven, no JDK, no sources)
COPY --from=build --chown=spring:spring /workspace/target/calculator.jar app.jar

USER spring

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    SERVER_PORT=8080

EXPOSE 8080

# Image-level health check (busybox wget ships with alpine)
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# exec -> java becomes PID 1 and receives SIGTERM for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

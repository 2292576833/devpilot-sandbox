FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/target/devpilot-sandbox-0.1.0.jar .
COPY data/policy.yaml /app/policy.yaml
EXPOSE 9091

LABEL org.opencontainers.image.title="DevPilot Sandbox"
LABEL org.opencontainers.image.description="AI Agent 本地安全执行沙箱中间件"
LABEL org.opencontainers.image.version="0.1.0"
LABEL org.opencontainers.image.licenses="Apache-2.0"

HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
  CMD java -jar devpilot-sandbox-0.1.0.jar --server.port=${HEALTH_PORT:-9091} || exit 1

CMD ["java", "-jar", "devpilot-sandbox-0.1.0.jar", "--server.port=9091"]

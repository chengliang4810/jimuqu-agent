FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY src /workspace/src
COPY web /workspace/web

RUN mvn -DskipTests package \
    && cp "$(find target -maxdepth 1 -type f -name 'jimuqu-agent-*.jar' ! -name 'original-*' | head -n 1)" /tmp/jimuqu-agent.jar

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /tmp/jimuqu-agent.jar /app/jimuqu-agent.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/jimuqu-agent.jar"]

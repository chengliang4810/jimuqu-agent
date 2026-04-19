FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/jimuqu-agent-0.0.1.jar /app/jimuqu-agent.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/jimuqu-agent.jar"]

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/jimuqu-agent-0.1.0-SNAPSHOT.jar /app/jimuqu-agent.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/jimuqu-agent.jar"]

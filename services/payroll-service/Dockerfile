FROM eclipse-temurin:17.0.12_7-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/payroll-service-*.jar app.jar
USER app
EXPOSE 8086
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM eclipse-temurin:17.0.12_7-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/media-service-*.jar app.jar
RUN mkdir -p /data/media && chown app:app /data/media
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

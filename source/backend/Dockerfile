FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/defect-triage-0.0.1-SNAPSHOT.jar app.jar
COPY --from=source/frontend/dist /static /app/static

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

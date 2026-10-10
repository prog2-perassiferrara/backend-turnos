FROM eclipse-temurin:25.0.2_10-jdk-noble AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src/main/ src/main/
RUN ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:25.0.2_10-jre-noble
WORKDIR /app
COPY --from=build /workspace/target/appointment-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

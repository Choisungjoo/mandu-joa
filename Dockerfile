FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode --quiet -DskipTests dependency:go-offline

COPY frontend ./frontend
COPY src ./src
# Also builds the React status screen (frontend-maven-plugin downloads its own Node).
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/target/helloworld-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

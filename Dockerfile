FROM maven:3.8.8-eclipse-temurin-8 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package -Pprod

FROM eclipse-temurin:8-jre

WORKDIR /app
COPY --from=build /app/target/gamestudio_robert_fedorco.war app.war

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

CMD ["java", "-jar", "app.war", "--spring.profiles.active=prod"]

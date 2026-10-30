# Local/demo image: the same WAR that production deploys, on the same Tomcat line.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src src
RUN mvn -B -ntp -q package -DskipTests

FROM tomcat:10.1-jdk21-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/target/registry.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml pom.xml
COPY shared-kernel/pom.xml shared-kernel/pom.xml
COPY account-domain/pom.xml account-domain/pom.xml
COPY account-application/pom.xml account-application/pom.xml
COPY account-infrastructure/pom.xml account-infrastructure/pom.xml
COPY account-service/pom.xml account-service/pom.xml

RUN mvn -q -pl account-service -am dependency:go-offline

COPY shared-kernel/src shared-kernel/src
COPY account-domain/src account-domain/src
COPY account-application/src account-application/src
COPY account-infrastructure/src account-infrastructure/src
COPY account-service/src account-service/src

RUN mvn -q -pl account-service -am package -DskipTests

FROM eclipse-temurin:21-jre

ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

WORKDIR /app

COPY --from=build /workspace/account-service/target/account-service-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY common/ common/
COPY wallet/pom.xml wallet/pom.xml
COPY wallet/src/main/ wallet/src/main/
COPY simulator/pom.xml simulator/pom.xml
RUN mvn -B -q -pl wallet -am -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /build/wallet/target/*.jar wallet.jar
USER 10001
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "wallet.jar"]

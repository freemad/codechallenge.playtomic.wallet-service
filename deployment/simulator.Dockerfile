FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY common/ common/
COPY wallet/pom.xml wallet/pom.xml
COPY simulator/pom.xml simulator/pom.xml
COPY simulator/src/main/ simulator/src/main/
RUN mvn -B -q -pl simulator -am -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /build/simulator/target/*.jar simulator.jar
USER 10001
EXPOSE 9999
ENTRYPOINT ["java", "-jar", "simulator.jar"]
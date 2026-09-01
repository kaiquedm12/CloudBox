# Build a partir da raiz do repositorio (localmente ou no Railway):
# docker build -f cloudbox-master/Dockerfile -t cloudbox-master .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY cloudbox-master/pom.xml ./cloudbox-master/pom.xml
COPY cloudbox-agent/pom.xml ./cloudbox-agent/pom.xml
COPY cloudbox-master/src ./cloudbox-master/src

RUN --mount=type=cache,id=maven-repository,target=/root/.m2 \
    mvn -pl cloudbox-master -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S cloudbox && adduser -S cloudbox -G cloudbox
COPY --from=build --chown=cloudbox:cloudbox \
    /app/cloudbox-master/target/cloudbox-master-0.1.0-SNAPSHOT.jar app.jar

USER cloudbox
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

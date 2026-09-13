FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY . .

RUN chmod +x mvnw \
    && ./mvnw -pl cloudbox-master -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S cloudbox \
    && adduser -S cloudbox -G cloudbox

WORKDIR /app

COPY --from=build --chown=cloudbox:cloudbox \
    /workspace/cloudbox-master/target/cloudbox-master-*.jar \
    /app/cloudbox-master.jar

USER cloudbox

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/cloudbox-master.jar"]

# cloudbox-agent
#
# Empacota o agente como imagem Docker pronta para ser publicada em um
# registry (Docker Hub ou GitHub Container Registry) e instalada nas maquinas
# do cluster via "docker run".
#
# Em producao, o container deve ser executado montando o socket do Docker
# e o filesystem do host para acessar o Docker daemon e o disco local:
#
#   docker run -d \
#     -v /var/run/docker.sock:/var/run/docker.sock \
#     -v /:/host/rootfs:ro \
#     <registry>/cloudbox-agent:<tag>
#
# A coleta de metricas e a integracao com o Docker Engine usam os recursos
# montados do host.
#
# Build a partir da raiz do repositorio:
#   docker build -f cloudbox-agent/Dockerfile -t cloudbox-agent .

# ---- stage build: compila o agente com Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY cloudbox-agent/pom.xml ./cloudbox-agent/pom.xml
COPY cloudbox-agent/src ./cloudbox-agent/src

RUN mvn -pl cloudbox-agent -am package -DskipTests

# ---- stage runtime: imagem final enxuta com o JAR ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/cloudbox-agent/target/cloudbox-agent-0.1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

# cloudbox-agent
#
# Empacota o agente como imagem Docker pronta para ser publicada em um
# registry (Docker Hub ou GitHub Container Registry) e instalada nas maquinas
# do cluster via "docker run".
#
# Execute com o socket do Docker montado. Consulte docs/instalar-agente.md
# para docker run e os campos de Optional settings no Docker Desktop.
#
#   docker run -d \
#     -v /var/run/docker.sock:/var/run/docker.sock \
#     -e AGENT_ADVERTISE_ADDRESS=192.168.1.50 \
#     <registry>/cloudbox-agent:<tag>
#
# No Docker Desktop, as metricas refletem o ambiente Linux da VM do Docker.
#
# Build a partir da raiz do repositorio:
#   docker build -f cloudbox-agent/Dockerfile -t cloudbox-agent .

# ---- stage build: compila o agente com Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY cloudbox-agent/pom.xml ./cloudbox-agent/pom.xml
COPY cloudbox-master/pom.xml ./cloudbox-master/pom.xml
COPY cloudbox-agent/src ./cloudbox-agent/src

RUN mvn -pl cloudbox-agent -am package -DskipTests

# ---- stage runtime: imagem final enxuta com o JAR ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG AGENT_VERSION=dev
LABEL org.opencontainers.image.title="CloudBox Agent" \
      org.opencontainers.image.description="Agente de execucao e monitoramento do cluster CloudBox" \
      org.opencontainers.image.source="https://github.com/kaiquedm12/CloudBox" \
      org.opencontainers.image.version="${AGENT_VERSION}"

# O curinga desacopla a imagem da versao Maven usada no nome do artefato.
COPY --from=build /app/cloudbox-agent/target/cloudbox-agent-*.jar app.jar

# Em bridge, a deteccao encontra o IP interno do container. Informe o IP do
# computador; com Docker Engine e --network host, a deteccao pode ser habilitada.
ENV DOCKER_HOST=unix:///var/run/docker.sock \
    AGENT_AUTO_DETECT_ADVERTISE_ADDRESS=false

ENTRYPOINT ["java", "-jar", "app.jar"]

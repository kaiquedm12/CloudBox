# Relatório de implementação do CloudBox Agent

## Atualização de andamento — 14/09/2026

A equipe relatou a conclusão do primeiro fluxo integrado com o **2048**: cadastro do computador pelo agente, envio de recursos, visualização do nó no painel, solicitação e execução do container pelo Docker e abertura do jogo em outro navegador por um endereço de acesso. O projeto passa a ter uma demonstração funcional integrada, além das verificações isoladas dos módulos.

Esta revisão documental confrontou esse relato com os arquivos atuais; não executou novamente a demonstração nem as suítes automatizadas. Contagens de testes e resultados anteriores abaixo são registros históricos, não resultados desta revisão.

A geração de endereço observada no teste ainda precisa ser vinculada à configuração/versão utilizada: `ContainerExecutionService.runContainer` configura limites de CPU e RAM, mas não publica portas; os DTOs do master não possuem URL e `ContainerList` não exibe link para a aplicação. Imagem/tag, portas, URL e origem do acesso não foram informadas. Portanto, o sucesso manual relatado está registrado, mas essa etapa de rede ainda não é reproduzível somente com este checkout.

O procedimento atualizado de instalação, login, execução e diagnóstico está no [README principal](README.md#como-rodar-localmente).

## 1. Objetivo

Este documento registra o trabalho realizado no módulo `cloudbox-agent`, explica como executar o ambiente local e apresenta formas de verificar o funcionamento da coleta de métricas, do registro no orquestrador do heartbeat e da execução de containers Docker comandada pelo master.

## 2. Estado inicial e configuração do módulo

O módulo `cloudbox-agent` já estava declarado no `pom.xml` pai e já possuía a classe principal `CloudboxAgentApplication` e o plugin do Spring Boot.

Foram adicionadas ao POM do agente:

- OSHI 6.6.5, para acesso às informações de hardware e do sistema operacional;
- docker-java e transporte Zerodep, conforme o POM e `DockerClientFactory`;
- starter REST Client do Spring Boot, usado na comunicação com o master;
- starter de testes do Spring Boot.

Também foram criados os pacotes `metrics`, `registration`, `docker`, `command`, `client` e `config`. O Maven Wrapper recebeu permissão de execução.

## 3. Coleta local de métricas

O `MetricsCollector` usa OSHI para coletar:

- percentual de CPU livre;
- memória RAM livre e total, em bytes;
- espaço livre e total dos sistemas de arquivos locais, em bytes;
- temperatura da CPU, em graus Celsius.

Os valores são transportados internamente pelo record `SystemMetrics`. Temperaturas iguais a zero, negativas, `NaN` ou infinitas são interpretadas como indisponíveis e representadas por `null`.

O `MetricsConsoleReporter` imprime as métricas no console, por padrão, cinco segundos após cada coleta. A primeira coleta ocorre após um segundo.

Exemplo observado nesta máquina:

```text
Metricas locais | CPU livre: 90.2% | RAM livre/total: 1.39 GiB / 5.72 GiB | Disco livre/total: 196.14 GiB / 233.68 GiB | Temperatura: 53.0 °C
```

Quando o sensor não está disponível:

```text
Temperatura: indisponivel
```

## 4. Comunicação com o cloudbox-master

O formato do JSON foi alinhado com os DTOs e endpoints existentes no `cloudbox-master`.

### Registro

O agente envia `POST /api/nodes/register` com:

```json
{
  "name": "cloudbox-agent",
  "cpuTotal": 8,
  "ramTotalMb": 5859,
  "diskTotalMb": 239287
}
```

O master responde com `id` e `token`. O `NodeRegistrationService` realiza esse registro quando a aplicação fica pronta. Caso o master esteja indisponível, o agente continua rodando e tenta registrar novamente no próximo heartbeat.

### Persistência da identificação

O `AgentTokenStorage` grava o `nodeId` e o token, por padrão, em:

```text
~/.cloudbox/agent-credentials.properties
```

O construtor usado pelo Spring foi marcado explicitamente para evitar ambiguidade de injeção no Spring Framework 7.

Durante a revisão para validação em máquinas físicas foi identificado que o arquivo é gravado e pode ser lido pela classe de armazenamento, mas o `NodeRegistrationService` ainda não chama `load()` ao iniciar. Por isso, uma reinicialização do agente registra atualmente um novo nó no master e pode deixar o registro anterior como duplicado/offline. Esse comportamento deve ser corrigido antes da avaliação prolongada.

### Heartbeat

O `HeartbeatScheduler` envia, por padrão a cada dez segundos, `POST /api/nodes/{id}/heartbeat` com:

```json
{
  "cpuFree": 6.66,
  "ramFreeMb": 1177,
  "diskFreeMb": 200740,
  "temperatureCelsius": 53.00
}
```

O token é enviado no header `Authorization: Bearer <token>`. A CPU livre é convertida do percentual medido para a quantidade equivalente de processadores lógicos livres. RAM e disco são convertidos de bytes para MB.

O master atualiza `lastHeartbeat` e define o status do nó como `ONLINE`. Sua configuração atual marca como `OFFLINE` os nós sem heartbeat por mais de 30 segundos.

## 5. Configurações do agente

As configurações ficam em `cloudbox-agent/src/main/resources/application.yml` e podem ser sobrescritas por variáveis de ambiente:

| Variável | Padrão | Finalidade |
|---|---:|---|
| `CLOUDBOX_MASTER_URL` | `https://cloudbox-production-55f7.up.railway.app/` | URL do orquestrador |
| `AGENT_NAME` | hostname ou `cloudbox-agent` | Nome do nó |
| `AGENT_TOKEN_FILE` | `~/.cloudbox/agent-credentials.properties` | Arquivo de credenciais |
| `AGENT_HEARTBEAT_INTERVAL` | `10000` | Intervalo do heartbeat em ms |
| `AGENT_HEARTBEAT_INITIAL_DELAY` | `2000` | Espera inicial do heartbeat em ms |
| `AGENT_COMMAND_POLL_INTERVAL` | `5000` | Intervalo de consulta de comandos em ms |
| `AGENT_COMMAND_POLL_INITIAL_DELAY` | `3000` | Espera inicial da consulta em ms |
| `DOCKER_HOST` | Automático por sistema operacional | Socket Unix ou named pipe Windows; pode ser sobrescrito |
| `DOCKER_CONNECTION_TIMEOUT` / `DOCKER_RESPONSE_TIMEOUT` | `5s` / `30s` | Timeouts do cliente Docker |
| `DOCKER_VALIDATION_ENABLED` | `false` | Habilita o runner de validação manual Docker |
| `AGENT_METRICS_INTERVAL` | `5000` | Intervalo do log de métricas em ms |
| `AGENT_METRICS_INITIAL_DELAY` | `1000` | Espera inicial das métricas em ms |

## 6. Como executar o ambiente completo

Todos os comandos desta seção partem da raiz do repositório `CloudBox`.

### 6.1. Iniciar o PostgreSQL

```bash
sudo docker compose up -d postgres
sudo docker compose ps
```

Verificar se o banco aceita conexões:

```bash
sudo docker compose exec postgres pg_isready -U cloudbox -d cloudbox
```

A saída deve conter `accepting connections`.

### 6.2. Executar o master

Em um terminal:

```bash
./mvnw -pl cloudbox-master spring-boot:run
```

O master está pronto quando aparecem mensagens equivalentes a:

```text
Tomcat started on port 8080
Started CloudboxMasterApplication
```

O endpoint de saúde pode ser consultado com:

```bash
curl http://localhost:8080/actuator/health
```

### 6.3. Executar o agente

Em outro terminal:

```bash
CLOUDBOX_MASTER_URL=http://localhost:8080 ./mvnw -pl cloudbox-agent spring-boot:run
```

As mensagens esperadas incluem:

```text
Agente registrado no orquestrador com nodeId=...
Heartbeat enviado para o nodeId=...
Metricas locais | CPU livre: ...
```

## 7. Como conferir a integração

Obtenha `TOKEN` pelo login descrito no [README](README.md#consultar-a-api-diretamente); as consultas abaixo exigem JWT de usuário.

Obtenha primeiro o JWT de usuário em `TOKEN`, seguindo [Consultar a API diretamente](README.md#consultar-a-api-diretamente). Consultar os nós registrados:

```bash
curl -s http://localhost:8080/api/nodes -H "Authorization: Bearer $TOKEN" | jq
```

Sem `jq`:

```bash
curl http://localhost:8080/api/nodes -H "Authorization: Bearer $TOKEN"
```

Resultado real obtido durante a validação:

```json
[
  {
    "id": "6da9f279-3f4f-4777-825e-d20fe80d77ca",
    "name": "cloudbox-agent",
    "status": "ONLINE",
    "cpuTotal": 8.00,
    "cpuFree": 6.66,
    "ramTotalMb": 5859,
    "ramFreeMb": 1177,
    "diskTotalMb": 239287,
    "diskFreeMb": 200740,
    "temperatureCelsius": 53.00,
    "lastHeartbeat": "2026-08-18T14:46:48.807250Z"
  }
]
```

Para confirmar a permanência como `ONLINE`, execute a consulta novamente depois de dez segundos. `lastHeartbeat` deve mudar e `status` deve continuar como `ONLINE`.

Para verificar a transição para `OFFLINE`, encerre o agente com `Ctrl+C`, aguarde mais de 30 segundos e consulte novamente. Esse teste interrompe temporariamente o critério de disponibilidade e deve ser usado apenas para validar o timeout.

## 8. Testes automatizados

Executar somente os testes do agente e seus módulos necessários:

```bash
./mvnw -pl cloudbox-agent -am test
```

No registro anterior de validação foram aprovados 14 testes no módulo do agente, incluindo:

- normalização da temperatura indisponível;
- limites do percentual de CPU;
- conversão de bytes para MB;
- gravação e leitura das credenciais;
- contrato HTTP e JSON de registro;
- contrato HTTP e JSON de heartbeat;
- envio do Bearer token.
- consulta autenticada de comandos pendentes;
- execução e confirmação de comandos `START`, `STOP` e `REMOVE`;
- nova tentativa de confirmação sem iniciar ou remover o mesmo container novamente;
- tratamento de falhas da Docker Engine e do master.

Resultado:

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 9. Problemas encontrados e resoluções

### Maven Wrapper sem permissão

Foi adicionada permissão de execução ao arquivo `mvnw`.

### Docker instalado parcialmente

Inicialmente somente o cliente Docker estava disponível, sem `dockerd` ou `docker.service`. O Docker Engine e os plugins necessários precisaram ser instalados e iniciados.

### Porta 5432 ocupada

Outra instância do PostgreSQL estava usando a porta publicada pelo Compose. A instância conflitante precisou ser parada antes de iniciar `cloudbox-postgres`.

### Falha de autenticação do PostgreSQL

O master recebeu `password authentication failed for user "cloudbox"`. A instância correta do Compose foi iniciada com banco, usuário e senha `cloudbox`, conforme `docker-compose.yml`.

### Construtor do AgentTokenStorage

O Spring encontrou dois construtores e tentou usar um construtor vazio inexistente. O construtor de produção foi marcado com `@Autowired`.

### Ciclo de vida dos containers

O contrato de comandos pendentes passou a informar uma ação explícita (`START`, `STOP` ou `REMOVE`) e, nas ações destrutivas, o identificador real do container na Docker Engine. O `PendingCommandPoller` chama `stopContainer` ou `removeContainer` e reporta `STOPPED` ou `REMOVED` ao master.

Para tolerar uma indisponibilidade temporária do master após a operação Docker, o agente mantém em memória as ações concluídas que aguardam confirmação. Nos ciclos seguintes ele repete somente o reporte de status, evitando executar novamente a operação local.

## 10. Situação dos critérios de aceite

### Coleta de métricas

- Métricas reais e coerentes foram confirmadas nesta máquina.
- O tratamento de temperatura indisponível está coberto por teste automatizado.
- A execução em uma segunda máquina física, exigida pelo critério original dessa etapa, ainda precisa ser documentada.

### Registro e heartbeat

Critério atendido:

- o agente se registrou no master;
- apareceu como `ONLINE` em `/api/nodes`;
- enviou recursos reais da máquina;
- `lastHeartbeat` foi preenchido e é atualizado enquanto o agente roda;
- o intervalo de 10 segundos permanece abaixo do timeout de 30 segundos do master.

### Validação prolongada em máquinas físicas

Critério ainda não atendido:

- a execução automatizada neste workspace não substitui a instalação em hardware distinto;
- ainda faltam logs contínuos de várias horas em pelo menos duas máquinas físicas;
- não há evidência suficiente para afirmar ausência de quedas em ambiente externo ao desenvolvimento;
- a duplicação de nós após reinício, descrita na seção 4, deve ser registrada na avaliação experimental e corrigida antes da rodada definitiva.

Para a avaliação experimental devem ser preservados, em cada máquina, sistema operacional, arquitetura, horário inicial/final, logs do agente, reinicializações, falhas de rede e amostras de `lastHeartbeat` obtidas no master.

## 11. Execução do 2048 e próximos passos

O agente é responsável por baixar a imagem, criar o container com nome `cloudbox-<id da solicitação>`, aplicar limites de CPU/RAM, iniciá-lo e reportar o identificador Docker ao master. Essa execução real foi confirmada pelo relato do 2048.

O ciclo atual consulta comandos a cada 5 s (com espera inicial de 3 s). A confirmação pendente é guardada em memória para evitar repetir uma operação já concluída durante indisponibilidade temporária do master; esse controle não sobrevive a um reinício do agente. Não há monitoramento contínuo do estado de cada container nesse poller: `RUNNING` resulta da confirmação de início e não de um teste HTTP do jogo.

A publicação de portas e o endereço da demonstração ainda precisam ser recuperados. Permanecem pendentes a recuperação da identidade na inicialização, testes prolongados em pelo menos duas máquinas e evidências manuais de parada e remoção. O guia operacional atualizado está no [README](README.md#como-rodar-localmente).

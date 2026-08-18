# Relatório de implementação do CloudBox Agent

## 1. Objetivo

Este documento registra o trabalho realizado no módulo `cloudbox-agent`, explica como executar o ambiente local e apresenta formas de verificar o funcionamento da coleta de métricas, do registro no orquestrador e do heartbeat.

## 2. Estado inicial e configuração do módulo

O módulo `cloudbox-agent` já estava declarado no `pom.xml` pai e já possuía a classe principal `CloudboxAgentApplication` e o plugin do Spring Boot.

Foram adicionadas ao POM do agente:

- OSHI 6.6.5, para acesso às informações de hardware e do sistema operacional;
- docker-java 3.4.1 e seu transporte HTTP Client 5;
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
| `CLOUDBOX_MASTER_URL` | `http://localhost:8080` | URL do orquestrador |
| `AGENT_NAME` | hostname ou `cloudbox-agent` | Nome do nó |
| `AGENT_TOKEN_FILE` | `~/.cloudbox/agent-credentials.properties` | Arquivo de credenciais |
| `AGENT_HEARTBEAT_INTERVAL` | `10000` | Intervalo do heartbeat em ms |
| `AGENT_HEARTBEAT_INITIAL_DELAY` | `2000` | Espera inicial do heartbeat em ms |
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
./mvnw -pl cloudbox-agent spring-boot:run
```

As mensagens esperadas incluem:

```text
Agente registrado no orquestrador com nodeId=...
Heartbeat enviado para o nodeId=...
Metricas locais | CPU livre: ...
```

## 7. Como conferir a integração

Consultar os nós registrados:

```bash
curl -s http://localhost:8080/api/nodes | jq
```

Sem `jq`:

```bash
curl http://localhost:8080/api/nodes
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

Na última execução foram aprovados sete testes:

- normalização da temperatura indisponível;
- limites do percentual de CPU;
- conversão de bytes para MB;
- gravação e leitura das credenciais;
- contrato HTTP e JSON de registro;
- contrato HTTP e JSON de heartbeat;
- envio do Bearer token.

Resultado:

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
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


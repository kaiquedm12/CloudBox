# Relatório de implementação do CloudBox Master

## 1. Objetivo

Este documento registra o trabalho realizado no módulo `cloudbox-master`, explica como executar o ambiente local e apresenta formas de verificar o funcionamento do registro de nós, do heartbeat, do algoritmo de agendamento e do cadastro de solicitações de containers.

## 2. Estado inicial e configuração do módulo

O módulo `cloudbox-master` foi criado junto com a estrutura de três módulos do repositório e evoluiu por fases, do setup da infraestrutura base até o algoritmo de agendamento.

O POM do master (`cloudbox-master/pom.xml`) inclui:

- spring-boot-starter-web, para a API REST;
- spring-boot-starter-data-jpa, para persistência;
- PostgreSQL (runtime), como banco;
- Flyway e flyway-database-postgresql, para migrações versionadas;
- spring-boot-starter-validation, para validação dos DTOs;
- spring-boot-starter-actuator, para o endpoint de saúde;
- spring-boot-starter-test, para os testes.

Foram criados os pacotes `node`, `container`, `scheduler`, `common` e os DTOs correspondentes.

## 3. Modelagem de dados e migrations

O schema do banco `cloudbox` é versionado pelo Flyway em `cloudbox-master/src/main/resources/db/migration`:

- `V1__enable_pgcrypto.sql` — habilita a extensão `pgcrypto`, usada pelo `gen_random_uuid()`;
- `V2__create_nodes_table.sql` — cria a tabela `nodes`;
- `V3__create_container_instances_table.sql` — cria a tabela `container_instances`;
- `V4__add_node_id_to_container_instances.sql` — adiciona `node_id` em `container_instances`, com índice.

A entidade `Node` (`node/Node.java`) mapeia a tabela `nodes`: id UUID gerado pelo banco, nome, token único, status (`ONLINE`/`OFFLINE`), CPU/RAM/disco totais e livres, temperatura e `lastHeartbeat`. A entidade `ContainerInstance` (`container/ContainerInstance.java`) mapeia `container_instances`: imagem, CPU, memória, disco, status e timestamps preenchidos automaticamente por `@PrePersist`/`@PreUpdate`.

A configuração usa `ddl-auto: validate`, ou seja, o Hibernate apenas valida o schema contra as entidades, sem alterá-lo. O schema é criado exclusivamente pelas migrations.

## 4. Registro de nós

O agente envia `POST /api/nodes/register` com:

```json
{
  "name": "cloudbox-agent",
  "cpuTotal": 8,
  "ramTotalMb": 5859,
  "diskTotalMb": 239287
}
```

O `NodeService.registerNode` cria o nó, gera um token UUID aleatório, define status `OFFLINE`, inicializa os recursos livres como iguais aos totais e responde `201 Created` com `id` e `token`:

```json
{
  "id": "6da9f279-3f4f-4777-825e-d20fe80d77ca",
  "token": "3f5f4a8c-..."
}
```

## 5. Heartbeat e status ONLINE

O agente envia `POST /api/nodes/{id}/heartbeat` com:

```json
{
  "cpuFree": 6.66,
  "ramFreeMb": 1177,
  "diskFreeMb": 200740,
  "temperatureCelsius": 53.00
}
```

O `NodeService.receiveHeartbeat` atualiza CPU, RAM e disco livres, a temperatura, define `lastHeartbeat` e o status `ONLINE`, respondendo `204 No Content`. Para um id inexistente, o `ResourceNotFoundException` é convertido em `404` pelo `GlobalExceptionHandler`.

## 6. Timeout de heartbeat

O `NodeHeartbeatMonitor` roda a cada `cloudbox.heartbeat.check-interval-seconds` (padrão: 10 s) e marca como `OFFLINE` os nós `ONLINE` cujo `lastHeartbeat` é anterior a `cloudbox.heartbeat.timeout-seconds` (padrão: 30 s). Assim, um nó que para de reportar deixa automaticamente de ser candidato ao agendamento.

## 7. Algoritmo de agendamento

O agendador (`SchedulerService.schedule(cpuRequested, ramRequestedMb)`) opera em três etapas:

1. **Filtrar** (`NodeCandidateFilter`): mantém apenas nós `ONLINE`, com CPU livre ≥ solicitada, RAM livre ≥ solicitada e temperatura abaixo de `cloudbox.scheduler.max-temperature-celsius` (padrão: 75 °C). Nós sem sensor de temperatura são aceitos.
2. **Pontuar** (`NodeScoringStrategy`): calcula, para cada candidato, a folga relativa de RAM e CPU (folga dividida pelo total do recurso) e soma as duas.
3. **Alocar**: escolhe o nó com maior pontuação, com desempate pela RAM livre restante e depois pela CPU livre restante.

Exemplo: para um pedido de 1 CPU e 1024 MB, entre um nó com 4 CPUs/8 GB, um com 8 CPUs/16 GB e um com 2 CPUs/2 GB livres, o agendador escolhe o de 8 CPUs/16 GB.

## 8. Cadastro de solicitações de containers

`POST /api/containers` recebe:

```json
{
  "imageName": "nginx:1.27",
  "cpuCores": 1,
  "memoryMb": 512,
  "diskMb": 128
}
```

O `ContainerService.create` consulta o agendador. Havendo nó candidato, registra a `ContainerInstance` com status `PENDING` e `nodeId` escolhido, respondendo `201 Created`. Sem nó disponível, responde `409 Conflict` com:

```json
{
  "error": "Nenhum nó disponível com recursos suficientes no momento"
}
```

`GET /api/containers` lista as solicitações cadastradas.

## 9. Configurações do master

As configurações ficam em `cloudbox-master/src/main/resources/application.yml`:

| Propriedade | Padrão | Finalidade |
|---|---:|---|
| `server.port` | `8080` | Porta HTTP do orquestrador |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/cloudbox` | URL do banco |
| `spring.datasource.username` / `password` | `cloudbox` / `cloudbox` | Credenciais do banco |
| `cloudbox.heartbeat.timeout-seconds` | `30` | Tempo sem heartbeat para marcar OFFLINE |
| `cloudbox.heartbeat.check-interval-seconds` | `10` | Intervalo da verificação de timeout |
| `cloudbox.scheduler.max-temperature-celsius` | `75` | Temperatura máxima aceita pelo filtro |

## 10. Como executar o ambiente completo

Todos os comandos desta seção partem da raiz do repositório `CloudBox`.

### 10.1. Iniciar o PostgreSQL

```bash
sudo docker compose up -d postgres
sudo docker compose ps
```

Verificar se o banco aceita conexões:

```bash
sudo docker compose exec postgres pg_isready -U cloudbox -d cloudbox
```

A saída deve conter `accepting connections`.

### 10.2. Executar o master

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

### 10.3. Executar o agente

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

## 11. Como conferir a integração

Consultar os nós registrados:

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

Para confirmar a permanência como `ONLINE`, consulte novamente depois de dez segundos: `lastHeartbeat` deve mudar e `status` deve continuar `ONLINE`. Para verificar a transição para `OFFLINE`, encerre o agente com `Ctrl+C`, aguarde mais de 30 segundos e consulte novamente.

Para validar o agendamento, envie uma solicitação de container:

```bash
curl -X POST http://localhost:8080/api/containers \
  -H "Content-Type: application/json" \
  -d '{"imageName":"nginx:1.27","cpuCores":1,"memoryMb":512,"diskMb":128}'
```

Deve retornar `201 Created` com o id da solicitação e o `nodeId` escolhido. A lista pode ser consultada em:

```bash
curl http://localhost:8080/api/containers
```

## 12. Testes automatizados

Executar somente os testes do master e seus módulos necessários:

```bash
./mvnw -pl cloudbox-master -am test
```

Foram aprovados dez testes:

- escolhe o nó com mais recursos livres (scheduler);
- retorna vazio quando nenhum nó tem recursos suficientes;
- não agenda em nó OFFLINE mesmo com recursos;
- não agenda em nó acima da temperatura limite;
- escolhe o nó com maior folga de recursos;
- desempate pela RAM livre restante;
- lista vazia para candidatos vazios;
- mantém apenas nós ONLINE com recursos suficientes;
- rejeita nó cuja temperatura excede o limite;
- aceita nó sem sensor de temperatura.

Resultado:

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 13. Problemas encontrados e resoluções

### Overflow de int nas subtrações do NodeScoringStrategy

As subtrações de folga de RAM e CPU podiam estourar o limite de `int`. As folgas foram convertidas para `long`/`BigDecimal` antes das subtrações, evitando overflow.

### Dependência da extensão pgcrypto

A geração de UUID no banco depende da extensão `pgcrypto`, habilitada pela primeira migration (`V1__enable_pgcrypto.sql`), que precisa rodar antes da criação das tabelas.

## 14. Situação dos critérios de aceite

### Registro e heartbeat

- o master recebe o registro do agente e devolve `id` e `token`;
- o nó aparece em `GET /api/nodes` e fica `ONLINE` após o primeiro heartbeat;
- `lastHeartbeat` é preenchido e atualizado enquanto o agente roda;
- o monitor marca como `OFFLINE` os nós sem heartbeat por mais de 30 segundos.

### Agendamento e containers

- o agendador filtra nós offline, sem recursos suficientes ou acima da temperatura limite;
- a escolha usa a folga relativa de RAM e CPU (estratégia "most available resources");
- `POST /api/containers` registra a solicitação com o `nodeId` escolhido e `PENDING`;
- sem nó disponível, a API responde `409 Conflict` com mensagem explicativa;
- a integração com a execução real do container pelo agente (docker-java) ainda precisa ser validada.
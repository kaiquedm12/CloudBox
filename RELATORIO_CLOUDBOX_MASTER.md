# Relatório de implementação do CloudBox Master

## Atualização de andamento — 14/09/2026

A equipe relatou a conclusão do primeiro fluxo integrado com o **2048**: cadastro do computador pelo agente, envio de recursos, visualização do nó no painel, solicitação e execução do container pelo Docker e abertura do jogo em outro navegador por um endereço de acesso. O projeto passa a ter uma demonstração funcional integrada, além das verificações isoladas dos módulos.

Esta revisão documental confrontou esse relato com os arquivos atuais; não executou novamente a demonstração nem as suítes automatizadas. Contagens de testes e resultados anteriores abaixo são registros históricos, não resultados desta revisão.

A geração de endereço observada no teste ainda precisa ser vinculada à configuração/versão utilizada: `ContainerExecutionService.runContainer` configura limites de CPU e RAM, mas não publica portas; os DTOs do master não possuem URL e `ContainerList` não exibe link para a aplicação. Imagem/tag, portas, URL e origem do acesso não foram informadas. Portanto, o sucesso manual relatado está registrado, mas essa etapa de rede ainda não é reproduzível somente com este checkout.

O procedimento atualizado de instalação, login, execução e diagnóstico está no [README principal](README.md#como-rodar-localmente).

## 1. Objetivo

Este documento registra o trabalho realizado no módulo `cloudbox-master`, explica como executar o ambiente local e apresenta formas de verificar o funcionamento do registro de nós, do heartbeat, do algoritmo de agendamento e do cadastro de solicitações de containers.

## 2. Estado inicial e configuração do módulo

O módulo `cloudbox-master` foi criado junto com a estrutura de três módulos do repositório e evoluiu por fases, do setup da infraestrutura base até o agendamento, a execução assíncrona via agente, a autenticação e a atualização do painel.

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
- `V4__add_node_id_to_container_instances.sql` — adiciona `node_id` em `container_instances`, com índice;
- `V5__add_docker_and_error_to_container_instances.sql` — armazena o identificador Docker e a mensagem de erro;
- `V6__create_users_table.sql` — cria os usuários para autenticação;
- `V7__create_default_admin_user.sql` — cria o administrador inicial quando o e-mail ainda não existe.

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

O agendador (`SchedulerService.schedule(cpuRequested, ramRequestedMb, diskRequestedMb)`) opera em três etapas:

1. **Filtrar** (`NodeCandidateFilter`): mantém apenas nós `ONLINE`, com CPU livre ≥ solicitada, RAM livre ≥ solicitada, disco livre ≥ solicitado e temperatura abaixo de `cloudbox.scheduler.max-temperature-celsius` (padrão: 75 °C). Nós sem sensor de temperatura são aceitos.
2. **Pontuar** (`NodeScoringStrategy`): calcula, para cada candidato, a folga relativa de RAM, CPU e disco (folga dividida pelo total do recurso) após descontar o pedido e soma as três parcelas.
3. **Alocar**: escolhe o nó com maior pontuação, com desempate pela RAM livre restante e depois pela CPU livre restante.

A pontuação usa a proporção de recursos restantes em relação ao total de cada nó; ter maior capacidade absoluta não garante a maior pontuação.

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
  "error": "Nenhum nó disponível com CPU, RAM e disco suficientes no momento"
}
```

O agente busca `GET /api/nodes/{id}/pending-commands` com seu token e executa `START`. Depois envia `POST /api/containers/{id}/status`, com `RUNNING` e `dockerContainerId`, ou `ERROR` e `errorMessage`. Essa confirmação fecha a etapa que antes era descrita apenas como cadastro de solicitação.

Registro de nó é público; heartbeat, comandos e reporte de status validam o token do agente e sua associação ao nó/container. As rotas de usuário exigem JWT obtido em `POST /api/auth/login`.

`GET /api/containers` lista as solicitações cadastradas e é consumido pelo dashboard.

O ciclo de vida passou a incluir ações assíncronas:

- `POST /api/containers/{id}/stop` aceita um container `RUNNING` e altera seu estado para `STOPPING`;
- `DELETE /api/containers/{id}` solicita a remoção e altera o estado para `REMOVING` quando existe um container Docker associado;
- o agente consulta comandos com ação `START`, `STOP` ou `REMOVE` e confirma o resultado pelo endpoint de status;
- as confirmações finais usam `RUNNING`, `STOPPED`, `REMOVED` ou `ERROR`;
- cada transição é publicada no WebSocket `/ws/cluster-status` para atualização imediata do dashboard.

Os registros removidos permanecem no banco com status `REMOVED`, preservando histórico para auditoria e avaliação experimental. A remoção refere-se ao container existente na Docker Engine, não ao apagamento do registro histórico.

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
CLOUDBOX_MASTER_URL=http://localhost:8080 ./mvnw -pl cloudbox-agent spring-boot:run
```

As mensagens esperadas incluem:

```text
Agente registrado no orquestrador com nodeId=...
Heartbeat enviado para o nodeId=...
Metricas locais | CPU livre: ...
```

## 11. Como conferir a integração

Obtenha primeiro o JWT de usuário em `TOKEN`, seguindo [Consultar a API diretamente](README.md#consultar-a-api-diretamente). Consultar os nós registrados:

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

Para confirmar a permanência como `ONLINE`, consulte novamente depois de dez segundos: `lastHeartbeat` deve mudar e `status` deve continuar `ONLINE`. Para verificar a transição para `OFFLINE`, encerre o agente com `Ctrl+C`, aguarde mais de 30 segundos e consulte novamente.

Para validar o agendamento, envie uma solicitação de container:

```bash
curl -X POST http://localhost:8080/api/containers -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"imageName":"nginx:1.27","cpuCores":1,"memoryMb":512,"diskMb":128}'
```

Deve retornar `201 Created` com o id da solicitação e o `nodeId` escolhido. A lista pode ser consultada em:

```bash
curl http://localhost:8080/api/containers -H "Authorization: Bearer $TOKEN"
```

## 12. Testes automatizados

Executar somente os testes do master e seus módulos necessários:

```bash
./mvnw -pl cloudbox-master -am test
```

No registro anterior de validação foram aprovados 41 testes no módulo master. Além dos cenários do agendador, a suíte cobre:

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
- autenticação JWT e autorização por token do agente;
- registro, heartbeat e timeout de nós;
- criação, listagem e transições de status dos containers;
- solicitação de parada e remoção;
- publicação de mudanças de nós e containers por WebSocket.

Resultado:

```text
Tests run: 41, Failures: 0, Errors: 0, Skipped: 0
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
- a escolha usa a folga relativa de RAM, CPU e disco (estratégia "most available resources");
- `POST /api/containers` registra a solicitação com o `nodeId` escolhido e `PENDING`;
- sem nó disponível, a API responde `409 Conflict` com mensagem explicativa;
- o agente recebe comandos explícitos de início, parada e remoção;
- a API mantém estados transitórios enquanto o agente executa a ação;
- as mudanças são propagadas em tempo real ao dashboard por WebSocket;
- a suíte automatizada valida o contrato e o fluxo de controle;
- a execução real em duas máquinas físicas ainda precisa ser comprovada na avaliação experimental.

## 15. Integração com a tela de containers

A página `/containers` consome `GET /api/containers` e apresenta imagem, nó alocado, status, recursos e data de criação. As ações autenticadas de parar e remover atualizam inicialmente o cache do dashboard com a resposta `202 Accepted` e depois são reconciliadas com os eventos WebSocket e uma nova consulta da lista.

Os estados transitórios `STOPPING` e `REMOVING` impedem ações duplicadas enquanto o agente trabalha. Os estados finais `STOPPED` e `REMOVED` registram a confirmação recebida do nó.

## 16. Pendências após o primeiro fluxo completo

- Consolidar publicação de portas e contrato de endereço de acesso usado no teste do 2048.
- Registrar evidências de escolha entre várias máquinas físicas e execução prolongada.
- Validar manualmente parada/remoção e recuperação após falhas.
- O disco solicitado participa do filtro e da pontuação, mas não é limitado pela Docker Engine no agente atual.
- O agendamento consulta as métricas disponíveis; o fluxo atual não debita reservas ao criar pedidos, o que precisa ser avaliado com solicitações concorrentes.

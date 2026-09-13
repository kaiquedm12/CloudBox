# CloudBox — documentação consolidada do projeto

> Levantamento baseado no código-fonte, migrations, configurações, contrato OpenAPI, testes e relatórios presentes no repositório em 8 de setembro de 2026. Quando a documentação antiga diverge do código, este documento considera o código atual como fonte principal.

## 1. Resumo executivo

CloudBox é uma plataforma de orquestração leve de containers para aproveitar computadores heterogêneos e potencialmente ociosos como um pequeno cluster privado. O projeto foi desenvolvido como Trabalho de Conclusão de Curso em Engenharia da Computação.

A solução possui três componentes:

| Componente | Responsabilidade |
|---|---|
| `cloudbox-master` | Orquestrador central: autenticação, registro e monitoramento de nós, agendamento, persistência, comandos de ciclo de vida e eventos em tempo real |
| `cloudbox-agent` | Serviço instalado em cada máquina: coleta métricas, registra o nó, envia heartbeat, consulta comandos e controla o Docker local |
| `cloudbox-dashboard` | Interface web: login, visão geral do cluster, detalhes de nós, criação e gerenciamento de containers |

O fluxo principal é assíncrono e orientado por polling:

1. O agente mede os recursos da máquina e registra um nó no master.
2. O master devolve um UUID do nó e um token opaco.
3. O agente envia heartbeats periódicos com CPU, RAM, disco e temperatura disponíveis.
4. Um usuário autenticado solicita um container pelo dashboard ou API.
5. O master escolhe um nó e grava a solicitação como `PENDING`.
6. O agente do nó consulta comandos pendentes, baixa a imagem e cria o container no Docker.
7. O agente informa o resultado ao master.
8. O master persiste o novo estado e publica um evento WebSocket ao dashboard.

## 2. Objetivo e proposta de valor

O CloudBox procura transformar máquinas comuns — desktops, notebooks, computadores de laboratório ou servidores antigos — em capacidade computacional compartilhada, sem exigir a complexidade operacional de uma plataforma Kubernetes completa.

Os objetivos identificados no projeto são:

- descobrir e registrar máquinas disponíveis;
- acompanhar disponibilidade e capacidade em tempo quase real;
- escolher automaticamente onde executar uma carga;
- iniciar, parar e remover containers remotamente;
- manter uma visão central do cluster;
- funcionar com agentes atrás de NAT, porque a comunicação operacional é iniciada pelo agente;
- servir como MVP e objeto de avaliação experimental de um TCC.

## 3. Escopo atual

### 3.1. Funcionalidades implementadas

- Registro automático de agentes no master.
- Geração de identidade de nó com UUID e token opaco.
- Coleta local de CPU, RAM, disco e temperatura com OSHI.
- Heartbeat periódico e transição automática `ONLINE`/`OFFLINE`.
- Persistência de nós, usuários e solicitações de containers em PostgreSQL.
- Agendamento por disponibilidade de CPU, RAM, disco e temperatura.
- Criação assíncrona de containers no Docker Engine do nó escolhido.
- Aplicação de limites de CPU e memória ao container Docker.
- Solicitação e execução de parada e remoção de containers.
- Histórico lógico: registros removidos permanecem no banco como `REMOVED`.
- Autenticação de usuários do dashboard com JWT.
- Autorização pontual de comandos e atualizações do agente pelo token do nó.
- Atualizações de métricas e estados por WebSocket.
- Dashboard responsivo em português e inglês, com tema claro/escuro.
- Alertas visuais de indisponibilidade, temperatura e uso crítico.
- Favicon próprio do CloudBox.
- Health check, Swagger/OpenAPI e migrations automáticas no master.

### 3.2. Funcionalidades declaradas ou desejadas, mas não implementadas integralmente

- Limite real de disco por container.
- Reserva transacional de recursos no master.
- Consulta de logs de containers.
- Reutilização da identidade persistida do agente após reinício.
- Descoberta automática de agentes na rede.
- Rede overlay entre nós.
- Volumes persistentes distribuídos.
- Migração de containers entre máquinas.
- Auto-scaling e rebalanceamento.
- Alta disponibilidade do master e do canal WebSocket.
- Multi-tenancy e autorização diferenciada por papel.
- Gestão de usuários pelo produto.
- Teste experimental prolongado em múltiplas máquinas físicas.

## 4. Arquitetura

```text
┌──────────────────────────────┐
│ Navegador                    │
│ Dashboard Next.js            │
└──────────────┬───────────────┘
               │ HTTPS / WebSocket
               ▼
┌──────────────────────────────┐
│ CloudBox Master              │
│ Spring Boot                  │
│                              │
│ Auth ─ Nodes ─ Scheduler     │
│ Containers ─ Realtime        │
└──────────────┬───────────────┘
               │ JPA / Flyway
               ▼
┌──────────────────────────────┐
│ PostgreSQL                   │
└──────────────────────────────┘

       REST iniciado pelo agente
               ▲
               │ registro, heartbeat,
               │ polling e status
┌──────────────┴───────────────┐
│ CloudBox Agent               │
│ Spring Boot + OSHI           │
└──────────────┬───────────────┘
               │ Docker Engine API
               ▼
┌──────────────────────────────┐
│ Docker daemon do nó          │
│ containers das cargas        │
└──────────────────────────────┘
```

### 4.1. Decisões arquiteturais observadas

- O master é o ponto central de controle e a fonte persistente do estado lógico.
- O agente inicia todas as comunicações REST relevantes, favorecendo máquinas atrás de NAT.
- Os comandos não são enviados diretamente ao agente: o agente consulta uma fila lógica representada pelos estados dos containers no banco.
- O dashboard usa o próprio servidor Next.js como BFF/proxy para não expor o JWT ao JavaScript do navegador.
- O WebSocket transporta notificações de mudança; os dados completos continuam sendo obtidos pela API REST.
- O master publica eventos depois do commit da transação quando há sincronização transacional ativa.
- O estado em tempo real e as tentativas pendentes do agente são mantidos em memória, sem broker externo.

## 5. Stack tecnológica

| Área | Tecnologia/versão |
|---|---|
| Linguagem do master e agente | Java 21 |
| Framework backend | Spring Boot 4.1.0 |
| Persistência | Spring Data JPA / Hibernate |
| Banco | PostgreSQL 16 no Compose |
| Migrações | Flyway |
| Segurança | Spring Security, BCrypt e JJWT 0.12.3 |
| Métricas do host | OSHI 6.6.5 |
| Integração Docker | docker-java 3.4.1, transporte zerodep |
| API | REST/JSON e WebSocket |
| Documentação da API | springdoc-openapi 3.1.0 e arquivo OpenAPI 3.0.3 |
| Dashboard | Next.js 16.3.0, React 19.2.8 e TypeScript |
| Estado remoto no frontend | TanStack Query 5 |
| Validação no frontend | Zod 4 |
| Estilos | Tailwind CSS 4 |
| Build Java | Maven Wrapper e projeto multi-módulo |
| Desenvolvimento local | Docker Compose |
| Deploy configurado do master | Docker + Railway |

## 6. Requisitos funcionais consolidados

### 6.1. Nós e métricas

- **RF-01 — Registrar nó:** o sistema deve aceitar nome, CPU total, RAM total e disco total e devolver `nodeId` e token.
- **RF-02 — Inicializar nó:** um nó novo deve iniciar `OFFLINE`, com recursos livres iguais aos totais.
- **RF-03 — Enviar heartbeat:** o agente deve enviar CPU, RAM e disco livres e, quando disponível, temperatura.
- **RF-04 — Atualizar disponibilidade:** o primeiro heartbeat válido deve deixar o nó `ONLINE`.
- **RF-05 — Detectar ausência:** o master deve marcar um nó como `OFFLINE` após o timeout sem heartbeat.
- **RF-06 — Listar nós:** usuários autenticados devem consultar todos os nós e suas últimas métricas.
- **RF-07 — Detalhar nó:** o dashboard deve apresentar métricas, heartbeat e containers alocados a um nó.

### 6.2. Agendamento e containers

- **RF-08 — Solicitar container:** o usuário deve informar imagem, CPUs, memória e disco desejados.
- **RF-09 — Validar solicitação:** imagem não pode ser vazia nem conter espaços; CPU, memória e disco devem ser inteiros positivos.
- **RF-10 — Selecionar nó:** apenas nós online, com recursos suficientes e temperatura segura podem receber a carga.
- **RF-11 — Informar falta de capacidade:** se nenhum nó atender ao pedido, a API deve responder `409 Conflict`.
- **RF-12 — Disponibilizar comando:** uma solicitação aceita deve ser gravada como `PENDING` e aparecer como ação `START` para o agente correspondente.
- **RF-13 — Executar container:** o agente deve baixar a imagem, criar e iniciar o container no Docker local.
- **RF-14 — Reportar resultado:** o agente deve informar `RUNNING`, `ERROR`, `STOPPED` ou `REMOVED` ao master.
- **RF-15 — Parar container:** somente um container `RUNNING` pode receber solicitação de parada.
- **RF-16 — Remover container:** containers `RUNNING`, `STOPPED`, `ERROR` ou `FAILED` podem receber solicitação de remoção.
- **RF-17 — Preservar histórico:** remover no Docker não deve apagar o registro do banco; ele passa para `REMOVED`.
- **RF-18 — Listar containers:** usuários autenticados devem visualizar todos os registros, recursos, nó, estado e erros.

### 6.3. Autenticação e interface

- **RF-19 — Login:** o dashboard deve autenticar usuário por e-mail e senha e receber JWT.
- **RF-20 — Proteger dashboard/API:** rotas de administração devem exigir JWT válido.
- **RF-21 — Proteger operação do agente:** consulta de comandos e atualização de status devem validar o token do nó proprietário.
- **RF-22 — Atualizar em tempo real:** alterações relevantes devem provocar atualização do dashboard por WebSocket.
- **RF-23 — Suportar idiomas:** a interface deve oferecer português do Brasil e inglês.
- **RF-24 — Suportar tema:** a interface deve oferecer tema claro e escuro, respeitando inicialmente a preferência do sistema.

## 7. Regras de negócio

### 7.1. Registro e disponibilidade de nós

1. Cada registro gera um novo UUID e um token UUID aleatório.
2. O nome do nó não é único no banco.
3. CPU, RAM e disco totais aceitam zero no contrato de registro.
4. Um nó recém-registrado fica `OFFLINE` até receber heartbeat.
5. Heartbeat substitui as métricas livres anteriores pelas novas medições e atualiza `lastHeartbeat`.
6. O timeout padrão é 30 segundos e a verificação ocorre a cada 10 segundos.
7. Apenas nós `ONLINE` participam do agendamento.
8. A temperatura é opcional. Nó sem leitura de temperatura continua elegível.
9. A temperatura deve ser estritamente menor que 75 °C por padrão. Exatamente 75 °C já torna o nó inelegível.

### 7.2. Elegibilidade e pontuação do agendador

Um nó candidato precisa satisfazer simultaneamente:

```text
status = ONLINE
cpuFree >= cpuRequested
ramFreeMb >= ramRequestedMb
diskFreeMb >= diskRequestedMb
temperatureCelsius ausente OU temperatureCelsius < limite
```

A pontuação é a soma da folga relativa dos três recursos:

```text
score = (cpuFree - cpuRequested) / cpuTotal
      + (ramFreeMb - ramRequestedMb) / ramTotalMb
      + (diskFreeMb - diskRequestedMb) / diskTotalMb
```

Regras adicionais:

- vence a maior pontuação;
- empate é resolvido primeiro pela maior RAM absoluta restante;
- persistindo o empate, vence a maior CPU absoluta restante;
- não há desempate final explícito por disco ou identificador;
- totais iguais a zero usam a folga absoluta do recurso para evitar divisão por zero;
- a seleção representa uma estratégia de “mais recursos disponíveis”, espalhando cargas em vez de preencher primeiro o menor nó.

### 7.3. Reserva de capacidade

O campo solicitado de CPU, RAM e disco é persistido no container e considerado na escolha inicial do nó. Entretanto, o master não reduz as métricas livres ao gravar um `PENDING`. A capacidade volta a ser percebida apenas pelos heartbeats reais do host.

Consequências:

- duas solicitações simultâneas podem escolher a mesma capacidade ainda não refletida pelo heartbeat;
- pode ocorrer sobrealocação entre criação lógica e atualização das métricas;
- `diskMb` atua como requisito de agendamento, mas não como reserva contábil transacional.

### 7.4. Estados do container

| Estado | Significado atual |
|---|---|
| `PENDING` | Alocado logicamente e aguardando ação `START` do agente |
| `SCHEDULED` | Declarado no enum/contrato, mas não atribuído pelo fluxo atual |
| `RUNNING` | Agente confirmou execução e pode informar `dockerContainerId` |
| `STOPPING` | Master solicitou parada; vira comando `STOP` |
| `STOPPED` | Agente confirmou a parada |
| `REMOVING` | Master solicitou remoção; vira comando `REMOVE` |
| `REMOVED` | Removido do Docker ou marcado removido quando não havia ID Docker |
| `ERROR` | Agente reportou falha operacional |
| `FAILED` | Declarado e aceito para remoção, mas não produzido pelo fluxo atual |

Transições efetivamente implementadas:

```text
criação:  PENDING ──START──> RUNNING
                         └──> ERROR

parada:   RUNNING ──pedido──> STOPPING ──agente──> STOPPED

remoção:  RUNNING | STOPPED | ERROR | FAILED
          └──pedido──> REMOVING ──agente──> REMOVED

sem dockerContainerId:
          RUNNING | STOPPED | ERROR | FAILED ──pedido──> REMOVED
```

O endpoint de status aceita do agente apenas `RUNNING`, `ERROR`, `STOPPED` e `REMOVED`. O código não valida que a transição pedida é compatível com o estado anterior; ele valida somente se o novo estado pertence a esse conjunto.

### 7.5. Idempotência e novas tentativas no agente

- Após criar um container, o agente guarda em memória o `dockerContainerId` enquanto tenta confirmar `RUNNING`.
- Após parar ou remover, guarda em memória a ação concluída enquanto tenta confirmar o estado final.
- Se o reporte falhar, o agente tenta reportar novamente sem repetir a operação Docker no mesmo processo.
- Esses controles são apenas em memória e são perdidos se o agente reiniciar.
- Falhas em `START` são convertidas em reporte `ERROR`; falhas em `STOP`/`REMOVE` voltam a ser tentadas no próximo polling.

### 7.6. Regras do dashboard

- Containers ativos na visão geral são os estados `PENDING`, `SCHEDULED`, `RUNNING` e `STOPPING`.
- O botão **Parar** só é habilitado para `RUNNING`.
- O botão **Remover** só é habilitado para `RUNNING`, `STOPPED`, `ERROR` e `FAILED` e exige confirmação do navegador.
- Alertas de utilização usam 75% como nível de aviso e 90% como crítico.
- Um nó entra no filtro de alertas se estiver offline, com temperatura maior ou igual a 75 °C, ou com CPU/RAM/disco em 90% ou mais de utilização.
- Totais agregados da visão geral consideram apenas nós online.
- Busca de nós considera nome e UUID.
- Ordenação pode ser por nome ou pelo maior percentual de uso entre CPU, RAM e disco.

## 8. Fluxos ponta a ponta

### 8.1. Inicialização do agente

1. Spring inicia o agente e habilita tarefas agendadas.
2. Ao receber `ApplicationReadyEvent`, `NodeRegistrationService` coleta métricas totais.
3. Envia `POST /api/nodes/register`.
4. Persiste `nodeId` e token em arquivo `.properties`.
5. O heartbeat verifica primeiro se o Docker responde ao `ping`.
6. Envia métricas ao master e o nó passa a `ONLINE`.
7. Em paralelo, o poller verifica Docker, consulta os comandos e os executa.

Observação: embora as credenciais sejam salvas e possam ser lidas por `AgentTokenStorage`, a inicialização atual não chama `load()`. Portanto, reiniciar o agente gera outro registro de nó.

### 8.2. Criação de container

1. O usuário abre o modal no dashboard.
2. Zod valida imagem, CPU, RAM e disco.
3. O BFF Next.js lê o JWT do cookie e encaminha `POST /api/containers` ao master.
4. O master valida novamente o DTO.
5. O scheduler filtra e pontua os nós.
6. Sem candidato, retorna `409`.
7. Com candidato, persiste o container como `PENDING` com `nodeId`.
8. O agente consulta `GET /api/nodes/{id}/pending-commands` e recebe `START`.
9. O agente baixa a imagem, cria o container com nome `cloudbox-{UUID}`, aplica CPU/RAM e inicia.
10. O agente reporta `RUNNING` e o ID do Docker ou `ERROR` e a mensagem de falha.
11. O master publica `CONTAINER_STATUS_CHANGED`.
12. O dashboard invalida e recarrega o cache de containers.

### 8.3. Parada

1. Usuário solicita parada de um `RUNNING`.
2. Master altera para `STOPPING`, responde `202 Accepted` e publica evento.
3. O comando pendente vira `STOP` com `dockerContainerId`.
4. Agente executa `docker stop`.
5. Agente reporta `STOPPED`.
6. Master persiste e publica o estado final.

### 8.4. Remoção

1. Usuário confirma a remoção.
2. Se houver `dockerContainerId`, master altera para `REMOVING` e responde `202`.
3. Agente usa remoção forçada no Docker e reporta `REMOVED`.
4. Se não houver ID Docker, master marca `REMOVED` imediatamente.
5. O registro permanece em `container_instances`.

### 8.5. Atualização em tempo real

1. Navegador autenticado abre `/ws/cluster-status`.
2. O master autentica o handshake pelo cookie `cloudbox_access_token`.
3. Mudanças de nó, métricas ou container geram uma mensagem JSON.
4. O frontend atualiza provisoriamente o item no cache e invalida a consulta REST.
5. Em desconexão, reconecta com backoff exponencial de 1 até 30 segundos.
6. Ao reconectar, força nova leitura de nós e containers para recuperar eventos perdidos.

## 9. CloudBox Master

### 9.1. Responsabilidades

- Expor API HTTP.
- Autenticar usuários.
- Registrar e monitorar nós.
- Persistir estado do cluster.
- Selecionar o nó para novas cargas.
- Representar comandos pendentes por estados persistidos.
- Validar tokens de agentes em operações sensíveis.
- Publicar mudanças por WebSocket.
- Expor health check e documentação Swagger.

### 9.2. Pacotes principais

| Pacote | Conteúdo |
|---|---|
| `auth` | Login e DTOs de autenticação |
| `security` | JWT, filtros, BCrypt e validação do token de agente |
| `user` | Usuário, papel e repositório |
| `node` | Entidade, registro, heartbeat, timeout e comandos do nó |
| `scheduler` | Filtro de candidatos, pontuação e seleção |
| `container` | Entidade, API, estados, comandos e ciclo de vida |
| `realtime` | Modelo, publicação e broadcast WebSocket |
| `common` | Exceções e tratamento HTTP global |
| `config` | OpenAPI e WebSocket |

### 9.3. Tratamento de erros

- Recurso ausente (`ResourceNotFoundException`) → `404` sem corpo.
- Falha de autorização pontual (`UnauthorizedException`) → `401` com `{ "error": "..." }`.
- Regra ou argumento inválido (`IllegalArgumentException`) → `400` com erro.
- Falta de JWT em rota protegida → `401` com `Token JWT ausente ou inválido`.
- Falta de capacidade → `409` com mensagem sobre CPU, RAM e disco.
- Não há handler global específico documentado para erros inesperados ou violações de banco.

## 10. CloudBox Agent

### 10.1. Coleta de métricas

O `MetricsCollector` usa OSHI:

- CPU livre: `1 - carga medida entre dois conjuntos de ticks`, limitada ao intervalo de 0% a 100%;
- CPU total: quantidade de processadores lógicos;
- RAM livre/total: memória disponível e total do sistema;
- disco livre/total: soma dos espaços utilizáveis e totais de todos os file stores com tamanho positivo;
- temperatura: leitura de CPU; zero, negativo, `NaN` ou infinito viram `null`.

Para a API:

- CPU livre percentual é convertida em quantidade equivalente de CPUs lógicas e arredondada para duas casas;
- bytes de RAM e disco são truncados para MB por divisão inteira por `1024²`.

### 10.2. Docker

Conexão padrão:

- Windows: `npipe:////./pipe/docker_engine`;
- Unix/Linux: `unix:///var/run/docker.sock`;
- valor configurado em `DOCKER_HOST` tem prioridade.

Operações implementadas:

- ping do daemon;
- pull de imagem;
- criação simples para validação manual;
- criação e início com limites;
- inspeção de estado e exit code;
- parada;
- remoção forçada.

Limites realmente aplicados em `runContainer`:

```text
NanoCPUs = cpuCores × 1.000.000.000
Memory   = memoryMb × 1.024 × 1.024 bytes
```

O `diskMb` recebido no comando não é passado para `runContainer` e nenhuma opção de storage é configurada no `HostConfig`. Logo, o valor é exibido, persistido e usado pelo scheduler, mas **não limita nem reserva o armazenamento gravável do container Docker**.

### 10.3. Validação manual do Docker

Quando `DOCKER_VALIDATION_ENABLED=true`, o agente:

1. baixa uma imagem configurável;
2. cria um container temporário;
3. inicia e inspeciona;
4. para e inspeciona novamente;
5. remove o container no bloco `finally`.

O padrão é desabilitado e usa `nginx:alpine` quando habilitado.

## 11. Dashboard

### 11.1. Rotas

| Rota | Função | Proteção |
|---|---|---|
| `/login` | Login | Pública |
| `/` | Visão geral do cluster | Cookie presente no proxy; validade confirmada ao acessar a API/WS |
| `/containers` | Listagem e ações de containers | Protegida |
| `/nodes/[id]` | Detalhes de um nó | Protegida |
| `/api/auth/login` | BFF de login | Pública |
| `/api/auth/logout` | Remove cookie local | Chamável pelo dashboard |
| `/api/[...path]` | Proxy autenticado para o master | Exige cookie JWT |
| `/ws/cluster-status` | Rewrite para o WebSocket do master | Handshake autenticado no master |

### 11.2. Funcionalidades visuais

- resumo de nós online, containers ativos, CPU, RAM, disco e alertas;
- filtro de nós por todos, online, offline ou com alerta;
- busca por nome/UUID;
- ordenação por nome ou maior utilização;
- visualização em grade e lista;
- atualização manual;
- indicação da conexão em tempo real;
- detalhes de cada nó e seus containers;
- listagem responsiva de containers em cards/tabela;
- modal acessível de criação, com foco contido e fechamento por `Escape`;
- ações de parar e remover, com estados ocupados e mensagens de erro;
- mensagens específicas para falta de capacidade (`409`);
- idioma persistido em `localStorage` (`cloudbox-language`);
- tema persistido em `localStorage` (`cloudbox-theme`), com preferência do sistema como padrão;
- redirecionamento ao login preservando apenas caminhos internos permitidos;
- favicon em `app/icon.svg`.

### 11.3. Estratégia de autenticação do dashboard

1. O navegador envia e-mail/senha à rota interna de login.
2. O servidor Next chama o master.
3. O JWT retornado é gravado em `cloudbox_access_token` com `httpOnly`, `SameSite=Lax`, path `/`, prioridade alta e `Secure` em produção.
4. Requisições do navegador usam rotas internas; o BFF lê o cookie e adiciona `Authorization: Bearer` ao upstream.
5. Um `401` do master apaga o cookie.
6. Logout expira o cookie, limpa o cache TanStack Query e redireciona a `/login`.

O proxy de páginas verifica apenas a existência do cookie. Um token expirado ainda pode permitir a renderização inicial da rota, mas a primeira chamada protegida recebe `401`, limpa a sessão e redireciona ao login.

## 12. API REST

### 12.1. Matriz de endpoints

| Método e caminho | Finalidade | Autenticação | Resposta principal |
|---|---|---|---|
| `POST /api/auth/login` | Autenticar usuário | Pública | `200` com JWT |
| `POST /api/nodes/register` | Registrar agente | Pública | `201` com ID/token |
| `POST /api/nodes/{id}/heartbeat` | Atualizar métricas | Atualmente pública no master | `204` |
| `GET /api/nodes` | Listar nós | JWT de usuário | `200` |
| `GET /api/nodes/{id}/pending-commands` | Obter comandos | Token do próprio nó | `200` com lista |
| `POST /api/containers` | Criar/agendar | JWT de usuário | `201` ou `409` |
| `GET /api/containers` | Listar containers | JWT de usuário | `200` |
| `POST /api/containers/{id}/stop` | Solicitar parada | JWT de usuário | `202` |
| `DELETE /api/containers/{id}` | Solicitar remoção | JWT de usuário | `202` |
| `POST /api/containers/{id}/status` | Confirmar resultado | Token do nó proprietário | `200` |
| `GET /actuator/health` | Saúde do master | Pública | `200` quando saudável |
| `/v3/api-docs/**` | OpenAPI gerado | Pública | JSON/YAML |
| `/swagger-ui/**` | UI da API | Pública | HTML/recursos |

Importante: o agente envia Bearer token também no heartbeat, mas o controller e a cadeia de segurança atual não o validam. Portanto, conhecer o UUID do nó é suficiente para alterar suas métricas via heartbeat. O registro também é público por desenho atual.

### 12.2. Exemplos de payload

Registro:

```json
{
  "name": "no-laboratorio-01",
  "cpuTotal": 8,
  "ramTotalMb": 16384,
  "diskTotalMb": 250000
}
```

Heartbeat:

```json
{
  "cpuFree": 5.75,
  "ramFreeMb": 12000,
  "diskFreeMb": 180000,
  "temperatureCelsius": 52.4
}
```

Criação de container:

```json
{
  "imageName": "nginx:1.27",
  "cpuCores": 1,
  "memoryMb": 500,
  "diskMb": 30
}
```

Comando pendente:

```json
{
  "containerId": "0c0f7c3d-7a4e-4f1b-9b1c-3e2d5a6b7c8d",
  "action": "START",
  "imageName": "nginx:1.27",
  "cpuCores": 1,
  "memoryMb": 500,
  "diskMb": 30,
  "dockerContainerId": null
}
```

Atualização de status:

```json
{
  "status": "RUNNING",
  "dockerContainerId": "docker-id-real",
  "errorMessage": null
}
```

### 12.3. Evento WebSocket

```json
{
  "eventType": "CONTAINER_STATUS_CHANGED",
  "resourceType": "CONTAINER",
  "resourceId": "0c0f7c3d-7a4e-4f1b-9b1c-3e2d5a6b7c8d",
  "previousStatus": "PENDING",
  "currentStatus": "RUNNING",
  "occurredAt": "2026-09-08T12:00:00Z"
}
```

Eventos existentes:

- `NODE_STATUS_CHANGED`;
- `NODE_METRICS_UPDATED`;
- `CONTAINER_STATUS_CHANGED`.

## 13. Segurança

### 13.1. Usuários

- Senhas são armazenadas como BCrypt.
- Login normaliza e-mail com `trim` e lowercase.
- JWT contém e-mail como subject, `userId`, `role`, emissão e expiração.
- Validade padrão do JWT: 3.600 segundos.
- Papéis existentes: `ADMIN` e `USER`.
- O papel vira autoridade `ROLE_*`, mas não há regras diferentes por papel nos endpoints atuais.
- A API é stateless e não cria sessão HTTP.

### 13.2. Usuário inicial

A migration V7 criou originalmente um administrador de demonstração com
`admin@admin.com` e senha `cloudbox`. A migration V8 remove esse usuário quando
ele ainda possui a senha insegura original.

Na inicialização, o master exige `ADMIN_EMAIL` e `ADMIN_PASSWORD` e cria um
administrador com papel `ADMIN` somente quando o e-mail configurado ainda não
existe. A senha deve possuir pelo menos 12 caracteres. Por segurança, não há
credenciais administrativas padrão.

Alterar `ADMIN_PASSWORD` após o usuário ser criado não redefine a senha já
armazenada.

### 13.3. Tokens de agentes

- São UUIDs aleatórios opacos, não JWTs.
- São persistidos em texto simples no banco e no arquivo do agente.
- Validam acesso ao próprio `nodeId` em comandos e ao nó proprietário na atualização de container.
- Não há rotação, revogação, expiração ou hash do token.
- Comparação é direta por string.

### 13.4. Pontos de atenção

- `JWT_SECRET`, `ADMIN_EMAIL` e `ADMIN_PASSWORD` são obrigatórios no master.
- O heartbeat não valida o token enviado.
- O WebSocket permite qualquer origem (`AllowedOriginPatterns("*")`), embora exija JWT no cookie para autenticar o handshake.
- CSRF está desabilitado. O cookie `SameSite=Lax` reduz parte do risco, mas não substitui uma análise de CSRF para implantação pública.
- Não há rate limiting, bloqueio por tentativas de login ou auditoria de ações.
- Montar o socket Docker concede ao agente poder elevado sobre o host; isso exige controle rígido da imagem e do ambiente.

## 14. Persistência e modelo de dados

### 14.1. Tabela `nodes`

| Campo | Tipo/conceito |
|---|---|
| `id` | UUID, chave primária |
| `name` | Nome do nó, até 100 caracteres no banco |
| `token` | Token opaco único |
| `status` | `ONLINE` ou `OFFLINE` |
| `cpu_total`, `cpu_free` | Decimal(6,2) |
| `ram_total_mb`, `ram_free_mb` | Inteiro |
| `disk_total_mb`, `disk_free_mb` | Inteiro |
| `temperature_celsius` | Decimal(5,2), opcional |
| `last_heartbeat` | Timestamp opcional |
| `created_at` | Timestamp obrigatório |

### 14.2. Tabela `container_instances`

| Campo | Tipo/conceito |
|---|---|
| `id` | UUID, chave primária |
| `image_name` | Nome/tag da imagem, até 255 caracteres |
| `cpu_cores` | CPUs solicitadas |
| `memory_mb` | RAM solicitada |
| `disk_mb` | Disco solicitado |
| `status` | Estado lógico do ciclo de vida |
| `node_id` | UUID do nó alocado, com índice |
| `docker_container_id` | ID devolvido pelo Docker, opcional |
| `error_message` | Texto de erro, opcional |
| `created_at`, `updated_at` | Timestamps |

Não existe foreign key explícita entre `container_instances.node_id` e `nodes.id` nas migrations atuais.

### 14.3. Tabela `users`

| Campo | Tipo/conceito |
|---|---|
| `id` | UUID, chave primária |
| `email` | Único, até 255 caracteres |
| `password_hash` | Hash BCrypt |
| `role` | `ADMIN` ou `USER` |
| `created_at` | Timestamp de criação |

### 14.4. Migrations

1. V1 habilita `pgcrypto`.
2. V2 cria `nodes`.
3. V3 cria `container_instances`.
4. V4 adiciona e indexa `node_id`.
5. V5 adiciona ID Docker e mensagem de erro.
6. V6 cria `users`.
7. V7 cria o administrador legado de demonstração.
8. V8 remove o administrador legado quando ele ainda usa a senha insegura.
9. V9 adiciona endereços de nós, portas de serviços e endpoints observados.

Hibernate usa `ddl-auto: validate`; alterações estruturais devem ser feitas por novas migrations Flyway.

## 15. Configuração

### 15.1. Master

| Variável/propriedade | Padrão | Uso |
|---|---|---|
| `PORT` | `8080` | Porta HTTP |
| `PGHOST` | `localhost` | Host PostgreSQL |
| `PGPORT` | `5432` | Porta PostgreSQL |
| `PGDATABASE` | `cloudbox` | Banco |
| `PGUSER` | `cloudbox` | Usuário |
| `PGPASSWORD` | `cloudbox` | Senha |
| `JWT_SECRET` | Obrigatório | Segredo Base64 para assinatura JWT |
| `JWT_EXPIRATION_SECONDS` | `3600` | Validade JWT |
| `ADMIN_EMAIL` | Obrigatório | E-mail do administrador inicial |
| `ADMIN_PASSWORD` | Obrigatório, mínimo de 12 caracteres | Senha do administrador inicial |
| `cloudbox.heartbeat.timeout-seconds` | `30` | Timeout de nó |
| `cloudbox.heartbeat.check-interval-seconds` | `10` | Frequência do monitor |
| `cloudbox.scheduler.max-temperature-celsius` | `75` | Limite térmico |

Propriedades Spring podem também ser sobrescritas pelas convenções usuais de variáveis de ambiente, por exemplo `CLOUDBOX_HEARTBEAT_TIMEOUT_SECONDS`.

### 15.2. Agente

| Variável | Padrão atual | Uso |
|---|---|---|
| `CLOUDBOX_MASTER_URL` | URL pública Railway definida no YAML | Endereço do master |
| `AGENT_NAME` | `HOSTNAME` ou `cloudbox-agent` | Nome do nó |
| `AGENT_TOKEN_FILE` | `${user.home}/.cloudbox/agent-credentials.properties` | Arquivo de identidade |
| `AGENT_HEARTBEAT_INTERVAL` | `10000` ms | Intervalo de heartbeat |
| `AGENT_HEARTBEAT_INITIAL_DELAY` | `2000` ms | Atraso inicial |
| `AGENT_COMMAND_POLL_INTERVAL` | `5000` ms | Intervalo de polling |
| `AGENT_COMMAND_POLL_INITIAL_DELAY` | `3000` ms | Atraso inicial do polling |
| `DOCKER_HOST` | Named pipe Windows ou socket Unix | Docker daemon |
| `DOCKER_CONNECTION_TIMEOUT` | `5s` | Timeout de conexão |
| `DOCKER_RESPONSE_TIMEOUT` | `30s` | Timeout de resposta |
| `DOCKER_VALIDATION_ENABLED` | `false` | Validação manual no startup |
| `DOCKER_VALIDATION_IMAGE` | `nginx:alpine` | Imagem de validação |
| `AGENT_METRICS_INTERVAL` | `5000` ms | Intervalo do log local |
| `AGENT_METRICS_INITIAL_DELAY` | `1000` ms | Atraso do log local |

### 15.3. Dashboard

| Variável | Padrão atual | Uso |
|---|---|---|
| `ORCHESTRATOR_URL` | URL pública Railway definida no código | API e rewrite WebSocket |
| `NEXT_PUBLIC_ORCHESTRATOR_URL` | Fallback secundário apenas para helper da API | Compatibilidade de configuração |
| `NODE_ENV` | Definido pelo Next | Ativa cookie `Secure` em produção |

É preferível usar apenas `ORCHESTRATOR_URL`, que permanece no servidor.

## 16. Execução local

### 16.1. Pré-requisitos

- Java 21;
- Docker Engine/Docker Desktop acessível ao agente;
- Node.js compatível com Next.js 16;
- npm;
- porta 5432 livre para o PostgreSQL local;
- portas 8080 e 3000 livres para master e dashboard.

### 16.2. Banco

Na raiz:

```powershell
docker compose up -d postgres
docker compose ps
```

O Compose atual sobe **somente o PostgreSQL**. Apesar de comentários e textos antigos mencionarem múltiplos nós simulados, os serviços master, agent e dashboard não estão definidos nesse arquivo.

### 16.3. Master

```powershell
.\mvnw.cmd -pl cloudbox-master spring-boot:run
```

Verificação:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

### 16.4. Agente

Defina explicitamente o master local, porque o YAML atual usa uma URL Railway por padrão:

```powershell
$env:CLOUDBOX_MASTER_URL = "http://localhost:8080"
.\mvnw.cmd -pl cloudbox-agent spring-boot:run
```

O Docker Desktop/daemon precisa estar ativo.

### 16.5. Dashboard

```powershell
Set-Location cloudbox-dashboard
Copy-Item .env.example .env.local
```

Ajuste `.env.local` para:

```dotenv
ORCHESTRATOR_URL=http://localhost:8080
```

Depois:

```powershell
npm install
npm run dev
```

Acesse `http://localhost:3000` e use o administrador inicial somente em desenvolvimento.

## 17. Build e deploy

### 17.1. Build Java

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Build por módulo:

```powershell
.\mvnw.cmd -pl cloudbox-master -am test
.\mvnw.cmd -pl cloudbox-agent -am test
```

### 17.2. Build do dashboard

```powershell
Set-Location cloudbox-dashboard
npm ci
npm run build
npm run start
```

### 17.3. Imagens Docker

Os Dockerfiles usam build multi-stage.

```powershell
docker build -f cloudbox-master/Dockerfile -t cloudbox-master .
docker build -f cloudbox-agent/Dockerfile -t cloudbox-agent .
```

Para o agente controlar o Docker do host em Linux, o desenho prevê:

```bash
docker run -d \
  -e CLOUDBOX_MASTER_URL=https://seu-master \
  -e AGENT_NAME=no-01 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /:/host/rootfs:ro \
  cloudbox-agent
```

O código atual não lê explicitamente `/host/rootfs`; o mount está documentado no Dockerfile como parte da estratégia de métricas do host e deve ser validado no sistema operacional alvo.

### 17.4. Railway

`railway.json` configura apenas o master:

- build pelo `cloudbox-master/Dockerfile`;
- comando `java -jar app.jar`;
- health check em `/actuator/health`;
- timeout de health check de 300 segundos;
- reinício em falha, máximo de 10 tentativas.

O master executa como usuário não-root na imagem final. O agente não define usuário não-root porque precisa acessar o socket Docker, mas as permissões concretas dependem do host.

Não há configuração de deploy do dashboard neste repositório além dos arquivos padrão do Next.js.

## 18. Testes e qualidade

### 18.1. Testes do master

A suíte cobre, entre outros:

- JWT válido, adulterado e expiração/claims;
- login e proteção de endpoints;
- exceções de autenticação;
- isolamento das rotas liberadas para agentes;
- validação do token por nó e por container;
- heartbeat, atualização de métricas e mudança para online;
- timeout e mudança para offline;
- filtro por CPU, RAM, disco, status e temperatura;
- pontuação e desempate do scheduler;
- ausência de candidato;
- comandos pendentes;
- transições de parada, remoção e status;
- mensagens WebSocket de nó, métricas e container.

### 18.2. Testes do agente

A suíte cobre:

- normalização de temperatura;
- limite do percentual de CPU;
- conversão de bytes para MB;
- gravação/leitura do arquivo de credenciais;
- contrato HTTP de registro, heartbeat, comandos e status;
- header Bearer;
- execução de `START`, `STOP` e `REMOVE`;
- nova tentativa de reporte sem repetir a ação no mesmo processo;
- resolução do Docker host em Windows, Linux e configuração explícita.

### 18.3. Dashboard

- Não existe script de testes automatizados no `package.json`.
- As verificações disponíveis são compilação TypeScript realizada pelo build e build de produção Next.js.
- A documentação anterior registra validação manual e `tsc`, mas também relata limitações ambientais em builds antigos; o resultado deve ser revalidado no ambiente atual sempre que houver mudanças.

## 19. Limitações, riscos e dívida técnica conhecidos

### 19.1. Prioridade alta

1. **Disco não limitado no Docker:** `diskMb` não chega a `ContainerExecutionService.runContainer`; nenhum `storage-opt size` ou mecanismo equivalente é aplicado. A solução depende do storage driver e sistema de arquivos do daemon e precisa falhar de forma clara quando quotas não forem suportadas.
2. **Heartbeat sem autenticação efetiva:** o token enviado pelo agente é ignorado nesse endpoint.
3. **Credencial do agente não reutilizada:** o arquivo é salvo, mas não carregado pelo registro; reinícios criam nós duplicados.
4. **Possível sobrealocação:** pedidos pendentes não reservam/deduzem recursos no master.
5. **Credenciais padrão inseguras:** administrador e segredo JWT de desenvolvimento não devem chegar a produção.

### 19.2. Consistência e resiliência

- Estado de repetição do agente fica apenas em memória.
- Reinício durante uma confirmação pendente pode provocar nova tentativa de criação e conflito de nome.
- Não há reconciliação periódica entre estado no banco e estado real do Docker.
- Containers parados externamente não são detectados automaticamente.
- Uma falha de `STOP` ou `REMOVE` pode interromper o processamento dos comandos seguintes daquele ciclo.
- Não há locking/reserva para múltiplas criações concorrentes.
- WebSocket e sessões vivem em uma única instância; escalar o master horizontalmente exigiria broker/pub-sub.
- O registro de nó não é idempotente e o nome não é único.

### 19.3. Métricas e capacidade

- A soma de file stores pode contar mounts que não representam a capacidade efetivamente utilizável pelo Docker.
- Em container, deve-se comprovar que OSHI está medindo o host desejado, e não apenas o namespace/filesystem do agente.
- CPU solicitada é inteira, embora a modelagem do nó aceite valores fracionários.
- Não existem limites máximos de solicitação além da capacidade reportada.
- Não existe reserva separada entre “livre medido” e “alocado pelo CloudBox”.

### 19.4. API e modelo

- `SCHEDULED` e `FAILED` existem, mas não são produzidos no fluxo atual.
- Atualização de status não valida uma máquina de estados completa.
- `node_id` não possui foreign key.
- Não há paginação, filtros ou ordenação no backend.
- Não há endpoints de usuário, nó individual, exclusão de nó, logs ou inspeção detalhada do container.
- O OpenAPI estático em `docs/cloudbox-openapi.yaml` pode divergir das anotações/código e não descreve todo o login/JWT do dashboard.
- Erros de Bean Validation seguem o formato padrão do Spring e não são normalizados pelo handler customizado atual.

### 19.5. Segurança e operação

- Tokens opacos de agente são armazenados sem hash e não expiram.
- CORS não possui política customizada explícita; WebSocket aceita qualquer origem.
- Não há rate limit, trilha de auditoria ou observabilidade distribuída.
- Não há TLS no aplicativo; presume-se terminação pelo ambiente de deploy.
- Acesso ao Docker socket equivale, na prática, a alto privilégio sobre o host.
- Não há assinatura/verificação de confiança das imagens Docker solicitadas.
- Qualquer imagem válida e acessível ao daemon pode ser solicitada por qualquer usuário autenticado.

### 19.6. Produto e avaliação

- A execução prolongada em duas ou mais máquinas reais ainda precisa de evidências documentadas.
- Não há licença definida.
- O roadmap do README não foi atualizado e ainda marca como pendentes várias funções já implementadas.
- Não há configuração completa de ambiente local em um único `docker compose up`.

## 20. Requisitos não funcionais inferidos

### 20.1. Disponibilidade

- Heartbeat do agente deve ocorrer com intervalo menor que o timeout do master.
- Falha temporária de registro/heartbeat não deve encerrar o agente.
- O frontend deve reconectar WebSocket e reconciliar dados perdidos via REST.

### 20.2. Segurança

- JWT deve ser assinado com segredo forte e diferente por ambiente.
- Senhas devem permanecer hasheadas.
- Token do dashboard não deve ser acessível ao JavaScript do navegador.
- Tokens de agente devem identificar e limitar o nó que pode operar o recurso.

### 20.3. Portabilidade

- Agente suporta conexão Docker por named pipe no Windows e socket Unix no Linux.
- Configurações operacionais devem ser externas via environment variables.
- Build deve ser reproduzível com Maven Wrapper e npm lockfile.

### 20.4. Usabilidade

- Interface deve ser responsiva, navegável por teclado e apresentar estados de carregamento/erro.
- Alterações de estado devem ser visíveis em tempo quase real.
- Português e inglês devem estar disponíveis.

### 20.5. Manutenibilidade

- Banco deve evoluir apenas por migrations versionadas.
- DTOs, serviços e responsabilidades estão separados por domínio.
- Regras críticas de scheduler, segurança e comandos devem permanecer cobertas por testes.

## 21. Trabalhos futuros já identificados

- Implementar limite de disco compatível com o storage driver do Docker e diagnóstico de suporte.
- Carregar e validar credenciais existentes antes de registrar um novo nó.
- Autenticar heartbeat com o token do nó.
- Criar reserva transacional de recursos e liberar reservas conforme o ciclo de vida.
- Implementar reconciliação do Docker e recuperação após reinício.
- Implementar logs de container.
- Adicionar rede overlay e descoberta entre cargas.
- Suportar volumes persistentes distribuídos.
- Implementar migração/reagendamento de workloads.
- Adicionar auto-scaling e rebalanceamento.
- Adicionar gestão de usuários, permissões reais e multi-tenancy.
- Adicionar auditoria, métricas operacionais, tracing e alertas do backend.
- Tornar WebSocket compatível com múltiplas instâncias.
- Completar Compose com master, dashboard e múltiplos agentes de teste.
- Criar testes automatizados do dashboard e testes ponta a ponta.
- Executar avaliação experimental com máquinas físicas heterogêneas.
- Atualizar README, OpenAPI e roadmap conforme o estado real.
- Definir licença do projeto.

## 22. Glossário

| Termo | Definição no projeto |
|---|---|
| Master/orquestrador | Serviço central que mantém estado e toma decisões |
| Agente | Processo executado em cada máquina participante |
| Nó | Representação persistida de uma máquina/agente |
| Heartbeat | Atualização periódica de disponibilidade e métricas |
| Workload/carga | Container solicitado pelo usuário |
| Comando pendente | Ação derivada do estado persistido: `START`, `STOP` ou `REMOVE` |
| Docker ID | Identificador real do container no daemon do nó |
| Reserva lógica | CPU/RAM/disco solicitados e registrados, sem garantia de dedução transacional atual |
| BFF | Camada Next.js que intermedeia navegador e master |
| WebSocket | Canal de notificação em tempo real do master para o dashboard |

## 23. Fontes internas consultadas

- `README.md`;
- `RELATORIO_CLOUDBOX_MASTER.md`;
- `RELATORIO_CLOUDBOX_AGENT.md`;
- `RELATORIO_CLOUDBOX_DASHBOARD.md`;
- `docs/cloudbox-openapi.yaml`;
- código e testes de `cloudbox-master`;
- código e testes de `cloudbox-agent`;
- código, configuração e tipos de `cloudbox-dashboard`;
- migrations Flyway;
- `pom.xml`, Dockerfiles, `docker-compose.yml` e `railway.json`.

## 24. Estado resumido do MVP

O CloudBox já demonstra o ciclo central de uma plataforma de orquestração: observa nós, agenda uma carga, cria o container no Docker remoto por meio do agente, acompanha estados e oferece controle pelo dashboard. O principal ponto funcional incompleto no contrato de recursos é o disco: ele influencia a escolha do nó, porém ainda não é imposto pelo Docker. Antes de tratar o sistema como pronto para uso real, também são necessárias correções de autenticação do heartbeat, identidade persistente do agente, reserva concorrente de recursos, segurança de credenciais e validação experimental em hardware múltiplo.

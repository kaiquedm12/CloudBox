# CloudBox

> Transforme computadores comuns em uma nuvem privada.

CloudBox é uma plataforma de orquestração de containers que transforma um conjunto de máquinas heterogêneas e não dedicadas (PCs de escritório, notebooks, servidores antigos, máquinas de laboratório) em um pequeno cluster de computação. Um agente instalado em cada máquina monitora os recursos disponíveis (CPU, RAM, armazenamento, temperatura e status) e reporta ao orquestrador central, que decide automaticamente em qual nó cada workload deve ser executado.

Este projeto é desenvolvido como Trabalho de Conclusão de Curso (TCC) em Engenharia da Computação.

---

## Índice

- [Andamento atual](#andamento-atual)
- [Motivação](#motivação)
- [Visão geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Como funciona](#como-funciona)
  - [Agente](#agente)
  - [Orquestrador](#orquestrador)
  - [Algoritmo de agendamento](#algoritmo-de-agendamento)
- [Stack tecnológica](#stack-tecnológica)
- [Escopo do MVP](#escopo-do-mvp)
- [Trabalhos futuros](#trabalhos-futuros)
- [Trabalhos relacionados](#trabalhos-relacionados)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Como rodar localmente](#como-rodar-localmente)
- [Roadmap](#roadmap)
- [Contexto acadêmico](#contexto-acadêmico)
- [Licença](#licença)

---

## Andamento atual

Atualização documental: **14/09/2026**.

O primeiro fluxo completo foi validado pela equipe com o jogo **2048**: o agente cadastrou o computador, enviou seus recursos, o nó apareceu no painel, o CloudBox encaminhou uma solicitação de container e o agente iniciou a aplicação pelo Docker. Segundo o relato da equipe, foi gerado um endereço e o jogo funcionou ao abri-lo em outro navegador. Esse resultado demonstra a integração dos componentes para executar uma aplicação em uma máquina cadastrada e disponibilizá-la pela rede no ambiente do teste.

**Limite de reprodução neste checkout:** o código inspecionado implementa a execução, mas ainda não contém configuração de publicação de portas, campo de URL na API ou link de acesso na tabela do painel. A imagem/tag, o endereço e o mecanismo de exposição usados na demonstração ainda precisam ser registrados. O resultado manual relatado está preservado; a geração automática de endereço não pode ser reproduzida apenas com os arquivos atuais.

| Etapa | Situação e evidência |
|---|---|
| Registro, métricas e heartbeat | Implementados; computador visualizado no teste relatado |
| Agendamento por CPU, RAM, disco e temperatura | Implementado; solicitação encaminhada a um nó no teste |
| Download e execução via Docker | Implementados; jogo 2048 executado no teste relatado |
| Login e dashboard com WebSocket | Implementados; painel utilizado na demonstração |
| Acesso ao jogo pela rede | Validado segundo a equipe; mecanismo de publicação ainda não localizado neste checkout |
| Parar e remover | Implementados; validação manual dessas ações não consta no relato do 2048 |
| Várias máquinas físicas e execução prolongada | Avaliação experimental pendente |

Detalhamento por módulo: [Master](RELATORIO_CLOUDBOX_MASTER.md), [Agent](RELATORIO_CLOUDBOX_AGENT.md) e [Dashboard](RELATORIO_CLOUDBOX_DASHBOARD.md).

## Motivação

É comum existir hardware ocioso em ambientes domésticos, laboratórios e pequenas empresas: notebooks antigos, PCs de escritório fora do horário de expediente, servidores obsoletos. Soluções de orquestração consolidadas como Kubernetes foram desenhadas para clusters homogêneos e dedicados, com uma curva de aprendizado e complexidade operacional que não se justifica para esse cenário.

O CloudBox propõe uma alternativa leve: detectar automaticamente os recursos computacionais disponíveis em máquinas heterogêneas e formar, de maneira ad-hoc, um cluster capaz de executar containers, sem exigir conhecimento prévio de orquestração por parte do usuário.

## Visão geral

```
PC escritório ─┐
PC laboratório ├──► CloudBox
Servidor antigo┤
Notebook ──────┘
```

A plataforma:

1. Detecta CPU, RAM, armazenamento, status e temperatura de cada máquina conectada
2. Forma um cluster com essas máquinas
3. Recebe pedidos como *"executar container com 4 GB de RAM e 2 CPUs"*
4. Escolhe automaticamente qual máquina deve executar o container

## Arquitetura

O sistema é dividido em três componentes principais:

| Componente | Responsabilidade |
|---|---|
| **Agente** | Roda em cada máquina. Coleta métricas de hardware e gerencia a execução de containers localmente via Docker |
| **Orquestrador (master)** | Mantém o registro dos nós, executa o algoritmo de agendamento e expõe a API |
| **Dashboard** | Interface web para visualizar o cluster e solicitar execuções |

```
┌────────────────┐      heartbeat       ┌─────────────────────────────┐
│  Máquina + Agente │ ───────────────▶ │  Registro de nós              │
└────────────────┘                     │  status, recursos, temperatura│
                                        └───────────────┬──────────────┘
                                                         ▼
                                        ┌─────────────────────────────┐
                                        │  Agendador                   │
                                        │  escolhe o melhor nó          │
                                        └───────────────┬──────────────┘
                                                         ▼
                                        ┌─────────────────────────────┐
                                        │  API + dashboard              │
                                        │  requisições e monitoramento  │
                                        └─────────────────────────────┘
```

## Como funciona

### Agente

- Executa como serviço em background em cada máquina do cluster
- A cada intervalo configurável (padrão: 10 s para heartbeat), coleta:
  - CPU disponível
  - RAM disponível
  - Armazenamento disponível
  - Temperatura
  - Status geral do nó
- Envia essas métricas ao orquestrador via **heartbeat** (modelo push) — evita a necessidade de o orquestrador acessar diretamente a máquina, o que facilita o funcionamento atrás de NAT/roteadores domésticos
- Consulta comandos pendentes no master a cada 5 s. Ao receber um comando de execução, aciona a **Docker Engine API** local para subir o container e reporta o resultado ao orquestrador

### Orquestrador

- **Registro de nós**: mantém o estado atual do cluster (quais máquinas estão online, quanto de recurso livre cada uma tem). Um nó é marcado como offline após um timeout sem heartbeat
- **Agendador (scheduler)**: recebe pedidos de execução e decide em qual nó rodar
- **API**: expõe endpoints REST para registro de agentes, solicitação de execução de containers e consulta de status
- **Dashboard**: interface (via WebSocket para atualização em tempo real) mostrando os nós do cluster, seus recursos e os containers em execução

### Algoritmo de agendamento

Para uma solicitação de execução (ex: 4 GB RAM, 2 CPUs), o agendador segue três etapas:

1. **Filtrar**: elimina nós offline ou sem recurso suficiente (RAM livre < solicitado, CPU livre < solicitado, disco livre < solicitado, temperatura acima do limite de segurança)
2. **Pontuar**: entre os nós candidatos, calcula uma pontuação baseada na folga de recursos disponível (estratégia "most available resources", inspirada no agendador padrão do Kubernetes) — evita concentrar carga sempre na mesma máquina
3. **Alocar**: escolhe o nó com melhor pontuação, envia o comando de execução ao agente correspondente e registra o container no banco de dados com seu status

## Stack tecnológica

| Camada | Tecnologia |
|---|---|
| Orquestrador (master) | Java, Spring Boot, PostgreSQL |
| Agente | Java, biblioteca OSHI (coleta de métricas de hardware), docker-java (controle do Docker local) |
| Comunicação | REST (heartbeat e comandos) + WebSocket (atualizações em tempo real no dashboard) |
| Dashboard | Next.js, TypeScript, React |
| Execução de containers | Docker Engine API |
| Autenticação | JWT |
| Ambiente de desenvolvimento | Docker Compose (simula múltiplos nós localmente) |

## Escopo do MVP

**Incluído no MVP:**

- Agente com auto-registro via token
- Coleta e heartbeat de CPU, RAM, disco, temperatura e status
- Agendador com filtro de recursos + pontuação por folga disponível
- API REST para solicitar execução de containers
- Dashboard em tempo real (WebSocket) com visão dos nós e containers
- Ciclo de vida básico de containers: iniciar, parar e remover implementados; consulta de logs pelo painel ainda pendente
- Marcação automática de nó como offline após timeout de heartbeat

## Trabalhos futuros

Itens fora do escopo do MVP, documentados como limitações e possíveis extensões:

- Rede overlay entre containers em nós diferentes
- Migração ao vivo (live migration) de containers entre máquinas
- Volumes persistentes distribuídos entre nós
- Alta disponibilidade do próprio orquestrador
- Suporte multi-tenant com múltiplos usuários e permissões
- Auto-scaling e rebalanceamento automático de carga

## Trabalhos relacionados

O CloudBox se posiciona como uma alternativa mais leve frente a soluções de orquestração consolidadas:

- **Kubernetes**: robusto e amplamente adotado, mas com complexidade operacional e curva de aprendizado elevadas para clusters pequenos e heterogêneos
- **Docker Swarm**: mais simples que o Kubernetes, porém ainda pressupõe que o usuário gerencie o cluster manualmente
- **k3s**: versão leve do Kubernetes, reduz overhead mas mantém o modelo conceitual do Kubernetes
- **Portainer**: interface de gerenciamento de containers, mas não realiza descoberta automática de recursos nem agendamento inteligente entre máquinas heterogêneas

O diferencial do CloudBox é a **descoberta automática de hardware ocioso e heterogêneo**, formando um cluster ad-hoc com uma barreira de entrada baixa para usuários sem experiência prévia em orquestração.

## Estrutura do repositório

```
cloudbox/
├── cloudbox-agent/       # Agente instalado em cada máquina
├── cloudbox-master/      # Orquestrador: registro de nós, agendador, API
├── cloudbox-dashboard/   # Interface web (Next.js)
├── docker-compose.yml    # PostgreSQL local; agentes são iniciados separadamente
└── RELATORIO_CLOUDBOX_*.md # Relatórios de implementação por módulo
```

## Como rodar localmente

Para configurar o endereço do nó, publicar uma porta e acessar o Nginx pelo
dashboard, consulte [Publicação de portas](docs/acesso-servicos.md).
O [plano de evolução](docs/plano-evolucao-servicos.md) registra a divisão do
trabalho e as funcionalidades que ainda não foram implementadas.

### Pré-requisitos

- Java 21 e acesso às dependências do Maven Wrapper (`mvnw`; no Windows, `mvnw.cmd`).
- Node.js e npm compatíveis com o Next.js instalado no dashboard.
- Docker Engine em execução, Compose disponível e permissão do usuário do agente para acessar o Docker. Confira com `docker info` no mesmo ambiente em que iniciará o agente.
- Portas locais 5432 (PostgreSQL), 8080 (master) e 3000 (painel) disponíveis.

Os comandos abaixo são para Bash, a partir da raiz do projeto. O Compose atual sobe **somente o PostgreSQL**.

### 1. Banco e master

```bash
docker compose up -d postgres
docker compose exec postgres pg_isready -U cloudbox -d cloudbox
./mvnw -pl cloudbox-master spring-boot:run
```

Em outro terminal, confira `curl http://localhost:8080/actuator/health`. As migrations V1–V7 são aplicadas na inicialização. O banco local usa nome, usuário e senha `cloudbox`.

### 2. Agente no computador que executará os containers

Em outro terminal:

```bash
export CLOUDBOX_MASTER_URL=http://localhost:8080
export AGENT_NAME=meu-computador
./mvnw -pl cloudbox-agent spring-boot:run
```

Defina a URL explicitamente: o padrão atual do agente aponta para o backend no Railway. Se o master estiver em outra máquina, use o endereço alcançável dessa máquina. `localhost` sempre se refere ao computador onde o processo está rodando.

O agente registra o nó, envia heartbeat a cada 10 s e consulta comandos a cada 5 s. O Docker usa `unix:///var/run/docker.sock` em sistemas Unix ou `npipe:////./pipe/docker_engine` no Windows, salvo configuração de `DOCKER_HOST`. As credenciais são gravadas em `~/.cloudbox/agent-credentials.properties`; atualmente um reinício ainda registra um novo nó, pois a inicialização não recarrega esse arquivo.

### 3. Dashboard e login

Em outro terminal:

```bash
cd cloudbox-dashboard
npm ci
```

Crie ou ajuste `.env.local` com:

```dotenv
ORCHESTRATOR_URL=http://localhost:8080
```

O `.env.example` aponta para Railway; para uso local, substitua esse valor. Inicie com `npm run dev` e abra [http://localhost:3000](http://localhost:3000). A migration V7 cria o usuário local `admin@admin.com`, senha `cloudbox`, caso o e-mail ainda não exista. Entre em `/login` e confira o nó `ONLINE` na visão geral. Em produção, o cookie de sessão exige HTTPS.

### 4. Solicitar e acompanhar uma aplicação

1. Abra `/containers` e o formulário de novo container.
2. Informe a imagem Docker e CPU, RAM (MB) e disco (MB), todos os recursos como inteiros positivos. Para repetir o 2048, use a mesma imagem/tag da demonstração, ainda não registrada neste repositório.
3. Envie a solicitação. O master escolhe um nó com recursos suficientes e cria o registro `PENDING`; sem candidato, retorna `409`.
4. Aguarde a consulta do agente e o download da imagem. A confirmação deve mudar o registro para `RUNNING`; falhas de início podem aparecer como `ERROR` com mensagem.
5. No computador do agente, use `docker ps --filter name=cloudbox-` para conferir a execução. A CPU e a RAM solicitadas são aplicadas como limites Docker; o disco participa do agendamento, mas ainda não é uma quota Docker.
6. Para testar o ciclo de vida, clique em **Parar** (`RUNNING → STOPPING → STOPPED`) e depois **Remover** (`REMOVING → REMOVED`). O registro permanece no histórico.

### 5. Conferir o acesso ao 2048 e registrar evidências

No teste relatado, a equipe abriu o endereço gerado em outro navegador e utilizou o jogo. Para reproduzir essa etapa, é necessário recuperar a configuração de exposição usada no teste: este checkout não publica portas nem fornece uma URL pela API/painel. `RUNNING` confirma a operação de início, mas não verifica a resposta HTTP da aplicação. A URL do master no Railway também não expõe automaticamente os containers executados no computador do agente.

Registre imagem/tag (preferencialmente digest), commit/branch, nó escolhido, recursos solicitados, IDs do registro e do container Docker, porta interna/externa, mecanismo de exposição e URL utilizada. Anexe data/hora, logs e capturas do painel e do jogo. Abrir outro navegador não comprova, por si só, acesso a partir de outra máquina ou pela internet; registre a origem do acesso.

### Consultar a API diretamente

As consultas e ações de usuário exigem JWT. Com `jq` instalado:

```bash
TOKEN=$(curl -fsS http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@admin.com","password":"cloudbox"}' | jq -r '.token')
curl -fsS http://localhost:8080/api/nodes -H "Authorization: Bearer $TOKEN"
curl -fsS http://localhost:8080/api/containers -H "Authorization: Bearer $TOKEN"
```

O token do agente é diferente do JWT de usuário e autentica heartbeat, consulta de comandos e reporte de status. Os contratos também podem ser consultados em `/swagger-ui/index.html` no master.

### Diagnóstico rápido

| Sintoma | Conferência |
|---|---|
| Nó não aparece | Verifique se agente e painel usam o mesmo master e se o registro foi aceito |
| Nó `OFFLINE` | Confira os logs do heartbeat; timeout padrão de 30 s, verificado a cada 10 s |
| API retorna `401` | Faça login novamente e envie o JWT nas chamadas diretas |
| Criação retorna `409` | Confira CPU, RAM e disco livres e temperatura do nó (limite de 75 °C) |
| Container fica `PENDING` | Confira conexão do agente com Docker/master e logs de consulta de comandos |
| Container em `ERROR` | Consulte a mensagem no painel e os logs do agente/download da imagem |
| Painel reconectando | Confira o encaminhamento WebSocket `/ws/*` e a URL do orquestrador; reinicie o Next após alterar o ambiente |
| Jogo não abre | Confira a exposição usada na demonstração, conectividade e portas; o código atual não publica uma porta automaticamente |

## Deploy do backend no Railway

O arquivo `railway.json` configura o build Docker do `cloudbox-master`, o
health check em `/actuator/health` e a politica de reinicio. O backend usa Java
21, executa as migrations do Flyway ao iniciar e escuta a variavel `PORT`
fornecida pelo Railway.

No projeto do Railway, crie um servico PostgreSQL e um servico ligado a este
repositorio. No servico do backend, configure estas variaveis de referencia
(considerando que o banco se chama `Postgres`):

```dotenv
PGHOST=${{Postgres.PGHOST}}
PGPORT=${{Postgres.PGPORT}}
PGDATABASE=${{Postgres.PGDATABASE}}
PGUSER=${{Postgres.PGUSER}}
PGPASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=<segredo-base64-com-pelo-menos-32-bytes>
JWT_EXPIRATION_SECONDS=3600
```

Depois do deploy, gere um dominio publico em **Settings > Networking** e
configure cada agente com a URL criada:

```dotenv
CLOUDBOX_MASTER_URL=https://seu-backend.up.railway.app
```

## Roadmap

- [x] Agente: coleta de métricas (CPU, RAM, disco, temperatura) com OSHI
- [x] Orquestrador: registro de nós e heartbeat
- [x] Orquestrador: algoritmo de agendamento (filtro + pontuação)
- [x] API REST para solicitação de execução de containers
- [x] Integração do agente com Docker Engine API
- [x] Dashboard: visualização do cluster em tempo real
- [x] Dashboard: solicitação de execução de containers
- [x] Primeiro fluxo integrado com 2048 validado pela equipe
- [ ] Consolidar no repositório a publicação de portas/URL usada na demonstração
- [ ] Validar manualmente parada e remoção e repetir build de produção do dashboard
- [ ] Recuperar identidade do agente após reinício e disponibilizar consulta de logs
- [ ] Avaliação experimental: testes com múltiplas máquinas reais
- [ ] Documentação final e redação do TCC

## Contexto acadêmico

Este repositório contém a implementação prática (MVP) desenvolvida como parte do Trabalho de Conclusão de Curso em Engenharia da Computação. O documento acadêmico completo — introdução, referencial teórico, metodologia e avaliação experimental — está disponível separadamente.

## Licença

A definir.

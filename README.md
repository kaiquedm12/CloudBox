# CloudBox

> Transforme computadores comuns em uma nuvem privada.

CloudBox é uma plataforma de orquestração de containers que transforma um conjunto de máquinas heterogêneas e não dedicadas (PCs de escritório, notebooks, servidores antigos, máquinas de laboratório) em um pequeno cluster de computação. Um agente instalado em cada máquina monitora os recursos disponíveis (CPU, RAM, armazenamento, temperatura e status) e reporta ao orquestrador central, que decide automaticamente em qual nó cada workload deve ser executado.

Este projeto é desenvolvido como Trabalho de Conclusão de Curso (TCC) em Engenharia da Computação.

---

## Índice

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
- A cada intervalo configurável (padrão: 5–10s), coleta:
  - CPU disponível
  - RAM disponível
  - Armazenamento disponível
  - Temperatura
  - Status geral do nó
- Envia essas métricas ao orquestrador via **heartbeat** (modelo push) — evita a necessidade de o orquestrador acessar diretamente a máquina, o que facilita o funcionamento atrás de NAT/roteadores domésticos
- Ao receber um comando de execução, aciona a **Docker Engine API** local para subir o container e reporta o resultado ao orquestrador

### Orquestrador

- **Registro de nós**: mantém o estado atual do cluster (quais máquinas estão online, quanto de recurso livre cada uma tem). Um nó é marcado como offline após um timeout sem heartbeat
- **Agendador (scheduler)**: recebe pedidos de execução e decide em qual nó rodar
- **API**: expõe endpoints REST para registro de agentes, solicitação de execução de containers e consulta de status
- **Dashboard**: interface (via WebSocket para atualização em tempo real) mostrando os nós do cluster, seus recursos e os containers em execução

### Algoritmo de agendamento

Para uma solicitação de execução (ex: 4 GB RAM, 2 CPUs), o agendador segue três etapas:

1. **Filtrar**: elimina nós offline ou sem recurso suficiente (RAM livre < solicitado, CPU livre < solicitado, temperatura acima do limite de segurança)
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
- Ciclo de vida básico de containers: iniciar, parar, ver logs, remover
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
├── docker-compose.yml    # Ambiente de desenvolvimento com múltiplos nós simulados
└── docs/                 # Documentação complementar, diagramas, decisões de arquitetura
```

## Como rodar localmente

> Seção a ser detalhada conforme a implementação avança.

```bash
# Clonar o repositório
git clone https://github.com/<usuario>/cloudbox.git
cd cloudbox

# Subir o orquestrador e nós simulados via Docker Compose
docker compose up -d

# Acessar o dashboard
http://localhost:3000
```

## Roadmap

- [ ] Agente: coleta de métricas (CPU, RAM, disco, temperatura) com OSHI
- [ ] Orquestrador: registro de nós e heartbeat
- [ ] Orquestrador: algoritmo de agendamento (filtro + pontuação)
- [ ] API REST para solicitação de execução de containers
- [ ] Integração do agente com Docker Engine API
- [ ] Dashboard: visualização do cluster em tempo real
- [ ] Dashboard: solicitação de execução de containers
- [ ] Avaliação experimental: testes com múltiplas máquinas reais
- [ ] Documentação final e redação do TCC

## Contexto acadêmico

Este repositório contém a implementação prática (MVP) desenvolvida como parte do Trabalho de Conclusão de Curso em Engenharia da Computação. O documento acadêmico completo — introdução, referencial teórico, metodologia e avaliação experimental — está disponível separadamente.

## Licença

A definir.

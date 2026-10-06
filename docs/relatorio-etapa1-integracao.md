# Relatório de integração — etapa 1

Data: 06/10/2026. Responsável neste tópico: Maestro (raiz, scripts e documentação).

## Resultado e escopo

**Aceite local pela API aprovado em sete verificações, com PostgreSQL e Docker reais.**
O Nginx respondeu HTTP 200 no endpoint informado pelo master. A etapa 1 completa
continua aberta: faltam admissão de nós, identidade do agente após reinício e o
fluxo pelo dashboard com acesso de outra máquina. Etapas 2–6 não foram iniciadas.

O ensaio usa `scripts/accept-service-ports.mjs`, um PostgreSQL temporário sem volume,
master/agente dos JARs locais e quatro solicitações reais ao scheduler. Credenciais
são aleatórias e as portas ficam em loopback. Nenhum módulo, migration, configuração
existente de implantação ou dado de desenvolvimento foi alterado.

## Organização da entrega

- Base preservada: `feat/atualizações`, commit `d530eb057f0266029a40c9c3f80fb04c7f5c95ff`.
- Branch deste tópico: `feat/etapa1-aceite-integracao`.
- Commits separados: `4c8166e` (`test(integration)`) para a automação e um commit
  `docs` para relatórios e evidências.
- O histórico usa branches `feat/*`/`feature/*` e commits com tipo/escopo; não foi
  encontrado um guia adicional de Git neste checkout. Nenhum push ou merge faz
  parte desta entrega. A base atual foi mantida para preservar a integração existente.

## Evidências desta execução

Comandos executados a partir da raiz:

```bash
./mvnw -B -pl cloudbox-master,cloudbox-agent -am package
node --check scripts/accept-service-ports.mjs
node scripts/accept-service-ports.mjs --help
node scripts/accept-service-ports.mjs
git diff --check
```

Build Maven aprovado: **66 testes no master e 31 no agente**, sem falhas, erros ou
testes ignorados. Esses resultados substituem as contagens históricas para este
checkout. TypeScript, build e navegador do dashboard não foram repetidos.

| Critério | Evidência real |
| --- | --- |
| Schema e autenticação | Flyway V1–V9 aplicadas em PostgreSQL 16.15; Hibernate iniciou com validação; login retornou JWT |
| Registro e heartbeat | Agente real ficou ONLINE; heartbeat sem token recebeu HTTP 401 |
| Porta automática | Nginx RUNNING; 80/TCP mapeada em `127.0.0.1:43007`; GET retornou HTTP 200 e página padrão; binding conferido por inspeção Docker |
| Porta explícita ocupada | Segunda solicitação para a mesma porta recebeu ERROR, mensagem de binding e endpoints vazios |
| Requisição sem portas | RUNNING com endpoints vazios e sem bindings no Docker |
| INTERNAL | RUNNING com endpoints vazios e sem publicação no host |
| Persistência | Reinício do master preservou as respostas de containers (incluindo portas, endpoints e erro) e o endereço anunciado do nó |

Execução aprovada: `5226a715-e388-4881-8dd9-f758f510fa29`, de
`2026-10-06T18:25:27.298Z` a `2026-10-06T18:27:21.549Z` (15:25–15:27, Brasília).
Ambiente: Java 21.0.12.1, Node.js 24.13.0 e Docker Desktop/Engine 29.7.2, socket
Unix local. O código dos módulos corresponde ao commit base acima; a automação e
os relatórios estavam no worktree, por isso a evidência registra `dirtyWorktree: true`.

Registro sanitizado versionado: [etapa1-2026-10-06.json](evidencias/etapa1-2026-10-06.json),
com digests das imagens, UUIDs, IDs Docker e resultados. Logs privados desta rodada:
`/tmp/cloudbox-ports-pUk7WW`. Esse diretório pode ser descartado pelo sistema; o
resumo versionado preserva a evidência. Não versionar credenciais nem logs integrais.

A primeira tentativa concluiu migrations/login/heartbeat e parou antes de criar
workloads. O agente reportava 83 °C, acima do limite padrão de 75 °C. A automação
passou a aguardar elegibilidade e registrar os recursos observados, mantendo o
limite do scheduler. A rodada aprovada prosseguiu depois da queda de temperatura.

Limpeza aprovada: processos Java e containers temporários removidos, banco efêmero
descartado sem recuperação. Imagens baixadas e evidências preservadas. A conferência
final do Docker não encontrou recursos desse ensaio; os containers preexistentes
foram preservados. O endereço do Nginx acima é evidência histórica e já não está ativo.

## Pendências encaminhadas por responsabilidade

| Responsável | Próximo tópico e limite do resultado atual |
| --- | --- |
| Orquestrador | Definir admissão de nós e atualização autenticada de advertiseAddress. O registro público ainda permite anunciar um destino sem comprovar propriedade |
| Agent | Restaurar identidade persistida no startup. `NodeRegistrationService` ainda não chama `AgentTokenStorage.load()`; labels evitam certas duplicações de containers, mas não resolvem o novo registro de nó |
| Dashboard | Repetir o percurso de criação e abertura com backend real, inclusive mudança de endpoint mantendo RUNNING e nó offline. O código foi inspecionado; não houve nova sessão de navegador |
| Maestro | Coordenar os contratos dessas correções e executar `smoke-service-ports.mjs` de outra máquina com endereço alcançável, registrando origem, rede e evidências |

As correções nos módulos devem ser executadas pelos respectivos responsáveis;
este tópico não modifica suas pastas. A restauração da identidade também exige
definir como tratar credenciais rejeitadas e mudanças do endereço anunciado junto
ao Orquestrador, antes de implementar comportamentos incompatíveis.

O ensaio não prova quota de disco, reserva consistente, health check, restart do
Docker, recuperação após reinício do agente, isolamento entre nós físicos ou
failover. Os recursos são coletados no processo do agente, enquanto o Docker
Desktop executa containers na sua VM: essa topologia local não representa dois nós
independentes nem comprova equivalência da capacidade medida à capacidade da VM.

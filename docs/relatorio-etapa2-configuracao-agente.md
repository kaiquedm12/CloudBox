# Etapa 2 — configuração básica no agente

Data: 08/10/2026. Entrega parcial: environment, command/args e restart no
consumidor de comandos e na execução Docker. **A etapa 2 completa não está pronta**:
master/dashboard ainda não oferecem essa configuração pela API/interface.

## Escopo e organização

- Branch: `feat/etapa2-configuracao-agente`, baseada em `origin/main` no commit
  `d4682cadc1c19a7bf64de497b6b5b1fd3de14d38`. O fetch inicial confirmou ausência de
  commits pendentes da main. O nome inicial `feat/etapa2-contrato-servicos` foi
  ajustado antes da publicação para refletir a implementação no agente.
- O tópico começou pela proposta compartilhada e continuou no
  `PendingCommandPoller`, indicado pelo usuário. Código alterado somente em
  `cloudbox-agent/`; raiz, documentação e testes do contrato compõem a integração.
- Master, dashboard e migrations não foram editados. Arquivos protegidos foram
  preservados. A próxima migration deve ter sua versão conferida pelo Orquestrador.
- O [protocolo de entregas](protocolo-entregas.md) exige novo fetch/comparação da
  main antes do push, commits coesos e publicação da branch, sem push direto na main.

## Implementado

1. `PendingCommand` transporta environment, command/args, restart e capacidades
   exigidas, mantendo construtores e padrões compatíveis com comandos antigos.
   Campos desconhecidos e opções ainda não suportadas são rejeitados, não ignorados.
   Coleções vazias de segredos/volumes e probe null são compatíveis; um pedido real
   dessas funcionalidades não chega à criação Docker. Quota obrigatória é rejeitada.
2. Environment valida nomes, valores string, NUL, quantidade e limite de 64 KiB.
   Command/args são arrays, sem conversão automática para shell nem coerção de
   números/booleanos para strings. Restart aceita NONE, ALWAYS, UNLESS_STOPPED e
   ON_FAILURE, com tentativas limitadas e rejeição de propriedades desconhecidas.
3. O executor aplica Env, Entrypoint, Cmd e RestartPolicy. Command fornecido sem
   args limpa o Cmd herdado; sem ambos, preserva os padrões da imagem.
4. A identidade da especificação inclui SHA-256 da configuração adicional, sem
   copiar seus valores para labels. A identidade antiga é preservada quando não
   há opções novas. Repetir a mesma especificação reutiliza o container; alterá-la
   com o mesmo identificador é rejeitado. O hash não é armazenamento seguro de
   segredos; environment permanece destinado a valores comuns e é inspecionável.
5. O poller preserva resultado e especificação durante retentativas de reporte,
   evitando nova criação e recusando confirmar uma especificação diferente.
   Falhas semânticas de um comando não impedem os demais comandos válidos do lote.
   JSON incompatível com os tipos impede a desserialização do lote inteiro.
6. Registro e heartbeat anunciam ENVIRONMENT, COMMAND_ARGS e RESTART_POLICY, com
   flags de quota false. O master atual ainda não persiste nem usa essas capacidades
   para selecionar nós; isso é requisito para ativar a configuração pela API.

## Contrato compartilhado

O [contrato candidato](service-configuration-contract.md), o
[OpenAPI proposto](cloudbox-stage2-proposal.openapi.yaml), o
[JSON Schema](contracts/service-configuration.schema.json) e os
[exemplos](contracts/stage2-examples.json) detalham a evolução completa da etapa.
Segredos, volumes, saúde e quotas nesses arquivos são propostas, não funcionalidades
implementadas. O OpenAPI vigente apenas aponta para a proposta separada; novas
rotas não foram apresentadas como disponíveis. Revisão do Orquestrador permanece
necessária antes de promover o contrato.

## Verificações executadas

- **46 testes Java aprovados**, zero falhas/erros/ignorados, com build do agente.
  Incluem desserialização HTTP real, validações, aplicação de opções, idempotência
  e retentativa de reporte.
- Um desses testes é opt-in e executou **quatro cenários em Docker real**: preservar
  Entrypoint/Cmd, substituir somente command, somente args e ambos. Inspecionou env,
  restart ON_FAILURE com três tentativas, ausência de endpoints e reutilização do
  mesmo ID; recusou a configuração alterada. Os containers temporários desse teste
  foram removidos, sem volumes nem dados persistentes.
- **52 testes do contrato aprovados**, incluindo exemplos válidos, casos negativos,
  regras semânticas e referências OpenAPI. São testes da proposta, não comprovação
  de autorização, criptografia ou persistência implementadas no master.
- Docker Engine observado: 29.8.2, socket local `unix:///var/run/docker.sock`.
- **Regressão integrada da etapa 1 aprovada**, com PostgreSQL, master, agente e
  Docker reais: migrations V1–V9/login, registro e heartbeat autenticado, Nginx com
  HTTP 200 em porta automática, erro de porta ocupada, legado sem portas, INTERNAL
  sem binding e persistência após reinício do master. A
  [evidência sanitizada](evidencias/etapa1-regressao-2026-10-08.json) registra sete
  verificações e limpeza concluída. O campo commit aponta para a base d4682ca com
  worktree alterado: o ensaio começou antes dos commits, usando esta implementação.
  Banco temporário e containers do ensaio foram removidos; imagens e logs privados
  ficaram preservados. Não foram removidos dados ou recursos preexistentes.

A primeira tentativa de regressão foi invalidada por reconstrução do JAR enquanto
o agente o utilizava: surgiram `NoClassDefFoundError` e timeout no caso legado.
A segunda execução ocorreu com build já concluído e passou. A evidência aprovada
não substitui teste de navegador/dashboard, acesso de outra máquina ou aceite
completo da etapa 2. Não reconstruir JARs durante um ensaio que os esteja usando.

Comandos de reprodução (executar o build antes do ensaio, nunca simultaneamente):

```bash
npm ci --ignore-scripts --prefix scripts/contracts
npm test --prefix scripts/contracts
CLOUDBOX_TEST_DOCKER_HOST=unix:///var/run/docker.sock ./mvnw -B -pl cloudbox-agent -am package
./mvnw -B -pl cloudbox-master,cloudbox-agent -am package -DskipTests
node scripts/accept-service-ports.mjs
```

Sem `CLOUDBOX_TEST_DOCKER_HOST`, o teste Docker é ignorado explicitamente; a suíte
isolada pode ser executada com `./mvnw -B -pl cloudbox-agent -am test`.
O teste Docker requer daemon acessível e baixa `nginx:alpine` quando necessário.

## Limitações e próximo tópico

- O teste inspeciona a política de restart configurada; não simula crash do processo
  nem reinício do daemon. Não comprova monitoramento contínuo ou restauração da
  identidade do nó após reinício do agente.
- Não há CRUD/resolução de segredos, volumes persistentes, probes ou quotas nesta
  implementação. Não foi realizado o aceite PostgreSQL com segredo e recuperação
  de dados. Não enviar a proposta de criação à API atual esperando esse comportamento.
- Próximo tópico do Orquestrador: revisar o contrato básico, validar/persistir
  environment, command/args e restart, propagar ao START e selecionar apenas nós
  com capacidades compatíveis. Rejeitar opções não implementadas na API. Depois,
  o Dashboard pode habilitar os mesmos campos.
- Antes da ativação de segredos e dos demais recursos: concluir admissão de nós,
  autorização/TLS, identidade persistente, afinidade/retenção de volumes e saúde
  contínua. Disco solicitado continua sem garantia de reserva consistente ou quota.

## Entrega

Commits de contrato e implementação: `5d234b8` e `40b7992`, respectivamente.
Nova conferência remota antes da publicação manteve `origin/main` em `d4682ca`,
sem alterações a integrar. Relatórios e protocolo ficam em commit próprio.
Destino: [branch no GitHub](https://github.com/kaiquedm12/CloudBox/tree/feat/etapa2-configuracao-agente).

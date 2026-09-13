# Evolução do CloudBox: divisão por terminal

Status: etapa 1 implementada no código (portas, advertiseAddress, endpoints e dashboard), com testes Java e verificação da interface. A demonstração com Docker/PostgreSQL reais e acesso por outra máquina permanece pendente por ausência de infraestrutura ativa. Etapas 2–6 continuam planejadas. Guia da entrega: [acesso-servicos.md](acesso-servicos.md).

## Responsabilidade e limites de edição

| Terminal | Pasta principal | Responsabilidade |
| --- | --- | --- |
| Orquestrador | `cloudbox-master/` | API, contrato de serviço, persistência, registro de nós, scheduler, autorização, estado desejado, aplicações, réplicas e reconciliação. |
| Agent | `cloudbox-agent/` | Traduzir o contrato em operações Docker, publicar portas, inspecionar endpoints, executar health checks, administrar volumes e redes e reportar estado observado. |
| Dashboard | `cloudbox-dashboard/` | Formulários, validação para o usuário, endpoints de acesso, saúde, logs, aplicações e acompanhamento de recuperação. |
| Maestro | raiz e `docs/` | Coordenar contratos entre os três módulos, atualizar OpenAPI em conjunto com o Orquestrador, integrar entregas, infraestrutura de demonstração, gateway, documentação e testes ponta a ponta. |

Cada terminal altera sua pasta. Alterações em outra área devem ser encaminhadas ao responsável. Maestro mantém os arquivos compartilhados; o Orquestrador especifica as mudanças da API para atualização de `docs/cloudbox-openapi.yaml`. Não são necessários novos terminais.

Preservar as alterações já existentes em `cloudbox-master/src/main/resources/application.yml`, `.env.example`, `Dockerfile`, `AdminUserInitializer.java` e `V8__remove_insecure_default_admin.sql`. Novas migrations devem usar a próxima versão disponível, sem sobrescrever migrations existentes.

## Base encontrada no código

- `ContainerRequest`, `PendingCommandResponse` e `PendingCommand` transportam apenas imagem, CPU, RAM e `diskMb`.
- `ContainerExecutionService.runContainer` aplica CPU/RAM; não configura portas, volumes ou health check.
- `NodeRegisterRequest` não contém endereço anunciado.
- `ContainerStatusReporter` reporta a criação, mas ainda é necessário monitoramento contínuo do estado observado.
- `ContainerService` persiste o disco solicitado, mas agenda considerando CPU e RAM. Isso não garante reserva ou quota de disco.
- O dashboard usa `types/container.ts`, `lib/container-schema.ts`, `components/create-container-modal.tsx` e `components/container-list.tsx`.
- `docker-compose.yml` provisiona somente o PostgreSQL do master; os agentes simulados estão comentados.
- O heartbeat está liberado na configuração de segurança e o controller não valida o token do agente. O registro também é público: definir admissão de nós e validação do endereço anunciado antes de tratá-lo como destino confiável.
- A revisão do terminal Agent encontrou o Docker CLI, mas o socket do contexto Docker Desktop estava indisponível. O aceite com containers reais depende de um daemon acessível; isso não impede a preparação do código e dos testes isolados.

## Contrato compartilhado: definir antes da implementação paralela

Evoluir inicialmente os endpoints atuais de containers com campos opcionais e padrões compatíveis. O conceito de serviço pode começar como um objeto de domínio, sem exigir imediatamente uma nova API de deployments.

Separar especificação desejada de resultado observado:

- Especificação: `imageName`, recursos, `ports`, `environment`, `secretRefs`, `volumes`, `restartPolicy`, `healthCheck`, `command`, `args` e `network`.
- Resultado: identificador Docker, nó, endpoints realmente publicados, estado do processo, saúde e erro sanitizado.
- Proposta de `ports`: `containerPort` entre 1 e 65535, `hostPort` opcional, `protocol` TCP/UDP, `exposure` HTTP/TCP/UDP/INTERNAL e `bindAddress` opcional. HTTP exige TCP; exposição TCP/UDP deve corresponder ao protocolo. `INTERNAL` não publica porta no host. Esses enums precisam ser fixados no OpenAPI antes de codificar.
- `advertiseAddress`: endereço do nó alcançável pelo cliente/gateway. `bindAddress`: interface local em que o Docker publica. São configurações diferentes; não anunciar `0.0.0.0` como endereço de acesso.
- `hostPort: null`: o Docker aloca a porta; o agente inspeciona após iniciar e reporta a associação real. Uma porta explícita ocupada produz erro compreensível.
- `endpoints`: lista com `containerPort`, `hostPort`, `protocol`, `address` e `url` opcional. URL somente para HTTP/HTTPS configurado; um endpoint TCP/UDP deve poder ser copiado sem botão de navegador.
- O master valida o nó atribuído antes de aceitar status/endpoints. Não confia em URLs arbitrárias fornecidas pelo cliente.
- `environment`: valores comuns. `secretRefs`: identificação do segredo e destino de injeção, nunca seu valor na especificação pública.
- `volumes`: nome lógico, destino absoluto no container, modo de leitura e tamanho persistente solicitado quando aplicável. Definir identidade, vínculo com nó e política de retenção.
- `healthCheck`: tipo HTTP/EXEC, parâmetros próprios, intervalo, timeout, período inicial e tentativas. Estados de saúde separados do processo: UNKNOWN, STARTING, HEALTHY, UNHEALTHY; serviços sem probe não recebem HEALTHY fictício.
- Definir `command`/`args` como arrays e documentar correspondência com Entrypoint/Cmd do Docker. Evitar conversão implícita para shell.
- `network`: identificador lógico de rede da aplicação e aliases de serviço, resolvidos pelo agente no nó escolhido.
- `ephemeralDiskMb` representa camada gravável; `volumeSizeMb`, armazenamento persistente. Definir transição de `diskMb` e rejeitar valores conflitantes. Não renomear o campo em um único módulo.
- Distinguir pedido de reserva contábil de pedido de limite obrigatório. Ausência de capacidade comprovada deve impedir prometer ou aceitar uma garantia de quota.

O Orquestrador propõe os formatos; Maestro registra exemplos e critérios no OpenAPI; Agent e Dashboard conferem o consumo. Não aceitar silenciosamente um campo que ainda não produz o comportamento solicitado.

## Etapa 1 — Nginx acessível pela rede

**Orquestrador**

- Ampliar DTOs de criação, comandos pendentes, registro de nó, atualização de status e respostas.
- Persistir endereço anunciado, portas desejadas e endpoints observados em entidades/migrations.
- Preservar requisições antigas sem portas. Validar combinações de protocolo/exposição, endereços e propriedade do container.
- Autenticar o heartbeat pelo token do nó e definir admissão/atualização de endereços anunciados; não usar o registro público atual como prova de confiança do destino.
- Atualizar notificações do cluster quando endpoints mudarem, mesmo sem mudança de RUNNING.

Arquivos de partida: `container/dto/*`, `ContainerInstance`, `ContainerService`, `node/dto/*`, `Node`, `NodeService`, controllers, `realtime/*` e `db/migration/`.

**Agent**

- Configurar e registrar `advertiseAddress`; separar a política de binding da publicação do endereço.
- Ampliar `PendingCommand` e a chamada a `runContainer` para transportar portas.
- Criar bindings Docker e inspecionar as portas efetivas após iniciar.
- Reportar endpoints e reaproveitá-los nas tentativas de reporte; evitar duplicação após reinício do agente, usando identidade estável/labels e inspeção.

Arquivos de partida: `docker/ContainerExecutionService`, `client/*`, `registration/NodeRegistrationService`, `command/PendingCommandPoller`, `command/ContainerStatusReporter` e configuração do agente.

**Dashboard**

- Ampliar tipos, schema e modal de criação para porta interna, porta do host opcional e exposição.
- Mostrar endereço real na lista/detalhe e botão “Abrir aplicação” para URL HTTP(S) válida retornada pelo master.
- Exibir estados sem endpoint, erro de binding e endereço indisponível.
- Atualizar os dados quando endpoints mudarem, inclusive mantendo o estado RUNNING.

**Maestro / aceite**

- Integrar contrato e verificar o fluxo dashboard → master → agente → Docker → master → dashboard.
- Subir `nginx:alpine`, publicar 80 em porta automática e acessar de outra máquina pelo endereço reportado.
- Conferir porta explícita ocupada, ausência de portas e exposição INTERNAL sem binding.
- Validar a topologia de demonstração: agentes que usam o mesmo Docker socket compartilham daemon e portas e não representam nós fisicamente independentes.

## Etapa 2 — Serviço configurável e persistente

**Orquestrador:** persistir environment/referências, criar entidade Secret criptografada com chave externa ao banco e acesso autorizado, disponibilizar resolução restrita ao agente responsável, modelar volumes com afinidade ao nó, restart, probes e comando. Receber saúde continuamente.

**Agent:** aplicar env, resolver segredos por canal autenticado e protegido, montar volumes nomeados, configurar restart/probes/comando, inspecionar saúde periodicamente e redescobrir containers após reinício. Descobrir capacidades do storage e rejeitar limites obrigatórios não suportados.

**Dashboard:** seções avançadas de configuração, seleção de segredos por nome/referência, seleção de volume, status de saúde separado e indicação honesta de disco reservado versus limitado. Segredos cadastrados não são devolvidos em campos de edição.

**Maestro / aceite:** demonstrar PostgreSQL com segredo e volume, gravar dados, remover/recriar apenas o container e recuperar os dados. Validar RUNNING com UNHEALTHY, recuperação de saúde, restart do Docker e rejeição de quota não suportada. Não remover volume na exclusão comum do container.

Definir o executor do probe HTTP explicitamente: não supor `curl`/`wget` disponível em qualquer imagem. Segredos injetados em variáveis podem ser vistos por administradores do Docker; documentar esse limite, restringir inspeção e preferir arquivos quando a aplicação oferecer suporte.

## Etapa 3 — Logs e inspeção

**Orquestrador:** endpoint autenticado, autorização por container/nó, comandos de consulta com correlação, timeout e limite de retorno, respeitando o modelo atual de polling do agente.

**Agent:** coletar stdout/stderr com limites de linhas/bytes/tempo, responder consultas e devolver apenas campos de inspeção permitidos. Não retornar configuração Docker completa com variáveis secretas.

**Dashboard:** painel de logs com carregar/atualizar, erro, vazio, indisponibilidade e encerramento da consulta ao sair da tela.

**Maestro / aceite:** verificar acesso indevido negado, limite de payload, ausência de exposição de configuração secreta e comportamento quando o nó está offline. Logs produzidos pela própria aplicação podem conter dados sensíveis; filtragem não deve ser anunciada como proteção absoluta.

## Etapa 4 — Aplicações e redes por aplicação

**Orquestrador:** Application/Deployment, serviços, dependências, estado desejado e política de posicionamento. No primeiro modelo de rede local, colocar serviços dependentes no mesmo nó; só distribuir com conectividade entre nós definida. Detectar dependências cíclicas e distinguir ordem de criação de prontidão.

**Agent:** criar/reutilizar redes bridge com labels de propriedade, conectar serviços com aliases, preservar volumes e remover redes somente quando não houver consumidores.

**Dashboard:** criação e detalhe da aplicação com seus serviços, dependências, rede, saúde e endpoints.

**Maestro / aceite:** WordPress + MySQL/MariaDB na rede da aplicação, banco sem publicação no host, comunicação pelo nome e persistência após recriação. Alternativa PostgreSQL: API Spring + PostgreSQL.

## Etapa 5 — Gateway, domínio e HTTPS

**Orquestrador:** registrar domínio/rota, selecionar endpoints saudáveis e atualizar destinos quando mudarem. Produzir configuração dinâmica para o gateway.

**Agent:** continuar reportando endpoints privados alcançáveis pelo gateway; informar falhas de rede e saúde.

**Dashboard:** mostrar domínio, URL HTTPS e estado de provisionamento/certificado somente após confirmação.

**Maestro:** provisionar Traefik, conectividade, DNS e certificados; integrar configuração dinâmica com atualização atômica e validação. Não presumir que labels em daemons independentes são descobertas por um único provider Docker.

**Aceite:** domínio resolve para o gateway, certificado é confiável no cliente, somente backends saudáveis recebem tráfego e mudança de endpoint atualiza a rota. Para laboratório, definir DNS e CA interna confiada pelos clientes; domínio local sozinho não fornece HTTPS válido.

## Etapa 6 — Comunicação entre nós, réplicas e recuperação

**Orquestrador:** registro de serviços, posicionamento entre nós, reconciliação desejado/observado, leases ou mecanismo equivalente de fencing, identificação de tentativas, limite/backoff e reserva consistente de recursos. Remover destinos indisponíveis do gateway e impedir que reportes antigos sobrescrevam a instância substituta.

**Agent:** anunciar endereço privado/VPN, executar comandos idempotentes, reportar identidade da instância/tentativa e tratar instâncias antigas no retorno de um nó.

**Dashboard:** mostrar réplicas desejadas/disponíveis, nós, eventos de recuperação e falhas que impedem reagendamento.

**Maestro:** preparar rede privada entre nós e gateway, firewall/TLS e ensaio com falhas reais. Manter CloudBox responsável pelo scheduling; Swarm fica como alternativa arquitetural, não dependência implícita.

**Aceite:** desligar o nó de um serviço stateless, observar substituição em outro nó, saúde confirmada e gateway atualizado. Religar o nó original e verificar ausência de instância antiga recebendo tráfego indevido. Testar também partição de rede, que difere de desligamento físico.

Volumes Docker locais não acompanham um serviço para outro nó. O scheduler deve respeitar essa restrição. Não prometer recuperação do banco em outro nó sem armazenamento compartilhado, replicação ou restauração validada. WordPress também pode manter uploads/plugins no filesystem: sua estratégia de persistência precisa existir antes de demonstrar failover sem perda de conteúdo.

## Sequência de integração

1. Fixar contrato e compatibilidade da etapa, com exemplos de entrada, comando e retorno.
2. Implementar master e agente em paralelo; dashboard usa os mesmos exemplos para montar a interface.
3. Cada terminal executa testes pertinentes e entrega arquivos alterados, evidências e limitações.
4. Maestro integra os módulos e valida o critério de aceite da etapa em ambiente disponível.
5. Registrar o que foi comprovado e o que depende de máquinas/rede externas antes de avançar.

## Referências para as decisões de implementação

- Publicação e interfaces: [Docker — Port publishing](https://docs.docker.com/engine/network/port-publishing/).
- DNS de containers no mesmo host: [Docker — Bridge network driver](https://docs.docker.com/engine/network/drivers/bridge/).
- Rotas geradas pelo master: [Traefik — File provider](https://doc.traefik.io/traefik/providers/file/).
- Compatibilidade do exemplo: [WordPress — FAQ Installation](https://wordpress.org/documentation/article/faq-installation/) exige MySQL/MariaDB para a instalação padrão.

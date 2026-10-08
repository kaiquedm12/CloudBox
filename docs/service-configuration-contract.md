# Etapa 2 — proposta de contrato para serviço configurável e persistente

Estado: **proposta testável para revisão do Orquestrador, Agent e Dashboard**.
Este tópico iniciou no Maestro e foi ampliado para o agente por solicitação do usuário
no `PendingCommandPoller`. O agente já consome environment, command/args e restart;
master/dashboard ainda não criam/persistem esses campos. Não enviar os exemplos
novos à API atual esperando sua execução. O Orquestrador deve revisar os formatos antes de promovê-los ao contrato
vigente, conforme a divisão de responsabilidades do projeto.

Estado da implementação parcial e testes: [relatório do agente na etapa 2](relatorio-etapa2-configuracao-agente.md).

Artefatos compartilhados:

- [OpenAPI candidato](cloudbox-stage2-proposal.openapi.yaml), recorte de rotas propostas;
- [JSON Schema](contracts/service-configuration.schema.json), versão `proposal.1`;
- [exemplos completos](contracts/stage2-examples.json), incluindo entrada, START,
  resposta RUNNING/UNHEALTHY e referências sem valores secretos;
- [contrato vigente](cloudbox-openapi.yaml), mantido sem novas rotas/campos ativos.

## Compatibilidade e ativação

Preservar os endpoints existentes de containers. `environment`, `secretRefs`,
`volumes`, `restartPolicy`, `healthCheck`, `command` e `args` são opcionais.
Coleções omitidas/null normalizam para `{}`/`[]`; restart omitido/null normaliza
para `NONE` com zero tentativas. `command`, `args` e `healthCheck` mantêm null quando
ausentes. Respostas e STARTs novos levam especificação normalizada e metadados
observados separados. STOP/REMOVE preservam o contrato atual.

`diskMb` continua aceito como alias legado de `ephemeralDiskMb`, ambos inteiros
positivos. Um deles é obrigatório; se ambos vierem, devem ser iguais. Durante a
transição, respostas/STARTs novos retornam os dois com o mesmo valor. Não renomear
isoladamente em um módulo. `volumeSizeMb` pertence ao cadastro de volume e não
substitui a camada gravável.

`diskMode` tem dois valores propostos: `REQUEST_ONLY` (padrão, pedido sem garantia
de reserva/quota) e `REQUIRED` (limite obrigatório). Não chamar REQUEST_ONLY de
"reservado". Nesta implementação inicial, REQUIRED fica indisponível: o master
deve responder 409 enquanto não houver adapter de storage testado que imponha o
limite; o agente também deve falhar antes de criar recursos. Tamanho de volume
sem REQUIRED é metadado de planejamento, não provisionamento físico garantido.

Rejeitar campos desconhecidos, valores conflitantes e funcionalidades desativadas
com 400. DTO aceitando um campo não basta para ativá-lo. O master deve selecionar
somente nós capazes de executar a especificação inteira, antes de persistir PENDING.

`capabilities` será acrescentado ao registro, heartbeat e resposta de nó, com
`contractVersion: 2`, lista `features` e flags de quota. Nós antigos, sem essa
informação, equivalem a lista vazia e quotas false. Capacidade nova exige identidade
autenticada, admissão e comprovação local; uma flag não prova posse do endereço.

| Especificação efetiva | Capacidade exigida |
| --- | --- |
| Environment não vazio | ENVIRONMENT |
| Command ou args fornecido | COMMAND_ARGS |
| Restart diferente de NONE | RESTART_POLICY |
| Referência injetada em variável | SECRET_ENV |
| Referência injetada em arquivo | SECRET_FILE |
| Volume nomeado | NAMED_VOLUMES |
| Probe EXEC / HTTP | HEALTH_EXEC / HEALTH_HTTP |

Sem nó capaz, retornar 409 sem emitir START parcial. O agente confere novamente
`requiredCapabilities` antes de produzir efeitos. Features só são anunciadas após
implementação/teste, nunca pelo fato de o código desserializar seus nomes. Workloads
legados continuam usando o comando vigente quando atribuídos a agentes antigos.

## Environment, command e args

Environment é um mapa de strings comuns. Até 128 chaves no formato
`[A-Za-z_][A-Za-z0-9_]*` (128 caracteres); até 4096 caracteres por valor, sem NUL;
máximo de 64 KiB UTF-8 no JSON do mapa. Valores comuns continuam visíveis na
especificação pública. Não tentar detectar se um valor digitado é um segredo.

`command` e `args` são arrays de 1–64 strings, com até 4096 caracteres por item,
sem NUL. Rejeitar primeiro item em branco e listas vazias. Não adicionar shell,
expandir variáveis nem concatenar argumentos implicitamente.

| command | args | Entrypoint / Cmd que o agente deve aplicar |
| --- | --- | --- |
| null | null | Preservar ambos da imagem |
| null | array | Preservar Entrypoint; substituir Cmd pelo array |
| array | null | Substituir Entrypoint; limpar Cmd herdado explicitamente |
| array | array | Substituir Entrypoint e Cmd pelos respectivos arrays |

Essas são decisões de contrato do CloudBox, que devem ter testes de inspeção Docker
para as quatro combinações. Shell só existe quando solicitado explicitamente,
por exemplo `command: ["/bin/sh", "-c"]` e `args: ["printf hello"]`. Referência de
mapeamento: [Docker — ENTRYPOINT/CMD](https://docs.docker.com/reference/dockerfile/#understand-how-cmd-and-entrypoint-interact).

## Segredos e autorização

Proposta inicial para o cluster compartilhado: somente ADMIN cadastra/lista
segredos e cria containers que os referenciam. Não há isolamento multi-tenant
novo nesta etapa; usuários USER não podem consultar referências ou valores por
atalhos de listagem. O Orquestrador deve aplicar a autorização também à listagem
de containers/configurações sensíveis ou restringir esses recursos a ADMIN.

`POST /api/secrets` recebe `{name, value}`; `value` é writeOnly. A resposta e
`GET /api/secrets` contêm apenas `id`, `name`, `version` e `createdAt`. Não retornar
valor, hash, ciphertext, nonce ou identificador de chave na API pública. Nomes são
únicos; entradas são imutáveis. Rotação usa novo cadastro e recriação explícita do
container; não alterar silenciosamente o valor referenciado por workloads existentes.

Armazenar ciphertext autenticado (proposta: AES-256-GCM), nonce novo por operação,
tag, versão e keyId. UUID/versão devem ser associados à autenticação criptográfica.
Material da chave fica fora do banco/repositório/logs e do JWT; recuperar o banco
sem a chave não basta. Chave ausente/incorreta bloqueia a operação, sem fallback
para texto aberto. O formato e ciclo de rotação da chave precisam ser revisados
pelo Orquestrador antes da implementação.

`secretRefs` contém UUID e destino, nunca valor. `ENV` exige `envName`; `FILE`
exige `filePath` restrito a `/run/cloudbox-secrets/<nome>`. Rejeitar destinos
repetidos e colisão com environment ou montagem de volume. Um mesmo segredo pode
ter destinos diferentes explicitamente solicitados.

O agente resolve por `POST /api/containers/{id}/secrets/resolve`, corpo `{}`:
token do nó atribuído, nó admitido, estado PENDING/RUNNING e referências persistidas
determinam a resposta. Não aceitar lista arbitrária de secretIds. Não exigir ID
Docker antes da criação, pois a injeção ENV ocorre antes de criar o container.
Retornar `Cache-Control: no-store`. Exigir HTTPS com certificado/CA validado tanto
no cadastro quanto na resolução; registrar apenas metadados de auditoria.
O proxy do dashboard não deve encaminhar a rota de resolução para usuários.

O registro público atual e a ausência de restauração da identidade do agente
são bloqueios de ativação de segredos. Corrigir admissão, credenciais persistidas
e atualização autenticada do endereço antes de habilitar a resolução.

ENV entra apenas no create Docker em memória. Administradores do Docker podem
inspecioná-lo; não anunciar sigilo contra o administrador do nó. Para FILE, o
adapter deve produzir arquivo em área volátil privada no host efetivo do daemon,
montado somente leitura, com permissões mínimas; não usar camadas/volumes persistentes
da aplicação. Ele deve recriar arquivos após reboot antes do restart do workload
e limpar somente arquivos de sua propriedade ao remover o container.

FILE não pode ser anunciado até esse ciclo funcionar no daemon usado. Um agente
fora da VM do Docker Desktop não deve presumir que seu `/tmp` é o `/tmp` do daemon.
Se a topologia não suportar o adapter, rejeitar SECRET_FILE; nunca trocar para ENV
automaticamente. O exemplo PostgreSQL escolhe FILE porque a imagem suporta
`POSTGRES_PASSWORD_FILE`: [imagem oficial](https://hub.docker.com/_/postgres).

## Volumes nomeados e afinidade

`POST /api/volumes` (ADMIN) cadastra `{name, volumeSizeMb?, diskMode?, retentionPolicy?}`.
Retenção é somente RETAIN. Nome lógico identifica o cadastro humano, UUID é a
identidade de referência; nome Docker determinístico: `cloudbox-volume-<UUID>`.
Não receber caminhos do host, nomes Docker arbitrários ou opções livres de driver.
`GET /api/volumes` retorna metadados, inclusive nodeId/state, sem caminhos do host.

O volume começa UNBOUND, sem nodeId. Na primeira atribuição, o master vincula-o
atomicamente ao nó junto com a solicitação do container. Volumes já BOUND restringem
o scheduler àquele nó; afinidades diferentes ou nó indisponível resultam em 409.
Uma falha depois da vinculação conserva a afinidade para impedir a criação de uma
cópia vazia em outro nó. Falha de provisionamento fica ERROR, com histórico de nó.

`volumes` na especificação contém `{volumeId, target, readOnly}`. Até 16 entradas,
UUIDs distintos, caminhos absolutos canônicos diferentes de `/`, sem sobreposição
entre montagens ou arquivos secretos. Neste primeiro modelo, permitir somente
um container associado por volume, inclusive enquanto PENDING/STOPPED/REMOVING;
liberar a associação após REMOVED confirmado. Isso evita dois bancos escritores.

O START inclui `resolvedVolumes` com UUID, dockerName, nodeId, volumeSizeMb,
diskMode e retenção. O agente confere seu próprio nó, nome e labels antes de criar
ou reutilizar. Nome existente sem labels correspondentes é conflito, não autorização
para adotar/apagar dados. Remover container não remove o volume; exclusão de volume
não é exposta por esta proposta. Usar nova solicitação com o mesmo volumeId para
recriar no mesmo nó. [Docker — ciclo de vida dos volumes](https://docs.docker.com/engine/storage/volumes/#a-volumes-lifecycle).

## Restart e saúde observada

Restart: NONE → `no`, ALWAYS → `always`, UNLESS_STOPPED → `unless-stopped`,
ON_FAILURE → `on-failure`. `maximumRetryCount` é 0–100; zero significa sem limite
de tentativas em ON_FAILURE e é o único valor permitido nas outras políticas.
Parada manual e reboot seguem a semântica Docker; não confundir UNHEALTHY com
saída do processo ou prometer que uma política de restart reinicie por saúde.
[Docker — restart policies](https://docs.docker.com/engine/containers/start-containers-automatically/).

Probe EXEC usa Docker Healthcheck `Test: ["CMD", ...exec]`; o executável deve
existir na imagem. HTTP será executado pelo Java HttpClient do agente, sem exigir
curl/wget na imagem. Suporta GET HTTP, status 200–299, sem redirects/cookies/auth
ou cabeçalhos arbitrários. Recebe porta interna e caminho; não aceita URL/hostname.

Neste recorte, HTTP exige porta TCP publicada e binding alcançável pelo agente.
Resolver destino usando inspeção Docker e endereço do host efetivo do daemon
configurado/admitido; nunca usar URL fornecida pelo cliente. INTERNAL com HTTP é
rejeitado nesta versão; use EXEC para probes internos. Essa restrição evita assumir
que o IP bridge é alcançável fora da VM Docker Desktop. A conexão ao master/gateway
não é prova de alcance desse binding. Consumir/descartar o corpo com limite e
cancelamento; não guardar conteúdo de resposta ou stdout/stderr de probe público.

Padrões: intervalo 30 s, timeout 5 s, período inicial 0 s, tentativas 3. Intervalo
1–3600, timeout 1–300 (não maior que intervalo), período inicial 0–3600, tentativas
1–100. HTTP inicia STARTING; falhas na janela inicial não contam, sucesso antecipa
HEALTHY; fora dela, N falhas consecutivas geram UNHEALTHY e um sucesso recupera
HEALTHY. Reinício observado do processo reinicia a janela. EXEC usa as transições
do Docker. [Docker — health status](https://docs.docker.com/reference/dockerfile/#healthcheck).

Sem probe explicitamente solicitado pelo CloudBox: UNKNOWN, inclusive se o Docker
executar um probe herdado da imagem. Não desativar implicitamente a configuração da
imagem para mudar esse comportamento legado. Adoção de probe herdado pode ser uma
opção futura; esta proposta não afirma monitorá-lo.

`health` contém status UNKNOWN/STARTING/HEALTHY/UNHEALTHY, checkedAt e reasonCode
limitado, separado de `status` do processo. Monitorar containers gerenciados a cada
5 s por padrão; descoberta por labels e identidade persistida após restart do agente.
Processo parado/desaparecido implica UNKNOWN e limpeza dos endpoints. Mudança apenas
de saúde deve publicar evento e invalidar o dashboard mesmo mantendo RUNNING.

Reportes novos levam ID Docker; após o primeiro RUNNING, o master rejeita IDs
divergentes. Agente antigo sem health só serve workloads legados, com UNKNOWN.
Se o monitor perder contato com Docker, enviar UNKNOWN com motivo PROBE_UNREACHABLE
quando possível. Se nó ficar offline/observação expirar (padrão 30 s), a interface
mostra saúde desconhecida/desatualizada; não mantém um selo de saúde atual baseado
em dado antigo. Isso ainda não é fencing nem reconciliação de réplicas da etapa 6.

## Próximas entregas por módulo e critérios de aceite

1. Orquestrador revisa esta proposta e fixa os DTOs, capabilities, defaults e
   rejeição de campos ainda desativados. Próxima migration livre observada: V10;
   conferir novamente antes de criar. Agent/Dashboard conferem os mesmos exemplos.
2. Implementar primeiro environment + command/args + restart com seleção por
   capacidades; testar tradução, reinício e defaults antes de ativar outros campos.
3. Resolver admissão/identidade/TLS, então segredos e volumes com retenção e afinidade.
4. Implementar probes, monitoramento contínuo e atualização visual de saúde.
5. Maestro executa aceite real: PostgreSQL com referência de segredo e volume;
   gravar marcador, remover só o container, recriar com mesmo volumeId e consultar
   o marcador. Validar RUNNING/UNHEALTHY/recuperação, restart do Docker em daemon de
   teste dedicado, referência indevida negada e REQUIRED rejeitado sem capacidade.

O exemplo usa `postgres:16` e `/var/lib/postgresql/data`. Fixar digest no ensaio e
não extrapolar esse destino para todas as versões da imagem. Não executar restart
de um daemon compartilhado para validar o teste. Volumes locais não se movem para
outro nó; o critério desta etapa é recriação no mesmo nó.

## Verificação do contrato

```bash
npm ci --ignore-scripts --prefix scripts/contracts
npm test --prefix scripts/contracts
```

As verificações validam schemas, exemplos, regras semânticas selecionadas, YAML e
referências locais. Não são testes do runtime, autorização, criptografia, Docker,
scheduler ou persistência. As mesmas regras precisam de testes nos módulos quando
implementadas; o JSON Schema não expressa sozinho igualdade entre aliases,
afinidade, permissões ou garantias de storage.

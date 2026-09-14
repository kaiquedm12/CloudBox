# Instalar o agent no Linux: terminal e Docker Desktop

O agent precisa acessar o Docker que executara os workloads. A imagem sozinha
nao pode montar o socket: configure esse acesso ao criar o container, pelo
terminal ou pelos campos de volumes do Docker Desktop.

## 1. Preparar o endereco e escolher o Docker

Use o IP da rede local do computador (por exemplo, `192.168.1.50`) ou um DNS
acessivel por quem usara os servicos. No Linux, `ip -br address` ajuda a localizar
o IP da interface Ethernet/Wi-Fi. Nao escolha o IP de `docker0`, de interfaces
`br-*`, nem o IP interno do container, como `172.17.0.2`.

Um IP de rede local atende usuarios com acesso a essa rede. Para usuarios em
outra rede, configure um endereco e um caminho de rede acessiveis a eles.

Se houver Engine e Desktop instalados, sao ambientes separados. Confira:

```bash
docker context ls
```

Para os containers aparecerem no Docker Desktop, use seu contexto:

```bash
docker context use desktop-linux
```

O contexto `default` normalmente aponta para o Docker Engine do sistema.

## 2. Executar pelo docker run

Pare o agent anterior antes de iniciar o substituto para evitar dois agents
para a mesma maquina. Se o nome `cloudbox-agent` ja estiver ocupado, use outro
nome ou remova o container antigo depois de conferir seus dados.

Troque o IP e o nome do no no exemplo:

```bash
docker run -d \
  --name cloudbox-agent \
  --restart unless-stopped \
  -e CLOUDBOX_MASTER_URL=https://cloudbox-production-55f7.up.railway.app/ \
  -e AGENT_NAME=meu-pc-linux \
  -e AGENT_ADVERTISE_ADDRESS=192.168.1.50 \
  -e AGENT_AUTO_DETECT_ADVERTISE_ADDRESS=false \
  -e DOCKER_HOST=unix:///var/run/docker.sock \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
  --mount type=volume,src=cloudbox-agent-data,dst=/root/.cloudbox \
  ghcr.io/kaiquedm12/cloudbox-agent:1.0.1
```

O exemplo usa rede bridge e funciona sem ativar host networking no Desktop.
O agent publica as portas dos workloads pelo Docker; nao e necessario publicar
uma porta no proprio container do agent para registro ou heartbeat.

Com **Docker Engine nativo no Linux**, tambem e possivel usar `--network host`
e `AGENT_AUTO_DETECT_ADVERTISE_ADDRESS=true`, omitindo `AGENT_ADVERTISE_ADDRESS`.
Confira se o IP detectado e acessivel aos usuarios antes de publicar servicos.

## 3. Executar pela interface do Docker Desktop

Em **Images**, encontre a imagem do agent, clique em **Run** e abra
**Optional settings**. Preencha:

**Container name:** `cloudbox-agent` (ou um nome livre).

**Volumes:**

| Host path | Container path |
| --- | --- |
| `/var/run/docker.sock` | `/var/run/docker.sock` |
| `/home/SEU_USUARIO/.cloudbox-agent` | `/root/.cloudbox` |

Crie a pasta de dados no Linux antes, com `mkdir -p "$HOME/.cloudbox-agent"`.
No campo da interface, substitua `SEU_USUARIO` pelo usuario real e use o caminho
absoluto; nao escreva `$HOME` ou `~` no campo. A pasta guarda as credenciais do
agent. Na versao atual, reiniciar o processo ainda pode cadastrar um novo no.

**Environment variables:**

| Variable | Value |
| --- | --- |
| `CLOUDBOX_MASTER_URL` | `https://cloudbox-production-55f7.up.railway.app/` |
| `AGENT_NAME` | `meu-pc-linux` |
| `AGENT_ADVERTISE_ADDRESS` | IP real do computador, por exemplo `192.168.1.50` |
| `AGENT_AUTO_DETECT_ADVERTISE_ADDRESS` | `false` |
| `DOCKER_HOST` | `unix:///var/run/docker.sock` |

Clique em **Run**. Apenas clicar em Run sem configurar Volumes nao concede
acesso ao Docker. As montagens sao definidas na criacao: para corrigir um
container existente sem socket, crie um substituto com essas opcoes.

O socket permite ao agent criar, parar e remover containers nesse Docker.

## 4. Compose pronto, gerenciavel pelo Desktop

Na raiz deste repositorio:

```bash
export AGENT_NAME=meu-pc-linux
export AGENT_ADVERTISE_ADDRESS=192.168.1.50
docker compose -f cloudbox-agent/compose.yml up -d
```

O projeto aparece em **Containers** no Desktop quando iniciado no contexto
`desktop-linux`. Depois, voce pode parar, iniciar e consultar logs pela interface.
O Compose exige o endereco anunciado e falha se a origem do socket nao existir,
em vez de criar uma pasta no lugar do socket.

Para usar outro socket no lado do daemon, defina `AGENT_DOCKER_SOCKET` antes
de executar o Compose. O destino dentro do agent continua `/var/run/docker.sock`.

## 5. Construir uma imagem local

Os exemplos acima usam o package `1.0.1`. Alterar o repositorio nao atualiza uma
imagem ja publicada ou um container existente. Para testar alteracoes locais,
compile na raiz:

```bash
docker build -f cloudbox-agent/Dockerfile -t cloudbox-agent:local .
```

No `docker run`, substitua a ultima linha por `cloudbox-agent:local`.
No Desktop, selecione essa imagem em **Images**. No Compose:

```bash
export CLOUDBOX_AGENT_IMAGE=cloudbox-agent:local
export AGENT_ADVERTISE_ADDRESS=192.168.1.50
docker compose -f cloudbox-agent/compose.yml up -d
```

A nova imagem testa a conexao com o Docker antes de iniciar o agent. Se falhar,
exibe instrucoes de montagem e a causa original; o agent nao chega a cadastrar
um novo no. Com `restart: unless-stopped`, o Docker tenta reiniciar o processo.
As instrucoes de volumes e variaveis tambem corrigem a configuracao da imagem
`1.0.0` existente, sem exigir a nova validacao.

A autodeteccao de endereco fica desabilitada por padrao na nova imagem para
evitar anunciar o IP interno da rede bridge. Sem endereco explicito, o agent
pode atender workloads internos, mas nao recebe workloads com portas publicadas.
O JAR executado diretamente no host continua com autodeteccao habilitada.

## 6. Conferir o funcionamento

```bash
docker logs --tail 100 cloudbox-agent
docker inspect cloudbox-agent --format '{{json .Mounts}}'
```

Na nova imagem, procure `Docker acessivel`, depois `Agente registrado` e
`Heartbeat enviado`. O heartbeat ocorre a cada 10 segundos por padrao. Atualize
o painel para conferir o horario do ultimo heartbeat.

Se aparecer `SocketException` ou falha de acesso ao Docker:

- Confira se a montagem existe e aponta para um socket, nao uma pasta.
- Confira se o Docker esta ligado e se o processo tem permissao no socket.
- No Desktop, se houver problema com o socket encaminhado do host, use
  `/var/run/docker.sock.raw` como origem e mantenha `/var/run/docker.sock` como
  destino. Essa origem acessa o socket da VM conforme a documentacao do Docker.
- Se a causa mencionar uma versao de API incompatível, conserve essa mensagem
  completa no diagnostico; montar o socket nao corrige incompatibilidade de API.

O socket `~/.docker/desktop/docker.sock` e o endpoint do Desktop para ferramentas
executadas **diretamente no host Linux**. Nao copie esse caminho para
`DOCKER_HOST` dentro do container sem uma montagem correspondente.

No Desktop, as metricas podem refletir os recursos da VM Linux do Docker, e
nao toda a RAM/CPU do computador. `Reconectando...` no painel indica a conexao
WebSocket do navegador com o master; se os logs confirmam heartbeat enviado,
investigue essa conexao do painel separadamente.

Referencias: [Docker Desktop no Linux](https://docs.docker.com/desktop/setup/install/linux/),
[socket da VM do Desktop](https://docs.docker.com/extensions/extensions-sdk/guides/use-docker-socket-from-backend/)
e [configuracoes de recursos](https://docs.docker.com/desktop/settings-and-maintenance/settings/).

## 7. Publicacao automatica pelo GitHub Actions

O workflow `.github/workflows/publish-agent-package.yml` executa ao enviar uma
tag `agent-vX.Y.Z` (ou `vX.Y.Z`). Ele testa o agent, valida o Compose, publica a
imagem versionada e `latest` no GHCR e, somente apos o sucesso, cria a release
da mesma tag com notas geradas, o Compose e este guia anexados.

Atualize a versao dos exemplos e do Compose antes de criar a proxima tag. O
Compose anexado tambem aceita `CLOUDBOX_AGENT_IMAGE` para selecionar a imagem
indicada nas notas da release. Execucoes manuais em branches publicam uma imagem
com tag de commit, sem criar release. Reexecutar uma tag preserva a release ja
existente.

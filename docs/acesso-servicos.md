# Publicação de portas — primeira entrega

O CloudBox passa a transportar portas desejadas até o agente, configurar o binding
no Docker e mostrar os endpoints observados no dashboard. Esta entrega não inclui
volumes, segredos, health checks, gateway, HTTPS ou reassendamento.

## Configurar o nó

Por padrão, o agente detecta automaticamente o IP da interface usada para alcançar
o master e envia esse endereço ao registrar o nó. O dashboard deve exibi-lo no
campo “Endereço anunciado”.

Quando a detecção automática não representar o endereço alcançável do host Docker
(por exemplo, agente dentro de container, VPN ou múltiplas interfaces), configure
o endereço manualmente no terminal que executa o agente:

```bash
export AGENT_ADVERTISE_ADDRESS=192.168.1.50
export AGENT_PORT_BIND_ADDRESS=0.0.0.0
export CLOUDBOX_MASTER_URL=http://192.168.1.10:8080
```

`AGENT_ADVERTISE_ADDRESS` pode ser IPv4, IPv6 sem colchetes ou hostname DNS sem
esquema/porta. O endereço não cria uma rota: clientes precisam conseguir alcançá-lo.
`AGENT_AUTO_DETECT_ADVERTISE_ADDRESS=false` desativa a detecção automática quando
nenhum endereço deve ser anunciado.
`AGENT_PORT_BIND_ADDRESS` é um IP de interface do host Docker. O padrão `0.0.0.0`
publica em todas as interfaces IPv4. Para restringir a uma interface privada,
configure seu IP; para acesso somente no próprio nó, configure `127.0.0.1`.
Esse comportamento corresponde à [publicação de portas do Docker](https://docs.docker.com/engine/network/port-publishing/).

É possível escolher outro IP por porta com `bindAddress`. Um binding loopback não
deve ser usado para demonstrar acesso de outra máquina. Hostnames são permitidos
no endereço anunciado, mas não no IP de binding.

O agente usa `DOCKER_HOST` (padrão `unix:///var/run/docker.sock`). A seleção de
contexto do Docker CLI não altera automaticamente essa configuração. Verifique
o daemon e configure o socket correto antes de iniciar o agente. Se o agente
acessa um Docker remoto, o endereço anunciado deve pertencer ao host do Docker.

Reinicie o agente após mudar sua configuração. O fluxo atual registra um nó no
startup; confira no dashboard o nó ONLINE e seu endereço. Requisições sem portas
continuam aceitas em nós que não anunciam endereço.

## Subir o Nginx pelo dashboard

Com master, banco de dados, agente e dashboard executando:

1. Abra a criação de container e informe `nginx:alpine`, 1 CPU e 256 MB de RAM.
2. Informe o disco solicitado, por exemplo 100 MB. Este campo não aplica quota.
3. Adicione a porta interna `80`, protocolo `TCP`, exposição `HTTP`.
4. Deixe a porta do host vazia para o Docker escolher uma porta livre.
5. Crie o container e aguarde RUNNING e um endpoint reportado.
6. Use “Abrir aplicação”; o destino terá o formato `http://192.168.1.50:32768`.

O número acima é ilustrativo. Use a porta retornada pelo Docker. RUNNING indica
estado do processo, não uma verificação de saúde HTTP.

A especificação equivalente enviada a `POST /api/containers` é:

```json
{
  "imageName": "nginx:alpine",
  "cpuCores": 1,
  "memoryMb": 256,
  "diskMb": 100,
  "ports": [
    {
      "containerPort": 80,
      "hostPort": null,
      "protocol": "TCP",
      "exposure": "HTTP",
      "bindAddress": null
    }
  ]
}
```

Para protocolo TCP/UDP sem HTTP, o dashboard oferece o endpoint para copiar.
Exposição `INTERNAL` não publica no host; omita porta do host e IP de binding.
Exposição omitida assume INTERNAL, e protocolo omitido assume TCP. Não são
aceitas portas fora de 1–65535, pares porta/protocolo duplicados ou mais de 32
portas por container. Se uma porta fixa estiver ocupada, a criação reportará erro.

## Validação automatizada com infraestrutura real

Para preparar um ambiente temporário completo no próprio computador:

```bash
./mvnw -B -pl cloudbox-master,cloudbox-agent -am package
node scripts/accept-service-ports.mjs
```

São necessários Java 21, Node.js 22+ e um daemon Docker local acessível por socket
Unix. O script usa o contexto Docker selecionado ou `DOCKER_HOST`, baixa
`postgres:16` e `nginx:alpine`, cria PostgreSQL sem volume persistente e inicia os
JARs locais com credenciais aleatórias e heap limitado. Não usa o Compose/banco
de desenvolvimento. Portas e serviços do ensaio ficam em `127.0.0.1`.

O roteiro verifica migrations V1–V9, login, heartbeat autenticado, Nginx HTTP com
porta automática, porta explícita ocupada, ausência de portas, INTERNAL sem binding
e persistência depois de reiniciar o master. Aguarda recursos elegíveis e mantém
o limite térmico padrão de 75 °C. Um timeout/falha não conta como aceite dos casos
seguintes; `evidence.json` registra os casos concluídos e os últimos recursos lidos.

Ao terminar, remove somente os containers e processos criados pelo ensaio. O banco
temporário em memória é descartado; imagens baixadas, logs e evidências permanecem.
O diretório privado é informado na saída (`/tmp/cloudbox-ports-*`). Logs e o arquivo
local de credenciais devem permanecer privados; compartilhe o relatório sanitizado,
não o diretório inteiro. `SIGINT`/`SIGTERM` solicita encerramento e limpeza; em caso
de interrupção forçada, confira os UUIDs e a label `com.cloudbox.acceptance-run`
do `evidence.json` antes de remover qualquer recurso.

Esse ensaio é local, pela API: não comprova o navegador/dashboard, acesso por
outra máquina, admissão de nós ou restauração da identidade do agente. Resultados
e pendências: [relatório da etapa 1](relatorio-etapa1-integracao.md).

### Usar um ambiente já iniciado e comprovar acesso remoto

O script abaixo usa um JWT de usuário já obtido no login, cria um Nginx pela API,
aguarda o reporte do agente e consulta o endpoint HTTP. Não simula nó ou Docker.

```bash
node scripts/smoke-service-ports.mjs --help
```

Configure `CLOUDBOX_ACCESS_TOKEN` com o JWT sem incluí-lo em arquivos versionados
e `CLOUDBOX_MASTER_URL` com a URL do master; então execute:

```bash
node scripts/smoke-service-ports.mjs
```

Execute de outra máquina para comprovar acesso externo. Cada execução cria um
container de demonstração e o preserva; o script informa seu ID Docker. Se desejar
limpá-lo, use a ação Remover do dashboard ou `DELETE /api/containers/{id}` com JWT
e aguarde REMOVED. A API recebe o UUID CloudBox, não o ID Docker. A remoção manual
no Docker não equivale a uma atualização automática do estado no master.

## Diagnóstico e limites

- Sem nó elegível: verifique heartbeat, recursos livres e advertiseAddress.
- Nó antigo enviando heartbeat sem token recebe 401; o agente do projeto já envia bearer.
- RUNNING sem endpoint: consulte erro de reporte e inspeção das portas no Docker.
- Endpoint inacessível: confira firewall, IP anunciado, interface de binding e rota
  entre cliente e nó. Configurar NAT ou abrir firewall não é automático.
- Nó OFFLINE: o dashboard não oferece a abertura como aplicação disponível.
- Registro de nós continua público no protocolo atual. Restrinja essa API à rede
  de confiança: validação sintática do endereço não comprova propriedade do IP.
- Publicação de porta não cria domínio, TLS, rede entre nós ou garantia de disco.
- Agentes usando o mesmo socket Docker compartilham portas e containers; isso não
  representa um ensaio de cluster com máquinas independentes.

## Histórico de verificação da entrega de portas

- Java: 62 testes do master e 21 do agente aprovados, incluindo controllers reais
  com repositórios isolados, heartbeat autenticado, seleção de nó com endereço,
  reporte de endpoints, limpeza em erro/parada e tradução para a API Docker com mocks.
- Dashboard: TypeScript aprovado; build de produção aprovado com
  `npm run build -- --webpack`. O Turbopack encontrou uma restrição do ambiente ao
  abrir uma porta interna; a configuração padrão do projeto foi preservada.
- Navegador: formulário HTTP envia porta automática como null; link usa o endpoint
  recebido; evento RUNNING → RUNNING atualiza a URL; nó offline preserva endereço e
  desabilita acesso. Verificados desktop e celular de 390 px, com API simulada.
- OpenAPI: YAML válido e referências locais resolvidas.
- Na entrega original, migrations/persistência em PostgreSQL real, Nginx com Docker
  ativo e acesso por outra máquina ficaram pendentes. O ambiente daquela rodada não
  tinha os serviços ativos. A revisão de 06/10/2026 e o estado atual de cada critério
  estão no [relatório de integração](relatorio-etapa1-integracao.md).

Contrato: [service-ports-contract.md](service-ports-contract.md).
Próximas entregas: [plano-evolucao-servicos.md](plano-evolucao-servicos.md).

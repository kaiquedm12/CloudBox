# Publicação de portas — primeira entrega

O CloudBox passa a transportar portas desejadas até o agente, configurar o binding
no Docker e mostrar os endpoints observados no dashboard. Esta entrega não inclui
volumes, segredos, health checks, gateway, HTTPS ou reassendamento.

## Configurar o nó

No terminal que executa o agente, configure o endereço alcançável da máquina que
hospeda o Docker. Por exemplo, substituindo o IP abaixo pelo IP real do nó:

```bash
export AGENT_ADVERTISE_ADDRESS=192.168.1.50
export AGENT_PORT_BIND_ADDRESS=0.0.0.0
export CLOUDBOX_MASTER_URL=http://192.168.1.10:8080
```

`AGENT_ADVERTISE_ADDRESS` pode ser IPv4, IPv6 sem colchetes ou hostname DNS sem
esquema/porta. O endereço não cria uma rota: clientes precisam conseguir alcançá-lo.
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
limpá-lo, confira esse ID no host e remova somente esse container. Nesta entrega,
o fluxo existente não oferece remoção pelo dashboard/API; a limpeza manual no
Docker também não equivale a uma atualização automática do estado no master.

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

## Verificação realizada nesta entrega

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
- Pendente: executar migrations e testar persistência em PostgreSQL real, publicar
  o Nginx com um daemon Docker ativo e acessar a partir de outra máquina. O ambiente
  disponível não tinha os serviços ativos; os testes acima não substituem esse aceite.

Contrato: [service-ports-contract.md](service-ports-contract.md).
Próximas entregas: [plano-evolucao-servicos.md](plano-evolucao-servicos.md).

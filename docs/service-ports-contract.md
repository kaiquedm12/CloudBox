# Contrato de implementação — etapa 1: portas e acesso

Este arquivo fixa o contrato compartilhado entre master, agente e dashboard para a primeira entrega. Campos das demais etapas não são implementados nesta entrega.

## Criação e comando pendente

Preservar `imageName`, `cpuCores`, `memoryMb` e `diskMb` e acrescentar `ports` (omitido/null equivale a `[]`). O comando pendente leva a mesma lista normalizada.

```json
{
  "imageName": "nginx:alpine",
  "cpuCores": 1,
  "memoryMb": 256,
  "diskMb": 100,
  "ports": [
    {"containerPort": 80, "hostPort": null, "protocol": "TCP", "exposure": "HTTP", "bindAddress": null}
  ]
}
```

- `containerPort`: inteiro obrigatório, 1–65535.
- `hostPort`: inteiro 1–65535 ou null para alocação Docker.
- `protocol`: TCP (padrão se omitido) ou UDP.
- `exposure`: INTERNAL (padrão seguro se omitido), HTTP, TCP ou UDP. HTTP/TCP exigem protocolo TCP; UDP exige protocolo UDP.
- `bindAddress`: IP literal opcional da interface do host; null usa a configuração `AGENT_PORT_BIND_ADDRESS`, padrão `0.0.0.0`. Nunca confundir esse IP com o endereço anunciado do nó. A primeira entrega suporta IPv4 e IPv6; sem hostnames neste campo.
- INTERNAL não aceita hostPort/bindAddress e não cria binding no host.
- Rejeitar chaves containerPort/protocol duplicadas e configurações incompatíveis; limitar a lista a 32 portas.
- Não alocar porta por sondagem anterior no Java; deixar o Docker arbitrar alocação/conflitos.
- Requisições antigas sem portas continuam funcionando. Manter overloads de construtores/métodos usados pelos testes/clientes internos quando conveniente.

## Registro e endereço do nó

`NodeRegisterRequest` e `NodeResponse` ganham `advertiseAddress` opcional (sem esquema, caminho ou porta): hostname DNS válido ou IP literal alcançável pelos clientes. Ausência é permitida para agentes antigos e workloads sem publicação. Rejeitar valores wildcard, URLs e caracteres inválidos. Registro continua com o fluxo atual; documentar a necessidade de admissão confiável de nós em rede não confiável.

Configuração do agente: `AGENT_ADVERTISE_ADDRESS` opcional e `AGENT_PORT_BIND_ADDRESS` opcional. Antes de executar um workload publicado, exigir advertiseAddress válido configurado. No master, workloads com portas publicadas só podem ser atribuídos a nós com advertiseAddress; preservar o scheduler existente para workloads internos. Selecionar entre candidatos elegíveis, sem simplesmente rejeitar se o primeiro nó escolhido não tiver endereço.

Não gerar URLs externas com `0.0.0.0` ou `::`. Um bind explícito loopback corresponde a acesso local; não anunciá-lo usando um endereço remoto do nó. Para bind explícito diferente do wildcard, usar esse endereço no endpoint; para o binding padrão, usar advertiseAddress. No agente, quando o binding configurado não for wildcard, materializá-lo no campo address do reporte para que o master confira/coerentemente derive o endpoint.

## Estado observado

O POST existente de status ganha `endpoints` opcional. Cada binding reportado tem:

```json
{"containerPort": 80, "hostPort": 32768, "protocol": "TCP", "address": "0.0.0.0"}
```

`address` no reporte é o IP de binding observado pelo Docker (não uma URL nem hostname arbitrário). Pode ser omitido por consumidores compatíveis; nesse caso usar bindAddress da especificação ou endereço anunciado. O agente inspeciona após start e reporta os bindings efetivos. A mesma chave pode ocorrer para IPv4/IPv6 em bindings observados; não perder um binding real.

O master valida propriedade do container (token/nó), porta solicitada, protocolo, hostPort explícita quando aplicável e endereço de binding compatível com a especificação. Rejeitar endpoint para porta INTERNAL/não solicitada. Não aceitar URL do reporte como fonte confiável.

`ContainerResponse` ganha `ports` e `endpoints` (listas, nunca null). O endpoint de resposta é:

```json
{"containerPort": 80, "hostPort": 32768, "protocol": "TCP", "address": "192.168.1.50", "url": "http://192.168.1.50:32768"}
```

- URL gerada no master apenas para exposição HTTP; TCP/UDP têm url null. IPv6 usa colchetes na URL.
- Se não houver endereço utilizável, não fabricar endpoint de acesso.
- RUNNING com endpoints omitidos preserva endpoints existentes; lista vazia explícita limpa. ERROR/STOPPED limpa endpoints de acesso obsoletos.
- Atualização de endpoints mantendo RUNNING deve invalidar dados do dashboard: reutilizar evento `CONTAINER_STATUS_CHANGED` com status anterior/atual iguais se necessário, mantendo compatibilidade do websocket.
- Se o nó ficar offline, a interface não deve oferecer abertura como aplicação disponível; preservar informação para diagnóstico sem prometer conectividade.

## Testes e aceite

Master: validações e defaults, persistência/migration V9 ou próxima livre, seleção de nó com endereço, token do heartbeat, autorização de reportes, URL/IPv6, portas não solicitadas, limpeza e mudança de endpoints mantendo RUNNING.

Agente: tradução para docker-java de HTTP/TCP/UDP/INTERNAL, porta automática/fixa, binding observado, reporte/retry, preservação do comportamento sem portas. Evitar duplicação após restart usando nome/labels e inspeção da identidade antes de reutilizar container existente. Não remover containers sem comprovar propriedade.

Dashboard: schema de portas, formulário de lista, endpoints HTTP validando URL, copiar TCP/UDP, ausência de endpoint, status/node offline e refresh via evento. Respeitar AGENTS.md e a documentação Next instalada.

Integração: Nginx porta 80 com hostPort null; IP configurável; GET no endpoint retornado. Distinguir testes mockados de validação Docker real e de acesso por outra máquina. Nenhum teste deve usar/apagar os dados de desenvolvimento existentes.

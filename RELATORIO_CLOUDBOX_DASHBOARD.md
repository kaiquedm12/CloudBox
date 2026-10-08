# Relatório de implementação do CloudBox Dashboard

## Etapa 2 — configuração básica do agente — 08/10/2026

Há exemplos candidatos de entrada, comando e resposta para a etapa 2. O agente implementa o consumo básico, mas o master ainda não recebe/persiste a configuração nova. O dashboard não foi alterado nem habilitou campos sem comportamento no backend; formulário avançado deve seguir a ativação do contrato pelo Orquestrador.

Evidências, reprodução e próximos passos: [relatório da etapa 2](docs/relatorio-etapa2-configuracao-agente.md). O [protocolo de entregas](docs/protocolo-entregas.md) inclui conferência da main remota antes do trabalho e do push. Os resultados da etapa 1 abaixo são históricos.

## Revisão da etapa 1 — 06/10/2026

O código atual oferece formulário de portas, endpoints para copiar, abertura de URL HTTP(S), indicação de nó offline e atualização por evento RUNNING → RUNNING. Esta revisão conferiu o consumo do contrato por leitura do código; não repetiu build nem navegador. O aceite local pela API não substitui o fluxo completo pelo dashboard.

O tópico desta entrega é o aceite local da etapa 1, conduzido no escopo Maestro (raiz, scripts e documentação). Consulte o [relatório de integração](docs/relatorio-etapa1-integracao.md) para execução com Docker/PostgreSQL reais, evidências, limitações e encaminhamentos. Etapas 2–6 não foram iniciadas. Os registros de setembro abaixo são históricos.

## Histórico de andamento — 14/09/2026

A equipe relatou a conclusão do primeiro fluxo integrado com o **2048**: cadastro do computador pelo agente, envio de recursos, visualização do nó no painel, solicitação e execução do container pelo Docker e abertura do jogo em outro navegador por um endereço de acesso. O projeto passa a ter uma demonstração funcional integrada, além das verificações isoladas dos módulos.

Esta revisão documental confrontou esse relato com os arquivos atuais; não executou novamente a demonstração nem as suítes automatizadas. Contagens de testes e resultados anteriores abaixo são registros históricos, não resultados desta revisão.

Imagem/tag, portas, URL e origem do acesso do teste do 2048 não foram informadas. Essa evidência histórica ainda precisa ser completada. A integração posterior acrescentou publicação de portas, endpoints no master e botão de acesso no dashboard; o roteiro atual está em [acesso-servicos.md](docs/acesso-servicos.md).

O procedimento atualizado de instalação, login, execução e diagnóstico está no [README principal](README.md#como-rodar-localmente).

## 1. Objetivo

Este documento registra a implementação da interface web do CloudBox, com foco na visualização do cluster, listagem de containers, atualizações em tempo real e ações de ciclo de vida.

## 2. Tecnologias e arquitetura

O dashboard utiliza Next.js 16.3, React 19, TypeScript, Tailwind CSS e TanStack Query. As páginas estáticas permanecem como Server Components e as áreas interativas são delimitadas como Client Components.

As chamadas do navegador usam rotas internas `/api/*`. O proxy do Next mantém o JWT em cookie `httpOnly`, encaminha a autenticação ao master e evita expor o token ao JavaScript do cliente.

## 3. Tela de containers

A rota `/containers` consome `GET /api/containers` por meio do hook `useContainers`. Os containers são ordenados pela data de criação, do mais recente para o mais antigo.

A listagem foi apresentada como tabela responsiva contendo:

- imagem Docker e identificador do registro;
- nome ou identificador do nó alocado;
- status atual;
- CPU e memória reservadas;
- data de criação;
- ações disponíveis.

Os estados exibidos são `PENDING`, `SCHEDULED`, `RUNNING`, `STOPPING`, `STOPPED`, `REMOVING`, `REMOVED`, `ERROR` e `FAILED`, com rótulos e cores distintas.

## 4. Ações de parar e remover

O botão **Parar** fica disponível somente para containers `RUNNING` e envia:

```text
POST /api/containers/{id}/stop
```

O botão **Remover** fica disponível para `RUNNING`, `STOPPED`, `ERROR` e `FAILED` e envia:

```text
DELETE /api/containers/{id}
```

Enquanto a mutation está em andamento, os botões do item são desabilitados. Erros retornados pela API são apresentados acima da tabela. Após uma resposta bem-sucedida, o cache local é atualizado e invalidado para reconciliação com o master.

## 5. Atualização em tempo real

O `ClusterStatusProvider` mantém uma conexão com `/ws/cluster-status`. Ao receber `CONTAINER_STATUS_CHANGED`, atualiza imediatamente o item correspondente no cache e solicita uma nova leitura de `GET /api/containers`.

Quando a conexão cai, o cliente reconecta automaticamente com espera exponencial limitada a 30 segundos. Ao restabelecer a conexão, os caches de nós e containers são invalidados para recuperar possíveis eventos perdidos.

Assim, o fluxo visual de uma parada é:

```text
RUNNING -> STOPPING -> STOPPED
```

E o fluxo de remoção é:

```text
RUNNING/STOPPED/ERROR/FAILED -> REMOVING -> REMOVED
```

## 6. Integração ponta a ponta

As ações da tela não alteram apenas a apresentação. O master registra o estado transitório, disponibiliza o comando ao agente responsável, o agente chama a Docker Engine local e reporta o resultado. O master persiste o estado final e publica a mudança pelo WebSocket.

Caso a operação Docker seja concluída e o master esteja temporariamente indisponível, o agente repete apenas a confirmação, reduzindo o risco de executar a mesma operação duas vezes.

## 7. Validação realizada

O relatório anterior registrou as seguintes verificações (não repetidas nesta revisão):

- `npx tsc --noEmit`: aprovado, sem erros TypeScript;
- `./mvnw test`: aprovado para master e agente;
- 41 testes aprovados no master;
- 14 testes aprovados no agente;
- `git diff --check`: aprovado, sem erros de whitespace.

O build padrão `npm run build` não foi concluído neste ambiente porque o Turbopack falhou ao tentar abrir uma porta interna durante o processamento de CSS (`Operation not permitted`). A tentativa com Webpack também encontrou um erro interno ao interpretar `TypeScript --showConfig`. Como a verificação direta do TypeScript passou, não foi identificado erro de tipos causado pela tela, mas o build de produção deve ser repetido em um ambiente sem essa restrição.

## 8. Situação do critério de aceite

Atendido no código e nos testes automatizados:

- a tela consome `GET /api/containers`;
- apresenta imagem, nó e status em tabela;
- oferece ação para parar containers em execução;
- oferece remoção do container na Docker Engine;
- recebe transições de status em tempo real pelo WebSocket;
- evita solicitações duplicadas durante estados transitórios.

Pendente de validação operacional:

- executar o build de produção do Next em ambiente sem a restrição observada;
- complementar o teste manual já relatado do 2048 com evidências (logs, capturas, versão e configuração de rede);
- validar manualmente as ações de parar/remover, não descritas no relato;
- repetir o fluxo visual com backend real e acesso por outra máquina; campos de porta e endpoints já estão integrados.

## 9. Evolução da interface e uso atual

- `/login`: autenticação por e-mail/senha, com JWT mantido em cookie `httpOnly` pelo Next.
- `/`: visão geral dos nós, métricas e indicação da conexão em tempo real.
- `/nodes/[id]`: detalhes do nó, heartbeat e containers alocados.
- `/containers`: criação com validação Zod de imagem, CPU, RAM, disco e portas; listagem com endpoints, erros de execução e ações de parar/remover.
- O disco solicitado também aparece na tabela; a interface oferece seleção de idioma e tema.

O usuário entra, verifica o nó online, solicita o container e acompanha `PENDING → RUNNING`. Esse percurso foi utilizado no primeiro fluxo relatado. O componente atual inclui campos de porta e botão para abrir URL HTTP(S) retornada pelo master, desabilitado quando o nó está offline ou o container não está RUNNING. O relato histórico de outro navegador não comprova acesso de outra máquina.

O [README do dashboard](cloudbox-dashboard/README.md) explica a configuração local; o [README principal](README.md#como-rodar-localmente) reúne a preparação do banco, master, agente e o roteiro de reprodução.

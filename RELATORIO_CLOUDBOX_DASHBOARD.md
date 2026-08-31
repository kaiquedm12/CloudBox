# Relatório de implementação do CloudBox Dashboard

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

Foram executadas as seguintes verificações:

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
- realizar teste manual com master, agente e Docker reais;
- confirmar visualmente o fluxo completo em navegador e registrar evidências para o TCC.

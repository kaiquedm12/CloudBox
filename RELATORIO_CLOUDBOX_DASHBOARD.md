# Relatório de implementação do CloudBox Dashboard

## Atualização de andamento — 14/09/2026

A equipe relatou a conclusão do primeiro fluxo integrado com o **2048**: cadastro do computador pelo agente, envio de recursos, visualização do nó no painel, solicitação e execução do container pelo Docker e abertura do jogo em outro navegador por um endereço de acesso. O projeto passa a ter uma demonstração funcional integrada, além das verificações isoladas dos módulos.

Esta revisão documental confrontou esse relato com os arquivos atuais; não executou novamente a demonstração nem as suítes automatizadas. Contagens de testes e resultados anteriores abaixo são registros históricos, não resultados desta revisão.

A geração de endereço observada no teste ainda precisa ser vinculada à configuração/versão utilizada: `ContainerExecutionService.runContainer` configura limites de CPU e RAM, mas não publica portas; os DTOs do master não possuem URL e `ContainerList` não exibe link para a aplicação. Imagem/tag, portas, URL e origem do acesso não foram informadas. Portanto, o sucesso manual relatado está registrado, mas essa etapa de rede ainda não é reproduzível somente com este checkout.

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
- localizar/integrar a exibição do endereço de acesso, ausente na tabela atual.

## 9. Evolução da interface e uso atual

- `/login`: autenticação por e-mail/senha, com JWT mantido em cookie `httpOnly` pelo Next.
- `/`: visão geral dos nós, métricas e indicação da conexão em tempo real.
- `/nodes/[id]`: detalhes do nó, heartbeat e containers alocados.
- `/containers`: criação com validação Zod de imagem, CPU, RAM e disco; listagem com erros de execução e ações de parar/remover.
- O disco solicitado também aparece na tabela; a interface oferece seleção de idioma e tema.

O usuário entra, verifica o nó online, solicita o container e acompanha `PENDING → RUNNING`. Esse percurso foi utilizado no primeiro fluxo relatado. O acesso ao jogo em outro navegador faz parte da demonstração informada, mas o componente atual não inclui campo de porta nem botão para abrir a aplicação.

O [README do dashboard](cloudbox-dashboard/README.md) explica a configuração local; o [README principal](README.md#como-rodar-localmente) reúne a preparação do banco, master, agente e o roteiro de reprodução.

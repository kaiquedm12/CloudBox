# CloudBox Dashboard

Frontend do painel de controle da CloudBox, criado com Next.js, TypeScript,
Tailwind CSS e TanStack Query.

## Desenvolvimento local

Prepare o banco, o master e o agente conforme o [guia principal](../README.md#como-rodar-localmente). Neste diretório, instale as dependências:

```bash
npm ci
```

Crie ou ajuste `.env.local` para usar o master local:

```dotenv
ORCHESTRATOR_URL=http://localhost:8080
```

O `.env.example` e o fallback atual apontam para Railway. Se optar por esse backend, use a mesma URL no agente (`CLOUDBOX_MASTER_URL`); para a instalação local, substitua explicitamente pelo endereço acima. Reinicie o Next após mudar essa configuração.

```bash
npm run dev
```

Abra [http://localhost:3000](http://localhost:3000) e faça login em `/login`. Em um banco inicializado pelas migrations atuais, a conta inicial é `admin@admin.com`, senha `cloudbox`, criada pela V7 caso esse e-mail ainda não exista.

`ORCHESTRATOR_URL` define a URL base do orquestrador. As chamadas a `/api/*` passam por Route Handlers do Next.js, que mantêm o JWT em cookie `httpOnly` e anexam o token ao header `Authorization`. A conexão `/ws/*` é encaminhada ao master pela configuração de rewrites. Em produção, use HTTPS para o cookie de sessão `secure`.

A visão geral faz a carga inicial por `GET /api/nodes` e recebe atualizações pelo
WebSocket autenticado `/ws/cluster-status`. O cliente reconecta automaticamente e
sincroniza os caches de nós e containers ao receber eventos.

A página `/containers` lista as cargas existentes em uma tabela, permite solicitar um novo
container com validação Zod e encaminha ações de parada e remoção ao agente do nó. Os cards da visão geral levam à página
`/nodes/[id]`, que reúne métricas, heartbeat e containers alocados no nó.

## Operar e acompanhar o primeiro fluxo

1. Na visão geral, confira o computador `ONLINE` e seus recursos; clique no nó para abrir `/nodes/[id]`.
2. Em `/containers`, abra o formulário de criação e informe imagem Docker, CPU, RAM e disco (MB). Os recursos devem ser inteiros positivos.
3. Envie e acompanhe a transição de `PENDING` para `RUNNING`. O download da imagem pode aumentar a espera; erros aparecem na listagem.
4. Use **Parar** para um container `RUNNING` e **Remover** nos estados permitidos. Aguarde a confirmação do agente; o histórico `REMOVED` permanece na lista.

A equipe validou o primeiro fluxo com o **2048**, incluindo abertura do jogo em outro navegador. A versão atual desta interface ainda não exibe URL de acesso nem campos de publicação de portas. Para repetir a parte de rede, falta registrar/integrar o mecanismo e a imagem/tag usados na demonstração. Consulte o [registro de andamento](../README.md#andamento-atual) e o [relatório do dashboard](../RELATORIO_CLOUDBOX_DASHBOARD.md).

## Scripts

- `npm run dev`: inicia o ambiente local.
- `npm run build`: gera a versão de produção.
- `npm run start`: executa a versão de produção gerada.

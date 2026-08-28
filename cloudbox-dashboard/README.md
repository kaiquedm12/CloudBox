# CloudBox Dashboard

Frontend do painel de controle da CloudBox, criado com Next.js, TypeScript,
Tailwind CSS e TanStack Query.

## Desenvolvimento local

Instale as dependências, copie o arquivo de ambiente de exemplo e inicie o
servidor de desenvolvimento:

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

Abra [http://localhost:3000](http://localhost:3000) no navegador.

`ORCHESTRATOR_URL` define a URL base do orquestrador. As chamadas a `/api/*`
passam por Route Handlers do Next.js, que mantêm o JWT em um cookie `httpOnly` e
anexam o token ao header `Authorization` sem expô-lo ao JavaScript do navegador.

A visão geral faz a carga inicial por `GET /api/nodes` e recebe atualizações pelo
WebSocket autenticado `/ws/cluster-status`. O cliente reconecta automaticamente e
sincroniza os caches de nós e containers ao receber eventos.

A página `/containers` lista as cargas existentes e permite solicitar um novo
container com validação Zod. Os cards da visão geral levam à página
`/nodes/[id]`, que reúne métricas, heartbeat e containers alocados no nó.

## Scripts

- `npm run dev`: inicia o ambiente local.
- `npm run build`: gera a versão de produção.
- `npm run start`: executa a versão de produção gerada.

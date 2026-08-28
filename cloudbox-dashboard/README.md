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

`NEXT_PUBLIC_ORCHESTRATOR_URL` define a URL base usada pelo client HTTP em
`lib/api.ts`. As chamadas a `/api/*` passam pelo proxy do Next.js para evitar
problemas de CORS entre o dashboard e o orquestrador durante o desenvolvimento.

A visão geral consulta `GET /api/nodes` com TanStack Query e atualiza os dados
automaticamente a cada cinco segundos.

## Scripts

- `npm run dev`: inicia o ambiente local.
- `npm run build`: gera a versão de produção.
- `npm run start`: executa a versão de produção gerada.

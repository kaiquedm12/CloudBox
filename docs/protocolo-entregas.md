# Protocolo de branches, commits e integração

Orientações confirmadas pelo usuário: trabalhar um tópico por vez, manter os
relatórios, publicar a branch no GitHub e sempre conferir novidades da main.

1. Conferir `git status --short --branch`, branch atual e alterações existentes.
   Preservar trabalho do usuário e respeitar a pasta de cada responsável.
2. Executar `git fetch origin --prune` e comparar HEAD com `origin/main` usando
   `git rev-list --left-right --count HEAD...origin/main`. Não confiar apenas na
   referência main local. Se o fetch falhar, registrar que a atualização não pôde
   ser verificada; não afirmar que a base está atualizada.
3. Para tópico novo, criar `feat/<etapa>-<topico>` a partir de `origin/main` após
   conferir que ela contém as entregas das quais o tópico depende. Se depender de
   uma branch ainda não integrada, registrar explicitamente a base e a dependência.
4. Em tópico já iniciado, integrar novidades de `origin/main` na branch de trabalho
   por merge não destrutivo; resolver conflitos dentro do escopo e repetir as
   verificações afetadas. Não reescrever histórico publicado nem usar force push.
5. Implementar e executar verificações proporcionais. Atualizar relatórios com
   evidências atuais, limitações e pendências; não reapresentar resultados antigos
   como execução nova. Conferir migrations livres e preservar arquivos protegidos.
6. Criar commits coesos com tipo/escopo (`feat`, `fix`, `test`, `docs` etc.), usando
   staging de arquivos explícitos. Revisar diff e `git diff --check`.
7. Antes de publicar, repetir o fetch e a comparação com `origin/main`. Se houver
   novidades, integrá-las e repetir as verificações afetadas antes do push.
8. Executar `git push -u origin <branch-do-topico>`; conferir sincronização e estado
   do worktree. Informar branch, commits, resultados e link da entrega.

O push da branch faz parte da entrega. Este protocolo não autoriza push direto na
main nem merge de PR automaticamente. Se a main avançar depois da conferência,
conferir novamente antes da integração final; fetch não trava o repositório remoto.

Limites de edição e ordem entre responsáveis: [plano de evolução](plano-evolucao-servicos.md).

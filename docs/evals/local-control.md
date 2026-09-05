# Oráculos adversariais de controle local

Estes casos são IR, não um parser/modelo de PERFORM. A correspondência com COBOL
é responsabilidade do produtor. Regras em [controle local](../domain/local-control.md).

## L1 — Dois sites, um trecho (O-56 / X-23)

C1 invoca S com porta END e resume R1. Após R1, C2 invoca o mesmo S e resume R2.
Boundary END em S, sob frame C1, só retorna a R1; sob C2, só a R2. Não existe
execução direta C1→R1 que salte o corpo. Origem/ID do corpo continua compartilhada.
A projeção que oferece ambos resumes deve declarar aproximação, não precisão.

## L2 — Range curta e longa (O-57 / X-24)

SHORT entra A, espera MID, resume RS. LONG entra A, espera END, resume RL.
A termina em boundary MID default B; B termina em boundary END default D.
Sob SHORT: A→RS. Sob LONG: A→B→RL. Com pilha vazia: A→B→D.
O teste não pode renomear labels para fazer matching; usar IDs e ports.

## L3 — Frame externo não vence o topo (O-58)

Pilha [externo espera A, interno espera B], topo=interno. Boundary A segue default
e preserva os dois frames. Implementação que busca o primeiro match na pilha falha.
Incluir duas ocorrências diferentes de boundary sinalizando a mesma porta.

## L4 — Erros e unwind (O-59/O-60)

Resume com pilha vazia → invalid_local_return.
Unwind 1 com profundidade 2 remove somente topo; unwind 3 → invalid_local_unwind.
Jump comum conserva os dois frames. Não inferir abandono de contexto por sair da
faixa textual da procedure. Return/halt possuem escopo diferente de local.resume.

## L5 — Nested, recursão e limite

Nested invocações retornam em LIFO quando suas portas correspondem. Uma fixture
recursiva exige representação que termine sem enumerar pilhas sem limite. Limitar
profundidade em experimento deve publicar ANALYSIS_LIMIT e não conformidade precisa
fora do limite. O teste não pode receber timeout e marcar isso como sucesso.

## Política de evidência

Um interpreter pequeno de teste pode validar traços finitos, com estado de pilha
explícito. Ele não usa a implementação do builder e não prova recursão geral.
Uma solução simbólica/contextual deve explicar como impõe matching, não somente
anotar edges. EVAL-CFG-016/017/018 cobrem as regras de topo, retorno e limites.

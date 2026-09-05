# Conhecimento canônico e economia de contexto

AGENTS contém somente regras universais e roteamento. Índices apontam, não repetem
contratos. Arquitetura descreve fronteiras; domínio descreve semântica local da IR;
engenharia descreve processo; evals descrevem provas; work items coordenam o slice.

Antes de criar documento, verificar onde a informação deveria viver. Não criar
segundo backlog, sumário permanente de cada sessão ou cópia integral de regras no
prompt. O estado transitório mora em state; decisões duráveis são promovidas para
canônico. História fica fora do contexto padrão.

IDs estáveis para invariantes, ADRs, evals e backlog. Não renumerar por conveniência.
Links devem ser relativos para recursos locais e por commit para fontes GitHub.
Metadados computáveis têm uma fonte única: backlog.json, registry.json, catalog.json,
profile-obligations.json e sources.lock.json. Markdown explica e aponta; gates
verificam as correlações essenciais. Não executar scripts arbitrários de documentos.

Prompts descrevem objetivo, tipo de trabalho, escopo e ponto de parada. O agente
busca o contexto no harness e código relevante, não exige repetir todo o projeto no
prompt. Mudanças não triviais exigem fontes/invariantes e um oracle, não um prompt
gigante. Leia apenas as rotas concretamente necessárias e expanda ao encontrar
uma dependência real. Evitar impor orçamento de tokens que obrigue omitir evidência.

Conflitos não são resolvidos escolhendo a narrativa mais recente: identificar a
autoridade e a revisão. Propostas não são fatos implementados. Verde documental
não é verde semântico. O check de links não garante que a documentação esteja certa.

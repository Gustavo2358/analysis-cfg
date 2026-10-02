# CFG JSON 5.0.0: controle local

`analysis-cfg-json` usa 5.0.0 somente quando há regras locais. As versões 1–4
permanecem byte-compatíveis nos produtos que não exigem essas regras. AIR continua
2.0.0, com capability `control.local@1` e binding JSON 1.0.0.

Cada elemento de `localControl` identifica `source` (ID CFG), `operation` (ID AIR)
e `kind`, com os campos abaixo. Referências CFG incluem publicação e ordinal;
referências AIR incluem publicação, unit e localId. Não correlacionar pelo nome.

| kind | Campos | Aplicação |
| --- | --- | --- |
| LOCAL_INVOKE | entry, resume, ports | Push de operation/resume/ports; segue entry |
| LOCAL_BOUNDARY | port, defaultDestination | Casa somente o topo; pop e resume se casar, senão default |
| LOCAL_RESUME | invalidExit | Pop e resume; pilha vazia segue invalidExit |
| LOCAL_UNWIND | count, destination, invalidExit | Remove exatamente count; excesso segue invalidExit |

`count` é string decimal canônica não negativa, sem limite de 64 bits. Os exits
inválidos têm tags AIR `invalid_local_return` e `invalid_local_unwind` e guardam a
operação responsável. `ports` são IDs de completion ports da mesma unit.

Existe exatamente uma regra para cada nó com terminador local e nenhuma aresta
ordinária saindo desse nó. Os destinos entry/resume/default/destination referem
sequences publicadas. Um local.invoke nunca concede uma aresta direta para resume.
`transitions` continua contendo apenas relações ordinárias. Construir reachability
apenas dessa lista em v5 perde chamadas e retornos; unir todos os resumes também
é incorreto. Usar `(Entry, nó, pilha)` e a semântica de [controle local](local-control.md).

A representação do grafo admite regras recursivas; o perfil atual de travessia e
solver recusa repetição de invocação simultaneamente ativa. O writer não certifica
terminação do programa ou completude da análise. Inventários PARTIAL e controle
aberto conservam suas qualificações.

## CFG JSON 6.0.0: guarda explícita

Somente produtos que contêm guardas usam 6.0.0. LOCAL_INVOKE admite então o
campo `reentryGuard: {activationKey: string, destination: CfgNodeId}`. A chave
não vazia é comparada exatamente no escopo publication/Unit da operation.
Se estiver presente em qualquer frame pendente, seguir destination mantendo
a pilha; caso contrário, empilhar com a chave e seguir entry. Destino é uma
Sequence da mesma Unit. Pop e unwind removem a chave com o frame. Invocações
sem guarda preservam sua regra e continuam sujeitas ao limite de recursão do
solver existente. Guardas não autorizam achatar retornos ou enumerar todas as
pilhas em tamanho polinomial. A representação do CFG continua compacta.

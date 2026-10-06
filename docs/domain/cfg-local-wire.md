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

## CFG JSON 7.0.0: rotas e abandono completo

Produtos que usam ao menos uma das formas abaixo exigem 7.0.0. Sem elas, a seleção
de versões 1–6 permanece igual. Campos opcionais ausentes conservam a semântica
anterior; versões anteriores rejeitam os campos novos.

- LOCAL_INVOKE admite `resumeRoutes: [{key: string, destination: CfgNodeId}]`.
  Chaves não vazias são únicas por regra; destinos pertencem à mesma Unit.
- LOCAL_RESUME admite `resumeKey: string`. Seleciona a rota do topo, desempilha
  e segue o destino. Chave ausente ou pilha vazia seguem `invalidExit`.
- LOCAL_BOUNDARY selecionada admite o par `resumeKey: string` e
  `invalidExit: CfgNodeId`; a presença da chave exige 7.0.0 independentemente
  de rotas em LOCAL_INVOKE. Primeiro verifica a porta somente no frame do topo:
  pilha vazia ou porta incompatível seguem `defaultDestination`, mantendo a
  pilha inteira e ignorando a chave. Porta compatível seleciona a chave exata
  nesse frame e remove um frame. Chave inexistente segue `invalidExit`
  (`invalid_local_return` da mesma operação), encerrando a ativação; nunca
  consulta ancestrais. Os dois campos são publicados juntos e omitidos juntos
  na forma legada, que mantém pop/resume do topo. Versões 1–6 rejeitam o par.
- LOCAL_UNWIND admite `all: true`, somente com `count: "0"`. Remove todos os
  frames e segue `destination`; pilha vazia também é válida.

Writer omite lista vazia, chave ausente e all=false. Rotas são ordenadas por chave
para publicação determinística. O oracle de wire verifica forma, fechamento,
versão mínima e unicidade. O interpretador independente conserva rotas em cada
frame; não mistura destinos entre invocações.

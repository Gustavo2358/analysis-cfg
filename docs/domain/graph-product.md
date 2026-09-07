# Produto CFG e observabilidade

## Produto próprio

O CFG é resultado derivado imutável vinculado à publicação IR, versão, entries,
capabilities, premissas e política de precisão usadas. Não gravar successors,
alcançabilidade ou caches mutáveis na IR. IDs CFG são próprios, correlacionados com
identidades IR; não reutilizar source span como identidade de nó.

Conceitos do produto completo (somente Entry/Return implementados agora):

- inventário de nós/sequences e seus operations/program points;
- transições conhecidas com outcome, predicado/case/tag quando aplicáveis;
- entradas distintas, saídas normais/excepcionais, halt/diverge e fronteiras abertas;
- correlação para IR/provenance, gaps, capabilities e limites da alegação.

## Modelo Java CFG-FIRST implementado

No package `io.github.gustavo2358.analysis.cfg.domain`:

- `CfgGraph`: classe final com referência à Publication original e inventário de
  nós/transições imutáveis. Materializa as listas de entries e normal exits uma vez
  durante a construção; `entries()` e `normalExits()` retornam essas mesmas listas
  em O(1), sem percorrer nós ou alocar novamente;
- `CfgNodeId(PublicationId, ordinal)`: identidade do CFG, distinta de qualquer ID
  AIR. Ordinais são atribuídos deterministicamente por namespace/ID e papel; não
  têm estabilidade prometida entre publicações/revisões diferentes;
- `CfgNode.EntryNode`: ID CFG e referência à `Entries.Entry` original;
- `CfgNode.SequenceNode`: ID CFG e referência à `Sequence` original, preservando
  Return/header/values/origin. Uma Sequence origina exatamente um nó;
- `CfgNode.NormalExit`: ID CFG, PublicationId, UnitId e EntryId. É sintético e
  não tem source span/origin inventado;
- `CfgTransition(from, to, kind, activationEntry)`: `ENTRY` estabelece a Entry
  corrente, e `RETURN` vale apenas sob essa Entry, terminando no seu NormalExit.

A regra segue AIR §04.8: não existe `return.entryScope` na entrada. Um Return
compartilhado tem uma transição condicionada por Entry da Unit, sem duplicar o nó
Sequence nem inventar um exit global. As transições não são arestas incondicionais:
um consumidor deve conservar `activationEntry` ao compor um caminho. Isso não
implementa frames locais nem consulta de reachability.

Órfãs permanecem no inventário, sem predecessor artificial. Suas regras Return
continuam materializadas, sem afirmar alcançabilidade. O grafo verifica unicidade
de IDs, fechamento e compatibilidade tipada das transições; seus containers são
copiados, mas nenhuma Publication, Unit, Entry, Sequence ou lista AIR é deep-copiada.
A referência à Publication mantém coverage, precisão, gaps, premises e todas as
origens resolvíveis, sem cache mutável ou callback.

`CfgProjectionIssue` identifica recusa de formas fora do slice; falhas não têm
produto parcial/fake. Branch predicates, invoke outcomes, open control, frames e
indirect targets não têm tipos antecipados neste checkpoint.

Divergência é comportamento sem próximo estado observável: sua representação não
cria caminho artificial até uma saída normal. Uma saída sintética é convenção do
produto, não statement-fonte. Não fabricar linhas para nós sintéticos.

## Consultas

Sucessores/predecessores estruturais podem ser índices materializados. Para controle
local, o contrato conceitual é `successors(point, context)`; a API concreta pode
separar projeção plana e consulta contextual. Projeção plana deve identificar sua
sobreaproximação. Não entregar lista supostamente exaustiva se existe ControlScope
aberto que admite destinos adicionais.

Não exigir um único entry/exit para toda publicação. Um super-entry sintético é
permitido só preservando a escolha de Entry e seu escopo, sem fundir inicializações.

## Retenção e ciclo de vida

O resultado pode reter referências ao modelo `air-java` imutável e copiar somente
fatos próprios/mínimos. Não deep-copiar toda a Publication por padrão nem manter
acesso preguiçoso a serviço/produtor para completar semântica. Registrar sempre
PublicationId, revisão/versão e correlações utilizadas. Índices derivados pertencem
ao CFG e não alteram a AIR. Publicação grande exige medir memória; a API de navegação
não força cópia O(N) a cada chamada.

## Saídas humanas e máquina

DOT/HTML são apresentação, não oracle de semântica. Exportar JSON é adapter próprio,
com schema próprio se necessário. Testes comparam o produto estruturado, não strings
de console nem layout de renderização. Relatórios sempre distinguem `INVALID_IR`,
`UNSUPPORTED_CAPABILITY`, resultado parcial e resultado conforme ao escopo declarado.

# Produto CFG e observabilidade

## Produto próprio

O CFG é resultado derivado imutável vinculado à publicação IR, versão, entries,
capabilities, premissas e política de precisão usadas. Não gravar successors,
alcançabilidade ou caches mutáveis na IR. IDs CFG são próprios, correlacionados com
identidades IR; não reutilizar source span como identidade de nó.

Conceitos do produto completo (Entry, instructions lineares, Jump, Branch, Return e Halt implementados):

- inventário de nós/sequences e seus operations/program points;
- transições conhecidas com outcome, predicado/case/tag quando aplicáveis;
- entradas distintas, saídas normais/excepcionais, halt/diverge e fronteiras abertas;
- correlação para IR/provenance, gaps, capabilities e limites da alegação.

## Modelo Java implementado

No package `io.github.gustavo2358.analysis.cfg.domain`:

- `CfgGraph`: classe final com `CfgSource` destacado e inventário de
  nós/transições imutáveis. Materializa entries, normal exits e halt exits uma vez;
  `entries()`, `normalExits()` e `haltExits()` retornam as mesmas listas em O(1),
  sem percorrer nós ou alocar novamente. `preciseControlCapabilities()` também
  é materializada: registra consumo preciso apenas de controle, atualmente
  memory.regions@1, sem alegação de efeitos/storage;
- `CfgNodeId(PublicationId, ordinal)`: identidade do CFG, distinta de qualquer ID
  AIR. Ordinais são atribuídos deterministicamente por namespace/ID e papel; não
  têm estabilidade prometida entre publicações/revisões diferentes;
- `CfgNode.EntryNode`: ID CFG, EntryId e LabelId inicial opcional;
- `CfgNode.SequenceNode`: ID CFG, LabelId, IDs das instructions em ordem e um
  `CfgControl` compacto. Predicados, operandos, efeitos, fallbacks, origins e gaps
  continuam pertencendo ao `ProgramStore`; uma Sequence origina exatamente um nó;
- `CfgNode.NormalExit`: ID CFG, PublicationId, UnitId e EntryId. É sintético e
  não tem source span/origin inventado;
- `CfgNode.HaltExit`: ID CFG, OperationId e HaltKind. Uma saída por ocorrência,
  sem singleton global e sem fabricar source span. HaltKind NORMAL/ABNORMAL é retido;
- `CfgTransition(from, to, kind, activationEntry)`: ENTRY estabelece a Entry;
  JUMP a conserva e usa somente LabelId explícito; BRANCH_TRUE/BRANCH_FALSE
  usam respectivamente os targets explícitos e conservam contexto e alternativa;
  RETURN termina no NormalExit
  dessa Entry; HALT termina em HaltExit sob esse contexto, sem successor do exit.

A regra segue AIR §04.8: não existe `return.entryScope` na entrada. Um Return
compartilhado tem uma transição condicionada por Entry da Unit, sem duplicar o nó
Sequence nem inventar um exit global. As transições não são arestas incondicionais:
um consumidor deve conservar `activationEntry` ao compor um caminho. Isso não
implementa frames locais nem consulta de reachability.

Órfãs permanecem no inventário, sem predecessor artificial. Suas regras Jump,
Branch, Return e Halt continuam materializadas por Entry, sem afirmar alcançabilidade.
HaltExit é compartilhado por ocorrência; as transições preservam cada activationEntry.
NormalExits são inventário por Entry, inclusive quando nenhum Return os utiliza. O grafo verifica unicidade
de IDs, fechamento e compatibilidade tipada das transições; seus containers são
copiados. O grafo não retém Publication, Unit, Entry, Sequence nem Terminator; a
testemunha fraca da Publication serve somente à admissão do caminho residente.
Coverage, precisão, gaps, premises e origins permanecem no `ProgramStore` pareado.

`CfgProjectionIssue` identifica recusa de formas fora do slice; falhas não têm
produto parcial/fake. O predicate de Branch permanece integralmente no
`ProgramStore`, incluindo TypeRef, dependencies, remainingReads, reason, operand
owner e origin. O SequenceNode retém somente OperationId e os dois LabelIds
estruturais. Literal true/false não elimina alternativa.
CfgGraph valida endpoints de Branch e distingue os braços no equals/hash de
CfgTransition; destinos iguais são válidos, duplicata exata não. Invoke outcomes,
open control, frames e indirect targets permanecem futuros.

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

O resultado copia somente fatos estruturais próprios/mínimos e não mantém o AIR
residente vivo. O `ProgramStore` pode reter ou paginar o modelo conforme sua
implementação. Não deep-copiar toda a Publication por padrão. Registrar sempre
PublicationId, revisão/versão e correlações utilizadas. Índices derivados pertencem
ao CFG e não alteram a AIR. Publicação grande exige medir memória; a API de navegação
não força cópia O(N) a cada chamada.

## Saídas humanas e máquina

DOT/HTML são apresentação, não oracle de semântica. Exportar JSON é adapter próprio,
com schema próprio se necessário. Testes comparam o produto estruturado, não strings
de console nem layout de renderização. Relatórios sempre distinguem `INVALID_IR`,
`UNSUPPORTED_CAPABILITY`, resultado parcial e resultado conforme ao escopo declarado.

## Pontos correlacionados no slice linear

SequenceNode.operations e CfgControl.operation bastam para identificar as posições
antes/depois de cada ocorrência e antes do terminador. O payload correspondente é
resolvido pelo `ProgramStore`, com OperationId, operandos e origins intactos. O nó
CFG é a Sequence, não a instruction.
Não existe uma classe ProgramPoint adicional neste slice nem identidade CFG disfarçada
de OperationId. AIR §08.2 exige correlação observável, não uma API física específica.

O metamorfismo split registra explicitamente a correspondência entre posições
originais e as duas Sequences ligadas por Jump; o passo auxiliar não perde operações.
Pontos before/after(outcome) tipados poderão ser necessários em slices com outcomes;
nenhuma API de dataflow é antecipada agora.

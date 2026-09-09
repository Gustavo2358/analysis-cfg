# analysis-cfg-json v1 — contrato local do produto CFG

Definido em WORK-CFG-026 antes da implementação. Identidade `schema: analysis-cfg-json`,
`schemaVersion: 1.0.0`. Propriedade exclusiva de analysis-cfg; não integra Analysis IR
nem aplica o binding AIR aos IDs CFG. Transporte de topologia derivada e correlações,
não serialização automática de records ou segunda Publication.

## Envelope e ordem de propriedades

Todos os campos são obrigatórios, sem valores implícitos:

1. `schema`, `schemaVersion`: strings constantes acima.
2. `airVersion`: major.minor.patch, componentes públicos da versão AIR.
3. `publication`: objeto `{ "localId": string }`.
4. `buildStatus`: somente `CFG_BUILT`; significa produto presente, não COMPLETE.
5. `projectionPolicy`: `KNOWN_SUBSET` ou `STRICT`, mapping explícito. CLI usa defaults/KNOWN_SUBSET.
6. `sourceKnowledge`: `{ "publicationInventory": status, "units": [...] }`.
   Cada item é `{ "unit": UnitId, "inventory": status }` na ordem das Units da
   Publication. Status tem mapping explícito `COMPLETE`, `PARTIAL`, `UNAVAILABLE`.
   O writer não fortalece fatos. GOBACK mantém PARTIAL em ambos os escopos.
7. `nodes`: na ordem exata de CfgGraph.nodes().
8. `transitions`: na ordem exata de CfgGraph.transitions().

O AIR de entrada permanece a fonte de origins, uncertainties, coverage items,
precision por dimensão, operands, instructions e demais fatos. O resumo de inventory
não certifica precisão das operações, reachability, totalidade do controle, perfil
AIR ou independência de gaps. Para essas perguntas, consultar a AIR correlacionada.
Não há timestamp, hostname, paths de máquina, process id ou revisão mutável do produtor.
A versão deste contrato identifica o produto; revisão do build fica na evidência Git.

## IDs e nós

Os objetos têm as propriedades nesta ordem; strings são componentes públicos exatos:

- PublicationId: `{ "localId": string }`.
- UnitId: `{ "publication": string, "localId": string }`.
- EntryId/LabelId/OperationId: `{ "publication": string, "unit": string, "localId": string }`.
  O campo que contém o ID define seu domínio, sem interpretar seu texto.
- CfgNodeId: `{ "publication": string, "ordinal": string }`. `ordinal` é decimal
  canônico não negativo de long (0..9223372036854775807), sem zeros iniciais exceto
  "0". É identidade CFG, válida no produto desta projeção; não é ID AIR nem promessa
  de estabilidade entre algoritmos/versões/policies.

Cada nó tem `id`, `kind` e campos de correlação, nesta ordem:

| kind | campos após kind |
| --- | --- |
| ENTRY | `entry`: EntryId |
| SEQUENCE | `label`: LabelId; `terminator`: `{ "kind": token, "operation": OperationId }` |
| NORMAL_EXIT | `unit`: UnitId; `entry`: EntryId |
| HALT_EXIT | `operation`: OperationId; `haltKind`: token |

Terminator tokens: `JUMP`, `BRANCH`, `RETURN`, `HALT`. Demais formas AIR são recusadas
pelo kernel; o writer também recusa explicitamente terminador fora deste v1.
Halt kinds são `NORMAL` e `ABNORMAL` (mappings explícitos).
Nenhum nome de enum, classe ou toString define o wire.

## Transições

Cada item: `kind`, `from`: CfgNodeId, `to`: CfgNodeId, `activationEntry`: EntryId.
Tokens explícitos: `ENTRY`, `JUMP`, `BRANCH_TRUE`, `BRANCH_FALSE`, `RETURN`, `HALT`.
Alternativas mesmo destino permanecem separadas. activationEntry qualifica a regra
contextual, não prova alcançabilidade. Não existe fallthrough implícito.

No GOBACK manual: Sequence(Return) tem ordinal "0", Entry "1", NormalExit "2";
as transições são ENTRY 1→0 e RETURN 0→2, ambas na primary-entry.

## Encoding, determinismo, limites e publicação

Os caps abaixo descrevem o produto legado, sem aprovação como política CP5.
[CORE-SIZE-001](decisions/ADR-0014.md) os classifica como
[dívidas de capacidade](../work/cp5-follow-ups.md#size-cap-debts); migração produtiva
fica para tarefa autorizada, mantendo validações de encoding, IDs e aritmética.

UTF-8 estrito sem BOM e sem newline final; JSON compacto, sem pretty printing.
Propriedades seguem as ordens acima. Aspas/backslash são escapados; controles U+0000
até U+001F usam `\u00xx` hexadecimal minúsculo. Outros scalars Unicode são UTF-8
literal; surrogate UTF-16 isolado causa falha de serialização, nunca substituição.
Mesmo resultado/ordem gera bytes idênticos, independente de locale ou máquina.

Input: um único AirJson.Limits (default 16 MiB, profundidade 128) governa leitura
física e codec. Ler no máximo o bound mais um byte antes de decode; excesso é
IMPLEMENTATION_LIMIT físico. Não carregar arquivo arbitrariamente grande.
Output: default 64 MiB de bytes; limite configurável pela API Java para operação/teste,
sem flag CLI. Excesso ou Unicode inválido gera CfgJsonException, sem truncamento.
Todos os bytes são produzidos antes de criar temp ou tocar no destino. Temp no
mesmo diretório, move com ATOMIC_MOVE/REPLACE_EXISTING; AtomicMoveNotSupportedException
aciona fallback explícito REPLACE_EXISTING. No fallback não há garantia de atomicidade.
Temp é removido em falha. Falha de input/build/serialização preserva destino existente.
Falha durante move depende das garantias do filesystem e nunca é reportada como sucesso.

## Compatibilidade e fronteira experimental

V1 enumera o domínio CFG atual completo. Evoluções incompatíveis exigem versão major
nova; adições de campos/tokens exigem decisão e versão nova do contrato, sem omissão
silenciosa. Não há CFG JSON reader neste checkpoint nem promessa de interoperabilidade
universal. Novos consumers devem selecionar versão suportada explicitamente.

A entrada usa exclusivamente shared air-json 0.1.0-SNAPSHOT em air-java
ce530a7e17ab12b23c48f29425f503ff920b09fb: `analysis-ir-json` bindingVersion 1.0.0,
airVersion 2.0.0, DRAFT em analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933.
Autorização humana explícita libera esse snapshot experimental sem promoção normativa.


No v1 também ficam fora do wire os índices de navegação (deriváveis das arrays),
preflight detalhado, limites ValidationOptions e preciseControlCapabilities do
objeto em memória. A policy efetiva é transportada, mas nenhuma qualificação de
capabilities/precisão de extensões é reivindicada pelo JSON v1.

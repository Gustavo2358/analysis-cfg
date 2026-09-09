# analysis-dataflow-result / 1.0.0 — snapshot de review

**Design/harness somente.** Nome/versão do futuro artefato derivado; nenhum writer,
codec, CLI ou resultado calculado existe nesta entrega. H6 aprova separação do
resultado/CLI; este snapshot permite review dos campos antes W5. Não é binding AIR,
CFG JSON v2 ou schema normativo do upstream.

[Exemplo CP4E](../evals/cp5/result-review.json) usa envelope `design` que declara
NOT_EXECUTED; `result` contém o expected conceitual. Não reportar seu executionStatus
STABLE como execução real. Statistics ficam unavailable/null, sem counters inventados.
[Contrato de campos](../evals/cp5/result-contract.json) é validado pelo harness junto
às relações de owner, scopes e remainder; não é um codec nem um validator AIR.

| Campo | Significado/obrigação |
| --- | --- |
| schema/version | analysis-dataflow-result / 1.0.0, evolução local explícita |
| analysisKey | implementação/versão, profile, direção, precisão, options, Entry; cache pertence à sessão/snapshot |
| publicationId/unitId/entryId | identidade completa, sem ordinais internos exportados |
| executionStatus | STABLE, ANALYSIS_LIMIT, UNSUPPORTED ou INVALID_INPUT; não é coverage |
| modelScope/sourceScope | KNOWN_GRAPH_ENTRY separado da abertura CONTROL/Unit da fonte |
| observations | ponto, subject/location e valor; ausente por recusa não equivale a lista de candidatos vazia |
| point | OperationId, Entry, before/after e outcome; after fronteira não admitida é UNSUPPORTED_POINT |
| subject/place/storage | ObjectPlace/ObjectId consultado e Cell/Storage base; Object não é memória exclusiva |
| reachability | REACHABLE/UNREACHABLE_IN_MODEL; não prova fonte inalcançável sob controle aberto |
| value | known(text), Candidates/Saturated, enumerated e modelValueRemainder |
| sourceUnknownRemainder/effectiveUnknownRemainder | abertura pertinente de fonte e OR com remainder de modelo |
| saturationReason/limitReason | cardinalidade local diferente de budget interrompido; causa/fase explícitas |
| precision | exatidão somente no modelo declarado; sem claim de testemunho de caminho ou fonte completa |
| premises/evidence/provenance | refs pertinentes da AIR/snapshot/regra, sem path tree ou causalidade inventada |
| statistics | métricas por fase/run/Entry/epoch; unavailable neste exemplo, medidas na implementação |

IDs usam domínio/localId e owners completos de Publication/Unit quando pertinentes.
A forma estrutural é proposta de transporte local, sem exigir codec AIR para validar
resultado. A prova futura de resolução dos refs usa a AIR correlacionada. IDs/hashes
CP4E só entram em fixtures/evidências, nunca constantes de produção.

O exemplo before(Return) conserva modelValue {PROGA} fechado, fonte CONTROL aberta,
restante efetivo true e precisão condicionada ao modelo. Nenhum gap text fecha scope.
Não enumerado por saturação mantém restante e motivo; não alcançado no modelo usa
value=null, nunca Candidates({},false). Run limitado/recusado não publica observações
provisórias como facts finais; limitReason não pode coexistir com STABLE.

W5 deve congelar por review o wire definitivo, budgets/defaults, parser nominal de
reports e falhas antes do writer. A CLI separada usará AIR file → reader → BuildCfg
→ sessão → PossibleValues → plano padrão → writer. Plano padrão observa destinos
escritos por Sequence em before(terminator), sem selecionar primeiro Object/WS-PGM/
PROGA ou varrer todos Objects por Sequence. CLI CFG existente mantém contrato próprio.

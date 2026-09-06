# WORK-CFG-001 — Discovery de fronteiras e plano executivo

Status: `completed`. Data de conclusão: 06/09/2026. Autorização executada:
`discovery` docs/harness-only.

## Resultado

A arquitetura pré-Java foi fechada sem blocker material. Analysis IR 2.0.0 continua
normativa em `0b2fbce7046010b22b32efa8cbc3e75ccba09442`; `air-java` é o owner do
modelo/validator Java no SHA `2108294d9dfeb89d0019ce75fab27172b15a75b9`, com
coordenadas `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT` e Java 21 sem preview.

`BuildCfg` recebe a `Publication` desse artefato e executa `AirValidator` no
preflight. Arquivo/memória convergem para essa porta. Binding JSON normativo pertence
ao `analysis-ir`, ainda não existe e não bloqueia CFG-FIRST. Lifetime permite reter
AIR imutável/IDs e índices próprios sem deep copy O(N), mutação ou callback ao
producer.

CFG-FIRST ficou definido como Entry → Sequence(`Return`) → normal exit, com
Publication/Unit/Entry scope preservado e sem fallthrough. `MVP-CFG-01`, transporte,
integrações e perfis permanecem posteriores. ADR-0001/0002 fecharam porta e
adapters; ADR-0003 fechou Sequence→nó; ADR-0004 registrou a consequência dos tipos
sealed; ADR-0007 fechou Java/modelo; ADR-0008 separou os milestones; ADR-0009
registrou o binding. Todos foram aceitos sem alegar implementação.

## Upstreams verificados

- `analysis-ir/main`: `0b2fbce7046010b22b32efa8cbc3e75ccba09442`, AIR 2.0.0;
- `air-java/main`: `2108294d9dfeb89d0019ce75fab27172b15a75b9`;
- `air-java ./scripts/check.sh`: exit 0, 94 checks e `java.base` apenas;
- `air-java mvn verify`: exit 0; CI remoto `contracts`: success no mesmo SHA;
- `proleap-poc/main`: `7a376f33f55127f53c63b86d3228671b9c6a348d`,
  frontend Java 17 cuja boundary termina no COBOL Semantic Product; o avanço pelo
  PR #30 foi detectado no challenge e revalidado antes do fechamento;
- `cobol-lower`: repositório ausente; componente upstream planejado, sem commit/API.

Limites conhecidos do `air-java` — SNAPSHOT sem tag/release, binding ausente e
validação/perfis não integrais — foram preservados como readiness futura, não
reinterpretados como incompatibilidade do slice mínimo.

## Backlog e evals

BACKLOG-CFG-002 foi reformulado para bootstrap Java 21/Maven + dependência
`air-java` e ficou `ready_for_authorization`, sem work item. BACKLOG-CFG-005 agora é
CFG-FIRST; BACKLOG-CFG-022 preserva linear/jump/halt; BACKLOG-CFG-006 mantém
branch/IF depois dele; BACKLOG-CFG-004/008 tornam JSON/CLI posteriores;
BACKLOG-CFG-023 cria o micro-E2E com dependência externa explícita;
BACKLOG-CFG-017 mantém a aceitação bilateral madura. EVAL-CFG-025/026 cobrem os
novos marcos; nenhum eval/perfil foi declarado implementado.

## Validação de encerramento

- `bash scripts/harness/check-docs.sh`: PASS, exit 0;
- `python3 scripts/harness/test_harness.py`: PASS, 38 testes, exit 0;
- `bash scripts/harness/check-fast.sh`: PASS, exit 0;
- JSONs alterados validados com `python3 -m json.tool`: exit 0;
- `git diff --check`: PASS, exit 0;
- inventário final: nenhum `.java` ou `pom.xml`;
- `architecture`, `semantic`, `performance`, `integration` e `full`: `UNAVAILABLE`,
  exit 3, como exigido para produto ainda não autorizado;
- `air-java ./scripts/check.sh`: PASS, 94 checks, exit 0;
- `air-java mvn verify`: PASS, exit 0; CI remoto `contracts`: success no SHA
  fixado.

Esses resultados fecham somente discovery, documentação e harness. Os gates de
produto não foram declarados PASS e nenhum eval de CFG foi executado.

## Escopo negativo preservado

Nenhum Java, POM, BuildCfg, CFG, adapter/codec JSON, mudança em upstream ou início de
BACKLOG-CFG-002. O PR deve permanecer aberto para human review, sem merge/auto-merge.

## Próximo checkpoint

Um novo work item de implementação pode ser promovido somente de
BACKLOG-CFG-002 e somente após autorização explícita. Nada foi iniciado
automaticamente.

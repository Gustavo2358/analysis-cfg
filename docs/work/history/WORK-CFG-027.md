# WORK-CFG-027 — 4D concluído

PR #11 [MERGED](https://github.com/Gustavo2358/analysis-cfg/pull/11), verificado via GitHub em 2026-09-09. Merge ec525cbbad96d70c9663faa88e2672148fa8ee71, base main do CP5. O status de review abaixo é histórico; não prevalece sobre o merge confirmado. Nenhuma evidência CP4 foi recalculada ou promovida.

## Registro anterior: work-item.json

```json
{
  "id": "WORK-CFG-027",
  "backlog_id": "BACKLOG-CFG-027",
  "title": "4D — pin 4B e payload escalar no CFG",
  "status": "active",
  "risk": "medium",
  "goal": "Consumir o golden Object + Cell + Assign + Return do merge 4B pelo reader compartilhado e kernel CFG existente, reproduzivelmente.",
  "authorization": "implementation",
  "authorization_evidence": "Pedido explícito do usuário em 2026-09-08 autoriza somente 4D no analysis-cfg, RED real, pin ce530a7e17ab12b23c48f29425f503ff920b09fb, fixture byte-identical, gates/challenges, commits, push e PR/CI; sem merge, sem 4C/4E.",
  "checkpoint": "4D",
  "must_read": [
    "AGENTS.md",
    "docs/work/index.md",
    "docs/engineering/work-item-protocol.md",
    "docs/engineering/gates.md",
    "docs/engineering/testing.md",
    "ARCHITECTURE.md",
    "docs/architecture/ports-and-adapters.md",
    "docs/architecture/cfg-json-v1.md",
    "docs/domain/core-control.md",
    "docs/engineering/toolchain-and-modules.md",
    "docs/sources/sources.lock.json",
    "docs/sources/upstream-state.md"
  ],
  "related_decisions": [],
  "related_invariants": [
    "INV-CFG-002"
  ],
  "evals": [
    "EVAL-CFG-032",
    "EVAL-CFG-031"
  ],
  "source_scope": [
    ".github/workflows/ci.yml",
    "docs",
    "scripts/project",
    "MANIFEST.sha256"
  ],
  "test_scope": [
    "cfg-adapters/src/test",
    "cfg-launcher/src/test"
  ],
  "must_not_change": [
    "cfg-kernel",
    "cfg-adapters/src/main",
    "cfg-launcher/src/main",
    "analysis_ir authority including binding version and DRAFT",
    "historical evidence (lifecycle/link repair only)",
    "sibling repositories",
    "CFG JSON schema/algorithm",
    "4C and 4E"
  ],
  "gates": [
    "fast",
    "architecture",
    "semantic",
    "integration"
  ],
  "stop_condition": "Commits/push/PR e CI verde no head exato; parar para review humano sem merge/auto-merge."
}
```

## Registro anterior: spec.md

# 4D — contrato

Autoridade AIR 2.0.0 @ 122ce54e1b9ef9b00646f93ece409ca8b63bc933, binding 1.0.0 DRAFT preservados. Runtime 4B @ ce530a7e17ab12b23c48f29425f503ff920b09fb. Golden escalar lido como bytes upstream, nunca reencodificado. Reader real → shared decode → BuildCfg defaults → CFG_BUILT/KNOWN_SUBSET.

Oracle independente: três nós Sequence(body, Return leave), Entry(start), NormalExit(alpha,start); ENTRY 1→0 e RETURN 0→2. Source Sequence e Publication idênticas às recebidas. Um Assign(set-program), ObjectPlace(data-slot), Literal TextValue(PROGA), Cell(backing-cell); referências por IDs completos. PARTIAL nos dois inventários, uncertainties/coverage preservadas. Zero delta de produção. CFG JSON v1 não contém instructions. Smoke com 4096 Assigns mantém três nós/duas transições sem threshold temporal. Input default 16 MiB/depth128 permanece.

## Fora de escopo

4C/4E, algoritmo CFG, dataflow, novo schema/nó/edge, streaming e mudança de limites.

## Objetivo

Consumir reprodutivelmente o transporte escalar 4B mantendo semântica CFG.

## Problema

Codec 1A recusa inventário de storage com IMPLEMENTATION_LIMIT, antes do kernel.

## Registro anterior: plan.md

# Plano

1. Confirmar baseline limpo e merges, arquivar lifecycle 2B.
2. Antes do repin/materialização: RED usando reader baseline e scalar upstream extraído em /tmp.
3. Pin ativo 4B, materializar bytes e proveniência, oracle in-memory e golden manual/CLI.
4. Guardas pin/fixture/suítes e challenges compiláveis, restore byte a byte, segundo GREEN.
5. Gates e Maven verify, commits/push/PR, CI head exato e review humano sem merge.

## Dependências

Somente air-java 4B mergeado. Não depende do 4C.

## Fatiamento

RED antes do repin, integração/pin mínimo, challenges e segundo GREEN, PR.

## Registro anterior: eval.md

# Evals

EVAL-CFG-032: payload e identidade via reader/porta, topologia manual, inventários, limites, smoke 4096 Assigns e CLI duas execuções. EVAL-CFG-031 integral preservado. Baseline RED deve ser AirJsonException IMPLEMENTATION_LIMIT, nunca I/O/compilação/configuração.

Challenges: CI old; lock/CI drift; fixture drift; oracle ignora instructions; Sequence perde Assign; nó/edge por Assign; Return sem edge; STRICT default; PARTIAL promovido; parser local; writer duplica AIR; regressão GOBACK ausente. Reportar cada detector, saída e restore.

## Casos adversariais

Os 13 challenges listados acima devem falhar pelas guardas ou asserções identificadas.

## O que prova corretude

Expected manual independente e payload observado na Sequence original; golden CFG isolado não prova Assign.

## Registro anterior: state.md

# WORK-CFG-027 — Estado

## Onde estamos

[PR #11](https://github.com/Gustavo2358/analysis-cfg/pull/11) aberto para review humano. Implementação e evidência em 97b8884e123b3cea5369966f32f21e5f7c22cebf, RED em c48a898; branch publicada. Baseline limpo/atualizado 2b4df46d53ce5b21a5c315d3f691b183cb6bd124, branch chore/pin-air-json-scalar-assign. Merge 4B e PR #10 confirmados por GitHub.

## Verde conhecido

RED baseline em c48a898: IMPLEMENTATION_LIMIT em $.publication.storage. Segundo GREEN em Temurin 21: fast (47), scope/manifest/diff check, architecture, semantic (84), integration (37), Maven clean verify (139). 14 challenges RED com restore byte a byte. Produção kernel/adapters/launcher sem delta. Scalar 14554 bytes, SHA-256 40b9cec1bcc1c1e40cf3b9e3c48e834835e478e1d84bccafa575d63497ef3b60; CLI determinística, payload por identidade, smoke 4096 Assigns com três nós/duas transições. GOBACK intacto. Performance UNAVAILABLE/exit 3. [Evidência](../evidence/WORK-CFG-027/validation.md).

## Restante

Confirmar CI no head exato após este vínculo documental; depois apenas review humano. Sem merge/auto-merge. Recibo remoto final no PR evita autorreferência do SHA no próprio commit. 4C/4E não iniciados.

## Descobertas que afetam o plano

ObjectPlace armazena ObjectId, não ponteiro para ObjectDeclaration; provar resolução por ID completo e mesma instância da declaração/célula na Publication retida. Upstream extraído em /tmp para builds; nenhuma fonte irmã alterada.

# WORK-CFG-026 — 2B concluído

PR #10 [MERGED](https://github.com/Gustavo2358/analysis-cfg/pull/10), verificado via GitHub em 2026-09-08: head aa2eea66e4ace0a6cf58bbbc49b16ee00d6ba1a3, merge 2b4df46d53ce5b21a5c315d3f691b183cb6bd124, merged_at 2026-09-08T00:16:46Z. Reader shared AirJson, writer CFG JSON v1 e CLI implementados. Evidência e contratos do 2B preservados abaixo; referências relativas ajustadas somente para navegação.

## Registro anterior: work-item.json

```json
{
  "id": "WORK-CFG-026",
  "backlog_id": "BACKLOG-CFG-026",
  "title": "2B — AIR JSON reader + CFG JSON output + CLI",
  "status": "active",
  "risk": "medium",
  "goal": "Entrada AIR JSON por arquivo e saída CFG JSON v1 pelo kernel existente, com cobertura honesta e integração executável.",
  "authorization": "implementation",
  "authorization_evidence": "Pedido explícito do usuário em 2026-09-07 autoriza somente 2B, uso experimental do binding DRAFT pinado via shared AirJson, módulos adapters/launcher, contrato local, gates, challenges, commits, push e PR. Não reutiliza WORK-CFG-025; sem merge/auto-merge.",
  "checkpoint": "2B — AIR JSON reader + CFG JSON output + CLI",
  "must_read": [
    "AGENTS.md",
    "docs/work/index.md",
    "docs/engineering/work-item-protocol.md",
    "docs/engineering/gates.md",
    "docs/engineering/testing.md",
    "ARCHITECTURE.md",
    "docs/architecture/ports-and-adapters.md",
    "docs/architecture/boundaries.md",
    "docs/engineering/toolchain-and-modules.md",
    "docs/sources/sources.lock.json"
  ],
  "related_decisions": [],
  "related_invariants": [
    "INV-CFG-002"
  ],
  "evals": [
    "EVAL-CFG-031"
  ],
  "source_scope": [
    "pom.xml",
    "cfg-adapters",
    "cfg-launcher",
    "scripts/project",
    "scripts/harness/test_harness.py",
    ".github/workflows/ci.yml",
    "README.md",
    "ARCHITECTURE.md",
    "docs",
    "scripts/analysis-cfg",
    "START_HERE.md",
    "MANIFEST.sha256"
  ],
  "test_scope": [
    "cfg-adapters",
    "cfg-launcher",
    "scripts/project",
    "scripts/harness/test_harness.py"
  ],
  "must_not_change": [
    "cfg-kernel",
    "analysis-ir and air-java pins",
    "other repositories",
    "../roadmap.md",
    "historical evidence claims (link repair only)"
  ],
  "gates": [
    "docs",
    "fast",
    "architecture",
    "semantic",
    "integration"
  ],
  "stop_condition": "Após gates/challenges, commits/push/PR próprio e CI verde no head exato, parar para review humano. Sem merge/auto-merge e sem iniciar E2E cross-repo/2A."
}
```

## Registro anterior: spec.md

# spec

## Problema
A porta em memória está pronta; faltam transporte físico e CLI.

## Objetivo
Arquivo AIR → shared AirJson → Publication → BuildCfg defaults/KNOWN_SUBSET → CFG_BUILT → arquivo analysis-cfg-json v1. Contrato local definido antes de código em docs/architecture/cfg-json-v1.md.

## Domínio de entrada suportado
Snapshot DRAFT analysis-ir-json 1.0.0 / AIR 2.0.0 pinado, limitado às formas do shared air-json 1A. Writer cobre todos os nós e transições atuais do kernel.

## Classes semânticas
Sucesso CFG_BUILT com inventário COMPLETE/PARTIAL; falha física/codec; recusa do kernel; serialização; output I/O.

## Premissas
Autorização humana explícita permite DRAFT sem promoção normativa. Porta e kernel permanecem byte a byte. IDs/cobertura vêm de componentes públicos, nunca reflexão ou string runtime.

## Comportamento esperado
Dois argumentos posicionais. Exit 0/2/3/4/5/6. Bytes completos antes de temp/move, limites explícitos e determinismo.

## Comportamento diante de incerteza
PARTIAL continua PARTIAL; nenhuma afirmação de alcance, completude global ou perfil IR. AIR original mantém os fatos não duplicados no produto.

## Fora de escopo
2A, cross-repo E2E, parser AIR próprio, novas operações, reader CFG, dataflow, orquestrador, promoção DRAFT.

## Registro anterior: plan.md

# plan

## Fatiamento
1. Reconciliar 025 e baseline; definir contrato, oracle manual e testes RED.
2. Materializar somente adapters/launcher; reader limitado, writer explícito e CLI.
3. Evoluir arquitetura e integration gate, CI, documentação e eval.
4. GREEN, challenges RED, restauração byte a byte, segundo GREEN, revisão integral, commits, push, PR e CI do head.

## Dependências
Baseline limpo b614712fda55fef12639cbe18fd90793faa1fb3b após fetch/pull; branch feat/air-json-cfg-cli. air-java b78f4068d8a479f48eb048b8d76fa60a0997dc4a obtido em cópia isolada /tmp; analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933. Roadmap pai lido como sequência, sem edição; instrução atual fixa CLI em dois posicionais sem policy flag.

## Superfície arquitetural provável
Kernel intacto. cfg-adapters contém AirJsonFileReader, AirInputLimitException, CfgJsonWriter, CfgJsonBytes, CfgJsonException. cfg-launcher contém AnalysisCfg (run/main). Infra CFG apenas escrita explícita UTF-8, sem parser JSON ou dependência externa adicional.

## Migrações requeridas
Reactor e gates evoluem com inventários exatos. Não há migração do modelo/porta/pins. Performance/full seguem indisponíveis.

## Registro anterior: eval.md

# eval

## O que prova corretude
EVAL-CFG-031: fixture upstream estática, golden CFG manual anterior ao writer, decode real, build real, topologia/correlações manuais, PARTIAL/KNOWN_SUBSET, equivalência com Publication em memória e bytes determinísticos.

## Casos adversariais
Arquivo ausente, limite max+1, BOM/UTF-8/versão/AIR inválida, forma válida fora do codec, recusa real do kernel, output impossível, serialização limitada/inválida preservando destino, args sem build. Writer cobre Entry/Sequence/NormalExit/HaltExit e seis kinds, IDs escapados e namespaces distintos.

## Casos de regressão
102 testes kernel, semantic 84 métodos e 41 testes baseline do harness preservados; harness expandido para 47. Integration exige 31 métodos em três suítes nominais.

## Propriedades/relações metamórficas
Duas execuções sobre mesmos bytes geram o mesmo golden. Sem metadata de máquina. Challenges dos 13 atalhos exigidos e ausência/skip/duplicação da suíte devem ficar RED, restaurar fontes byte a byte e obter segundo GREEN.

## Expectativas de escala
Limites operacionais de input/output, sem benchmark nem claim de performance. Testes planejados ainda não são evidência executada.

## Registro anterior: state.md

# WORK-CFG-026 — Estado

## Onde estamos

2B implementado, revisado e validado localmente. [PR #10](https://github.com/Gustavo2358/analysis-cfg/pull/10) aberto para main; sem merge/auto-merge. PR #9 reconciliado e 025 encerrado.
Baseline b614712fda55fef12639cbe18fd90793faa1fb3b, branch feat/air-json-cfg-cli.
Contrato e RED por API ausente commitados em 7d22315 antes da produção.
Implementação em a3ff7c4e0408998ec6ca76a35c51436a16492e3a; push e head remoto confirmados.

## Verde conhecido

Segundo GREEN em Temurin 21: docs/fast (47), architecture (102 kernel + 31 transporte),
semantic (84), integration (31), Maven clean verify (133) e execução CLI/golden passaram.
17 challenges RED, restauração byte a byte. Performance/full executados com exit 3
UNAVAILABLE. Scope/manifest/diff check passaram. [Evidência](../evidence/WORK-CFG-026/validation.md).

## Restante

Confirmar CI no head final após este vínculo documental e parar para review humano.
O recibo remoto do head final fica no PR para evitar autorreferência do SHA neste
commit. Review humano ainda não recebido; item permanece ativo. Não fazer
merge/auto-merge. E2E cross-repo não iniciado.

## Descobertas que afetam o plano

Upstream obtido em cópia isolada /tmp, sem alterar repo irmão. Exceções AirJson
atravessam intactas; limite físico tem tipo próprio porque construtores do upstream
não são públicos. O modelo pinado já recusa surrogate isolado nos IDs; o writer
também protege sua primitiva UTF-8. Fallback de move é testado sem alegar atomicidade.
Memória/arquivo provam observações de controle/coverage do v1; não duplicam toda a AIR.

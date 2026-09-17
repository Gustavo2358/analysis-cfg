# Verificação proporcional — FD-H e FD-W

Autoridade: [Lean Harness](../../engineering/lean-harness.md), gates de cada repo
e scope da wave. Nenhum executor novo. G0–G6 abaixo são seleção de custo, não
checklist administrativo. Teste não executado nunca é PASS.

## Custo e marcos

| Classe | Quando / obrigação |
| --- | --- |
| H / G0 / DOCS_ONLY | H0–H4: YAML/JSON/links/escopo, docs gate e higiene Git; sem build semântico |
| FAST / G1–G2 | loop: teste novo da regra + negativo, regressão da família; compile/lint pertinente |
| FAST / G3 | pequenos contratos SP/codec/reader nas duas pontas; retirada de campo necessário deve falhar |
| FAST / G5 | `lean.py fast` fixo uma vez após estabilizar cada repo produtivo; não substituir por seleção ad hoc |
| QUALIFICATION / G4+G6 | cross-repo selecionado; efeitos/storage/CFG/dataflow em W3/W4; CICS/APIs W8/W10 se alteram núcleo/contrato; adversariais/metamórficos |
| FINAL / E2E / G6 | W11: core N+C nos pins finais, corpus fixado, compatibilidade CALL+FILE e escala; qualification-local dos repos materiais. D/W10 tem qualificação posterior própria |

G6 se justifica por lei compartilhada alterada, consumers não delimitáveis,
regressão fora da frontier ou qualificação global. Rodar full a cada commit não
agrega sinal. Requisitos técnicos locais prevalecem sobre esta otimização.
Mudança apenas documental/pin de conteúdo equivalente não invalida toda a suíte.
Remoto: **FAST ONLY**, nenhum full/workflow_dispatch caro.

## Ambiente e comandos existentes

Executar na raiz do **worktree do repo indicado**. Java 21, Maven e Python com
PyYAML conforme bootstrap local. `LOWER_BUILD_ROOT`/`CFG_BUILD_ROOT` apontam para
`.harness-results/build` do worktree ou outro caminho exclusivo da campanha.
Não reutilizar build mutável de outra campanha; não confundir jars SNAPSHOT com
fonte pinada. Logs em `.harness-results/`, novos diretórios por execução.

| ID usado no item | Repo / comando real e escopo |
| --- | --- |
| H-DOCS | cada produto modificado: `python3 -B scripts/harness/lean.py docs` (política, pins, navegação, contracasos lean; sem Maven) |
| F-DECL | frontend: `mvn -B -ntp -Dtest=ProcedureFileProgramReferenceResolverTest,SemanticProductIntegrityValidatorTest,SemanticProductStatementInventoryTest,SemanticProductMoveCallContractTest test` |
| F-STORAGE | frontend: `mvn -B -ntp -Dtest=StorageLayoutTest,StorageOverlayTest,PartialWriteEffectsTest,ScopedStorageEvidenceTest test` |
| F-CICS | frontend: `mvn -B -ntp -Dtest=CicsProgramControlTest test` mais testes novos da família FILE |
| L-INPUT | lower: `python3 -B scripts/harness/lean.py fast`; focal abaixo reutiliza classes já compiladas |
| A-CODEC | air-java: `bash scripts/check.sh` (offline compile + modelo + CodecSuite + boundaries) |
| C-DEP | CFG: `mvn -B -ntp -pl analysis-adapters -am -Dtest=W1dDependencyTest,PartialDependencyTest,CicsInvokeRouteTest,CicsTargetTimingTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| C-VALUES | CFG: `mvn -B -ntp -pl analysis-values -am -Dtest=RegionalConcreteOracleTest,RegionalValuesTest,EvidencePreservingPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test` |
| B-WIRE | CFG após C-DEP gerar outputs: `python3 -B scripts/project/test_dependency_wire.py`; W1 amplia com reader novo/negativos sem relaxar antigo |
| Q-SHARED | repos produtivos materiais: `python3 -B scripts/harness/lean.py qualification-local`; full local, registrar necessidade e limites |

Os selectors F/C acima protegem capacidades existentes; a wave **acrescenta seus
testes novos** ao comando focal após criá-los e registra o comando concreto.
Não alegar que essas classes atuais já provam FILE. `failIfNoSpecifiedTests=false`
permite módulos auxiliares do reactor; verificar que os testes nomeados rodaram,
com zero skips inesperados, e que nenhuma classe alvo faltou.

Loop focal lower (após bootstrap/compile; não executar InputSuite como main):

```bash
python3 -B - <<'PY'
from pathlib import Path
import sys
sys.path.insert(0, 'scripts/harness')
from focal import execute
import run
execute(Path.cwd(), run.build_root(), 'fast')
PY
```

Depois de editar fontes/testes, recompilar primeiro no cache pinado; não testar
classes antigas. Novo teste lower entra nas suítes Java main existentes; novo
teste codec no CodecSuite. Não inventar selectors JUnit nesses dois repos.

## Fronteiras B-SP / B-AIR / E-SELECTED

B-SP: expected SP manual + writer frontend + decoder/admission in-memory/file
lower; negativa de inventário ausente/variant/version inválidos; testar binding
e origem, não só roundtrip. W0 não precisa construir CFG.

B-AIR: A1–A4/A6 e O1–O5 em modelo manual; validator + codec compartilhado + consumer.
A5/captura D pertence à extensão W10; não é requisito para fechar core.
Testar owner/ref/capability inválidos e campo removido. Roundtrip sozinho não
prova semântica. IR somente muda após D-AIR, sem falso green por ignorar extensão.

E-SELECTED começa em W1; usar `scripts/project/prepare_w2d_producers.py` (existente):

```bash
python3 -B scripts/project/prepare_w2d_producers.py \
  --work .harness-results/fd-w1/producers \
  --lock docs/sources/sources.lock.json
```

`--work` deve ser novo; o script recusa diretório existente e verifica pins/dirty
state. O lock só é atualizado depois da qualificação bilateral de cada produtor.
Compilar CFG no mesmo conjunto AIR pinado antes do consumo. O `producers.json`
gerado contém main/classpath frontend e lower, sem necessidade de inventar JAR.
Composição CLI verificada nos drivers existentes (`e2e_w2d.py`, `e2e_partial.py`):

```text
ExplorerMain --source <fixture.cbl> --copybooks <cpy-autorizados> --output <novo-dir>
CobolLower <novo-dir/cobol-semantic-product.json> <novo.air.json>
io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg <novo.air.json> <novo.cfg.json>
io.github.gustavo2358.analysis.launcher.AnalysisDependencies <novo.air.json> <novo.dep.json>
```

W1 implementa `scripts/project/e2e_file_dependencies.py --work <novo-dir> --producers <producers.json>` no padrão de driver existente (fixtures
em `analysis-adapters/src/test/resources/file-dependencies/`), com flags e comando
documentados quando existirem. Isso é teste de integração da pipeline vigente,
não outro harness. Executar CLIs reais, examinar exit code e produtos novos,
validar external file name/sourceKind/owner/records/sites/roles/supports/remainders/CALL;
SG1 rejeita mecanismo DD/environment afirmado ou bindingMechanism UNKNOWN.
Repetir duas vezes para T52; induzir falha com output antigo para T53.

**Não rodar cegamente scripts históricos:** `e2e_w1d.py` exige SP 1.3;
`e2e_partial.py` enumera versões só até 2.8; storage composition exige 2.8/1.1.
Nenhum deles é qualificação FD pronta. Adaptar o oracle pertinente à evolução
contratual comprovada, preservando assertions; não só trocar o número até passar.
`artefatos-e2e/run-e2e.sh` pertence ao CP3 e sobrescreve outputs antigos: não usar.

## E-FINAL / W11

DoD core: N+C completos no escopo publicado; D permanece PLANNED/NOT_RUN para
extensão posterior, sem bloquear W11. N04/N18 são D apesar dos IDs históricos.
Não executar casos D como requisito core nem alegar suporte D. T37 cobre values
no core; T31–T35/A5 e APIs/Report Writer qualificam-se em W10 após autorização.

Coortes: fixtures autorais com expected manual → pequena amostra real da matriz
→ corpus CardDemo em `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e` (origem em
`docs/evals/cp6/carddemo-full-pins.json`). Preparar pins FD próprios; não executar
antigos pins de analisadores como se atuais. Corpus confidencial não presumido.
Reusar runner/metrics de `scripts/project/carddemo_baseline.py` após adaptação
versionada do consumer na wave; não alterar produtos brutos históricos.

Publicar população tentada, blockers por etapa, known+unknown por dimensão,
delta dos vetores CALL, precision/recall somente onde haja verdade de referência.
Contagem/crash-free não bastam. Selecionar SG1–SG5, CALL-X1–X6, MR1–MR9 e mutantes
por risco. Medir tempo/memória por etapa com muitos arquivos/usos/aliases/
candidatos/unidades e SORT participantes; não prometer SLA nem teto semântico.

## Evidência, falhas e atualização

Registrar commit/repo/pin, comando real, exit, resultado e propriedade; preservar
logs e outputs brutos. **PASS/FAIL** executados, **NOT_RUN** com motivo, **REUSED**
com prova de entradas materiais iguais, **BLOCKED** com causa. H3 define estratégia,
não executa FILE. Não replicar logs nos AGENTS ou exigir receipts para retomar.

READ deve invalidar somente evidência afetada. Após falha: classificar RED esperado,
bug, oracle antigo ou ambiente; corrigir, repetir o focal e as fronteiras invalidadas.
Falha de recursos/infra não é unknown semântico (ADR-0014). Sem autorização de merge
em H ou W11; parar para revisão humana com limitações explícitas.

C-DEP em W1: o POM fixa failIfNoSpecifiedTests=true; `-am` com selector focal
falha antes do módulo alvo. Construir o reactor atual (`mvn -DskipTests install`,
sem alegar testes) e executar o selector em `-pl analysis-adapters` sem `-am`.
O FAST fixo continua executando todos os módulos e verifica métodos/skips.

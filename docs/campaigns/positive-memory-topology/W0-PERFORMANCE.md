# W0 / W0-R1 — current-behavior sensitivity and performance evidence

Campaign: POSITIVE_MEMORY_TOPOLOGY. Original W0 measurements below are historical and reused unchanged in W0-R1; the new R1 experiments are separated at the end. **CURRENT_BEHAVIOR; no proposed semantic change implemented.**

## Reproducible scope

- Product baseline: CFG `98fa57c3db2edf9f70bb7a99bb667dbf36d28104`; AIR Java `646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa`; AIR spec `3fff18e2c16663a3f599207457caa1946d2e0945`.
- Temurin Java 21.0.12, fixed `-Xms256m -Xmx1g`. Existing `RegionalCostProbe`, `RegionalExplosionFixturesTest.fixture` and real `RegionalValuesAnalysis` with EXPERIMENTAL_PHYSICAL. Two warmups and three solves per process; metrics describe the last solve, not the sum of three.
- Synthetic N independent-intended 8-byte regions, one whole view each, P sequential literal writes to region 0, no source copies/loops/entry conditions. All production classes unchanged. Paired publication differs only by one valid DisjointStorage premise; the existing fixture test asserts that relationship.
- The separated control is a **sensitivity proxy**, not a new implementation and not a semantically equivalent AIR publication under today's rules. It removes the cross-base targets but adds premise metadata to direct targets. Full outputs between *open and separated* are intentionally different; do not compare them as byte-equivalent.
- Six original probe executions plus six neutral diagnostic repeats. Diagnostics copy only the existing test probe to an ignored directory, rename the main class and add two public-metric print statements. No production class overlay, filtering, mock solver, semantic switch or event structure change. For each configuration the original and diagnostic runs have byte-identical complete `facts.typed`, `targets.typed`, and `metrics.txt`. Thus instrumentation neutrality and repeat determinism are checked directly.
- Raw evidence: anchor `.harness-results/w0/`; local summary `.positive-memory-topology/evidence/measurement-summary.json`. Builds, caches and raw snapshots are deliberately not versioned. The stable source, commands, aggregate metrics and output hashes below are versioned.

## Preparation and solve

Pairs are current open → explicit-separation control. Events are measured with `Execution.preparationMetrics`, not inferred from elapsed time.

| Metric | 4 regions / 5 writes | 16 / 50 | 32 / 100 |
| --- | ---: | ---: | ---: |
| Prepared targets | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Prepared Events / physical plans | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Other-base comparisons | 15 → 15 | 750 → 750 | 3,100 → 3,100 |
| Correlation groups | 4 → 4 | 16 → 16 | 32 → 32 |
| Max bases/group | 1 → 1 | 1 → 1 | 1 → 1 |
| Premise memberships | 0 → 4 | 0 → 16 | 0 → 32 |
| Solve physical target applications | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Live encoded edges | 7 → 1 | 31 → 1 | 63 → 1 |
| Live expanded-equivalent edges | 19 → 1 | 766 → 1 | 3,132 → 1 |
| Live compact event rows | 15 → 0 | 750 → 0 | 3,100 → 0 |
| Historical interned compact event rows | 60 → 0 | 19,875 → 0 | 159,650 → 0 |
| Interned nodes | 39 → 6 | 1,566 → 51 | 6,332 → 101 |
| Interned encoded edges | 54 → 6 | 2,316 → 51 | 9,432 → 101 |
| Interned expanded-equivalent edges | 84 → 6 | 20,691 → 51 | 162,882 → 101 |
| Union requests/pairs | 15 → 0 | 750 → 0 | 3,100 → 0 |
| Projected selections | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Concrete fallback calls | 5 → 5 | 50 → 50 | 100 → 100 |
| Expanded labels | 5 → 5 | 50 → 50 | 100 → 100 |

UNPROVEN-only target counts: **15→0, 750→0, 3,100→0**. Their event attribution is exact in this fixture: one physical prepared event per target; no other explicit writes, entry facts, logical plans or outcomes. These targets have only `UNPROVEN_BASE_SEPARATION`, MAY and sourceApplicable=false. Remaining direct events: 5, 50, 100. Direct target strength is unchanged.

Logical plans = 0; object pairs materialized = 0; partition segments = N. Separation does not reduce the preparation **base comparison** loop: it still visits other bases. Therefore the experiment measures downstream avoidance and does not measure the additional preparation gain of actually removing that loop under a new contract.

The compact cross-base unknown rows correspond to P producer-distinct unknown labels per other base: `(N−1)×P`. Unspecified entry content remains, excluded from writer rows. Current live encoded edges are `1+2(N−1)` for these nonempty producer sets; expanded equivalents `1+(N−1)(P+1)`. The separated state materializes one overwritten group; other groups still exist with implicit unknown entry content. No world-count or reachability reduction is claimed from the materialized-edge count.

`internedProvenanceRows` is the sum across interned labels/history, **not unique events or heap bytes**. For this fixture it matches `(N−1)×(P(P+1)/2+P)`: 60 / 19,875 / 159,650. This directly exposes retained prefix-set growth; it is not a claim that arbitrary programs follow the same formula.

## Observation/replay amplification

The diagnostic records cumulative metrics after the existing probe asks one BEFORE-Return storage observation per region, each in a separate batch. These numbers are **solve + replay + projection**, distinct from solve-only above. A production consumer with different batching/demand need not incur these exact counts.

| Metric after N separate observations | 4 / 5 | 16 / 50 | 32 / 100 |
| --- | ---: | ---: | ---: |
| Physical target applications | 100 → 25 | 13,600 → 850 | 105,600 → 3,300 |
| Content reads | 104 → 29 | 13,616 → 866 | 105,632 → 3,332 |
| Union requests/pairs | 75 → 0 | 12,750 → 0 | 102,300 → 0 |
| Projected selections | 119 → 29 | 14,366 → 866 | 108,732 → 3,332 |
| Concrete fallback calls | 25 → 25 | 850 → 850 | 3,300 → 3,300 |
| Expanded labels | 44 → 29 | 1,616 → 866 | 6,432 → 3,332 |

The post-observation 32/100 case makes 105,600→3,300 target applications and 108,732→3,332 projected selections. The fallback count remains 3,300; avoiding invented interference does not eliminate direct overwrite/observation work. These are structural counts, not timings or byte allocation measures.

## Remaining expensive controls and limits

The existing FAST also executed `RegionalCompositionTest`, `RegionalAlternativesTest`, `RegionalConcreteOracleTest`, `RegionalStructuralOracleTest` and `RegionalFallbackStressTest` as selected by the fixed profile. Partial-copy stress proves fallback use and complete replay/capture preservation. It has unproved fan-out too, so its totals are **not** a clean estimate of legitimate post-migration cost. No stress result is relabeled as a positive-topology execution.

Real branch joins and explicit AllMemory/MAY can still supply P legitimate events. Exact Events unions can still retain quadratic prefix history; multi-child union can still take a×b overlap comparisons; source selection and output can remain large. The measured separated witnesses have one live overwritten value and zero compact unknown rows, but **typical corpus event distributions are not measured**. Defer persistent Events until post-topology profiling demonstrates residual material cost.

Elapsed wall/thread-CPU observations are retained in raw logs, but there was concurrent local FAST/build activity and only small synthetic runs. No speedup percentage, memory/SLA, corporate gain or global complexity claim is made. No RSS/retained heap/JFR was collected in W0. Existing corporate closeout was non-convergent and lacked exact config/counters; its unresolved status is preserved.

## Commands

Run from the permanent campaign worktree. Prepare exact dependencies using the repository harness; set JAVA_HOME/PATH to Java 21. W0 bootstrapped an isolated local clone of AIR Java at the lock SHA and copied a reusable Maven cache, then ran the ordinary mandatory FAST (which validates source pin and compiles it). No snapshot dependency was substituted.

```sh
python3 -B scripts/harness/lean.py fast
python3 scripts/project/regional_cost_probe.py --regions 4 --producers 5 --warmups 2 --repeats 3 --output .harness-results/w0/n4p5-open
python3 scripts/project/regional_cost_probe.py --regions 4 --producers 5 --warmups 2 --repeats 3 --disjoint --output .harness-results/w0/n4p5-separated
python3 scripts/project/regional_cost_probe.py --regions 16 --producers 50 --warmups 2 --repeats 3 --output .harness-results/w0/n16p50-open
python3 scripts/project/regional_cost_probe.py --regions 16 --producers 50 --warmups 2 --repeats 3 --disjoint --output .harness-results/w0/n16p50-separated
python3 scripts/project/regional_cost_probe.py --regions 32 --producers 100 --warmups 2 --repeats 3 --output .harness-results/w0/n32p100-open
python3 scripts/project/regional_cost_probe.py --regions 32 --producers 100 --warmups 2 --repeats 3 --disjoint --output .harness-results/w0/n32p100-separated
```

For preparation and post-observation counters, reproduce the neutral test-main copy with this Python snippet (uses the same dependency classpath patterns as the existing runner). It fails on changed probe anchors and asserts neutrality of every repeated product:

```python
from pathlib import Path
import os, subprocess
root = Path.cwd()
out = root / '.harness-results/w0/diagnostic'
out.mkdir(parents=True, exist_ok=True)
source = root / 'analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalCostProbe.java'
text = source.read_text().replace('RegionalCostProbe', 'W0TopologyDiagnostic')
for old, new in [
    ('printCounters();', 'printCounters();\n            System.out.println("W0_PREPARATION "+new TreeMap<>(execution.preparationMetrics()));'),
    ('System.out.println("W3_TARGETS_SHA256 "+RegionalSemanticSnapshot.digest(targets));',
     'System.out.println("W3_TARGETS_SHA256 "+RegionalSemanticSnapshot.digest(targets));\n            System.out.println("W0_POST_OBSERVATION "+new TreeMap<>(execution.metrics()));')]:
    assert text.count(old) == 1
    text = text.replace(old, new)
probe = out / 'W0TopologyDiagnostic.java'
probe.write_text(text)
m2 = root / '.harness-results/build/m2'
patterns = ('io/github/gustavo2358/air-java/*/*.jar',
            'org/junit/jupiter/junit-jupiter-api/*/*.jar',
            'org/opentest4j/opentest4j/*/*.jar',
            'org/apiguardian/apiguardian-api/*/*.jar',
            'org/junit/platform/junit-platform-commons/*/*.jar')
cp = os.pathsep.join([str(root / module / 'target' / kind)
                     for module in ('analysis-values', 'analysis-kernel', 'cfg-kernel')
                     for kind in ('test-classes', 'classes')]
                    + [str(jar) for pattern in patterns for jar in m2.glob(pattern)])
subprocess.run(['javac', '--release', '21', '-cp', cp, '-d', str(out), str(probe)], check=True)
for n, p in ((4, 5), (16, 50), (32, 100)):
    for separated in (False, True):
        name = f'n{n}p{p}-' + ('separated' if separated else 'open')
        dest = out / name
        cmd = ['java', '-Xms256m', '-Xmx1g', '-cp', str(out) + os.pathsep + cp,
               'io.github.gustavo2358.analysis.values.W0TopologyDiagnostic',
               str(n), str(p), '2', '3', str(dest)]
        if separated:
            cmd.append('disjoint')
        result = subprocess.run(cmd, check=True, text=True, capture_output=True)
        (dest / 'run.log').write_text(result.stdout)
        for filename in ('facts.typed', 'targets.typed', 'metrics.txt'):
            assert (dest / filename).read_bytes() == (root / '.harness-results/w0' / name / filename).read_bytes()
```

## Stable output hashes

SHA-256 of actual typed snapshot file bytes; original and neutral diagnostic repeat match. These are different from a formatter's digest of an in-memory list if that formatter wraps the list differently.

| Case | facts.typed SHA-256 | targets.typed SHA-256 |
| --- | --- | --- |
| n4p5-open | `3bf6ed5144cc53452781cca4809aee08a6eea32727697a092159400c9c5fcbbe` | `85513f581d39e62f9dd3aead82afede6031d138c59f4f24c0fc15db8468ddab2` |
| n4p5-separated | `22d1216224174d58fb77291cf18e21ae45874c43032917c6bdc0153512072583` | `ded88aacc3a2d2559b761987f6cfe09301e50218fad1f63a6627cd14ac1417cd` |
| n16p50-open | `e9b3792a76ffbd96d302075402c341701adfb9cbcb26c634af88cf8ea0656302` | `f6a26180cba8e0090c652faa8e3619889a1e3bcdbd64775c0b5b262151c538b5` |
| n16p50-separated | `46362c3a17a37828d0049df03b9ddcd2a1cabb8f66069f46f994e7dea54c1efe` | `13c686f3519373249b72d19e8b84061b71779f5efed2628d30e4aab22bd64ac4` |
| n32p100-open | `9e8437da4f81de7deb3b717f2fe78fe0c5244943c09b7e18e594f44c491ca496` | `027c14c2ea4b40906741282c18ecc736a057078ec6b1b882f7d8434b5d613983` |
| n32p100-separated | `a8b83a27df02c7a540607931835839f3185e8e20c204eb5afd0c48c773f28c6d` | `8acf2cf46de578a5dee8cd7cb5fefe8219b521ac420fae72e6d9177d3af43d87` |

## Validation ledger

| Check | W0 status / scope |
| --- | --- |
| Local `lean.py fast` | **PASS CODE_CHANGE**, 96.773 s; 598 required unit/contract methods, zero skips; architecture/wire/harness boundaries passed |
| Six baseline sensitivity runs | **PASS**, real admitted AIR/CFG/session/solver, no semantic mutation |
| Six isolated diagnostic repeats | **PASS**, exact facts/targets/solve-metrics equality per case |
| 42 immutable source references | **PASS**, inspected file bytes equal linked Git blobs |
| Main report Q1–Q12 / T1–T10 | Discovery and oracle design, **not future semantics PASS** |
| Full source E2E / five-repo full / corporate run | **NOT RUN**; no product/pin change; synthetic causal boundary selected; limits explicit |
| Parent source/logical/file/dependency qualification | **REUSED context**, parent exact SHAs/CI, not W0 executions |

No baseline output was edited to obtain PASS. No thresholds, hidden caps, arbitrary precision loss or new solver were used. The exact permanent campaign PR/commit and remote checks are recorded in GitHub and the final handoff.


## W0-R1 — novas caracterizações, sem implementação

**CURRENT_BEHAVIOR / OBSERVED**, 17 fontes pequenas criadas nesta complementação, quatro CLIs reais, Java 21, timeout 120 s/JVM, `-Xmx2g`, sem `--storage-profile` físico ou flag de experimento semântico. Todas as 68 etapas concluíram com exit 0. Uma execução por fonte; latências são apenas custos observados de processo, não benchmark estatístico. Nenhuma fonte/corpus corporativa executada ou publicada.

O runtime de frontend/lower/AIR está nas baselines de discovery §2. Classpath teve hashes conferidos contra o build anterior, sem discrepâncias. O build CFG a6d703ac66341ccd046f3f2e33e4c3bc13388d54 tem fontes produtivas/POMs idênticos a 98fa57c3db2edf9f70bb7a99bb667dbf36d28104. Não se usou uma classe sobreposta de produção. O arquivo local `runtime.json` captura os comandos e os hashes; o teste FAST R1 também recompilou/verificou a baseline do worktree da campanha.

### Resultado por percurso

V/O/S = vistas lógicas no SP / objetos AIR / storages AIR. Targets = contador de **preparação**; coluna E/P contém Events preparados no provider regional ou producers preparados no escalar, conceitos distintos. Nenhum desses números mede bytes de memória nem posições históricas de Events. Nodes/ops = transferências durante solve.

| Caso | V/O/S | Candidatos por site | Model remainder | Estado do site | Targets / E ou P | Solve nodes / ops |
| --- | --- | --- | --- | --- | --- | --- |
| base | 2 / 2 / 2 | OLDPGM | false | COMPLETE | 0 / 1 | 5 / 4 |
| layout-independent | 0 / 3 / 0 | OLDPGM | true | PARTIAL | 0 / 1 | 5 / 4 |
| layout-family | 0 / 4 / 0 | OLDPGM | true | PARTIAL | 0 / 1 | 5 / 4 |
| if-equality | 2 / 2 / 2 | NEWPGM, OLDPGM | false | COMPLETE | 0 / 2 | 7 / 7 |
| if-class | 2 / 2 / 2 | NEWPGM, OLDPGM | true | COMPLETE | 0 / 2 | 12 / 12 |
| evaluate-no-other | 2 / 2 / 2 | NEWPGM, OLDPGM | false | COMPLETE | 0 / 2 | 7 / 7 |
| evaluate-condition | 2 / 2 / 2 | NEWPGM, OLDPGM | true | COMPLETE | 0 / 2 | 12 / 12 |
| perform-until | 2 / 2 / 2 | NEWPGM, OLDPGM | true | COMPLETE | 0 / 2 | 10 / 12 |
| perform-times | 2 / 2 / 2 | NEWPGM | false | COMPLETE | 0 / 2 | 9 / 10 |
| goto | 2 / 2 / 2 | OLDPGM | false | COMPLETE | 0 / 1 | 6 / 5 |
| alter | 2 / 2 / 2 | OLDPGM | true | COMPLETE | 0 / 2 | 14 / 13 |
| display | 2 / 2 / 2 | OLDPGM | false | COMPLETE | 0 / 1 | 6 / 5 |
| compute | 2 / 3 / 2 | OLDPGM | true | PARTIAL | 17 / 1 | 7 / 7 |
| accept | 2 / 2 / 2 | OLDPGM | true | COMPLETE | 0 / 1 | 8 / 8 |
| call-before | 2 / 2 / 2 | EXTERNAL / OLDPGM | false/true | COMPLETE/COMPLETE | 0 / 1 | 6 / 5 |
| layout-scale-10 | 0 / 12 / 0 | OLDPGM | true | PARTIAL | 0 / 1 | 5 / 4 |
| layout-scale-50 | 0 / 52 / 0 | OLDPGM | true | PARTIAL | 0 / 1 | 5 / 4 |

Todos os casos têm `sourceValueRemainder=true`, `interpretationUnknownRemainder=true` e `openControlRemainder=true` na saída corrente. O `analysisStatus=COMPLETE` significa que o cálculo solicitado terminou; não significa fonte completa nem modelValueRemainder=false. Nos casos layout/compute, `analysisReasons=[PHYSICAL_PROPAGATION_DISABLED]`; não foi reaberto o modo físico para escondê-lo. Todos preservam ao menos o candidato sustentado, de modo que a observação **não** demonstra perda universal de nomes. Demonstra perda de estrutura/projeção, mudança de provider e abertura/estado PARTIAL, mesmo com nome recuperado.

- **Nasce no producer:** SYNC preservado remove todas as vistas lógicas desses exemplos; UnknownBinding/global bounds continuam explícitos na AIR. IF class/EVALUATE TRUE/UNTIL parcial viram Opaque; assinatura CALL desconhecida publica writes/read ALL. A matriz M01–M15 aponta o local de cada causa.
- **Ampliação/consumo:** `compute` prepara 17 targets com apenas 2 bases materializadas e 1 Event lógico, zero planos/escritas físicas. Atribuição por motivo de cada target não foi instrumentada nesse probe; não chamar os 17 de Events nem atribuí-los todos a uma única causa. A leitura de C2 mostra amplificações independentes de resolver/scopes.
- **Trabalho legítimo:** IF equality e EVALUATE literal sem OTHER mantêm OLD/NEW e model fechado; PERFORM TIMES possui Branch de repetição e candidato NEW. Esse trabalho estrutural não será eliminado porque há diagnóstico.
- **Recusa sem fan-out:** layout com 1/10/50 SYNC tem 0 targets porque as bases nem foram materializadas. Contar zero como sucesso seria errado: todas as variantes perdem vistas, acionam provider mais amplo e ficam PARTIAL/model aberto.
- **Escala pequena:** base sem SYNC tem 2 vistas/2 storage; com 1/10/50 declarações SYNC fica 0/0, objetos 3/12/52, ocorrências de `AllMemory` no JSON 5/14/54. O solve regional fica em 5 nodes/4 ops e 1 Event lógico nos três casos. Isso mede publicação/recusa atual, não garante custo constante nem conformidade futura.

Replay separado: todos os 17 casos publicaram `observation.sequencesReplayed=1`, `observation.operationsReplayed=0`; o ponto BEFORE é atendido no limite de sequência. Esses contadores não incluem solve. Tempos finos de preparação, solve e replay não foram medidos separadamente; os respectivos **contadores** foram preservados no JSON bruto. Latência de dependency CLI inclui todas essas fases, bootstrap JVM e serialização.

### Latência observada de processo (segundos, uma execução)

| Caso | Frontend | Lower | CFG | Dependencies total |
| --- | ---: | ---: | ---: | ---: |
| base | 1.116 | 0.715 | 0.264 | 0.365 |
| layout-independent | 1.116 | 0.665 | 0.264 | 0.365 |
| layout-family | 1.116 | 0.715 | 0.264 | 0.415 |
| if-equality | 1.116 | 0.715 | 0.264 | 0.364 |
| if-class | 1.220 | 0.715 | 0.264 | 0.364 |
| evaluate-no-other | 1.116 | 0.665 | 0.264 | 0.364 |
| evaluate-condition | 1.216 | 0.665 | 0.264 | 0.364 |
| perform-until | 1.166 | 0.715 | 0.264 | 0.364 |
| perform-times | 1.116 | 0.715 | 0.264 | 0.364 |
| goto | 1.116 | 0.715 | 0.264 | 0.364 |
| alter | 1.166 | 0.665 | 0.264 | 0.365 |
| display | 1.116 | 0.665 | 0.264 | 0.365 |
| compute | 1.116 | 0.715 | 0.264 | 0.415 |
| accept | 1.116 | 0.715 | 0.265 | 0.365 |
| call-before | 1.066 | 0.665 | 0.264 | 0.365 |
| layout-scale-10 | 1.116 | 0.715 | 0.314 | 0.415 |
| layout-scale-50 | 1.166 | 0.865 | 0.365 | 0.515 |

### Metadados isolados e controle positivo de Unknown

Driver novo em pacote de teste usa `ValuesFixtures.graph`, sem alterar produção. Mantém literalmente mesmos objetos, bases, operações e consulta BEFORE. Primeiro adiciona somente registros Uncertainty não referenciados; depois varia Coverage/Uncertainty de publicação (0/1/50). Compara candidatos, model remainder, alcance, base, query, premissas, evidence, provenance e candidateSupports; compara mapas completos de preparação, solve e replay, não apenas duração.

| Gaps | Candidato | Model | Source / effective atuais | Producers | Ops solve / replay | Projeção semântica e trabalho |
| --- | --- | --- | --- | ---: | --- | --- |
| 0 | PROGA | fechado | false / false | 1 | 2 / 1 | referência |
| 1 | PROGA | fechado | true / true | 1 | 2 / 1 | iguais à referência |
| 50 | PROGA | fechado | true / true | 1 | 2 / 1 | iguais à referência |

No modo REGISTRY_ONLY, os três tamanhos também mantêm `source=false/effective=false`: são registros diagnósticos sem consumidor semântico nesse percurso. A tabela acima é do modo COVERAGE_PARTIAL. A versão inicial do probe e seus logs foram preservados antes de acrescentar esse segundo controle.

**Conclusão limitada:** o caminho escalar já separa parte de source/model e não cria producers nesse exemplo; a agregação `effective` ainda muda por cobertura. Não foram medidos metadados de objeto/operation precision nem ByteImage regional. A leitura de C3/C14 mostra que estes entram em capture/igualdade; o oráculo de isolamento completo O9 continua NOT_IMPLEMENTED.

O mesmo driver executou o teste existente de HavocMust/HavocMay: MUST elimina A e deixa unknown; MAY retém A+unknown. PASS do comportamento semântico legítimo, não da nova regra de gaps. Houve uma tentativa de compilação do driver sem AIR no classpath (falha de setup); logs mantidos. Com as classes AIR fixadas, compilação e execução passaram. Nenhum resultado de fonte foi reescrito.

### Reprodução da caracterização R1

Instrumentos neutros locais: `.positive-memory-topology/evidence/w0-r1/characterize.py`, `GapMetadataProbe.java`, `runtime.json`, `probe-commands-fixed.json`, `observations.json`, logs e subpastas. Os scripts abaixo ficam aqui para revisão/reprodução sem versionar runtime/cache/outputs. O driver Python espera o runtime local da baseline já qualificado e verifica hashes; em outro checkout, preparar o runtime nesses SHAs e adaptar **somente caminhos/classpath**, sem mudar defaults semânticos. Não executar o script histórico de corpus ao importar/configurar comandos.

```python
"""Neutral CURRENT_BEHAVIOR source probes; no production overlays or semantic flags."""
import pathlib,json,subprocess,hashlib,time,collections
R=pathlib.Path('/home/gustavo/workspace/teste-e2e'); E=pathlib.Path(__file__).resolve().parent
J='/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem/bin/java'
old=R/'artefatos-e2e/dependency-preservation-20260919/baseline'
commands={}
for stage in ['frontend','lower','cfg','dependency']:
    c=json.loads((old/f'{stage}.command.json').read_text()); i=c.index('-cp')
    commands[stage]=[J,'-Xmx2g','-cp',c[i+1].replace('.source-dependencies-w3','.dependency-preservation'),c[i+2]]
manifest=json.loads((R/'artefatos-e2e/dependency-preservation-closeout-20260919/e2e/build-manifest.json').read_text())
for name,expected in manifest['classpathSha256'].items():
    p=pathlib.Path(name);h=hashlib.sha256()
    if p.is_dir():
        for f in sorted(x for x in p.rglob('*') if x.is_file()):
            h.update(str(f.relative_to(p)).encode()+b'\0');h.update(hashlib.sha256(f.read_bytes()).digest())
    else:h.update(p.read_bytes())
    assert h.hexdigest()==expected,name
(E/'runtime.json').write_text(json.dumps({'commands':commands,'classpathSha256':manifest['classpathSha256'],'baselinePins':{'frontend':'edb64520a6269be9fa6d71cd47e6974112fbfece','lower':'f8e181f95929c650181c989318f8ba23d1e68a1a','cfg':'98fa57c3db2edf9f70bb7a99bb667dbf36d28104','air':'646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa'},'cfgBuildEquivalence':'a6d703ac66341ccd046f3f2e33e4c3bc13388d54 has identical production sources/poms to CFG baseline'},indent=2)+'\n')
data='01 PGM PIC X(8).\n01 FLAG PIC X.'
pre='MOVE "OLDPGM" TO PGM\n'
post='CALL PGM\nGOBACK.'
cases={
 'base':(data,pre+post),
 'layout-independent':(data+'\n01 ODD PIC X SYNC.',pre+post),
 'layout-family':('01 FAMILY.\n 05 PGM PIC X(8).\n 05 ODD PIC X SYNC.\n01 FLAG PIC X.',pre+post),
 'if-equality':(data,pre+'IF FLAG = "Y"\n MOVE "NEWPGM" TO PGM\nEND-IF\n'+post),
 'if-class':(data,pre+'IF FLAG IS NUMERIC\n MOVE "NEWPGM" TO PGM\nEND-IF\n'+post),
 'evaluate-no-other':(data,pre+'EVALUATE FLAG\n WHEN "Y" MOVE "NEWPGM" TO PGM\nEND-EVALUATE\n'+post),
 'evaluate-condition':(data,pre+'EVALUATE TRUE\n WHEN FLAG IS NUMERIC MOVE "NEWPGM" TO PGM\nEND-EVALUATE\n'+post),
 'perform-until':(data,pre+'PERFORM BODY UNTIL FLAG IS NUMERIC\n'+post+'\nBODY.\n MOVE "NEWPGM" TO PGM.'),
 'perform-times':(data,pre+'PERFORM BODY 2 TIMES\n'+post+'\nBODY.\n MOVE "NEWPGM" TO PGM.'),
 'goto':(data,'GO TO DEST.\nOTHER-P.\n MOVE "BADPGM" TO PGM\n GOBACK.\nDEST.\n'+pre+post),
 'alter':(data,'ALTER START-P TO PROCEED TO OTHER-P.\nSTART-P.\n GO TO DEST.\nOTHER-P.\n MOVE "BADPGM" TO PGM\n GOBACK.\nDEST.\n'+pre+post),
 'display':(data,pre+'DISPLAY PGM\n'+post),
 'compute':(data+'\n01 N PIC 9.',pre+'COMPUTE N = 1 + 2\n'+post),
 'accept':(data,pre+'ACCEPT PGM\n'+post),
 'call-before':(data,pre+'CALL "EXTERNAL"\n'+post),
}
for n in [10,50]:cases[f'layout-scale-{n}']=(data+'\n'+'\n'.join(f'01 ODD-{i} PIC X SYNC.' for i in range(n)),pre+post)
def walk(x):
    if isinstance(x,dict):
        yield x
        for v in x.values():yield from walk(v)
    elif isinstance(x,list):
        for v in x:yield from walk(v)
rows=[]
for name,(declarations,body) in cases.items():
    p=E/name;p.mkdir(exist_ok=False)
    src='IDENTIFICATION DIVISION.\nPROGRAM-ID. PROBE.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n'+declarations+'\nPROCEDURE DIVISION.\n'+body+'\n'
    (p/'input.cbl').write_text(''.join('       '+line+'\n' for line in src.splitlines()))
    row={'case':name,'status':'OBSERVED','phases':{}}
    for stage,args in [('frontend',['--source',p/'input.cbl','--copybooks',p,'--output',p/'sp']),('lower',[p/'sp/cobol-semantic-product.json',p/'air.json']),('cfg',[p/'air.json',p/'cfg.json']),('dependency',[p/'air.json',p/'dependencies.json'])]:
        cmd=commands[stage]+list(map(str,args));(p/f'{stage}.command.json').write_text(json.dumps(cmd,indent=2))
        start=time.monotonic()
        with (p/f'{stage}.log').open('w') as log:
            try:code=subprocess.run(cmd,cwd=R/'.dependency-preservation'/('proleap-poc' if stage=='frontend' else 'cobol-lower' if stage=='lower' else 'analysis-cfg'),stdout=log,stderr=subprocess.STDOUT,timeout=120).returncode
            except subprocess.TimeoutExpired:code='TIMEOUT'
        row['phases'][stage]={'exitCode':code,'seconds':round(time.monotonic()-start,6)}
        if code:row['status']='PIPELINE_FAILURE';break
    if (p/'sp/cobol-semantic-product.json').exists():
        sp=json.loads((p/'sp/cobol-semantic-product.json').read_text());row['logicalViews']=len(sp.get('storage',{}).get('logicalTextViews',[]))
        row['spGapCodes']=dict(collections.Counter(code for x in walk(sp) for code in x.get('gapCodes',[])))
    if (p/'air.json').exists():
        air=json.loads((p/'air.json').read_text())['publication'];row['airKinds']=dict(collections.Counter(x['kind'] for x in walk(air) if 'kind' in x));row['airGapCodes']=dict(collections.Counter(x['code'] for x in air.get('uncertainties',[])))
        row['objects']=sum(len(u['objects']) for u in air['units']);row['storage']=len(air['storage'])
    if (p/'dependencies.json').exists():
        dep=json.loads((p/'dependencies.json').read_text());row['sites']=[{k:s.get(k) for k in ['command','targetKind','targetStatus','analysisStatus','analysisReasons','modelValueRemainder','sourceUnknownRemainder','interpretationUnknownRemainder','effectiveUnknownRemainder','controlUnknown','candidates']} for s in dep['sites']];row['metrics']=dep['metrics']
    row['sha256']={str(f.relative_to(p)):hashlib.sha256(f.read_bytes()).hexdigest() for f in p.rglob('*') if f.is_file()}
    (p/'observation.json').write_text(json.dumps(row,indent=2)+'\n');rows.append(row)
    print(name,row['status'],row.get('logicalViews'),[[c['referenceName'] for c in s['candidates']] for s in row.get('sites',[])],flush=True)
(E/'observations.json').write_text(json.dumps(rows,indent=2)+'\n')
```

```java
package io.github.gustavo2358.analysis.values;
import java.util.*;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
/** CURRENT_BEHAVIOR only. New driver, no overlaid production class. */
public final class GapMetadataProbe {
    public static void main(String[] args) {
        var original=graph(new String[]{"PROGA"},new int[][]{{}},1,false,false);
        Object projection=null, preparation=null, solve=null, replay=null;
        for(String mode:List.of("REGISTRY_ONLY","COVERAGE_PARTIAL")) for(int n:List.of(0,1,50)) {
            var gaps=new ArrayList<Evidence.Uncertainty>();
            for(int i=0;i<n;i++)gaps.add(new Evidence.Uncertainty(new UncertaintyId(original.id(),"diagnostic-"+i),"MODELING_GAP_PROBE",List.of(Evidence.Dimension.VALUES),new Scopes.PublicationScope(original.id()),"Diagnostic fixture only",origin(original.id())));
            var coverage=new Evidence.Coverage(n==0?Evidence.InventoryStatus.COMPLETE:Evidence.InventoryStatus.PARTIAL,new Scopes.PublicationScope(original.id()),List.of(),gaps.stream().map(Evidence.Uncertainty::id).toList());
            var p=replace(original,original.units(),mode.equals("REGISTRY_ONLY")?original.coverage():coverage,gaps,original.premises());
            var q=ValuesTest.before(p,0,0);var run=execute(p);var prep=run.preparationMetrics();var obs=run.observe(List.of(q));var f=obs.batch().observations().getFirst().value();
            var semantic=List.of(q,f.cell(),f.reachability(),f.candidates(),f.modelValueRemainder(),f.premises(),f.evidence(),f.provenance(),f.candidateSupports());
            if(n==0){projection=semantic;preparation=prep;solve=run.solveMetrics();replay=obs.stateMetrics();}
            else if(!projection.equals(semantic)||!preparation.equals(prep)||!solve.equals(run.solveMetrics())||!replay.equals(obs.stateMetrics()))throw new AssertionError("semantic/work projection differs");
            System.out.println("mode="+mode+" gaps="+n+" candidates="+f.candidates()+" model="+f.modelValueRemainder()+" source="+f.sourceUnknownRemainder()+" effective="+f.effectiveUnknownRemainder()+" prepare="+prep.get("producersPrepared")+" operations="+run.dataflow().metrics().operationsTransferred()+" replay="+obs.batch().metrics().operationsReplayed()+" semanticAndWorkProjectionEqual=true");
        }
        new ValuesTest().directReadIsCopyWhileOtherEffectsAndIndirectStorageRemainUnsupported();
        System.out.println("Existing HavocMust versus HavocMay positive control PASS (MUST kills A; MAY retains A plus unknown)");
    }
}
```

Compilar o driver em diretório isolado usando `javac -cp <classes-produto-e-teste-CFG>:<air-model>:<air-json>:<junit-api-e-dependências> -d <probe-classes> GapMetadataProbe.java`; executar seu main `io.github.gustavo2358.analysis.values.GapMetadataProbe` com o mesmo classpath mais `<probe-classes>`. As classes produtivas são as classes normais; o novo nome só é o driver. Os comandos exatos desta execução estão no registro local referido acima.

### Gates e limites de inferência

FAST obrigatório: `PASS CODE_CHANGE elapsed_seconds=95.738`, 598 métodos requeridos, zero skips. Higiene DOCS_ONLY e verificação de referências/blobs conferem o diff documental final; checks remotos no SHA publicado constam do PR45. Full/corpus não repetidos porque produção/pins/contratos não mudaram.

As medidas históricas 32/100 (3.200→100 targets; 159.650→0 posições históricas compactas) são **reutilizadas**, não R1. Os probes R1 caracterizam gaps atuais e não implementam a política, não estimam heap por contagem de Events e não provam desempenho corporativo. Persistent Events só será reavaliado após medir trabalho legítimo residual; combinações, cópias e saída podem continuar grandes.

Hashes de reprodução (SHA-256, sem fonte corporativa):

| Arquivo local | SHA-256 |
| --- | --- |
| `characterize.py` | `d354833d74e13b045de8f2afeef8ff4e39d81b150d38f1faf8e5464348073049` |
| `GapMetadataProbe.java` | `34063164e03ff20a58dc5d98fa74c4375f68653275d30fd8285e37ed99becb55` |
| `runtime.json` | `c97e3f5f02c966b95b03214ec46f245c69371fd7490931e96d1d30e81b8f1f82` |
| `observations.json` | `93c73b23039f76c953aba8e89419385adaabde2a374b090bfb3300d036b6a06f` |
| `probe.log` | `7bcc297032e5578c0f6bd565924ad8af896282f5df11430b5d46eedfd663827d` |

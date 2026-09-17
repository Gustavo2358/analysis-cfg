# W1 — contrato implementado e oráculos

Scope: OPEN/READ/CLOSE N-LR estáticos. Efeitos/controle permanecem abertos até
W3/W4; valores FILE computados aguardam W7 e catálogo CICS aguarda W8. W10 não
foi iniciado. Nenhuma dimensão de resolução externa existe no resultado.

## D-AIR

Prova de perda contra o modelo baf848ab, sem codec: Resource sem associação e
ArtifactRelation com source ArtifactId não conservam UnitId/record/use. Prova
executável e log em `artefatos-e2e/file-dependencies-20260916/w1/`.
Norma mínima `resource.bindings@1`, IR fb153ae5, Draft PR #7: ResourceDeclaration
neutra owner/name/classification/nameSource/objects/uses; LocalResource sem alvo
externo; UnknownResource com desconhecimento explícito. Invoke/Target existentes
reutilizados, sem ResourceId como Target nem primitiva COBOL na AIR.
AIR d215d2ba: modelo/validator/codec qualificados com A1–A4/A6 independentes.

## D-WIRE

Writer único `analysis-dependency-result@2.0.0`, com
`analysisBoundary=COBOL_SOURCE_ONLY` e seção separada `fileDependencies`.
Top-level sites/edges, profiles, métricas e status de análise conservam significado
CALL. Status FILE fica nas dimensões próprias; efeitos abertos não tornam um
nome literal desconhecido. Reader Python fecha chaves, enums, refs e projeções.
Históricos 1.1/1.2 conservam ramos estritos; cópia congelada do reader anterior é
fixture de rejeição, não writer alternativo nem implementação de produção.

FILE: declarations(id, owner, logicalFile, classification, sourceKind, targetKind,
namespace/name, objects por papel, origin); sites(owner, entry, sequence,
operation, action, namespace, targetKind, bindings por papel, valuePoint,
candidates/supports, unknownRemainder, reachability, effects/control, origens,
uncertaintyRefs, analysisReasons); edges são projeções de candidatos dos sites
não provadamente inalcançáveis. sourceKind ASSIGNMENT_NAME preserva o fato fonte.
LocalResource nunca ganha nome externo. Declaração sozinha nunca cria edge.
Inventário usa coverage AIR conservador; ausência da capability de associação
significa UNAVAILABLE, não inventário vazio conhecido.

## Algoritmo e fronteira

FileDependencyAnalysis indexa declarações/usos por OperationId e inventaria Invoke
com categoria file explícita. Nenhum parsing de nomes, localIds ou fonte.
FileDependencyConsumer apenas consulta fatos preparados: literal ExactName
preserva candidato e suporte do target; computed permanece explicitamente aberto
em W1. Não consulta values para literais. ReachabilityProvider e PlanningExecution
compartilham sessão/cache com CALL. O consumer não recebe capacidade de executar
solver. As ordens de saída usam IDs completos, sem fusão por grafia.

resource.bindings@1 não muda controle: projeção CFG e índice estrutural reconhecem
somente essa capability/version adicional após preflight AIR. Capabilities
desconhecidas continuam recusadas. Nenhuma lei do solver/lattice mudou.
Indexação O(resources + associações + sites), memória linear; ordenação de saída
O(n log n). Reachability reutiliza BFS existente O(V+E) por entry; sem cutoff.

## Oráculos e reprodução

ResourceBindingOracle é modelo manual copiado da fixture AIR d215d2ba, sem saída
de produtor. FileDependencyTest verifica A1/A2/A3/A4/A6, owner/record/use, ausência
de leitura espúria de FROM, alvo computed aberto, CALL independente e falha de
serialização preservando arquivo anterior. Reader testa mutantes de campos,
refs, suporte, nomes, domínio e projeção. CALL/CICS existentes permanecem gates.

`scripts/project/e2e_file_dependencies.py` usa prepare_w2d_producers existente.
Coorte fonte autoral em `analysis-adapters/src/test/resources/file-dependencies/w1`:
static, declaration-only, empty, call-only, mixed, multi-open. Cada uma roda CLIs
reais duas vezes, compara todos os produtos byte a byte e examina suporte até span
COBOL. Induz falha de output e exige exit 6, diagnóstico e limpeza temporária.

O selector C-DEP com `-am` não ultrapassa módulos sem testes nomeados porque o POM
fixa failIfNoSpecifiedTests=true. Reprodução sem relaxar a política: construir os
módulos atuais com `mvn -DskipTests install` (compilação, não PASS de teste), depois
`mvn -pl analysis-adapters -Dtest=FileDependencyTest,W1dDependencyTest,PartialDependencyTest,CicsInvokeRouteTest,CicsTargetTimingTest,W1dAdversarialTest test`.
FAST fixo continua obrigatório no reactor inteiro. Logs/red/falhas preservados;
checkpoint final e comandos exatos no handoff E2E, após gates concluídos.

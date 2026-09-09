# Checkpoint 4D — validação e handoff

## Baseline e autoridade

- analysis-cfg main limpa após fetch/pull --ff-only: `2b4df46d53ce5b21a5c315d3f691b183cb6bd124`.
- Branch: `chore/pin-air-json-scalar-assign`; work item [WORK-CFG-027](../../history/WORK-CFG-027.md), implementation explicitamente autorizada.
- [PR #10](baseline-pr10.json) mergeado reconciliado no histórico 026; evidência anterior preservada.
- AIR normativa 2.0.0: `122ce54e1b9ef9b00646f93ece409ca8b63bc933`; seção analysis_ir inteira do lock inalterada, bindingVersion 1.0.0/DRAFT mantidos.
- air-java runtime: `ce530a7e17ab12b23c48f29425f503ff920b09fb`, merge [PR #6](upstream-pr6.json); head aprovado `f3698a78b2fe8d989247bfdeaf9fd667e5db1368`, merged_at 2026-09-08T16:03:41Z. Main já no merge, nenhum avanço observado. CI do merge: [contracts](https://github.com/Gustavo2358/air-java/actions/runs/34248728661), [harness](https://github.com/Gustavo2358/air-java/actions/runs/34248728645), ambos success consultados nesta sessão.
- Pins ativos atualizados em lock, CI checkout/verificação, scope guard, toolchain, índice de fontes, upstream-state, ADR-0007 e referência de runtime no contrato CFG. SHA 1A restante no ADR-0009/PROVENANCE GOBACK é histórico; no challenge é contracaso. Nenhuma evidência antiga foi repinada.

A configuração SSH global inválida foi contornada por `git -c core.sshCommand='ssh -F /dev/null'`; rede autorizada pelo sandbox para fetch/pull/GitHub/Maven. Nenhuma configuração do sistema mudou. Fontes upstream foram extraídas por `git archive <SHA>` em /tmp/cfg-4d/air-old e air-4b, sem branch/commit/push em irmãos. Builds usam Maven repos inicialmente vazios e distintos: /tmp/cfg-4d/m2-old e m2-4b. Não são artefatos versionados.

## Proveniência escalar

Repository: Gustavo2358/air-java. Merge: `ce530a7e17ab12b23c48f29425f503ff920b09fb`.
Upstream path: `air-json/src/test/resources/scalar-assign.canonical.json`.
Git blob: `b37ff744819132cd0bfe115ce9a4382b19a29060`.
Upstream/local SHA-256: `40b9cec1bcc1c1e40cf3b9e3c48e834835e478e1d84bccafa575d63497ef3b60`.
Bytes upstream/local: **14554**.
Local: [scalar-assign.canonical.json](../../../../cfg-adapters/src/test/resources/air/scalar-assign.canonical.json).
[Metadata durável](../../../../cfg-adapters/src/test/resources/air/scalar-assign.provenance.json).

Extração por git show do merge e recomputação de SHA-256/Git blob, igualdade física reexecutada pelo integration gate contra o checkout upstream. Nenhum encode usado para materializar fixture. O expected [CFG manual](../../../../cfg-adapters/src/test/resources/cfg/scalar-assign.manual.json) foi escrito com objetos/ordem/IDs explícitos conforme o contrato v1, independentemente do writer/builder.

## RED, payload e controle

[RED](red.md) commitado em `c48a898`: baseline compila e passa 133 testes; mesmo reader de produção + codec antigo recusa scalar com `IMPLEMENTATION_LIMIT` em `$.publication.storage`, antes de BuildCfg. Não é erro de arquivo, DNS ou compilação.

Depois do pin: AirJsonFileReader → shared AirJson.decode → Publication → BuildCfg defaults/preflight → CFG_BUILT, KNOWN_SUBSET. O oracle in-memory observa:

| Fato | Resultado |
| --- | --- |
| PublicationId | cp4b-scalar-manual |
| UnitId | (cp4b-scalar-manual, alpha) |
| EntryId / LabelId | start / body, na mesma Unit |
| Nós / transições | 3 / 2 |
| Nós ordenados / ordinals | Sequence(Return) 0, Entry 1, NormalExit 2 |
| Transições contextualizadas em start | ENTRY 1→0; RETURN 0→2 |
| Policy / sourceKnowledge | KNOWN_SUBSET; Publication PARTIAL e Unit PARTIAL |
| Instruction count | 1 |
| Assign OperationId | (cp4b-scalar-manual, alpha, set-program) |
| Destination | ObjectPlace, ObjectId (cp4b-scalar-manual, alpha, data-slot) |
| StorageId | (cp4b-scalar-manual, backing-cell), via ObjectDeclaration.CellBinding |
| Value | Literal → TextValue → PROGA |
| Return OperationId | (cp4b-scalar-manual, alpha, leave) |

CfgGraph retém a mesma Publication, Unit e Sequence. Assign, destination, Literal, TextValue, ObjectDeclaration e Cell retêm identidade Java. ObjectPlace referencia por ObjectId completo a declaração real; não possui ponteiro Java de entidade. Coverage, uncertainties, origins e premises permanecem no snapshot original. Nenhuma promoção de partialidade/precisão.

O smoke cria 4096 Assigns válidos com IDs distintos e somente uma Sequence/Return; mantém três nós, duas transições e cada instruction por identidade. Não há threshold de tempo nem benchmark; nenhuma alegação de complexidade global do validator.

## CLI e GOBACK

[Comandos/recibos CLI](cli/results.json): dois processos reais, exit 0, stdout/stderr vazios, buildStatus CFG_BUILT no output. [Run A](cli/scalar-a.cfg.json) e [run B](cli/scalar-b.cfg.json) são byte-identical ao golden manual: 1432 bytes, SHA-256 `bbb7f3198acc81eb32a520214a7e7dbeabcfea69a8b1801e499a4dd7be6b01f0`.

[GOBACK CLI](cli/goback.cfg.json) mantém três nós/duas transições ENTRY/RETURN, KNOWN_SUBSET e PARTIAL. AIR e CFG golden originais byte-identical ao baseline e AIR igual ao merge 4B; [hashes/audit](source-audit.json). Todos os 31 métodos anteriores permanecem obrigatórios. Infraestrutura de temp/move/cleanup e schema v1 sem delta; Assign não é duplicado no CFG JSON.

## Challenges e segundo GREEN

[Runner reproduzível](../../../../scripts/project/challenge_scalar.py), [14 resultados finais](challenges-complete/results.json). Todos detectados, fontes e fixtures restauradas byte a byte; hashes dos originais preservados. Casos:

1. CI regressa ao codec antigo.
2. Lock diverge da CI.
3. Scalar local muda PROGA→PROGB e perde igualdade upstream.
4. Oracle de payload perde @Test; integração detecta método nominal ausente após Maven SUCCESS.
5. SequenceNode perde Assign.
6. Cada Assign gera nó CFG.
7. RETURN edge passa a depender da presença de Assign (GOBACK o falsifica).
8. Return não gera edge.
9. Defaults passam a STRICT.
10. Publication PARTIAL é promovida no sourceKnowledge serializado.
11. Reader troca shared decoder por parser local compilável; guarda arquitetural rejeita.
12. Writer duplica instructions da AIR no wire.
13. Suíte GOBACK TransportTest desaparece; detector rejeita após Maven SUCCESS.
14. Lock e CI regridem juntos ao pin antigo; guarda do merge autorizado rejeita.

Os mutantes Java dos itens 5–10/12 foram detectados por assertion failures com zero errors/skip, após compilação bem-sucedida. Item 11 tem compile exit 0 separado. Itens 4/13 compilam e passam Maven mas falham no inventário nominal. 1–3/14 são guardas de configuração/proveniência, não alegam falha Java.

As duas tentativas exploratórias foram preservadas em [challenges](challenges/results.json) e [challenges-final](challenges-final/results.json): a primeira encontrou seletor Maven sem suíte kernel; a segunda gerou exceção de terminador em vez de assertion no item 7. Não contam como RED semântico final. O runner foi ajustado para incluir a suíte real SemanticInterpreterRegistryTest e isolar a dependência indevida de Assign na emissão do edge, sem enfraquecer testes/guardas do produto.

[Segundo GREEN](second-green.json), Temurin 21.0.12.1+1, `JAVA_HOME=/tmp/cfg-2b/jdk21`, PATH com seu bin, `MAVEN_OPTS=-Dmaven.repo.local=/tmp/cfg-4d/m2-4b`:

| Comando | Resultado |
| --- | --- |
| bash scripts/harness/check-fast.sh | PASS, 47 testes harness |
| bash scripts/harness/check-architecture.sh | PASS, 102 testes kernel, bytecode 21/boundaries |
| bash scripts/harness/check-semantic.sh | PASS, 84 métodos obrigatórios, zero skip |
| bash scripts/harness/check-integration.sh | PASS, cinco suítes / 37 métodos transporte, regressões kernel |
| mvn -B -ntp clean verify | PASS, 139 testes |
| bash scripts/harness/check-performance.sh | UNAVAILABLE, exit 3 |
| python3 -B scripts/project/check_scope.py | PASS após manifest final; zero delta de produção |
| git diff --check | PASS |

[Logs brutos comprimidos e hashes](logs.json) preservam os bytes originais. Full não executado, pois o gate performance permanece indisponível. CI remota do head exato será registrada no [PR #11](https://github.com/Gustavo2358/analysis-cfg/pull/11); gates locais não inferem resultado remoto nem review humano.

## Limites e input contract for Checkpoint 4E

O 4D termina no PR para review humano, sem merge/auto-merge. Sem edição de fontes/branches/commits/push de irmãos; sem 4C, 4E, dataflow, nó/edge/schema novo, streaming ou algoritmo CFG novo. [Audit](source-audit.json): delta de produção do kernel/adapters/launcher é zero.

Default de input continua **16 MiB / depth 128**, incluindo o limite físico no reader. O fixture pequeno passa; AIRs acima de 16 MiB seguem limitadas. O codec 4B é subset text, não cobertura integral do binding; SNAPSHOT exige instalação pelo SHA aprovado. PARTIAL não certifica completude, perfil AIR, reachability ou valores calculados.

No 4E, o golden manual upstream do 4B poderá ser substituído pela **AIR real produzida pelo 4C**, mantendo exatamente:

```text
air.json → AirJsonFileReader → BuildCfg → CFG_BUILT → cfg.json
```

O produtor deverá entregar o binding AIR compatível com o pin, referências fechadas/validadas, forms suportadas, inventários honestos e respeitar os limites. IDs do output serão os da AIR recebida; não se exigem os IDs manuais do fixture 4B. Payload Assign continua recuperável na AIR pareada, CfgGraph.publication() ou SequenceNode.source(), sem duplicação no CFG JSON. O 4E não foi executado.

---
id: WORK-CFG-040
title: "Discovery: classify CardDemo entry-start blockers"
status: IN_PROGRESS
scope:
  - Investigação de ENTRY_START_UNAVAILABLE; relatório somente, sem implementação
---

# CardDemo entry-start discovery

**READY FOR HUMAN REVIEW — 2026-09-13 UTC.** Causas estabelecidas; nenhuma implementação realizada. `IN_PROGRESS` registra o PR ainda sujeito a revisão/merge.

## 1. Symptom

Dos 73 fontes/variantes tentados, 67 produziram SP e 27 chegaram a AIR/CFG/dependency. **40 bloqueados por `ENTRY_START_UNAVAILABLE`: 37 com `INPUT_MISSING` / 28 CALLs, e 3 sem `INPUT_MISSING` / 9 CALLs.** São 37 CALL sites observados sem resultado downstream, não 37 sites resolvidos ou recuperáveis garantidos.

Base: [JSON](carddemo-full-baseline.json) e [relatório original](carddemo-full-baseline.md), preservados. Commits inspecionados: `proleap-poc@59ac43bc3ab4bd186092a13732381512cbdaddba`, `cobol-lower@7da4980067c2860b3827ecc3eea641a6afa2a526`; `analysis-cfg` avançou para `b13ba042e53a4f93c6556418f478e5946d6a48f1` pelo merge do baseline (#25). Corpus: `aws-mainframe-modernization-carddemo@59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`.

## 2. Trace

**Há dois mecanismos de perda, não uma falha de transporte no lower:**

```text
3 programas:
source: MAIN-PARA → ENTRY 'DLITCBL' → operação executável
→ parser: entryStatement e operação seguinte presentes
→ AST: ambos presentes; AstBuilder recusa o ENTRY como start
→ ProcedureEntry existe, mas startStatementId = empty       ← primeira perda
→ SP: EntryFact KNOWN; ExecutableStart UNAVAILABLE/null
→ SpInput preserva a ausência → PartialProgramAdmission bloqueia

37 programas:
COPY não encontrado → placeholder + diagnóstico UNRESOLVED_COPY
→ fonte expandido incompleto                               ← perda de input
→ parser/AST ainda reconhecem o começo do corpo disponível
→ ResolutionAnalysisReport contém GapCategory.INPUT
→ projeção suprime o start pela presença de QUALQUER gap INPUT
                                                            ← perda da relação publicada
→ SP: EntryInventory/EntryFact/start INPUT_MISSING; statement=null
→ SpInput preserva a ausência → PartialProgramAdmission bloqueia
```

**Regra concreta do frontend.** [AstBuilder.buildProcedure / firstProcedureStatement](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/src/main/java/io/github/gustavo2358/cobolexplorer/AstBuilder.java#L445) percorre relações tipadas de sentences/paragraphs/sections. O primeiro contexto só vira `startStatementId` se for `StatementContext`, **`statement.entryStatement() == null`**, e houver seu nó em `builtStatements`. Encontrar ENTRY faz `start=null`; não procura o executável seguinte. É limite deliberado, também documentado em [Entry primária](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/docs/domain/cobol-semantic-product.md#L271) e no teste existente `emptyBodyAndLeadingAlternateEntryDoNotFabricateStart`.

A [projeção `entries()`](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/projection/CobolSemanticProductProjector.java#L149) publica start somente quando `origin != null AND !inputMissing AND !declaratives AND origin.startStatementId().isPresent()`. Depois exige target `Ast.Statement` da unit e identidade SP publicada. `inputMissing` é `anyMatch(GapCategory.INPUT)` sobre o relatório; não testa localização/impacto do gap. **Assinatura USING parcial, provenance agregada inexata e capability do statement inicial não são gates dessa condição.**

**Regra concreta do lower.** [SemanticProductJsonWriter](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/transport/SemanticProductJsonWriter.java#L78) e [Materialize.executableStart](https://github.com/Gustavo2358/cobol-lower/blob/7da4980067c2860b3827ecc3eea641a6afa2a526/adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/Materialize.java#L423) transportam disponibilidade/referência sem derivação. A validação exige KNOWN se e somente se há referência a statement existente da unit. Em seguida, [PartialProgramAdmission.plan](https://github.com/Gustavo2358/cobol-lower/blob/7da4980067c2860b3827ecc3eea641a6afa2a526/core/src/main/java/io/github/gustavo2358/lower/application/PartialProgramAdmission.java#L50) exige exatamente uma entrada e `start.statement().isPresent()`. Nos 40 há uma entrada, mas a referência está vazia. O stderr real é `Lowering BLOCKED_LOWERING / ENTRY_START entry: usable explicit primary entry required`; `ENTRY_START_UNAVAILABLE` é sua classificação no runner.

`structure.roots` significa containment ROOT, não entradas executáveis: o [port](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/CobolSemanticPort.java#L84) coleta todos os statements nessa condição. Os três têm 36/51/38 roots; os outros 37, entre 30 e 581. Continuations locais não identificam a entrada da invocação. **Não há outra prova tipada publicada suficiente para H3.** Escolher primeiro JSON/root, menor ProgramPoint ou nó sem predecessor seria heurística insegura.

## 3. Root-cause classes

| Classe | Programas / CALLs | Primeiro ponto de perda | Causa concreta | Provável owner | Confiança |
| --- | ---: | --- | --- | --- | --- |
| A — ENTRY inicial | 3 / 9 | Construção de `ProcedureEntry` na AST | Primeiro contexto é ENTRY; guard explícito deixa start vazio | proleap-poc / AstBuilder, depois projeção | HIGH |
| B — COPYs CICS ausentes | 35 / 10 | Preprocessing: input incompleto; projeção: referência de entry suprimida | `DFHAID` + `DFHBMSCA` → INPUT global → `!inputMissing` falso | input/configuração; proleap-poc se futura prova localizada for necessária | HIGH |
| C — COPYs MQ ausentes | 2 / 18 | Mesmas camadas de B | Seis COPYs MQ → INPUT global → `!inputMissing` falso | input/configuração; mesma política de projeção | HIGH |

Contagens são por fonte/variante, como no baseline. B e C diferem nas dependências, mas compartilham o mecanismo causal. HIGH qualifica o diagnóstico atual, não a recuperação futura.

## 4. The 3 non-input-missing programs

Todos estão em `app/app-authorization-ims-db2-mq/cbl/`. Parser sem erros, copy input COMPLETE, sem gap INPUT, sem declaratives; um único `entryStatement` em cada programa. O primeiro executável após esse ENTRY está presente no source, na árvore do parser, na AST e no inventário SP:

| Programa | ENTRY: linha original / AST id | Primeiro executável: linha original (expandida), AST id → SP id | CALLs |
| --- | --- | --- | ---: |
| DBUNLDGS.CBL | 165–167 / 393 | `PERFORM 1000-INITIALIZE THRU 1000-EXIT`, 170 (359), 403 → `statement:20` | 4 |
| PAUDBLOD.CBL | 171 / 379 | `DISPLAY 'STARTING PAUDBLOD'`, 173 (310), 385 → `statement:27` | 3 |
| PAUDBUNL.CBL | 158 / 350 | `PERFORM 1000-INITIALIZE THRU 1000-EXIT`, 161 (298), 356 → `statement:20` | 2 |

As `ProcedureEntry` das divisões AST 383/371/344 existem, com `signatureClausesPresent=true`, `declarativesPresent=false`, **`startStatementId=Optional.empty`**. Nenhum produto inspecionado oferece outro start primário canônico. `normalContinuations` conhece, por exemplo, 403→406, 385→389 e 356→359, mas não ENTRY→operação seguinte: [addSentenceContinuations](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/src/main/java/io/github/gustavo2358/cobolexplorer/AstBuilder.java#L494) também faz `current=null` para ENTRY. Essa informação local não recompõe a relação perdida de entrada.

Nos três, o SP publica exatamente `EntryInventory(status=PARTIAL, scope=PRIMARY_ONLY, gapCodes=[ALTERNATE_ENTRIES_NOT_PROJECTED])`; um `EntryFact(entry:0, PRIMARY, availability=KNOWN, coverage=PARTIAL)`; `ExecutableStart(UNAVAILABLE, null)`; gaps `EXECUTABLE_START_NOT_AVAILABLE` e `ENTRY_SIGNATURE_NOT_PROJECTED`; lowering/CFG readiness BLOCKED. Assinaturas PARTIAL, com 3/2/1 parâmetros respectivamente, RETURNING ausente. ENTRY aparece apenas como `OBSERVED / PRESERVED_STATEMENT / GENERIC_PRESERVED_STATEMENT`, sem continuation. PAUDBLOD conserva DISPLAY→PERFORM no SP; isso ainda não liga a entrada ao DISPLAY.

A leitura semântica das regras IBM indica um início executável determinado nesses layouts: a entrada ordinária começa no corpo não declarativo e a entrada alternativa transfere ao executável seguinte ao ENTRY. Aqui não há operação anterior ao ENTRY; ambas chegam à operação indicada na tabela. Há dois nomes de entrada, **não evidência de starts executáveis distintos ou de H4**. Os contratos de parâmetros permanecem distintos — em PAUDBLOD, inclusive, PROCEDURE USING tem dois parâmetros e ENTRY USING, um. Referências: [IBM 6.4, Procedures, p. 266](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf#page=294) e [ENTRY statement](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-entry-statement). Isso fundamenta a direção futura, sem converter essa leitura em um fato SP já existente. A classe é H5 (ENTRY ainda preservado) com mecanismo H2 na construção do fato canônico, antes da projeção SP.

## 5. INPUT_MISSING population

Classificação dos 37 pelos artifacts existentes: **o único código de gap INPUT é `UNRESOLVED_COPY`**, sem lexer/parser errors, declaratives ou ENTRY. São 82 ocorrências: 35 de cada COPY CICS; duas de cada `CMQGMOV`, `CMQMDV`, `CMQODV`, `CMQPMOV`, `CMQTML`, `CMQV`. Todos os placeholders estão dentro de `dataDivision` no parse tree disponível, antes da Procedure Division. Isso localiza as lacunas; não prova o conteúdo dos arquivos ausentes.

B contém 18 fontes do checkout e 17 variantes ZIP. A enumeração das relações tipadas do parser encontra como primeiro statement `execCicsStatement` em 10, `setStatement` em 23 e `initializeStatement` em 2: nenhum aciona o guard de ENTRY da classe A. C contém apenas COACCT01 e CODATE01, ambos com MOVE inicial. As diferenças de statement inicial não alteram a condição causal `!inputMissing`.

| Representante | Fato anterior à projeção | Ponto bloqueado / SP |
| --- | --- | --- |
| B: `app/cbl/COACTUPC.cbl` | COPYs nas linhas 615/616; Procedure Division 858; `EXEC CICS HANDLE ABEND` em 862 (expandida 3259); `startStatementId=3155` | Nó publicado como `statement:852`; a referência de entrada é suprimida por INPUT |
| B: variante ZIP `migrated_app/cbl/COACTUPC.cl2` | Mesmas linhas, statement inicial e AST id 3155 | Mesmo mecanismo; nó SP `statement:845` |
| C: `app/app-vsam-mq/cbl/COACCT01.cbl` | Seis COPYs nas linhas 71/75/79/83/87/90; MOVE inicial em 180 (206); `startStatementId=296` | Nó SP `statement:0`, continuation KNOWN para `statement:135`; entry ainda suprimida |
| C: `app/app-vsam-mq/cbl/CODATE01.cbl` | Mesmas seis posições de COPY; MOVE inicial em 129 (135); `startStatementId=187` | Nó SP `statement:0`, continuation KNOWN para `statement:117`; entry ainda suprimida |

**Causalidade, não só correlação:** [PreprocessorEngine](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/src/main/java/io/github/gustavo2358/cobolexplorer/PreprocessorEngine.java#L178) recebe `library.resolve(requested).isEmpty()`, emite `UNRESOLVED_COPY` e substitui a diretiva por `*> UNRESOLVED COPY ...`. [ResolutionAnalysisReport.addInputGaps](https://github.com/Gustavo2358/proleap-poc/blob/59ac43bc3ab4bd186092a13732381512cbdaddba/src/main/java/io/github/gustavo2358/cobolexplorer/ResolutionAnalysisReport.java#L198) converte esse diagnóstico em INPUT. A projeção citada usa exatamente esse predicado para não copiar o start da AST. Não houve perda do statement inicial pelo parser nos representantes.

Nos 37, inventory, entry availability/coverage, start availability e signature availability são INPUT_MISSING; `start.statement` e parameterCount são null, RETURNING UNKNOWN; gaps de start/signature/input explícitos e lowering/CFG BLOCKED. **H1 é confirmado quanto ao input expandido incompleto e ao motivo da recusa atual.** Não foi provado que essas lacunas tornam o início do corpo necessariamente ambíguo: o veto é global. Relaxá-lo exigiria uma prova de independência entre input faltante e entry, hoje ausente do contrato.

## 6. Likely fix directions

- **A, frontend:** derivar semanticamente a relação da entrada primária ao executável após ENTRY inicial, na camada canônica do `proleap-poc`, e deixar o projector traduzir esse fato. Preservar ENTRY observado, gaps de alternate inventory e assinaturas parciais. Não basta remover `entryStatement()==null`, pois isso apontaria para o ENTRY preservado; nem escolher o próximo item do JSON.
- **B/C, input:** disponibilizar as dependências reais nas bibliotecas configuradas, sem stubs. É a direção com maior teto populacional: **até 37 programas / 28 CALLs**. O source expandido resultante ainda precisaria de validação; não foi produzido nesta investigação.
- **B/C, alternativa futura:** se o produto precisar trabalhar sem essas dependências, investigar uma prova canônica localizada de entry, preservando a incompletude das demais dimensões. A mera localização textual do COPY em DATA DIVISION não autoriza ignorá-lo.
- **Lower/modelo:** nenhuma evidência sustenta fallback no `cobol-lower` ou necessidade de possible-entry/multi-entry para estes casos. Entradas alternativas com interfaces distintas merecem escopo próprio; não justificam inventar starts.

## 7. Recommendation

**Próxima implementação de produto recomendada: proleap-poc, construção canônica do primary executable start diante de ENTRY inicial**, com prova semântica explícita e preservação da parcialidade. É o caso delimitado sem dependências ausentes. **Teto: até 3 programas / 9 CALLs atualmente observados. Confiança HIGH na causa; recuperação downstream não testada nem prometida.** A frente de input B/C oferece o maior teto bruto (37/28), mas depende dos arquivos reais; não deve ser confundida com retirar um guard.

Incertezas restantes: comportamento com os COPYs reais; prova de entry independente de input incompleto; eventuais recusas posteriores do lower; alcance efetivo dos CALLs; contratos/runtime de IMS e de entradas alternativas. Nenhuma delas muda os mecanismos de bloqueio identificados. Não houve execução COBOL/IMS nem confirmação de ganho de coverage.

Verificação focada: leitura/classificação dos 40 SP, stderr, parser/AST/resolution snapshots e fontes pré-processados existentes; hashes dos 40 SP conferidos contra o baseline; totais 40/37 CALLs reconciliados. Como `ast-data.js` não exporta `ProcedureEntry`/`normalContinuations`, um probe descartável em `/tmp/carddemo-entry-discovery/EntryProbe.java` usou o JAR exato do baseline para reconstruir **somente parser/AST de sete preprocessed artifacts** (os três A e quatro representantes acima). Confirmou campos internos, IDs/contagens da AST e zero parser errors. SourceMap identidade nesse probe serve somente à inspeção estrutural; linhas originais/provenance vêm dos artifacts preservados. Classificador e saídas temporárias estão no mesmo diretório; não são nova infraestrutura do repositório.

Não houve rerun completo, geração nova de SP/AIR/CFG, alteração de código produtivo, testes novos, mudança de semântica ou aumento de coverage. Validação documental **PASS** (`lean.py docs`, 13 testes existentes de política; `git diff --check`); FAST técnico/Full/mutation/performance/qualification **NOT_RUN**. **STOP: conclusão pronta para revisão humana.**

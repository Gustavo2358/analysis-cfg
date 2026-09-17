# FD-W11 — resultado da qualificação N+C

**BLOCKED em C06: autoridade exata para READ DATASET não fechada.**
Os gates executáveis restantes passaram. Isso não fecha a DoD integral de W8/W11.
W10 permanece TODO / NOT_AUTHORIZED; nenhum merge, auto-merge ou release.
Evidência local completa: `artefatos-e2e/file-dependencies-20260916/w11/HANDOFF.md`.

## Pins e execução

[pins materiais](w11-pins.json), bundle `fd-w11/pipeline-3`:

| Camada | Commit exercitado |
| --- | --- |
| frontend | `aaecf8c1b1851c03dc13b8120e07308079f7e679` |
| lower | `24ee0ef933f8f9c6a49ee610d12ac822e8e7d8b6` |
| AIR | `5fe0224e5d2514286d6d23d486655334300383da` |
| consumer | `1ae5dfee9727d40481339a9f359ed4d7f4f7c5e4` |
| norma AIR | `fb153ae50f343022db45d20d627e1afac85de916` |
| CardDemo | `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e` |

SP2.28/fileInventory1.6/compilation1.0; wire dependencies2.3.
Java21.0.12, frontend release17. Checkouts limpos e345 arquivos de runtime
hasheados. Cache privado, barreira curta antes do fan-out, até três processos
pesados; FAST→Q sequenciais por repo. Outputs de cada tentativa separados.
Frontend HEAD52ea26c acrescenta somente harness/docs: `git diff aaecf8c1 HEAD --
src pom.xml` vazio. Consumer posterior ao pin recebe somente documentação.
Esses deltas não invalidam o bundle, a qualification-local ou o corpus.

## Gates e propriedades

Todos os comandos finais abaixo retornaram exit0. Caminhos `fd-w11/...` são
relativos a `.harness-results/`; cópias duráveis e comandos completos no E2E.

| Gate / comando | Resultado e propriedade |
| --- | --- |
| frontend `lean.py fast` / `qualification-local` | PASS352 /946 testes, zero falhas; um skip histórico somente em Q, mais normalizer/naming. FAST repetido após fix de harness, naming8 PASS |
| lower mesmos entrypoints | PASS FAST core2340+adapters; Q244296 semânticos+39215 performance/arquitetura |
| AIR mesmos entrypoints | PASS FAST187model/127codec/40harness; Q clean verify187/127 |
| CFG mesmos entrypoints | PASS FAST504 zero skips; Q Maven, semântica, performance, integração/CLI; runner26/métricas5/reader18 |
| `python3 -B .../final-barrier-4.py` | PASS B-SP control/scope/entry, B-AIR41 manuais, B-WIRE18; reader antigo rejeita wire novo e projeção CALL preservada; hashes/pins intactos |
| E-SELECTED drivers `e2e_file_*`, `resume-selected-2.py` | PASS101 fontes distintas ×2;8scope+55native+38CICS, quatro produtos determinísticos por fonte |
| `e2e_file_laws.py --runtime .../runtime-file-3.json` | PASS MR1–MR9 e SG1–SG5 core; CALL-X pertinentes; tracing real sem acesso ao arquivo de negócio e invariância a ambiente |
| `e2e_file_scale.py --runtime .../runtime-file-3.json` | PASS21 witnesses:1/8/32 arquivos, usos, layouts, aliases, candidatos, unidades e SORT; sem truncamento dos fatos esperados |
| `file_focal_mutants.py --runtime ... --maven-repo .../m2-3` | PASS baseline18;5KILLED: BEFORE/AFTER, owner, record-object, SORT input, DDNAME |
| `carddemo_baseline.py --runtime .../runtime-file-3.json --pins .../w11-pins.json` | PASS execução completa73 inputs; estados parciais e recusas abaixo preservados |
| `file_corpus_oracles.py --corpus .../corpus-4` | PASS49 usos nativos em4 programas + inventário/binding de2READ FILE; READ DATASET NOT_QUALIFIED |
| verificação dos arquivos de evidência | PASS manifests de9180 arquivos prévios e5324 finais, descompressão e comparação de todos os hashes |

Coortes têm seis fixtures memory em comum, contadas uma vez nas101. Pares completos
anteriores à interrupção foram REUSED somente após oracle e igualdade A/B dos
quatro produtos; arquivos interrompidos/vazios não foram aceitos. Logs das
execuções finais: barrier-4, selected-native-4, selected-cics-4b, control-4,
laws-4, scale-4, mutants-4, corpus-4, corpus-oracles-4.

Qualification-local é REUSED após deltas exclusivos de harness/docs por identidade
produtiva. Reader do relatório final é REUSED dos71 artefatos admitidos pelo runner,
com SHA exato verificado contra measurements; não se afirma nova execução do
reader ao sumarizar. O relatório separa novos resultados e não comparáveis.

## Corpus e CALL

73 entradas tentadas, sem editar source/copybooks/ZIP. Frontend71PARTIAL/2BLOCKED;
lower71PARTIAL/2NOT_REACHED; CFG65PARTIAL/6BLOCKED/2NOT_REACHED;
dependency71PARTIAL/2NOT_REACHED.15973 statements,85 declarações,133CALL sites,
376FILE sites:354 nativos com nome conhecido e22 CICS computed sem valor provado.
CALL:81 conhecidos com remainder,52unknown. As três formas READ DATASET não
entram na contagem FILE. Contagem não é oracle nem precisão/recall.

Comparação com tentativa2:70 programas/119 vetores CALL comparáveis inalterados;
com baseline histórico:27 programas/36 vetores inalterados. Zero mudança, adição
ou remoção nos conjuntos comparáveis.3 e46 programas respectivamente não
comparáveis são explícitos. A correção CBSTM03A recuperou14CALL/99FILE; não se
confunde recuperação de entrega com regressão ou prova de precisão global.

Duas recusas frontend: TAB em fonte fixed de COTRTLIC e normalização do CBSTM03A
no ZIP. Seis recusas CFG estrito: logical-copy sem precondição física descarregada
em CBTRN01C/02C/03C e cópias ZIP. O consumer dependency usa a porta partial-analysis
já existente; o runner conserva CFG BLOCKED ao executar esse ramo independente.

| Etapa | mediana / p95 / máximo (ms) | RSS máximo (KiB) |
| --- | --- | --- |
| frontend |2125.7 /4872.4 /7143.2|439420|
| lower |1817.1 /3277.7 /5144.2|682396|
| CFG |716.0 /1370.1 /2471.2|899616|
| dependency |1020.0 /2022.9 /3225.3|1183424|

Medidas incluem startup JVM/carga compartilhada; não são SLA. O reader Python
independente tem custo quadrático em origins e demorou minutos nos maiores
produtos; o tempo total do runner inclui essa validação, stageMs mede somente CLI.
Escala21: máximos por etapa5825.8/1620.4/767.6/1020.9ms e
325340/216444/143600/153568KiB. Não há extrapolação assintótica nem teto semântico.

## Correções e tentativas preservadas

- Lower ee876281: negociação LOGICAL_SOURCE omitida em SP2.21–2.28 causava53
  recusas. Oracle independente das oito versões + negativo2.18 RED→GREEN.
- Frontend aaecf8c1/lower24ee0ef9: WRITE ao fim de parágrafo confundia ordinary
  com intrinsic. RED bilateral e negativo de aresta KNOWN cruzando parágrafo;
  relação AST canônica e validação ajustadas, sem mudar o contrato PERFORM.
- Frontend52ea26c: quatro falhas naming no CI reproduzidas sem ripgrep. Scan
  Python remove dependência opcional e falso PASS de process substitution;
  oito testes e FAST fixo verdes. Não se presume ambiente remoto sem prova.
- Mutante SORT input inicialmente sobreviveu; oracle A6 fortalecido por papéis
  independentes. Dependência JUnit ausente foi tentativa INVÁLIDA, não KILLED.
- Erros mecânicos de cwd/classpath/barreira, rede Maven, ptrace e interrupção,
  fixtures de escala com controle prévio não suportado e corpus1/2/3 permanecem
  brutos. Escala final usa IF textual suportado, mantendo32 alternativas e
  remainder de fonte; nenhum oracle foi relaxado para ocultar falha.

FAIL históricos acima têm correção/execução substituta identificada. NOT_RUN:
W10/D, full remoto, lookup externo e gates pós-merge. Nenhum deles é PASS.

## Bloqueio material C06

COACTVWC.cbl contém READ DATASET nas linhas727/776/826. O catálogo C06 inclui
aliases conforme autoridade exata; não cabe promover nem excluir silenciosamente
a forma observada. API CICS TS5.6 consultada documenta FILE; SPI5.6p672 fecha
DATASET→FILE em SET. Não foi localizada autoridade5.6 que feche READ DATASET.
Manuais históricos/CICS TX/6.x não foram usados como substitutos; tentativas de
acesso exato adicionais retornaram403, registradas em authority-read-dataset.

Fontes pinadas: API5.6 SHA256
`3c3295d5b7f013a559b1cc742a7e810c0247bfc0bd3790b3545ab08327bc3c5b`;
SPI5.6 `417fe5ed2dfb3c5c630919e3e9abbd1358ad3ed25f162f59b4b29a99c0021f4d`.
URLs/seções da qualificação W8 em [perfis](profiles.md) e [W8](w8-implementation.md).
Os três comandos permanecem OBSERVED/EMBEDDED_LANGUAGE, sem dependência FILE
inventada. SET DATASET e demais casos comprovados conservam seus PASS.

C06 e a aceitação integral W8/W11 ficam **BLOCKED**; demais regras mantêm sua
qualificação. Desbloqueio: autoridade CICS TS5.6 para o alias READ (incluindo seus
limites), ou decisão humana explícita sobre o requisito C06. Após isso executar
oracle independente e apenas os gates materialmente invalidados. Não é pendência
D nem falta de DSNAME/JCL. Sem essa decisão, não declarar o core integral concluído.

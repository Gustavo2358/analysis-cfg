# FD-W9 — unidades e escopo

QUALIFIED_LOCAL. Entrada limpa: frontend4356722, lowerdcb6f49, CFG937d5e7;
AIR0035c5a/IRfb153ae inalterados. W8 qualificada; W10 NOT_AUTHORIZED.

Regra anterior ao código: IBM Enterprise COBOL6.4 SC27-8713-03, update2026-04-28,
PDF/hash fixado em profiles.md; cap7pp63–66, GLOBAL/EXTERNALpp184–185/197.
GLOBAL torna nomes acessíveis a programas contidos; record/subordinados de FD
GLOBAL herdam o atributo. Qualificação e shadowing seguem o resolver canônico.
EXTERNAL é atributo de recurso no run unit, distinto de visibilidade nominal;
mesma grafia sozinha não autoriza fundir declarações nem simular runtime compartilhado.

O modelo CompilationUnit já inventaria todas as unidades. A CLI exporta apenas
primary; o SP selecionado pode projetar referência DATA ancestral com handle local,
mas não publica sua associação de captura. Uso FILE nominal pode preservar owner
ancestral enquanto a declaração está somente no produto daquele owner. Esses são
os contracasos a fechar, sem parsing downstream nem atribuição transitiva ao pai.

Desenho a provar por oráculos: produto de compilação versionado que envolve os
produtos unitários existentes, parentage canônico, capturas DATA por identidade e
visibilidade FILE/GLOBAL explícitas. Manter entrada unitária compatível e limitada.
Análise semântica compartilhada calculada uma vez; projection/serializer só
transportam fatos e IDs. Lower compõe unidades em Publication AIR com IDs separados,
containingUnit/visibleObjects/AliasBinding existentes; ResourceDeclaration conserva
owner original e associa usos dos outros owners. Não executar interprocedural CALL.

Aliases de captura devem fechar referências; não inferir valores do pai executando
CALL. Entradas e efeitos parciais ficam locais/explicitados. Declarações homônimas
EXTERNAL permanecem entidades separadas sem prova adicional de sharing. Nenhuma
mudança normativa AIR presumida. Se representação falhar, registrar contraexemplo
antes de alterar estratégia; codec não é prova de perda normativa.

Oráculos: pai sem I/O e filho com FILE/CALL; irmãos F/F e external-name iguais;
GLOBAL OPEN/WRITE/record ownership; local-shadow/qualificação/ambiguidade; EXTERNAL
sem captura por nome; COPY repetido/REPLACING; unidade parcialmente modelada sem
apagar outra. Negativos wire/memory para owner/captura/parent ausentes ou contraditos,
permutações e source→SP→AIR→CFG→FILE+CALL por CLI. Gates abaixo; integração final pendente.

Produtor inicial W9 fe9c0a5: checkpoint local não qualificou a fronteira.
B-SP novo revelou contraexemplo COPY repetido: índice transitório FileIoMemory
usava AST localId sem unidade, associando registro da outra unidade. Lower recusou
captura fora da ancestralidade (focal-7), preservando a fronteira. Correção requer
Key(unit,node) em todos os índices/capturas de memória, não fallback por nome.
RAW failure e novo oracle frontend preservados; W9 segue IN_PROGRESS.

Produtor final d120036: COPY usa Key(unit,node); focal21, FAST352 (fast-5.log) e
Q945+normalizer (qualification-4.log, um skip histórico) PASS. Execuções finais
sequenciais; tentativa anterior sofreu colisão de build, preservada como FAIL de
ambiente, sem sustentar o checkpoint. GLOBAL READ captura o record original e
preserva CALL disjunto; foreign physical view não provada conserva gap/MAY.

Codec AIR5fe0224 qualificado/pushed: visibleObjects já normativo, 187model/127codec/
40harness FAST PASS. IR inalterado. Consumer manual pai/filho prova associação sem
transitividade; RED real no pin codec W8, pin atualizado. Manual28 e FAST CFG
(fast-1.log) PASS; consumer produtivo inalterado. Lower focal seis composições,
nove negativos e permutação PASS; FAST PASS, qualification-local PASS.
Integração CLI e regressões qualificadas abaixo.

## Fechamento

Pins: frontend d120036cb71f81ff3055ca1a47be32ac88dcad7a,
lower9c4e7a340145f9031b7260616e4029f42c6cce91, AIR5fe0224e5d2514286d6d23d486655334300383da,
IRfb153ae50f343022db45d20d627e1afac85de916. Lower HEADc31cd92 difere somente na
frase documental sobre quais suítes rodam no full; produção/testes/build idênticos.
Bundle imutável `fd-w9/producers-final`, construído uma vez no lock final.

| Resultado | Comando / prova |
| --- | --- |
| PASS | Frontend `lean.py fast`352 e `qualification-local`945+normalizer; logs fast-5/qualification-4, exit0; um skip histórico |
| PASS | Lower `lean.py fast`2340core+adapters; `qualification-local`205081semânticos+39215performance (244296 combinado), arquitetura; exit0 |
| PASS | AIR `lean.py fast`187model/127codec/40harness; AIR existente, visibleObjects transportado; exit0 |
| PASS | CFG `lean.py fast`504 sem skips e manual28, `test_dependency_wire.py`18; exit0 |
| PASS | `e2e_file_scope.py --work .harness-results/fd-w9/e2e-scope-2 --producers .harness-results/fd-w9/producers-final/producers.json`: seis fontes×2; exit0 |
| PASS | Mesmo driver com `--case cics-nested --case cics-nested-closed`, output `e2e-cics-scope-2`: duas fontes×2; exit0 |
| PASS | `e2e_file_auxiliary.py --sp-version 2.28.0 --work .harness-results/fd-w9/e2e-native-2 --producers .harness-results/fd-w9/producers-final/producers.json`:33fontes×2; exit0 |
| PASS | `e2e_cics_files.py --work .harness-results/fd-w9/e2e-cics-1 --producers .harness-results/fd-w9/producers-final/producers.json`:38fontes×2; exit0 |
| REUSED | CFG produção, CALL/provider/solver e wire2.3 sem delta W9; AIR normativa sem delta; lower HEADdoc equivale ao pin produtivo |
| NOT_RUN | CFG qualification-local final/corpus/metamórficos/mutantes W11; D/W10 não autorizado |
| BLOCKED | nenhum |

Todos os E-SELECTED comparam bytes SP/AIR/CFG/dependencies A/B e oráculos manuais,
não somente contagens. CICS contido prova owner do filho, FILE ACCOUNTS e SYSID R001
com supports/BEFORE. CALL local posterior conserva candidatos mas abre modelo por
may-write/control-all. Retirar somente esse CALL fecha o modelo; CALL no pai não
interfere. Source remainder continua explícito em ambos. Oracle inicial omitia
essa lei do CALL, falhou, foi corrigido com a contraprova; output FAIL preservado.

Limites: cada produto unitário conserva PRIMARY_ONLY; missing COPY com entrada
utilizável conserva unidades e gap. Uma unidade sem entrada utilizável rejeita a
composição atomicamente pelo contrato prévio, sem omissão silenciosa. Captura
nominal não inventa layout nem valor inicial ancestral: view ausente mantém gap/MAY.
Não executar pai→filho nem inferir sharing EXTERNAL por nome. Nenhuma dimensão
DSNAME/JCL é modelada. STOP só após W11; W10 permanece NOT_AUTHORIZED.

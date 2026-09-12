# CP6 W2D — real IF CALL dependencies

[Work item Lean](WORK-CFG-034.yaml). Baseline confirmado por fetch:
`7549982461c04bb924ef935e4411762eab39e277`. Branch:
`feat/cp6-w2d-real-if-dependency`. COMPLETED / MERGED no [PR #20](https://github.com/Gustavo2358/analysis-cfg/pull/20).
W2 = APPROVED / MERGED / CLOSED. Git/PR/testes são o registro.

Qualified/source HEAD: `0ea0fea5fe2d8d1688d94b7a666edf13b2f3e2ca`.
Merge: `cad8b45cdcd2d380cb98ba9984f34859551430e0`.
Tree preservado em ambos: `7548ca15210d7f2fbc09b99040e7c90bcf700791`.
Esta sincronização documental ocorre no PR de MOVE→MOVE (WORK-CFG-035);
a validação abaixo registra o W2D já mergeado.

Produção Java: nenhuma alteração. Solver, lattice, strong updates, fixed point,
CFG core e interpretação genérica de nomes permanecem iguais ao baseline.
W2D adiciona pins, fixtures/oráculos e integração local, sem nova infraestrutura.

## Autoridades

| Componente | Pin integrado usado | Produto |
| --- | --- | --- |
| analysis-ir | `51b4d9a8ae0364232bd97103cd73a77e1a34996c` | AIR 2.0.0; JSON binding 1.0.0 DRAFT |
| air-java | `760593b923ca7311f699547c36349f54eb0dac42` | W2C original `1d22068e9d9c1d100ecdef734e5b6995252e7ede` |
| proleap-poc | `4a29b7b730f9b61b289f46dc8227d5321adbd3ec` | SP 1.4.0; W2A original `4ffabded1aad39316b8a6f337f732976fdb3ca3e` |
| cobol-lower | `a8fbffd3be43032327a8fff85283e2d206ba6419` | W2B original `2b7fa3a5cee865eef5007d6d870618e032047e1e` |

Nenhum sibling foi modificado. Os produtores são compilados de checkouts isolados
nos SHAs completos, com AIR recompilada antes do lower. Os JARs AIR/lower usados
na execução vêm desses builds, evitando depender de snapshots substituíveis no cache.

## Oráculos e desenvolvimento

Antes do repin, `W2dModelTest` passou com AIR W1: Branch Unknown BOOL, closed/open,
supports específicos, orphan `BADPROG ` e permutação. A porta `DependencyAnalysis`
executa CFG → planning → PossibleValues → consumer; os asserts usam resultado,
rawCandidates, supports e origins públicos. Não há consulta a internals do solver.

O primeiro RED real executou o frontend W1 pinado no COBOL closed. O SP emitido
foi 1.3.0; `e2e_w2d.py` falhou com `W2D requires SP 1.4.0; actual real producer
emitted 1.3.0`. O log normal ficou em `.harness-results/w2d-baseline-red.log`.
Após o repin, o pipeline real passou sem mudanças produtivas. Ajustes iniciais
no oracle Python corrigiram apenas nomes de campos do binding existente.

Closed A/B: mesmo CALL site contém `PROGA` e `PROGB`; fatos preservam `PROGA   `
e `PROGB   `. Cada raw/interpreted candidate e edge conserva somente o Assign
do seu próprio MOVE, resolvido pelo DAG de origins até as linhas 11 e 13 do COBOL.
Open A/B: somente `PROGA   ` → `PROGA`, com support no MOVE da linha 11.

| Caso | modelValueRemainder | sourceValueRemainder | interpretationUnknownRemainder | effectiveUnknownRemainder |
| --- | --- | --- | --- | --- |
| AIR closed completo / nome exato | false | false | false | false |
| AIR open completo / nome exato | true | false | false | true |
| COBOL closed real | false | true | true | true |
| COBOL open real | true | true | true | true |

O teste adicional varia a política de nome sem alterar o fechamento do modelo
e mantém source openness com política exata. Os campos permanecem independentes;
o wire existente usa o nome `interpretationUnknownRemainder`.

Closed/open ignoram a posição física de sequences e o bloco órfão inalcançável.
O CFG conserva ENTRY, BRANCH_TRUE/FALSE, JUMP, INVOKE_NORMAL e RETURN existentes.
O predicate não é avaliado; o false path aberto preserva o estado de entrada.
Closed/open A/B têm bytes idênticos para COBOL, SP, AIR, CFG e dependency result.

## Validação

W1: fixtures e testes originais preservados; singleton mantém PROGA e seu Assign,
literal usa o caminho direto com zero preparações/execuções de PossibleValues.
Um probe CLI adicional comparou os resultados das fixtures W1 com os JARs AIR
W1/W2 recompilados: dependency result byte a byte idêntico para singleton e literal
(outputs em `.harness-results/w2d-w1-wire/`).

`challenge_w2d.py`: 10/10 mutações compiladas mortas por oráculos focais, baseline
restaurado PASS. Casos: troca BRANCH_TRUE/FALSE; perda do Assign PROGB; false open
redirecionado por bloco artificial; orphan indevidamente tornado alcançável;
supports cruzados; closed remainder forçado; open remainder apagado; candidato
removido quando open; candidato inventado; singleton W1 eliminado. A primeira
mutação também é rejeitada pela guarda existente de endpoints CFG. Não houve
mutação em solver/lattice. Logs locais em `.harness-results/w2d-mutations-final/`.

A primeira Full detectou uma consulta indevida do guard de goldens ao sibling
AIR, que não continha o novo commit. Os goldens eram idênticos no checkout pinado.
`check_scalar_contract.py` agora usa `AIR_JAVA_CHECKOUT`, já validado, preservando
todas as verificações de SHA/blob/bytes e as fixtures históricas. Uma regressão
Python com repositório Git isolado passou RED → GREEN e também rejeita source sujo.

FAST final: PASS, 293 métodos obrigatórios sem skips, 72.326 s, incluindo a
regressão Python do checkout isolado. `python3 -B scripts/harness/lean.py
qualification-local`: PASS; suíte completa, arquitetura, semântica, performance,
integração W5 e W2D closed/open reais A/B passaram. Logs normais em
`.harness-results/w2d-fast-qualified.log` e `.harness-results/w2d-full-qualified.log`.
Remote CI continua FAST ONLY; Full e mutation somente LOCAL / ON-DEMAND.

## Limites

Closed prova fechamento apenas no modelo de valores da AIR. SP/AIR reais ainda
publicam PARTIAL, política de nomes desconhecida e open control; não há promessa
de resolução completa em runtime. Não houve ampliação de perfis de efeitos,
predicados, AIR normativa ou upstreams. O algoritmo é o fixed point existente;
nenhuma enumeração de caminhos foi introduzida. O PR #20 foi aprovado e mergeado;
o novo trabalho MOVE→MOVE segue em WORK-CFG-035.

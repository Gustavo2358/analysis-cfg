# state

## Onde estamos
CFG-FIRST implementado e oracle verde; modelo/projeção, envelope e hooks reais
estão no diff. Docs duráveis atualizadas. Work continua active até gates finais/CI.

## Verde conhecido
Lifecycle/pins e RED no plan/eval. 37 testes Java passaram, incluindo 19 de
EVAL-CFG-025. Semantic PASS/exit 0. Mutante de fallthrough morto com exit 1 por
invariante e por oracle independente; revertido. Architecture ampliado executado
com sucesso, exit 0, 13 fontes e 20 classfiles exatos. CI ainda não executada neste SHA.

## Restante
Gates finais locais executados e registrados no eval: docs/harness/fast,
architecture/semantic, clean test/verify e diff-check exit 0; performance/integration/full
exit 3 honestos. Restam push/CI, encerramento condicional e PR novo sem merge.

## Descobertas que afetam o plano
Retorno usa Entry da ativação (§04.8), sem seletor AIR entryScope. Regra RETURN
condicionada por Entry conserva escopos e inventário; não afirma reachability.
Performance/integration continuam indisponíveis. EVAL-CFG-001/009 continuam planned.

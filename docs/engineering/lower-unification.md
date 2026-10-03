# Consumo do lower unificado

Status: qualificado localmente; revisão humana e merge pendentes.

O produtor cobol-lower está fixado em `6dae2889d74377f82857b38e19599429cd76ea93`.
O lower eliminou a expansão de cadeias em entradas históricas, CICS, ESCAPE,
DAGs estruturais e chamadas inline alcançadas por GO TO. Não há mudança de
contrato, opcode, modelo AIR ou frontend nesta etapa.

## Algoritmo do consumidor

O CFG conserva a tabulação por frame/valor abstrato. O teste com 100 destinos
expôs consultas repetidas de viabilidade durante a publicação dos resultados.
`CallerWitnesses` mantém um número limitado de caminhos completos comprovados,
usados somente para respostas positivas. Seus bits não são combinados entre
caminhos. A busca curta considera uma chave necessariamente presente na consulta;
cada predicado da cadeia e o predicado completo continuam sendo verificados.
Uma falha de cache mantém a busca simbólica exata. Não há truncamento de contextos,
valores, candidatos ou profundidade. A prova e os limites estão em
[dataflow-context-tabulation.md](../architecture/dataflow-context-tabulation.md).

## Qualificação

- FAST e qualification-local passaram no lower e no consumidor.
- 48 testes focais passaram. O oráculo independente comparou 2.400 análises geradas
  forward/backward, com raízes IN/OUT exatas e transferências não distributivas.
- O teste permanente de 100 destinos em ambos os sentidos passou. No consumidor
  anterior, a mesma suíte excedeu 35 segundos com heap de 512 MiB.
- 560 fontes foram executadas novamente em toda a pipeline com o lower unificado.
  Depois do último ajuste no consumidor, CFG e dependencies foram reexecutados nos
  560 produtos congelados. Hashes dos inputs reutilizados estão registrados; não
  se apresenta esse segundo ciclo como uma nova execução de frontend/lower.
- Na última reexecução, CFG e todos os campos de dependencies.json são iguais aos
  da execução anterior do lower unificado, exceto os dois objetos de métricas.
- Entre lower antigo e unificado, a correspondência bidirecional por fonte passou
  em 560/560, sem diferença sem explicação. Cada candidato, suporte, prova,
  uncertainty e remainder tem testemunho ou explicação verificada. A comparação
  estrita e os registros originais permanecem preservados. Onze mutações negativas
  foram rejeitadas pelo auditor; não houve agregação cega de sites ou provas.
- Preservadas 916 ocorrências-fonte de programas / 1.082 candidatos e 487 de arquivos
  / 463 candidatos. Permanecem 439 COMPLETE e 121 PARTIAL. Sites físicos mudam por
  compartilhamento e inventário explícito; cada mudança está auditada.
- Doze stress tests históricos/mistos e seis casos ampliados passaram com heap de
  512 MiB. CICS/ESCAPE com 100 destinos completaram valores, alcançabilidade e
  dependências; a maior etapa levou 17,49 segundos com outros gates em paralelo.
  O pico RSS observado no conjunto ampliado foi 411,3 MiB. Heap e RSS são distintos.

Os oráculos E2E agora verificam ativação, corpo e dois retornos explícitos, com a
continuação exata de cada chamador. Fronteiras opacas de reentrada são admitidas
nos casos fechados somente quando o oráculo de pilhas prova que são inalcançáveis.
Candidatos, suportes, ordem física permutada e efeitos continuam verificados.

Evidência no workspace: `artefatos-e2e/lower-unification-20261003`, especialmente
`cfg-gates-06.json`, `corpus-cfg15/summary.json`, `explanations-11-02/summary.json`,
`cfg15-tests.json`, `stress-cfg15` e `extra-scale-cfg15`. O runtime `qualified-15`
registra hashes dos 154 arquivos de produção do consumidor. Experimentos e falhas
intermediárias são preservados separadamente, sem serem apresentados como PASS.

O programa corporativo original não estava disponível. BDDs e domínios abstratos
mantêm sua complexidade intrínseca; não há garantia polinomial universal. Não houve
merge. AIR #26 e analysis-ir #9 continuam sendo pré-requisitos dos PRs lower #56 e
CFG #62. O frontend permanece em `e6d1fa7f54bee07469bdb7ebd4a98701ca68419b`.

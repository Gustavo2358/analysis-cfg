# eval

## O que prova corretude
Oracle estruturado manual antes do builder: entry(E) → sequence(L) → normal-exit(U,E).
As relações e correlações esperadas vêm da norma fixada, nunca do builder ou DOT.

## Classes positivas
Entry/Return mínimo; múltiplas Entries/Units; inventário órfão; Return entryScope
conforme contrato real a confirmar antes dos testes.

## Casos adversariais
C1 label ausente → INVALID_IR. C2 terminador ausente não construível/INVALID_IR.
C3 Return seguido fisicamente por Sequence sem edge. C4 permutação física.
C5 órfã preservada sem predecessor artificial. C6 entries e exits distintos.
Halt/Jump/instructions fora do slice; capability desconhecida não vira sucesso.

## Propriedades/relações metamórficas
Permutar Sequences mantendo IDs/referências não muda o CFG correlacionado.
Verificar identidade dos objetos AIR, listas e imutabilidade do produto.

## Casos de regressão
Preservar testes de boundary, registry e preflight. Matar temporariamente mutante
Return → próxima Sequence física; reverter antes do GREEN final. Gate sem suíte
ou com zero testes deve falhar.

## Expectativas de escala
Índices uma vez; custo proporcional ao inventário e transições emitidas mais
ordenação determinística se necessária. Sem claim de gate performance.

## RED observado
EVAL-CFG-025/15 testes escritos em EvalCfg025Test, expected manual anterior ao
builder. Compilação falhou com exit 1 por produto/API inexistentes. Sem golden
regenerado; fixture usa initialLabel explícito. Os testes incluem também retenção
de dois operandos Return e identities homônimas entre domínios/Units.

## GREEN, mutante e gate semântico
Após implementação, 15/15 casos focais e 18 regressões passaram (exit 0).
Challenge acrescentou quatro casos de permutação de Entries, inventário/corpo
indisponível e capability reconhecida/registrada sem semântica: 37/37, exit 0.

Mutação temporária em CfgFirstProjection: destino de Return = próxima Sequence
da lista AIR, senão exit. Teste returnNeverFallsThroughToPhysicalNextSequence:
exit 1, 1 erro pela guarda dos endpoints do produto. Desativando temporariamente
essa guarda, o mesmo teste deu exit 1, 1 falha de assertEquals: expected L → exit,
observed L → other. Projeção e guarda originais foram restauradas em finally.
O semantic gate subsequente passou com 19 casos obrigatórios, zero skips, exit 0.

O detector semantic executa 25 fixtures negativas de relatório. Inventário de
métodos escrito explicitamente; não é descoberto dos próprios testes.

## Finding do harness corrigido
Fast expôs uma premissa antiga no teste test_19_unavailable_never_passes:
a fixture herdava semantic=implemented, mas esperava UNAVAILABLE. O teste agora
materializa explicitamente a condição unavailable/hook=null na cópia temporária
para cada gate, inclusive architecture. A assertiva exit 3 foi preservada; o teste
não executa mais produto por acidente e continua falsificando a mesma obrigação.

## Challenge final
Falsificações reais: imports temporários de filesystem/reflection/ServiceLoader
rejeitados (architecture exit 1); classe CFG autorizada removida temporariamente
rejeitada pelo inventário do hook (exit 1); Publication AIR local rejeitada (exit 1).
Todos restaurados. Semantic com MAVEN_ARGS=-DskipTests=true fez Maven terminar
sem casos, mas o hook recusou relatório ausente (exit 1). Detector também rejeita
cada método obrigatório ausente.

Revisão do código/testes: initialLabel é a única origem de edge ENTRY; nenhuma
posição física governa produção; IDs CFG distintos de AIR; exits/Entries por
namespace e ativação; órfãs mantidas; containers imutáveis e assertSame da AIR;
nenhum deep copy ou terminador reparado; Halt/Jump/instructions recusados; sem
JSON/frontend/I/O; registro de capability não finge interpretação. Estados de
falha sem graph continuam distintos e preservam preflight. EVAL-CFG-001/009 e
perfis AIR não foram promovidos. BACKLOG-CFG-022 continua planned sem work item.

## Gates finais locais executados
MAVEN_OPTS=-Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-005-m2; runtime local
Temurin 25.0.4, --release 21 sem preview.

| Comando | Exit | Resultado |
| --- | --- | --- |
| bash scripts/harness/check-docs.sh | 0 | PASS |
| bash scripts/harness/check-harness.sh | 0 | 41/41 |
| bash scripts/harness/check-fast.sh | 0 | PASS |
| bash scripts/harness/check-architecture.sh | 0 | 37 testes; 13 fontes/20 classfiles exatos |
| bash scripts/harness/check-semantic.sh | 0 | 19 testes EVAL-CFG-025, zero skips |
| mvn -B -ntp clean test | 0 | 37/37 |
| mvn -B -ntp clean verify | 0 | 37/37 |
| git diff --check | 0 | PASS |
| bash scripts/harness/check-performance.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-integration.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-full.sh | 3 | fast/architecture/semantic PASS; para em performance |

CI remota ainda pendente; não foi reportada como verde por antecipação.

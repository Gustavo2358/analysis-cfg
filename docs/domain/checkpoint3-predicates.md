# Checkpoint 3 — predicados escalares

Autorização: implementação solicitada em 2026-10-04; revisão por PR, sem merge.

## Regra e autoridade antes da implementação

IBM Enterprise COBOL for z/OS 6.4 Language Reference, `igy6lr40.pdf`, páginas
276–279 (comparações), 287–289 (abreviações), 341–342 (EVALUATE).
Fonte primária: https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf.
PDF oficial local consultado via extração textual; páginas HTML IBM deram 403.
AIR 2.0.0 §§ tipos/operandos (expressões puras, unknown e reads), controle e
contrato de consumidores governam a representação downstream.

LANGUAGE_GUARANTEED: numeric compares algebraic values, independent of scale;
text equality pads the shorter operand to the right with spaces; ordering depends
on collation; abbreviated relations inherit the last stated subject/operator,
logical NOT does not propagate; EVALUATE selects the first matching WHEN.
ARCHITECTURE_GUARANTEED: resolution precedes predicate normalization; projection
only translates canonical facts; topology alone supplies successors and loop phases.
UNCERTAIN: runtime data, missing address/type proof, external collation/settings.
These uncertainties cannot authorize candidate exclusion or exact memory effects.

## Escopo finito e algoritmo

Generalizar a árvore existente de condições publicada no facet `conditionNames`,
preservando definição/uso/SET de nível 88. A árvore compartilhada distingue leituras,
literais e relações de seus operadores booleanos. Relações completas, abreviadas e
combinadas são normalizadas após binding; IF, WHEN e UNTIL usam o mesmo algoritmo.
EVALUATE de sujeito único suporta TRUE/FALSE e valores escalares; WHENs repetidos
na mesma ação são alternativas OR. THRU/ANY, ALSO, classes, funções e aritmética geral ficam
fora da capacidade exata. Não há solver geral de viabilidade nem AP.

Lower somente traduz árvores publicadas e verifica domínio/acesso usando os fatos
SP/AIR atuais. EQ/NE textual usa fitting; ordem textual sem collation fica aberta.
Relações numéricas INT/DECIMAL alinham domínio sem truncar. Quando não há prova de
acesso, preservar escopo de leitura, referências de índice/endereço e incerteza;
não criar ObjectPlace exato por binding nominal. Endereço não comprovado usa Opaque
com leituras conhecidas e limitadas, ambos os ramos e fronteira não executável;
UNKNOWN puro só usa remainingReads=none com fechamento independente das leituras.
READS_OPEN registra efeito de leitura conhecido com referências ainda abertas:
usa Opaque com reads amplos, sem inventar writes/dependências de recursos nem totalidade.
Funções/superfícies sem prova mantêm efeitos conservadores; abertura de endereço
e possibilidade de impureza são fatos separados. Unknown BOOL só abstrai a
comparação pura conhecida; nenhuma operação impura recebe pureza por conveniência.

Invariante: cada decisão conserva todos os reads conhecidos e as alternativas ainda
possíveis. A ordem de WHEN e os testes BEFORE/AFTER de PERFORM vêm da topologia.
Custo proporcional à sintaxe/operandos; não enumerar ranges nem distribuir AND/OR
em combinações. A terminação decorre do percurso de árvores finitas. Oracles:
relações por família, abreviações/NOT/grouping, mixed 88, first match/no match,
reads/aliases/índices, round-trip/rejeição de payload e dependencies E2E.

## Limites e evidência de qualificação

A árvore registra significado e causalidade de origem, não garante endereço válido
ou verdade em execução. Grupos sem domínio/acesso comprovado conservam o tipo
disponível do objeto; a ocorrência aberta não certifica sameDomain e publica
TYPE_UNKNOWN próprio para o Place Choice. Ausência de collation não bloqueia o
fluxo padrão: somente a ordem textual fica aberta. COPY/declarações ausentes
continuam input real, diferente de capacidade não implementada.

O provedor condicional nominal legado não consome toda a nova árvore de WHEN/UNTIL.
O dependencies pode conservar candidatos sustentados por hipóteses de fonte mesmo
quando os valores executáveis já restringem um alvo. Leituras numéricas de runtime
e BOOL constante isolado não recebem nova propagação no consumidor; relações
numéricas entre constantes são a precisão adicional delimitada desta mudança.

Testes cobrem as seis relações, abreviação/distribuição e NOT, mistura com 88,
seleção ordenada e PERFORM BEFORE/AFTER, fitting textual, índices, tipos abertos,
collation, contrato e round-trip AIR. A qualificação integrada e as evidências
brutas ficam no workspace em priority3-predicates-20261004/REPORT.md do repositório
local artefatos-e2e. AIR/analysis-ir permanecem nos pins integrados, sem alterações.

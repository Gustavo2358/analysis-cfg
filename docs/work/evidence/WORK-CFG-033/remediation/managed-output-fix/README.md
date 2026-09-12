# Correção do inventário em presença de builds isolados

O commit 16d4397 passou a Full Qualification local em 707.316 s, mas os Fast CIs
remotos 34657400477 e 34657403312 falharam antes dos testes Java. Localmente o work
estava fora do checkout; remotamente estava em .harness-results. O scanner contou
fontes AIR exportadas nesse diretório como produção analysis-cfg.

O RED focal reproduz o erro. A correção exclui somente stores de build/evidência
na raiz (.harness-results, .cache e .git) do inventário Java/POM. Mantém a exclusão
de target já existente. Fontes extras nos módulos, inclusive sob uma pasta interna
chamada .harness-results, continuam sendo detectadas. O DAG Maven nominal e as
inspeções de bytecode/dependências permanecem integrais. Nenhum teste é selecionado
por caminho ou diff; o Fast continua fixo.

Três contracasos: GREEN. Fast com work dentro do checkout, reproduzindo o GitHub:
PASS em 273.324 s, 286 métodos e todos os boundaries. Logs, receipts
de checkout/falha remota, RED/GREEN e o receipt da full anterior permanecem aqui.
A qualificação anterior certifica apenas 16d4397; o novo HEAD exige outra Full
Qualification antes do push, cujo receipt final ficará no handoff externo.

Nenhum Java de produção, teste Java, contrato wire, pin ou sibling mudou nesta
correção do scanner. W2 continua NOT_STARTED / NOT_AUTHORIZED.

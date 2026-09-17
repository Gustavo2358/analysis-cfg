# cobol-zos-dynamic-call-minimal@1

Esta é a política mínima autorizada explicitamente pelo usuário para W1D. Não é
resolução geral IBM, prova de existência de programa ou inferência de opções do
compilador. A implementação fica em `CallNameInterpreter`, fora de CFG, values,
lattice e solver. Ver [contrato do produto](../architecture/analysis-dependency-result-v1.md).

Para computed `program/cobol.program`, remover somente U+0020 consecutivos no fim
do raw. Aceitar o resultado somente se corresponder integralmente a
`[A-Z_$][A-Z0-9_@#$]{0,7}`. Literal não recebe trim. Preservar raw e suportes em
todos os casos. `UnknownName` permite o candidato sob essa hipótese explícita,
mas mantém `interpretationUnknownRemainder=true`; `ExtensionName` não conhecida
produz somente raw e interpretação aberta.

| Raw | Forma | Candidato | Interpretação |
| --- | --- | --- | --- |
| `PROGA   ` | computed | `PROGA` | Aberta se NamePolicy desconhecida |
| `PROGA` | literal | `PROGA` | Aberta se NamePolicy desconhecida |
| `$PROGA` | literal | `$PROGA` | Aberta se NamePolicy desconhecida |
| `$PROGA   ` | computed | `$PROGA` | Aberta se NamePolicy desconhecida |
| `$TEST123`, `$ABC1`, `$ABCDEFG`, `$ABCDEFG` | qualquer | o mesmo nome, incluindo `$` | Aberta se NamePolicy desconhecida |
| `_PROGA`, `A$PROGA` | qualquer | o mesmo nome | Aberta se NamePolicy desconhecida |
| `PROGA   `, `$PROGA   ` | literal | nenhum | Aberta |
| `$proga`, ` $PROGA`, `$PROG-A`, `$ABCDEFGH`, `@PROGA`, `#PROGA` | qualquer | nenhum | Aberta |
| `proga`, ` PROGA`, `PROG-A`, `1PROG`, `ABCDEFGHI` | qualquer | nenhum | Aberta |
| vazio, só espaços, tab final, NBSP final | computed | nenhum | Aberta |

Não implementar uppercase conversion, truncation, hyphen/leading-digit
translation, PGMNAME variants, DLL rules ou nested lookup. Valores fora da
política não são descartados do produto raw. Não há confidence score, tentativa
de localizar callee ou comparação com corpus.

## Correção pré-release R2-REV-F2

Por decisão explícita do produto, `$` faz parte do nome: `$PROGA` produz candidate
`$PROGA`, nunca `PROGA`. Computed `$PROGA   ` produz o mesmo referenceName com
rawValue integral preservado; literal com padding continua rejeitado. O limite é
oito caracteres incluindo `$`; não se ampliam outros caracteres, case ou linkage.
Aceitar o nome não prova existência do programa e não fecha modelValueRemainder,
sourceValueRemainder ou a interpretação aberta de UnknownName.

O identificador permanece **cobol-zos-dynamic-call-minimal@1**, conforme a
[política pré-release](../architecture/extensibility.md#política-de-versionamento-pré-release).
Não existe baseline publicado/consumidor externo cuja rejeição incorreta deva ser
preservada. O JSON atual emite interpretationProfile=per-site e esse mesmo @1 em
nameProfile de cada site COBOL; nenhuma versão de wire é alterada.

## Autoridade IBM e limite da inferência

Consultadas em 2026-09-11 as fontes oficiais:

- [Enterprise COBOL 6.4 Language Reference, edição 28 June 2024](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf), CALL statement e PROGRAM-ID/PGMNAME.
- [Making dynamic calls, IBM Enterprise COBOL](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=program-making-dynamic-calls): o vínculo dinâmico depende de DYNAM/NODLL, nomes do objeto/alias e restrições de comprimento/case. A página encontrada pelo índice é 6.3; não é apresentada como uma especificação completa de 6.4.
- [PGMNAME, IBM Enterprise COBOL 6.4](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=options-pgmname), referência de opções; acesso direto do portal pode retornar 403.

Essas autoridades distinguem o valor usado na chamada, o nome externo e o
linkage/runtime. A regex e o trim U+0020 são um subconjunto humano explícito para
esta wave; não se deduz deles que toda implementação COBOL trate nomes do mesmo
modo. O fixture real mantém policy UNSPECIFIED no SP, UnknownName na AIR e
interpretação aberta no resultado, mesmo quando produz o candidato `PROGA`.

# cobol-zos-dynamic-call-minimal@1

Esta é a política mínima autorizada explicitamente pelo usuário para W1D. Não é
resolução geral IBM, prova de existência de programa ou inferência de opções do
compilador. A implementação fica em `CallNameInterpreter`, fora de CFG, values,
lattice e solver. Ver [contrato do produto](../architecture/analysis-dependency-result-v1.md).

Para computed `program/cobol.program`, remover somente U+0020 consecutivos no fim
do raw. Aceitar o resultado somente se corresponder integralmente a
`[A-Z_][A-Z0-9_@#$]{0,7}`. Literal não recebe trim. Preservar raw e suportes em
todos os casos. `UnknownName` permite o candidato sob essa hipótese explícita,
mas mantém `interpretationUnknownRemainder=true`; `ExtensionName` não conhecida
produz somente raw e interpretação aberta.

| Raw | Forma | Candidato | Interpretação |
| --- | --- | --- | --- |
| `PROGA   ` | computed | `PROGA` | Aberta se NamePolicy desconhecida |
| `PROGA` | literal | `PROGA` | Aberta se NamePolicy desconhecida |
| `PROGA   ` | literal | nenhum | Aberta |
| `proga`, ` PROGA`, `PROG-A`, `1PROG`, `ABCDEFGHI` | qualquer | nenhum | Aberta |
| vazio, só espaços, tab final, NBSP final | computed | nenhum | Aberta |

Não implementar uppercase conversion, truncation, hyphen/leading-digit
translation, PGMNAME variants, DLL rules ou nested lookup. Valores fora da
política não são descartados do produto raw. Não há confidence score, tentativa
de localizar callee ou comparação com corpus.

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

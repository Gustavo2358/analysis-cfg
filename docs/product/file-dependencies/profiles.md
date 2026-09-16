# Perfis finitos e autoridade

Decisão humana da revisão H4 em 2026-09-16: **N + C = CORE REQUIRED;
D = EXTENDED PROFILE posterior**, sem bloquear o DoD do core em W11.
Nenhum perfil está implementado/qualificado por esta documentação. A seleção não
afirma qual compilador o usuário executa. Mudança de escopo requer motivo/impacto
explícitos, sem apagar linhas pendentes. [Matriz](coverage.md) separa obrigação de
entrega, perfil semântico e evidência; W10 permanece TODO, aguardando autorização
posterior da extensão. W7 continua obrigatório para nomes computados do core CICS.

## N — nativo obrigatório

**IBM Enterprise COBOL for z/OS 6.4**, Language Reference **SC27-8713-03**.
N-LR: `revision/update = 2026-04-28`; `accessed = 2026-09-16`.
A [biblioteca oficial](https://www.ibm.com/support/pages/enterprise-cobol-zos-documentation-library)
identifica essa revisão e informa que o sufixo pode permanecer igual entre
atualizações: publication number + data, não apenas SC27-8713-03.
[PDF oficial corrente](https://www.ibm.com/docs/en/SS6SG3_6.4.0/pdf/lrmvs.pdf).
O download retornou HTTP 403 nesta revisão: **sha256 indisponível**, sem atribuir
hash de outro PDF à revisão 2026. A referência H1 a 28/06/2024 foi substituída;
não reusar sua paginação como se fosse da revisão atual. Biblioteca registra HTML
6.4 atualizado em 2026-06-30; essa data não substitui a revisão do PDF.
Escolha coerente com
`StorageLayoutSemantics.PROFILE_ID=ibm-enterprise-6.4-fixed-display-1047@1`.
Não adotar regras IBM i/Linux/Micro Focus só porque o parser aceita sua sintaxe.

Entradas para consulta dirigida: capítulos de Input-Output, File section e
Procedure division; procurar o nome da cláusula/statement da matriz. Em W0, ler
SELECT/ASSIGN, FD/SD, qualificação e GLOBAL/EXTERNAL. O
[ASSIGN 6.4](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=section-assign-clause)
e [Allocating files](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=files-allocating)
confirmam o external name derivado de assignment-name. O runtime pode utilizá-lo
como ddname ou nome de variável de ambiente; esta escolha externa não é inferível
do fonte nem pertence ao contrato. Não tratar o nome como data item COBOL.
Conservar grafia/interpretação; prefixos dependem da forma, sem split universal.

N não exige resolver configuração de execução. Organização/modo/keys/status são
fatos do fonte. Opções como VLR, THREAD/RECURSIVE e modalidades de área entram
como premissas explícitas somente se afetarem a regra implementada; ausentes,
não justificar MUST/status exato. W3/W4/W6 registram a tabela de efeitos/controle
com seção da autoridade antes do código. H4 não aprovou tabelas ainda inexistentes.
RERUN sort/merge sem EVERY: confirmar seção RERUN da revisão fixada em W6;
a página 155 citada em H1 pertencia ao PDF histórico. W3/W4/W6 devem registrar
seção/revisão consultada e hash se houver download, antes de fechar suas regras.

## C — CICS obrigatório no core

**CICS TS for z/OS 5.6**, catálogo C-FC da
[referência oficial de comandos](https://www.ibm.com/docs/en/cics-ts/5.6.0?topic=development-cics-command-summary),
acessado em H1. W8 inclui READ/WRITE/REWRITE/DELETE, STARTBR/READNEXT/READPREV/
RESETBR/ENDBR/UNLOCK, mais INQUIRE FILE/SET FILE da referência SPI 5.6.
Não transferir opções exclusivas de 6.x/TX para este perfil.
FILE, SYSID, buffers, responses e handlers têm papéis próprios. Nem SELECT/FD
nem catálogo CICS externo são pré-requisitos para publicar o nome FILE.
Aliases sintáticos DATASET não criam identidade DSNAME.

## D — extensão posterior, não bloqueia o core

**GnuCOBOL 3.2**, subset D-GC32, sem incorporar runtime/compilador.
Autoridade: [índice oficial dos guias](https://gnucobol.sourceforge.io/guides.html)
e [Programmer’s Guide](https://gnucobol.sourceforge.io/HTML/gnucobpg.html),
seções SELECT, FILE SECTION, Report Writer e Built-in subroutines.
O HTML é móvel; W10 deve registrar a revisão da seção 3.2 efetivamente usada
antes de fechar regras. Recurso exclusivo de versão posterior não entra por inferência.

Subset finito: ASSIGN literal/DYNAMIC/EXTERNAL, LINE SEQUENTIAL, READ PREVIOUS,
lock/SHARING/UNLOCK, COMMIT/ROLLBACK, Report Writer (REPORT/RD/INITIATE/GENERATE/
TERMINATE) e streams/dispositivos nomeados no fonte. Sem resolver ambiente.
Operações sem alvo expresso só se relacionam a conectores se o contrato e estado
intraprograma provarem essa relação; não criar um filename para COMMIT.

Catálogo API D-v1: `CBL_OPEN_FILE`, `CBL_CREATE_FILE`, `CBL_CLOSE_FILE`,
`CBL_READ_FILE`, `CBL_WRITE_FILE`, `CBL_DELETE_FILE`, `CBL_RENAME_FILE`,
`CBL_COPY_FILE`, `CBL_CHECK_FILE_EXIST`, `CBL_FLUSH_FILE`.
W10 materializa para cada entrada assinatura, posições/papéis, direção de buffer,
handle, efeitos/outcomes e referência exata. O guia diferencia FLUSH de Micro
Focus: em GnuCOBOL é stub de compatibilidade; testá-lo como negativo de I/O
efetivo, preservando CALL. Não generalizar contrato entre runtimes.

D07 wrappers proprietários: **OUT_OF_PROFILE**, nenhum contrato fornecido.
Nomes sugestivos de API não autorizam classificação. Outras bibliotecas, funções
POSIX, Micro Focus e Veryant não estão no catálogo inicial. O subset D continua
planejado integralmente para a extensão, sem ser requisito de W0–W9/W11.
N04/N18 mantêm IDs históricos, mas pertencem a D; não são pendências do core N.
Quando W10 for autorizada, reduzir seu catálogo exige revisão explícita.

## Autoridade ainda a fechar por implementação

W0 tem autoridade suficiente para começar o contrato nominal. W1 fecha associação
AIR e versão de resultado; W3 confirma bytes/outcomes/ordem de INTO/FROM; W4 USE;
W6 discrimina cláusulas documentais/efetivas; W7 consulta de FILE computado CICS;
W8 operandos SPI/conditions 5.6; W10 DYNAMIC/captura e assinaturas 3.2.
São pré-condições locais da wave, com
parada se não houver prova, e não dependência de releitura do discovery.

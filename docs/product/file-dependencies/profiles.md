# Perfis finitos e autoridade

Seleção de planejamento para revisão H4; não afirma qual compilador o usuário
executa. Nenhum perfil está implementado/qualificado por esta documentação.
Mudar esta seleção após H4 exige registrar motivo e impacto na matriz, sem apagar
linhas pendentes. [Matriz](coverage.md) distingue perfil, implementação e evidência.

## N — nativo obrigatório

**IBM Enterprise COBOL for z/OS 6.4**, Language Reference **SC27-8713-03,
4ª edição, atualização 28/06/2024**. O [PDF oficial](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf)
foi acessado em H1; é a autoridade N-LR. Escolha coerente com
`StorageLayoutSemantics.PROFILE_ID=ibm-enterprise-6.4-fixed-display-1047@1`.
Não adotar regras IBM i/Linux/Micro Focus só porque o parser aceita sua sintaxe.

Entradas para consulta dirigida: capítulos de Input-Output, File section e
Procedure division; procurar o nome da cláusula/statement da matriz. Em W0, ler
SELECT/ASSIGN, FD/SD, qualificação e GLOBAL/EXTERNAL. O
[ASSIGN 6.4](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=section-assign-clause)
distingue assignment-name de data item; conservar grafia e interpretação do
perfil. Prefixos dependem da forma; não aplicar split por hífen universal.

N não exige resolver configuração de execução. Organização/modo/keys/status são
fatos do fonte. Opções como VLR, THREAD/RECURSIVE e modalidades de área entram
como premissas explícitas somente se afetarem a regra implementada; ausentes,
não justificar MUST/status exato. W3/W4/W6 registram a tabela de efeitos/controle
com seção da autoridade antes do código. H4 não aprovou tabelas ainda inexistentes.
RERUN sort/merge sem EVERY aparece na p. 155 impressa do N-LR: gap real do parser.

## C — CICS confirmado no plano

**CICS TS for z/OS 5.6**, catálogo C-FC da
[referência oficial de comandos](https://www.ibm.com/docs/en/cics-ts/5.6.0?topic=development-cics-command-summary),
acessado em H1. W8 inclui READ/WRITE/REWRITE/DELETE, STARTBR/READNEXT/READPREV/
RESETBR/ENDBR/UNLOCK, mais INQUIRE FILE/SET FILE da referência SPI 5.6.
Não transferir opções exclusivas de 6.x/TX para este perfil.
FILE, SYSID, buffers, responses e handlers têm papéis próprios. Nem SELECT/FD
nem catálogo CICS externo são pré-requisitos para publicar o nome FILE.
Aliases sintáticos DATASET não criam identidade DSNAME.

## D — extensões explicitamente selecionadas

**GnuCOBOL 3.2**, subset D-GC32, sem incorporar runtime/compilador.
Autoridade: [índice oficial dos guias](https://gnucobol.sourceforge.io/guides.html)
e [Programmer’s Guide](https://gnucobol.sourceforge.io/HTML/gnucobpg.html),
seções SELECT, FILE SECTION, Report Writer e Built-in subroutines.
O HTML é móvel; W7/W10 devem registrar a revisão da seção 3.2 efetivamente usada
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
POSIX, Micro Focus e Veryant não estão no catálogo inicial. D continua obrigatório
no subset selecionado; reduzir catálogo exige revisão explícita, não sumiço de caso.

## Autoridade ainda a fechar por implementação

W0 tem autoridade suficiente para começar o contrato nominal. W1 fecha associação
AIR e versão de resultado; W3 confirma bytes/outcomes/ordem de INTO/FROM; W4 USE;
W6 discrimina cláusulas documentais/efetivas; W7 revisão de DYNAMIC; W8 operandos
SPI/conditions 5.6; W10 assinaturas 3.2. São pré-condições locais da wave, com
parada se não houver prova, e não dependência de releitura do discovery.

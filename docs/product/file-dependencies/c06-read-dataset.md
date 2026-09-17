# C06 — compatibilidade humana READ DATASET

Decisão humana explícita recebida em 2026-09-17 nesta campanha, após o handoff
W11 BLOCKED (CFG e0e7559 / E2E f4fc757). Identificador: **C06-HUMAN-20260917**.

No perfil CICS TS for z/OS5.6, `EXEC CICS READ DATASET(x)` é forma legada
semanticamente equivalente a `EXEC CICS READ FILE(x)`. Canonicalizar somente
essa combinação comando/opção e manter grafia original, operand e provenance.
Os aliases já autorizados individualmente (SET DATASET/OBJECTNAME) permanecem.
Nenhum outro comando recebe DATASET→FILE por generalização.

Fundamento documental fornecido pela decisão humana: READ File Control usa FILE
na5.6; SET FILE5.6 documenta DATASET legado; documentação oficial5.5 contém READ
DATASET; notas5.6 indicam continuidade compiler/translator; documentação IBM
posterior conserva a forma. Esta é uma decisão de compatibilidade do perfil,
não uma alegação nova de que o PDF API5.6 pinado contém literalmente esse alias.
A decisão resolve a insuficiência documental registrada no checkpoint anterior.

O target é CICS FILE, namespace `cics.file`, com literal ou consulta computed
BEFORE já existente. DATASET não é DSNAME, dataset físico, JCL, allocation nem
permissão de lookup. Não adicionar dimensão externa ou diminuir confiança pela
sua ausência. Efeitos, resposta, controle, nome e remainders seguem READ FILE.

## Oracle e frontier antes da implementação

Positivos: literal e host qualificado/refmod; target canônico FILE, papel READ,
spelling DATASET/offsets conservados; produto tipado e comparação com READ FILE.
Negativos: comandos não autorizados, FILE+DATASET duplicados, operand ausente ou
truncado, INTO/SET em conflito, queues/journals/Program Control separados.
E2E real: três sites COACTVWC nas linhas727/776/826, mesmos bindings e estados que
READ FILE; nenhuma identidade física. CALL/disjunção de buffers e Program Control
comparados com oráculos independentes e baseline, sem expected derivado do output.

Classe C2 com fronteira C3: frontend F-CICS e FAST; B-SP lower com novos bytes;
E-SELECTED CICS/aliases e corpus afetado. Contrato/SP/AIR/wire/solver inalterados.
Q anterior, native/scope/escala/mutantes não relacionados são REUSED somente com
prova de delta restrito; não repetir full/corpus inteiro por mudança localizada.
Ampliar gates se surgir regressão fora dessa frontier. W8/W11 dependem dos
novos oráculos; W10 continua TODO/NOT_AUTHORIZED, sem merge/release.

## Resultado inicial / impacto observado

Frontend F-CICS22/FAST355 PASS. RED bilateral lower confirmou a recusa de
READ/DATASET pela regra typed aliases command scoped; extensão mínima dessa
regra, mantendo READNEXT adversarial recusado, necessária além do frontend.
Sem alteração do tradutor de efeitos, solver, AIR ou wire.

Inventário para seleção de testes encontrou35 READ DATASET em19 fontes (inclui
ZIP), todos dentro da forma autorizada. Reexecutar os19 e controles próximos.
Há52 ocorrências DATASET em outros comandos (REWRITE4/STARTBR12/READPREV12/
ENDBR8/WRITE6/READNEXT8/DELETE2), contadas para observação, não como suporte: ficam
NOT_QUALIFIED nesta retomada conforme limite humano. Não promovidas, não movidas
para D, não usadas para ampliar automaticamente o alias ou inferir DSNAME.

Primeira barreira: literal passou; o oracle computed exigia modelo fechado em
fixture com CALL local aberto. Isso contradiz a lei já qualificada em W9: CALL
local conserva candidate+model remainder. Expected corrigido por essa regra,
sem mudança produtiva; contraprova FILE canônico idêntico entra na comparação
metamórfica. Tentativa falha preservada, não contada como PASS.

## Resultado final

**C06 PASS; W8/W11 QUALIFIED_LOCAL.** F-CICS22/FAST355 e lower FAST2340+
adapters35 PASS; RED anterior reproduzido em ambas as fronteiras.41 fixtures
CICS×2 determinísticas; pares literal/computed FILE↔DATASET preservam semântica
FILE e vetores CALL.21 programas do corpus (19 afetados+2 controles) reexecutados:
35 sites recuperados;41 vetores CALL inalterados e dois controles com quatro
produtos byte a byte iguais. COACTVWC727/776/826 publica três CICS FILE computed
com bindings e provenance corretos, ainda com remainder unknown honesto.

[W11](w11-qualification.md) registra pins/gates novos e REUSED. Os52 aliases de
outros comandos continuam NOT_QUALIFIED. Nenhuma alteração AIR/wire/solver nem
lookup externo. Handoff bruto em `artefatos-e2e/file-dependencies-20260916/w11-c06/`.
STOP para revisão humana; W10 não iniciado, nenhum merge/auto-merge/release.

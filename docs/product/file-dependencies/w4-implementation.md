# FD-W4 — handlers, status e USE

QUALIFIED_LOCAL; ver [estado](state.md). CORE N+C,
COBOL_SOURCE_ONLY/SOURCE-CLOSED/INTRAPROGRAM. W10 não autorizado; nenhum merge.

## Regra e contrato

[D-EFFECT](d-effect.md) fixa SC27-8713-03 atualizado em 2026-04-28, seções de
DECLARATIVES/FILE STATUS/READ/INVALID KEY/WRITE EOP/USE. SP2.25/fileInventory1.4
publica corpos declarativos, entry/completions, rotas por evento e FILE_HANDLER.
USE não é statement executável nem prefixo da entrada primária. Handler explícito
END/INVALID KEY domina USE; seleção por arquivo precede seleção por modo. OPEN
fornece seu modo; nos demais sites o modo corrente sem prova conserva alternativas
mais remainder. Binding incompleto não apaga corpos plausíveis. DEBUGGING não é
USE de erro; permanece inventariado com gap.

Efeitos W3 precedem dispatch: status antes do handler/USE, INTO apenas em READ
bem-sucedido e após o status. Outro erro não vira sucesso nem status00. READ sem
USE aplicável admite NOT AT END em OTHER_ERROR, sem INTO. EOP é WRITE executado,
não EOF. IF/EVALUATE, período/END-*, I/O aninhado e saídas explícitas conservam
statements e associações, sem linearizar clauses alternativas.

Lower admite o shape fechado nas portas JSON e memória. Verifica ownership,
seleção/precedência, continuidade estrutural, evento/efeito, handlers, erro crítico
e corpo/entrada/completions. Redução para AIR existente: branches, efeitos/jumps e
corpos compartilhados. O retorno de USE tem bound somente dos resumes efetivos;
memória/dependências não são aplicadas de novo na entrada/retorno. Retornos de
vários sites são unidos com LOCAL_RETURN_CONTEXT_NOT_PROVEN; não se alega matching
de pilha. Recursão não clona ocorrências nem impõe limite. Erros críticos conservam
saídas possíveis. Norma/modelo/codec/produção CFG não precisaram mudar.

## Limites explícitos

- Modo corrente depois de OPEN e matching entre várias ativações USE continuam
  parciais intraprograma. Nomes estáticos de arquivo não perdem seu conhecimento.
- Seleção GLOBAL ancestral/captures pertence à integração W9. LINAGE completo W6.
- Status não provado é unknown; a autoridade não autoriza inferir00 de Normal AIR.
- Corpos complexos mantêm as abstrações de controle já publicadas pelo frontend;
  sintaxe observada não é prova de execução precisa.
- Custo de seleção O(usos × USE visíveis + rotas); retorno O(resumes publicados).
  O bound W3 com todas as entradas do fonte fica somente nos inputs históricos.

## Pins e gates concluídos

Frontend `1c21f21750aa3572e39fce71ccc156a357699d81`; lower
`9f80a1ff4f25f23d8ff2088a638f5e0f4dcf1ffc`. AIR/IR inalterados nos pins W1.

| Estado | Gate / comando | Propriedade |
| --- | --- | --- |
| PASS | frontend focal162, GO TO7 e entrada20 | eventos/seleção/estrutura/CALL e regressão |
| PASS | frontend `python3 -B scripts/harness/lean.py fast` (fast-3.log),336 | FAST fixo |
| PASS | frontend `python3 -B scripts/harness/lean.py qualification-local` (qualification-local-1.log),898/1 skip histórico | Q-SHARED, regressão/naming |
| PASS | lower compile + `FileControlSuite`, seis SPs reais e mutantes | B-SP, wire+memory, codec e compartilhamento |
| PASS | lower `lean.py fast` (fast-1.log),2340 core + adapters | FAST fixo CALL/FILE/storage/control |
| PASS | lower `lean.py qualification-local` (qualification-local-1.log),semântica244296/performance39215 | Q-SHARED; contadores de teste, sem precisão/recall |
| PASS | CFG C-VALUES17; FileIoOutcomeOracleTest5 + FileUseControlOracleTest2 | O1–O5, status antes de USE, retorno sem kill global, CALL condicional |
| PASS | CFG `python3 -B scripts/harness/lean.py fast`; reader11 | FAST e contratos atuais |
| PASS | prepare_w2d_producers.py, diretório fd-w4/producers | clones limpos, reconstrução nos pins exatos |
| PASS | `python3 -B scripts/project/e2e_file_control.py --work .harness-results/fd-w4/e2e-1 --producers .harness-results/fd-w4/producers/producers.json` | 14 nativos +6 memória +8 controle ×2, SP/AIR/CFG/dependency determinísticos |
| REUSED | AIR/IR e produção CFG inalterados | novas operações não foram necessárias |
| NOT_RUN | Q-SHARED CFG e corpus amplo | sem delta produtivo CFG; corpus/escala final W11 |

Logs/tentativas/outputs brutos ficam em `.harness-results/fd-w4` e no handoff
local E2E. Os REDs e falhas mecânicas não são ocultados. Nenhum blocker material.

Observação de wire para20 fontes byte a byte iguais: 3754405 bytes AIR em W3 e 3276279 em W4 (somas da coorte, sem SLA). Detalhe bruto em observed-wire-size.json.

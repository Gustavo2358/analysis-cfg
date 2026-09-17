# FD-W5 — SORT/MERGE e SD

QUALIFIED_LOCAL; entrada W0/W3/W4 qualificada. W10 não autorizado.

## Regra / oráculo antes da produção

Autoridade LANGUAGE_GUARANTEED: IBM Enterprise COBOL6.4 SC27-8713-03,
update 2026-04-28; mesmo PDF/hash de W0. Lidos MERGE pp400–404, RELEASE
pp434–435, RETURN pp435–437 e SORT pp452–459. USING/GIVING fornecem todos
os FD participantes e executam abertura/leitura ou escrita/fechamento implícitos,
com USE em erro. SD é área local, sem nome externo. INPUT precede ordenação,
OUTPUT a sucede; ranges de parágrafos/seções retornam ao ponto da fase seguinte,
como PERFORM. MERGE requer pelo menos dois inputs, não admite INPUT PROCEDURE
nem repetição de arquivo. SORT de tabela não é FILE.

RELEASE FROM equivale a MOVE antes de RELEASE; fonte disjunta permanece. O SD
deixa de ter conteúdo disponível, salvo SAME RECORD AREA. RETURN altera a área
SD e, somente em sucesso, copia INTO depois da obtenção do registro; END executa
AT END, sucesso NOT AT END. Nem RELEASE nem RETURN nativo é CALL externo.

Algoritmo planejado: AST tipada conserva participantes/papéis e endpoints locais;
resolução canônica fornece IDs; análise publica fases/ranges/completions. O
projector não resolve nomes. Lower traduz fatos para controle/efeitos AIR gerais,
com corpos compartilhados e retorno delimitado quando contexto não é provado.
Participantes da mesma fase conservam inventário e origem, sem produto cartesiano
ou ordem total de execução afirmada. Não há solver novo ou limite de ocorrências.
Custos proporcionais ao inventário, relações de ranges e alternativas emitidas;
qualquer aproximação deve ter gap próprio, sem apagar alvos conhecidos.

Oráculos manuais: T23/A6 SORT S USING A B GIVING C => input A/B, output C,
work S; T24 input/output/range com CALL preservado uma vez; T25 MERGE dois inputs
e output, negativo duplicado/INPUT; T26 FROM de outro FD só usa SD, transferência
anterior e invalidação regional; T27 RETURN END sem INTO, sucesso com INTO;
T28 SORT tabela com/sem KEY => zero FILE. Adversariais incluem kind FD↔SD,
participante removido no wire, referências/ranges fora da unidade, USE implícito,
mesmo FD em papéis diferentes de SORT e coexistência PERFORM/CALL.

Representação SD confirmada contra AIR15 no pin W1: `LocalResource` é apenas
declarativo (I-RB-03), inclusive quando o tipo Java cabe em ResourceDescription.
Não será usado como target executável nem como nome calculado desconhecido.
As operações locais existentes (opaque com memória/controle delimitados) recebem
vínculos `ResourceDeclaration.uses` por papel work/release/return. O consumer
FILE materializa esses usos locais a partir dos vínculos, sem interpretar
observedKind, provenance ou IDs. Não há interação externa, candidato ou remainder
de nome externo para SD; os alvos USING/GIVING continuam invokes comuns.
Não é necessária alteração normativa, de modelo ou codec AIR.

Fases USING/GIVING têm seleção conservadora dos participantes/iterações, com
gap de ordem/contagem, sem impor ordem total ou gerar pares entrada×saída.
As fronteiras de procedimentos compartilham corpos e unem somente os resumes
publicados e a continuação ordinária; contexto de retorno permanece explícito.


Consumer seleciona invocações FILE e operações referenciadas por recursos locais
no índice AIR, por kind concreto. Não altera interpretação CFG ou provider de CALL.
Custo O(recursos + vínculos + invocações + ocorrências selecionadas por entrada),
mais ordenação determinística da saída. Sem reinterpretar observedKind ou IDs.
Wire2.1 é aditivo e fechado; detalhes no checkpoint D-WIRE dos contratos.


E-SELECTED1: 13 casos×2 PASS; RETURN buffer negativo foi rejeitado por fonte de
teste sem AT END obrigatório (IBM RETURN p436). Evidência preservada, fixture
corrigida. A mesma conferência revelou um OUTPUT PROCEDURE vazio usado como
positivo de range: p436 exige pelo menos um RETURN. O positivo passa a ter RETURN;
novo oracle exige gap para OUTPUT vazio. INPUT vazio é permitido (SORT pp457–458).
Disponibilidade KNOWN de endpoints/range nunca certifica todas as restrições
transitivas: CALL/GO TO/PERFORM/declarativos continuam com os limites de efeitos e
controle publicados. Sem análise interprocedural universal nesta wave.


## Checkpoint / evidência

Pins finais: frontend9d4de9b252cc67dfdce1def568ee9915318bec16;
lower e50b5e19231e4183bc0c2184a2e14f7bdca65ae1; AIR/IR W1 inalterados.
Logs `.harness-results/fd-w5/` e arquivo local `artefatos-e2e/.../w5/HANDOFF.md`.

- PASS: frontend FileSortContractTest13 e família156, FAST336. Q-SHARED910 em
 8fd8faad, um skip histórico previsto, REUSED para semântica compartilhada;
 restrição OUTPUT vazio e fixture válida cobertas por focal13/FAST final.
- PASS: lower FileSortSuite12 exports reais iguais ao produtor,9 negativos wire,
 3 memory, grafo de fases, codec/determinismo. FAST2340+adapters e Q-SHARED
 semântica244296/performance39215 em88572c01; produção idêntica no pin final,
 fixture/pin/documentação revalidados por focal/B-SP/E-SELECTED (REUSED gates).
- PASS: CFG oráculos AIR locais2 e famílias CALL/FILE, FAST487, reader12.
 Inventário compilado alterou apenas dependências java.util.Set; gate de
 fronteiras continua fechado. Nenhuma alteração em solver/provider/CFG/AIR/IR.
- PASS: prepare_w2d_producers.py em producers-final; e2e_file_sort.py em e2e-final:
 42 fontes×2. Suplemento extra-phases: MERGE OUTPUT PROCEDURE e SORT com múltiplos
 GIVING,2 fontes×2 nos mesmos pins. Total44; SP/AIR/CFG/dependency determinísticos.
 RELEASE mantém suporte disjunto; sucesso RETURN INTO não mantém singleton velho;
 handlers/USE condicionais, CALL/PERFORM e T28 preservados.
- NOT_RUN: qualification-local CFG e corpus/escala ampla, reservados W11;
 mudança aqui é seleção/projeção do consumer, sem algoritmo geral invalidado.
- BLOCKED: nenhum. Próxima W6; sem merge/auto-merge/release.

# Backlog por slices

**Não é autorização de implementação.** Metadados/dependências em [backlog.json](backlog.json); cada item tem critério de aceitação próprio. Fontes semânticas fixadas e regras de arquitetura prevalecem sobre exemplos da conversa.

## Caminho executável

001 discovery concluído → 002 Java 21/Maven + `air-java` → 003 porta/seam →
005 **CFG-FIRST** em memória. Esse primeiro produto é Entry → Sequence(`Return`) →
normal exit e não depende de branch, codec ou CLI.

Depois, 022 acrescenta linear/jump/halt e 006 completa **MVP-CFG-01** com
branch/IF-ELSE. 004 cria transporte quando o binding upstream existir; 008 prova
arquivo/memória e CLI posteriormente. `invoke`/`raise` entram em 007. Nenhum desses
subsets equivale a AIR-STRUCTURE completo.

## Ampliação

007, 009 e 010 ampliam/completam capabilities estruturais → 011 qualifica perfil.
012 endurece escala. 013 é discovery de controle local, seguido por 014/015. 016 é
indireção limitada. 023 é o micro-E2E condicionado ao lowerer externo; 017 preserva
a aceitação bilateral madura e 018 integra módulos sem mudar a porta. 019/020 ficam
adiados. 021 leva gates a CI e não bloqueia desenho semântico.

| Item | Entrega | Dependências | Estado |
| --- | --- | --- | --- |
| [BACKLOG-CFG-001](backlog/BACKLOG-CFG-001.md) | Discovery de fronteiras e plano executivo | — | completed |
| [BACKLOG-CFG-002](backlog/BACKLOG-CFG-002.md) | Bootstrap Java 21/Maven e boundary air-java | BACKLOG-CFG-001 | active |
| [BACKLOG-CFG-003](backlog/BACKLOG-CFG-003.md) | Fronteiras Clean e seam semântico de extensões | BACKLOG-CFG-002 | planned |
| [BACKLOG-CFG-004](backlog/BACKLOG-CFG-004.md) | Fixtures AIR JSON e adapter de transporte | BACKLOG-CFG-002, BACKLOG-CFG-003 | planned |
| [BACKLOG-CFG-005](backlog/BACKLOG-CFG-005.md) | CFG-FIRST: Entry, Return e normal exit | BACKLOG-CFG-002, BACKLOG-CFG-003 | planned |
| [BACKLOG-CFG-006](backlog/BACKLOG-CFG-006.md) | Bifurcação e IF/ELSE estrutural | BACKLOG-CFG-022 | planned |
| [BACKLOG-CFG-007](backlog/BACKLOG-CFG-007.md) | Invoke, raise e resultados de controle delimitados | BACKLOG-CFG-006 | planned |
| [BACKLOG-CFG-008](backlog/BACKLOG-CFG-008.md) | CLI e prova posterior arquivo/memória | BACKLOG-CFG-004, BACKLOG-CFG-006 | planned |
| [BACKLOG-CFG-009](backlog/BACKLOG-CFG-009.md) | Dispatch, ciclos e múltiplas entradas | BACKLOG-CFG-006 | planned |
| [BACKLOG-CFG-010](backlog/BACKLOG-CFG-010.md) | Envelopes abertos e compatibilidade de extensões | BACKLOG-CFG-007, BACKLOG-CFG-009 | planned |
| [BACKLOG-CFG-011](backlog/BACKLOG-CFG-011.md) | Qualificação AIR-STRUCTURE@2 e hardening de regressão | BACKLOG-CFG-007, BACKLOG-CFG-009, BACKLOG-CFG-010 | planned |
| [BACKLOG-CFG-012](backlog/BACKLOG-CFG-012.md) | Escala, performance e gates de qualidade | BACKLOG-CFG-008 | planned |
| [BACKLOG-CFG-013](backlog/BACKLOG-CFG-013.md) | Discovery de representação e matching local | BACKLOG-CFG-009, BACKLOG-CFG-010 | planned |
| [BACKLOG-CFG-014](backlog/BACKLOG-CFG-014.md) | Semântica local: frames e portas de conclusão | BACKLOG-CFG-011, BACKLOG-CFG-013 | planned |
| [BACKLOG-CFG-015](backlog/BACKLOG-CFG-015.md) | Consultas pareadas e conformidade AIR-LOCAL-CONTROL@2 | BACKLOG-CFG-014, BACKLOG-CFG-012 | planned |
| [BACKLOG-CFG-016](backlog/BACKLOG-CFG-016.md) | Controle indireto e AIR-INDIRECT-CONTROL@2 | BACKLOG-CFG-011 | planned |
| [BACKLOG-CFG-017](backlog/BACKLOG-CFG-017.md) | Aceitação bilateral com CobolLower | BACKLOG-CFG-008 | planned |
| [BACKLOG-CFG-018](backlog/BACKLOG-CFG-018.md) | Integração em monólito modular Maven | BACKLOG-CFG-023 | planned |
| [BACKLOG-CFG-019](backlog/BACKLOG-CFG-019.md) | Evolução de contrato e otimizações opcionais | BACKLOG-CFG-011 | deferred |
| [BACKLOG-CFG-020](backlog/BACKLOG-CFG-020.md) | Análises posteriores — fronteira reservada | BACKLOG-CFG-011 | deferred |
| [BACKLOG-CFG-021](backlog/BACKLOG-CFG-021.md) | CI dos gates e higiene do harness | BACKLOG-CFG-001 | planned |
| [BACKLOG-CFG-022](backlog/BACKLOG-CFG-022.md) | Fluxo linear, jump e halt | BACKLOG-CFG-005 | planned |
| [BACKLOG-CFG-023](backlog/BACKLOG-CFG-023.md) | E2E mínimo Semantic Product → AIR → CFG | BACKLOG-CFG-005 + cobol-lower externo | planned |

**As construções COBOL são motivadores, não unidade de extensão do CFG.** IF→branch; GO TO/NEXT SENTENCE→jump; EVALUATE→dispatch/branches; loops→controle explícito; PERFORM/THRU→capability local quando compatível. O lowerer estabelece essa equivalência. Nenhum item autoriza assumir suporte de dialeto não provado.

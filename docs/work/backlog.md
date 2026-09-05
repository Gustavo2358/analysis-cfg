# Backlog por slices

**Não é autorização de implementação.** Metadados/dependências em [backlog.json](backlog.json); cada item tem critério de aceitação próprio. Fontes semânticas fixadas e regras de arquitetura prevalecem sobre exemplos da conversa.

## Caminho para o MVP

001 discovery → 002 modelo/build → 003 fronteiras/seam → 004 fixtures/codec + 005 linear → 006 branch + 007 invoke → 008 demonstração arquivo/memória. Isso entrega MVP-CFG-01, não AIR-STRUCTURE completo. Testes de domínio nascem em memória mesmo antes do adapter.

## Ampliação

009 e 010 completam capabilities estruturais → 011 qualifica perfil. 012 endurece escala. 013 é discovery de controle local, seguido por 014/015. 016 é indireção limitada. 017/018 integram sem mudar a porta. 019/020 ficam adiados. 021 leva gates a CI e não bloqueia desenho semântico.

| Item | Entrega | Dependências | Estado |
| --- | --- | --- | --- |
| [BACKLOG-CFG-001](backlog/BACKLOG-CFG-001.md) | Discovery de fronteiras e plano executivo | — | ready_for_authorization |
| [BACKLOG-CFG-002](backlog/BACKLOG-CFG-002.md) | Bootstrap Java/Maven e modelo IR mínimo compartilhável | BACKLOG-CFG-001 | planned |
| [BACKLOG-CFG-003](backlog/BACKLOG-CFG-003.md) | Fronteiras Clean e seam semântico de extensões | BACKLOG-CFG-002 | planned |
| [BACKLOG-CFG-004](backlog/BACKLOG-CFG-004.md) | Fixtures independentes e adapter de transporte | BACKLOG-CFG-002, BACKLOG-CFG-003 | planned |
| [BACKLOG-CFG-005](backlog/BACKLOG-CFG-005.md) | Núcleo CFG: projeção linear e saídas | BACKLOG-CFG-002, BACKLOG-CFG-003 | planned |
| [BACKLOG-CFG-006](backlog/BACKLOG-CFG-006.md) | Bifurcação e IF/ELSE estrutural | BACKLOG-CFG-005 | planned |
| [BACKLOG-CFG-007](backlog/BACKLOG-CFG-007.md) | Invoke e resultados de controle delimitados | BACKLOG-CFG-005 | planned |
| [BACKLOG-CFG-008](backlog/BACKLOG-CFG-008.md) | CLI mínima e fechamento MVP arquivo/memória | BACKLOG-CFG-004, BACKLOG-CFG-006, BACKLOG-CFG-007 | planned |
| [BACKLOG-CFG-009](backlog/BACKLOG-CFG-009.md) | Dispatch, ciclos e múltiplas entradas | BACKLOG-CFG-008 | planned |
| [BACKLOG-CFG-010](backlog/BACKLOG-CFG-010.md) | Envelopes abertos e compatibilidade de extensões | BACKLOG-CFG-008 | planned |
| [BACKLOG-CFG-011](backlog/BACKLOG-CFG-011.md) | Qualificação AIR-STRUCTURE e hardening de regressão | BACKLOG-CFG-009, BACKLOG-CFG-010 | planned |
| [BACKLOG-CFG-012](backlog/BACKLOG-CFG-012.md) | Escala, performance e gates de qualidade | BACKLOG-CFG-008 | planned |
| [BACKLOG-CFG-013](backlog/BACKLOG-CFG-013.md) | Discovery de representação e matching local | BACKLOG-CFG-009, BACKLOG-CFG-010 | planned |
| [BACKLOG-CFG-014](backlog/BACKLOG-CFG-014.md) | Semântica local: frames e portas de conclusão | BACKLOG-CFG-011, BACKLOG-CFG-013 | planned |
| [BACKLOG-CFG-015](backlog/BACKLOG-CFG-015.md) | Consultas pareadas e conformidade de controle local | BACKLOG-CFG-014, BACKLOG-CFG-012 | planned |
| [BACKLOG-CFG-016](backlog/BACKLOG-CFG-016.md) | Controle indireto com universo fechado | BACKLOG-CFG-011 | planned |
| [BACKLOG-CFG-017](backlog/BACKLOG-CFG-017.md) | Aceitação bilateral com CobolLower | BACKLOG-CFG-008 | planned |
| [BACKLOG-CFG-018](backlog/BACKLOG-CFG-018.md) | Integração em monólito modular Maven | BACKLOG-CFG-008 | planned |
| [BACKLOG-CFG-019](backlog/BACKLOG-CFG-019.md) | Evolução de contrato e otimizações opcionais | BACKLOG-CFG-011 | deferred |
| [BACKLOG-CFG-020](backlog/BACKLOG-CFG-020.md) | Análises posteriores — fronteira reservada | BACKLOG-CFG-011 | deferred |
| [BACKLOG-CFG-021](backlog/BACKLOG-CFG-021.md) | CI dos gates e higiene do harness | BACKLOG-CFG-001 | planned |

**As construções COBOL são motivadores, não unidade de extensão do CFG.** IF→branch; GO TO/NEXT SENTENCE→jump; EVALUATE→dispatch/branches; loops→controle explícito; PERFORM/THRU→capability local quando compatível. O lowerer estabelece essa equivalência. Nenhum item autoriza assumir suporte de dialeto não provado.

# Gates técnicos

A política vigente é [LEAN HARNESS / GIT-IS-THE-RECORD](lean-harness.md).

| Comando | Uso |
| --- | --- |
| `python3 -B scripts/harness/lean.py fast` | Desenvolvimento: política, compile, testes focais e arquitetura |
| `python3 -B scripts/harness/lean.py docs` | Documentação: navegação, política e higiene Git; sem build |
| `python3 -B scripts/harness/lean.py ci` | Push/PR: classifica docs/código e executa apenas FAST |
| `python3 -B scripts/harness/lean.py qualification-local` | Full local sob demanda; PASS / FAIL |
| `python3 -B scripts/harness/test_lean.py` | Contracasos da política A–L |

Não há gate de certificado, receipt, hash chain ou consistência histórica.
Source locks continuam obrigatórios. Teste não executado nunca é PASS.
Mutation/challenges permanecem ferramentas técnicas locais sob demanda.

# Qualification local

[LEAN HARNESS / GIT-IS-THE-RECORD](lean-harness.md) é a política vigente.

Desenvolvimento: `python3 -B scripts/harness/lean.py fast`.
Full local sob demanda: `python3 -B scripts/harness/lean.py qualification-local`.
O comando mantém compile, suites completas, arquitetura, performance e integração
local com produtores fixados. Mutation e challenges específicos permanecem comandos
locais explícitos. O resultado normativo é PASS / FAIL, sem receipt ou tarball exigido.

CI remoto usa o inventário focal existente de `docs/evals/cp6/fast-test-inventory.json`.
Não há qualification remota/manual. Histórico não participa da decisão de executar
e nem do resultado técnico. Logs opcionais ficam nos diretórios ignorados.

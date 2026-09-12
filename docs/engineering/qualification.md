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

CP6 W2D: a Full prepara frontend, lower e AIR nos commits do source lock em
checkouts isolados, executa closed/open duas vezes e valida padding, supports até
os MOVEs, remainders, permutação e bytes determinísticos. Nenhum build ocorre nos
sibling working trees. `W2D_SOURCE_ROOT` pode apontar para clones locais que já
contenham os commits; o SHA e a limpeza são sempre verificados. `W2D_MAVEN_REPO`
seleciona o cache de dependências; os JARs produtivos usados vêm dos builds pinados.

Comandos focais locais, após compilar o reactor e seu runtime classpath:

```bash
python3 -B scripts/project/prepare_w2d_producers.py --work .harness-results/w2d-producers
python3 -B scripts/project/e2e_w2d.py --work .harness-results/w2d-e2e --producers .harness-results/w2d-producers/producers.json
python3 -B scripts/project/challenge_w2d.py --work .harness-results/w2d-challenge
```

Use diretórios novos por execução. O challenge compila dez mutações de implementação
numa cópia descartável e aplica os mesmos oráculos JUnit focais; não modifica
solver/lattice, arquivos produtivos do checkout ou CI. O FAST remoto inclui os
modelos AIR baratos e as regressões W1, sem pipeline real ou mutation.

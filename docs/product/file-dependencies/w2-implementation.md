# W2 — operações nativas qualificadas localmente

SP2.23/fileInventory1.2 acrescenta WRITE, REWRITE, DELETE_RECORD e START ao
slice OPEN/READ/CLOSE; AST tipada preserva opções por arquivo, operandos
RECORD/INTO/FROM/KEY/ADVANCING, relação START, terminador e handlers. Record-name
é resolvido como DATA e associado ao FD pelo ID publicado. FROM de outro FD não
cria leitura desse arquivo. Sem parsing de texto no lower/consumer.

Ações AIR: open/read/write/rewrite/delete-record/start/close. ResourceUse conserva
papéis input/output/update/delete-record/position/lifecycle e modos de OPEN.
Nenhuma mudança em AIR/IR nem no consumer de W1. Decoder SP2.23 fechado e admission
file/memory validam operandos e corpos; SP2.22 preserva estrutura UNAVAILABLE.
Efeitos/outcomes permanecem OPEN até W3/W4, sem MUST por nome do verbo. CALL de
handler é inventariado uma vez e não vira sucessor normal incondicional.

## Pins / evidência

- Frontend `b559292c97e004504fb867c4724298dc1637b6f2`: FileNativeOperationTest,
  12 oráculos novos; F-DECL ampliado147 e FAST335 PASS, zero skips.
- Lower `ee38519283a6b762b86704a6ee198748424b6825`: FileNativeOperationSuite,
  FileStaticSliceSuite, FileDeclarationSuite PASS; FAST core2340 + todas as
  suites adapter/CALL/storage/control PASS. Fixtures native/delete-handlers
  idênticas byte a byte ao produtor. Mutantes wire/memory exercitados.
- AIR `d215d2bafbbabc714a3e9d0f2ff9e8927e0bf8f4` / IR
  `fb153ae50f343022db45d20d627e1afac85de916` REUSED sem alterações de W2.
- CFG: FAST485 obrigatório e arquitetura PASS; C-DEP22 zero skips; reader11 PASS.
- E-SELECTED: coorte13 + EVALUATE1, cada fixture duas vezes via CLIs reais nos
  pins acima. Determinismo SP/AIR/CFG/dependency, nomes/owner/records/origens,
  CALL/FILE, handlers, IF/EVALUATE/PERFORM/GO TO e falha de output PASS.

Comando reproduzível (coorte completa14):
`python3 -B scripts/project/e2e_native_files.py --work <novo-dir> --producers <producers.json>`.
Execução registrada usou e2e-1 (13) e e2e-evaluate (1), sem repetir casos aprovados
sem necessidade. Logs/comandos/exits/hashes/outputs e tentativas RED/falhas estão
em `artefatos-e2e/file-dependencies-20260916/w2/HANDOFF.md`.

Limites: declaração/estrutura qualificadas não fecham efeitos, status ou caminhos
por outcome. Qualification-local/corpus/performance NOT_RUN em W2, conforme
checkpoints W3/W4/W8/W11. D-EFFECT é a próxima obrigação. W10 não autorizado.

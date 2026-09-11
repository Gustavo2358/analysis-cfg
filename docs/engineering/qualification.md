# Fast CI e qualificação explícita

Decisão de orquestração de WORK-CFG-033 / PR #18. A qualidade exigida para fechar
uma wave permanece; muda quando a bateria pesada é executada. Não há path filtering.
As descrições antigas em [gates](gates.md) registram checkpoints históricos.

| Autoridade | Disparo | Entrada executável | Evidência |
| --- | --- | --- | --- |
| FAST_CI | Cada push/PR; também reprodução local | `scripts/harness/check-pr-fast.sh` | receipt com authority FAST_CI; no GitHub, também receipt do checkout real |
| LOCAL_QUALIFICATION | Execução local explícita, obrigatória para fechamento complexo | `scripts/harness/check-qualification.sh` | receipt do commit/tree limpos e de cada fase |
| REMOTE_MANUAL_QUALIFICATION | Somente workflow_dispatch | `.github/workflows/qualification.yml` chama o mesmo check-qualification.sh | Mesmo plano/gates; authority e contexto GitHub distintos |

## Fast automático

[ci.yml](../../.github/workflows/ci.yml) faz checkout, setup Java 21/Python 3.12,
verifica o pin AIR e chama a entrada Fast. O script exporta/builda a AIR exata em
checkout isolado, registra origem/JARs, verifica scope/manifest, roda docs/harness,
compila o reactor inteiro e executa os **286 métodos fixos** do
[inventário nominal](../evals/cp6/fast-test-inventory.json). Ausência, skips, erros,
métodos estranhos e reports antigos são recusados; Maven clean precede os testes.

A lista inclui todos os testes CFG/kernel e CFG wire/goldens, os cinco testes de
versionamento, unit tests de structure/solver/values/planning/delivery, CLI e
W1D focal. W1dModelTest executa seus três casos contratuais; seu probe N/2N fica
na qualification. Os demais testes de escala e WideResultTest também ficam lá.
O inventário seleciona métodos explicitamente, sem tomar decisões a partir do diff.

O mesmo gate de arquitetura faz todas as verificações de fontes, imports,
classfiles, descriptors, jdeps e DAG Maven em ambos os perfis. `--test-profile fast`
muda somente a seleção Maven; não desliga boundaries, transporte, solver/lattice
ou DefaultValuePlan. O perfil full continua sendo o default do gate arquitetural.

Fast não prepara frontend/lower, não roda performance CP5 W1–W5, vertical COBOL,
repetição da vertical, campanha de mutações ou full histórico. Os testes de
harness falsificam configurações/receipts em temporários; não são uma campanha
de mutações do produto. O Fast remoto só certifica esse conjunto de verificações.

## Full local e manual remoto

Autoridade compartilhada: [run_validation.py](../../scripts/project/run_validation.py).
Não há uma lista paralela de gates pesados no YAML. A lista full local e manual
remota é idêntica e verificada pelo guard de orquestração:

1. Export/build da AIR exata e receipt de fontes/JARs; scope/manifest inicial.
2. Repositório Maven histórico separado, sem colisão de SNAPSHOTs; export/build
   dos produtores históricos W5 nos pins existentes.
3. `check-full.sh`: docs/harness, arquitetura completa, semântica, performance
   W1–W5 e integração CP5 real (inclui full regression, CP3/CP4E/overwrite).
4. Export/build dos produtores W1A/W1C exatos; gate W1D com escala N/2N.
5. Vertical real W1D duas vezes: quatro casos, hashes/determinismo e auditoria
   da origem MOVE. O oracle independente agora também exige o CFG Invoke v2.
6. Campanha W1D de 18 mutantes semânticos + dois de proteção de fonte, restauração
   exata e segundo GREEN; scope/manifest final e comparação dos siblings.

Nenhum gate foi deletado. Os cinco probes que antes também apareciam como passos
avulsos no YAML são coordenados uma vez por `check-full.sh`, cujo gate performance
já executa W1–W5. Os scripts de challenges históricos permanecem disponíveis.
Não é necessário repetir toda a qualification remota após uma qualification local
válida para o mesmo HEAD. O workflow manual não é disparado pelo Fast.

Pré-requisitos locais: JDK 21 em JAVA_HOME/PATH, Maven, Python 3.12+, Git e checkouts
irmãos com os objetos AIR/W1A/W1C/históricos fixados disponíveis. O script lê os
siblings e cria checkouts/archives isolados, sem mudar seus HEADs ou fontes.
Dependências Maven podem exigir rede no primeiro uso. Reutilizar cache Maven não
reutiliza produtos de análise: AIR/produtores são construídos novamente.

```sh
bash scripts/harness/check-pr-fast.sh \
  --work .harness-results/fast-local-01 --maven-repo "$PWD/.cache/validation-m2"
bash scripts/harness/check-qualification.sh \
  --work .harness-results/qualification-local-01 --maven-repo "$PWD/.cache/validation-m2"
```

Cada diretório `--work` deve ser novo. O diretório `build/` contém exports/caches
reproduzíveis; `evidence/` contém o receipt e as evidências preserváveis. A full
exige fonte commitada e limpa antes de qualquer gate. A execução serial protege
reports e mutações; não execute outros builds/edições concorrentes nesse checkout.

## Receipts e fechamento

`<work>/evidence/receipt.json` registra authority, source commit/tree/status, base
CP6 e baseline da remediação, pins, contexto GitHub quando houver, comandos/cwd/env,
exit code, resultado/duração por fase, logs brutos, reports e hashes SHA-256.
Receipts dos produtores, AIR, performance, E2E/determinismo e mutações ficam no
mesmo pacote. Reports são capturados antes do próximo clean. Outputs antigos não
são atribuídos a uma fase nova: somente arquivos gravados pela fase são copiados.
Falha interrompe as próximas fases (`NOT_RUN`) e permanece `FAIL`, com log original.
Falha antes de iniciar processo fica `ERROR`, sem exit code inventado.

A fonte e os siblings são comparados novamente ao término. PASS exige todos os
gates e restauração exata; alteração de HEAD/tree/status torna o receipt inválido.
O GitHub preserva evidence/ com `if: always()`, inclusive em falha. Caches, toolchains
e builds não entram no Git nem no artifact de evidência.

Para evitar circularidade, o receipt da qualification final é produzido **após o
commit e antes do push**, fora da árvore versionada. Seu caminho e hash, juntamente
com HEAD/tree, entram no handoff de entrega e no PR. A evidência versionada registra
o desenvolvimento e aponta esse protocolo; não antecipa PASS do HEAD final. Qualquer
correção de fonte após o commit exige nova qualification do novo HEAD. Fast CI remoto
e qualification local nunca são tratados como a mesma autoridade.

O PR #18 permanece OPEN / DRAFT / AWAITING_HUMAN_REVIEW. W2 permanece
NOT_STARTED / NOT_AUTHORIZED; nenhum gate inicia implementação ou merge.

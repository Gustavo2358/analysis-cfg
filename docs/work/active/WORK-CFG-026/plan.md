# plan

## Fatiamento
1. Reconciliar 025 e baseline; definir contrato, oracle manual e testes RED.
2. Materializar somente adapters/launcher; reader limitado, writer explícito e CLI.
3. Evoluir arquitetura e integration gate, CI, documentação e eval.
4. GREEN, challenges RED, restauração byte a byte, segundo GREEN, revisão integral, commits, push, PR e CI do head.

## Dependências
Baseline limpo b614712fda55fef12639cbe18fd90793faa1fb3b após fetch/pull; branch feat/air-json-cfg-cli. air-java b78f4068d8a479f48eb048b8d76fa60a0997dc4a obtido em cópia isolada /tmp; analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933. Roadmap pai lido como sequência, sem edição; instrução atual fixa CLI em dois posicionais sem policy flag.

## Superfície arquitetural provável
Kernel intacto. cfg-adapters contém AirJsonFileReader, AirInputLimitException, CfgJsonWriter, CfgJsonBytes, CfgJsonException. cfg-launcher contém AnalysisCfg (run/main). Infra CFG apenas escrita explícita UTF-8, sem parser JSON ou dependência externa adicional.

## Migrações requeridas
Reactor e gates evoluem com inventários exatos. Não há migração do modelo/porta/pins. Performance/full seguem indisponíveis.

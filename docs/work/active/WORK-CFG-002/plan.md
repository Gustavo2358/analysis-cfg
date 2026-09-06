# Plano — WORK-CFG-002

## Fatiamento

1. Confirmar e fixar os snapshots `analysis-ir` e `air-java` atuais.
2. Promover o harness à fase de implementação autorizada.
3. Escrever o expected negativo do gate arquitetural antes da configuração final.
4. Criar parent Maven e módulo mínimo `cfg-kernel`, sem adapters.
5. Implementar somente o preflight delegando ao `AirValidator`.
6. Exercitar publicação válida, inválida, incompleta e obrigação semântica.
7. Provar RED com dependência proibida temporária, reverter e provar GREEN.
8. Verificar checkout limpo, dependency tree, bytecode, testes e gates; arquivar o item.

## Dependências

`analysis-ir` `122ce54e1b9ef9b00646f93ece409ca8b63bc933` e `air-java`
`6a4091e5394fc22b3d2ada9abbdb530eb3572a58`, confirmados em `main` remoto.
O CI instala `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT` a partir desse SHA
antes de construir o consumer.

## Superfície arquitetural provável

Um parent Maven agregador, um JAR `cfg-kernel`, um pequeno entry point de preflight,
testes em memória e um hook de arquitetura baseado no build/bytecode real.

## Migrações requeridas

Alterar o harness de `docs_only` para `implementation`, mantendo semantic,
performance e integration indisponíveis. Atualizar o lock e as referências do
binding JSON agora presente, ainda DRAFT.

## Artefatos esperados

POMs versionados, CI com upstream fixado, classe de preflight, testes JUnit,
gate arquitetural, documentação de reprodução e evidência RED→GREEN.

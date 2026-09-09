# CP5 — protocolo de challenges

Integra o ritual de [testes existente](testing.md) e o padrão de
`scripts/project/challenge_scalar.py`/`challenge_transport.py`. Nenhum mutante de
engine foi executado na preparação: classes ainda não existem.
[Inventário declarativo](../evals/cp5/challenges.json), com ativação por Wave e
oracle/probe decisivo. IDs locais de challenge não são work items novos.

## Ciclo de evidência obrigatório

1. Baseline GREEN no HEAD/árvore e gate nominal definidos; salvar fontes/hash/diff.
2. Aplicar uma mutação focal em alvo concreto; compilar com sucesso. Erro incidental
   de build/configuração não prova RED semântico/performance. Dependência proibida
   pode legitimamente falhar no gate arquitetural após compilação bem-sucedida.
3. Executar gate esperado; exigir exit não zero **e** diagnóstico/asserção nominal
   relacionado à mutação. Timeout/OOM incidental ou outra falha não conta.
4. Restaurar byte-exact em finally; comparar inventário/hashes, inclusive em falha.
5. Segundo GREEN no mesmo conteúdo restaurado; preservar logs e manifest completos.

Mutante sobrevivente exige corrigir oracle com regressão permanente; não alterar
log passado nem enfraquecer semântica. Análises/oracles lentos test-only podem usar
recomposição/mapas completos em grafos pequenos; não entram em produção.

## Ativação segura

Cada Wave substitui target/hook nulos por paths/classes reais e registra command,
compile command, expected diagnostic e referência do teste. Guards de inventory,
javap/jdeps/imports complementam comportamento; regex não prova complexidade.
S4b deve matar recomposição semanticamente correta com agenda adversa; S3 confronta
contadores com alocação/retention externa; S5/S6 usam solver/domínio reais em W4.
Não criar Java de mutantes contra APIs futuras só para preencher inventário.

Recibo de campanha: source HEAD e hash da árvore, baseline command/exit/log/hash,
mutant ID/diff, compile command/exit/log, gate/diagnostic/exit/log, hashes before/after,
restore byte-exact, second GREEN command/exit/log. Report completo e falhas são
preservados em evidence do work item; raw logs em .harness-results com hashes no
recibo. Ausente/não executado/skip não é PASS nem RED legítimo.

## Arquitetura futura

[Plano de dependências](../evals/cp5/architecture.json) prepara o DAG, packages e
inventários. W1 vincula jdeps/javap e sources/classfiles exatos do módulo; W2 amplia
solver/SPI sem AIR concreto; W3 values; W4 API restrita de consumers; W5 composição.
Um módulo que importa tipo AIR sem dependência direta air-java deve falhar, mesmo
compilando por transitividade: contracaso Maven específico no inventário.
Sem freeze de nomes de container/policy/default: evolução legítima dos inventários
acompanha propriedades e evidências aprovadas.

CP5-F01 registra o launcher preexistente com dependências AIR transitivas. O
contracaso do harness usa POM/source sintéticos em diretório temporário e não afirma
compilação de engine. A prova Maven compilável será ativada em W1; o detector estrito
já detecta a dívida existente, cuja exceção de preparação exige bytes do baseline.
[Detalhe e parada para review](../work/cp5-follow-ups.md#cp5-f01--launcher-com-dependência-air-transitiva-no-baseline).

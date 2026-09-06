# plan

## Fatiamento
1. Confirmar lifecycle anterior, main limpa e pins; promover este work.
2. Ler API/norma, fechar modelo mínimo, expected manual e adversariais; observar RED.
3. Implementar projeção e envelope; GREEN, mutante de fallthrough e metamorfismo.
4. Evoluir inventários arquiteturais, semantic hook e CI; challenge e gates reais.
5. Promover docs, arquivar somente com critérios satisfeitos, commits e novo PR.

## Dependências
WORK-CFG-003 encerrado antes desta promoção; PR #4 MERGED no GitHub em
2026-09-06T22:51:28Z. Main limpa/HEAD=origin/main:
f3da26a5181d821e9531e88be239802421e93eb9. Branch feat/cfg-first-return.
As mains upstream foram consultadas via git ls-remote e coincidem com sources.lock.
Nenhum pin foi atualizado. Checkouts detached e repo Maven ficam em /tmp.

## Superfície arquitetural provável
Produto no namespace domain; coordinator mantém a porta existente e preflight.
IDs/transições tipados, sem extensão para Return. Índices próprios namespaced.

## Migrações requeridas
Evoluir READY_FOR_CFG_PROJECTION para distinguir produto real de falhas sem graph;
preservar options, diagnostics e seam de capabilities.

## Artefatos esperados
EVAL-CFG-025 executável, semantic gate com inventário obrigatório, architecture
com inventário exato ampliado, CI Temurin 21 e histórico com evidência real.

## Decisão concreta antes do código produtivo
CfgGraph retém Publication por referência e listas próprias imutáveis. CfgNode é
sealed com EntryNode/SequenceNode/NormalExit. CfgNodeId é do CFG (PublicationId +
ordinal determinístico), correlacionado pelo source AIR dos nós. CfgTransition
usa ENTRY/RETURN e activationEntry explícita. CfgProjectionIssue identifica forma
fora do slice. CfgFirstProjection é regra de domínio chamada após preflight.
Sem seleção de Entry em BuildOptions; todas são inventariadas. Ordenação por IDs
namespaced só estabiliza o produto, nunca descobre successors. Custo O(N log N + T),
com T incluindo retornos condicionados para cada Entry da Unit.

CfgBuildResult terá CFG_BUILT + Optional<CfgGraph>; falhas sem produto. Acrescentar
UNSUPPORTED_INPUT para core fora do slice e preservar os estados anteriores de
falha. Publication completa sem Units produz inventário realmente vazio; isso não
é placeholder. Inventário parcial/indisponível ou body indisponível serão recusados
com motivo tipado, sem apagar as lacunas de entrada/preflight.

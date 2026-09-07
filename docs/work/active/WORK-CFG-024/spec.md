# spec

## Problema

Admissão hard-coded COMPLETE bloqueia inventário PARTIAL semanticamente válido.
Autoridade de escopo: roadmap do workspace, seção Checkpoint 0A, e pedido desta sessão.

## Objetivo

ProjectionPolicy no domínio, passada por BuildOptions: KNOWN_SUBSET default e STRICT opt-in.
A primeira aceita COMPLETE/PARTIAL; STRICT somente COMPLETE. UNAVAILABLE continua recusado.
BuildCfg → coordinator → AirValidator/preflight → capabilities → admissão → projeção.

## Discovery antes da implementação

Base main limpa e atualizada: 7edb1cd7827531ab30548d878cea3a06a483bc04.
Branch feat/cfg-known-subset-policy. Pull ff-only passou usando SSH sem configuração global defeituosa.
CoreCfgProjection.unsupported contém as duas exigências de COMPLETE, na Publication e em cada Unit.
CfgBuildCoordinator chama unsupported somente após invalidade, limites, capabilities e validação incompleta.
CfgGraph verifica endpoints tipados, namespace e activationEntry; nenhuma regra consulta inventory.
CoreCfgProjection.project enumera toda Sequence/Entry conhecida, inclusive órfãs, e usa apenas labels explícitos.
Unit AVAILABLE e preflight garantem entries/sequences e fechamento dos destinos. Não há dependência implícita
em completude global para Entry/Jump/Branch/Return/Halt. Alegações de ausência/exaustividade precisariam dela;
este produto não calcula alcance nem certifica todos os caminhos possíveis (AIR §06.8, §08.2/6.3).

AirValidator/ReferenceChecks.coverage exige razões para PARTIAL/UNAVAILABLE, fechamento de referências e
coverage items explicados. Esses checks são validade AIR, não a policy, e permanecem intactos.
Body indisponível, terminadores não suportados inclusive órfãos, extensão sem semântica, capabilities sem
intérprete, AIR inválida e validation limits/incomplete validation continuam impedindo produto.

BuildOptions é o ponto público adequado; ProjectionPolicy reside no domínio para que a aplicação passe
somente o conceito, sem inverter dependências. Aplicação continua chamando o único CoreCfgProjection.
Construtor anterior BuildOptions(ValidationOptions) e overload unsupported(Publication) usam o novo default.
CfgBuildResult NÃO precisa de status novo: graph().publication() retém exatamente a Publication imutável,
com coverage da Publication/Units, gaps, uncertainties, premises e origins; options registra a policy.
CFG_BUILT significa produto existente, sem claim de completude. Falhas conservam diagnostics tipados.

EVAL-CFG-025.unavailableInventoryIsNotACompleteEmptyGraph fixa PARTIAL e UNAVAILABLE como rejeição default.
Será reconciliado para STRICT mantendo ambas recusas; EVAL-CFG-030 prova os novos defaults e UNAVAILABLE em ambos.
BuildCfgContractTest fixa um componente nas opções: passará a exigir dois tipos e default explícito.

## Premissas

IR_GUARANTEED: AIR pinada 122ce54e1b9ef9b00646f93ece409ca8b63bc933, §06.1/2/8 e §08.2/6.3.
ARCHITECTURE_GUARANTEED: preflight upstream e referências AIR originais imutáveis.
EXPLICIT_CONTRACT: decisão 0A KNOWN_SUBSET default; sem alegar irrelevância causal das lacunas para análises futuras.
Upstream só evidência: EntryGobackLowerer publica ambas coverages PARTIAL, Return com controle EXACT e
outras dimensões UNAVAILABLE. Nenhum gap code/texto desse produtor entra em decisão ou fixture do CFG.
O air-java do workspace está além do pin; a compilação usa git archive do SHA pinado em /tmp/cfg-0a-air-java,
com Maven repo isolado /tmp/cfg-0a-m2, sem modificar upstream nem atualizar source lock.

## Fora de escopo

Transporte, JSON, CLI, codecs, lowering, controle aberto, novas operações, análises e mudanças upstream.

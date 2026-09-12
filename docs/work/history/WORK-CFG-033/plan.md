# CP6 W1D — plano

## Fatiamento

1. W1D.1: congelar SHAs/trees, exports e JARs; executar COBOL real e capturar CFG/vertical RED.
2. W1D.2: TDD de Invoke Normal tipado, indexação e semântica de continuação; GREEN.
3. W1D.3: TDD de perfil effects genérico, BEFORE/continuação/loop, NoMemory e rejeições; GREEN.
4. W1D.4: RED de produto com ValueFact real já disponível; W4/reachability/consumer/wire separado; GREEN.
5. W1D.5: duas verticais reais, wire independente, desafios compiláveis, restauração e segundo GREEN; gates canônicos CP5; commit/push/Draft PR e CI do HEAD exato.

## Dependências

W1A `53d774026a1e4bcd969c7783a1d277aaa87b5f2f`, W1B `2a37f5e980ba25fdc79614a66030a84d8bf5b8c9`, W1C `9de3825da64898258e647727393f01b9e9198d9e`, AIR `51b4d9a8ae0364232bd97103cd73a77e1a34996c`. Compilar archives isolados, preservar checkouts ocupados e usar JDK21. Produtores históricos CP5 usam repositório Maven separado para não sobrepor snapshots do lower W1D.

Gates adaptam inventários atuais estritamente à slice autorizada. Evidências/inventários CP5 permanecem históricos. O único ajuste de README da evidência discovery é o caminho para seu estado arquivado. Gate de fonte protege a alteração exata de roteamento e recusa qualquer outra mudança naquela evidência.

## Plano da remediação de wire e CI

1. Confirmar branch/HEAD limpos, baseline remota e Draft PR #18.
2. Preservar RED de versão; corrigir somente o writer por mappings explícitos
   que ligam token a requisito de contrato; preservar bytes v1.
3. Extrair autoridade executável compartilhada, Fast fixo de testes/arquitetura
   e qualification completa local/manual, sem path filtering.
4. Executar focais e Fast local; preservar evidência sem editar os artifacts anteriores.
5. Commitar a entrega, executar uma Full Qualification local no HEAD final limpo
   e registrar receipt externo ao commit (sem circularidade). Corrigir falhas e
   requalificar qualquer novo HEAD. Somente então push na mesma branch.
6. Aguardar Fast remoto, registrar checkout/duração/resultados, manter PR OPEN/DRAFT.

O novo estado QUALIFIED depende do receipt real do HEAD de entrega, distinguindo
LOCAL_QUALIFICATION de FAST_CI. A authority REMOTE_MANUAL_QUALIFICATION fica disponível
sem disparo automático nesta entrega.

# Contratos, decisões e exemplos de intenção

Este é desenho de harness, não schema implementado. [Brief](brief.md) é normativo
para o escopo. Exemplos abaixo são manuais, independentes do futuro produtor;
**NOT_RUN**, não Publications validadas nem fixtures GOLDEN copiadas da produção.

## Interfaces e consumo bilateral

| Informação necessária | Produtor → consumer | Prova de perda exigida |
| --- | --- | --- |
| owner/conector, SELECT e FD/SD, origens | nominal frontend → SP/lower → inventário FILE | remover owner funde homônimos; remover FD duplica entidade |
| record ownership/visibilidade/captures | frontend → lower; consumer recebe associação explicativa | WRITE REC liga ao FD errado ao remover owner |
| ASSIGN variante/nome/perfil | frontend → lower → File consumer | literal IBM tratado como variável falha; nenhum lookup externo |
| resource descriptor + uso/ação/papel | lower → AIR → consumer | remover participante SORT perde uma entrada |
| storage, reads/writes, outcomes | frontend/lower → CFG/effects/values | MUST indevido perde CALL disjunto ou mantém valor velho |
| estado capturado no OPEN | lower → providers gerais → consumer | nome alterado após OPEN não altera conexão antiga |
| inventário/coverage por dimensão | produtores → consumidores → JSON | CFG parcial não apaga literal/declaração conhecidos |
| sites/supports/identidades/origens | consumer → serializer → reader independente | remoção de ref/dimensões quebra oracle, não só snapshot |

W0 decide a shape tipada **SP**, alinhada a `INTERNAL-CONTRACT-DEV-001`: writer
corrente + decoder/admission coordenados, rejeição explícita de versão superseded,
sem dual writer por padrão. Extensão de SP não obriga alteração AIR em W0.

## Representação AIR: resultado da leitura H1/H2

`LiteralTarget(file, namespace, name, NamePolicy, origin)` e `ComputedTarget`
representam alvos de uso. `Resource` é declarativo; não é nova variante de Target.
`Artifacts.Relation` parte de ArtifactId, com destination artifact/literal.
Nem um Resource global sem owner nem `declares_resource` artifact→literal provam
sozinhos vínculo unit/conector/record/uso. `BindingWriter.empty(resources)` e
`BindingReader.resources.empty()` recusam inventários não vazios.

**Decidido:** reutilizar invoke/efeitos/controle; completar codec existente se
usado; não chamar ausência de codec de ausência de conceito normativo.
**D-AIR ainda aberta em W1:** menor transporte tipado da associação indispensável.
Proposta preferida: extensão estrutural neutra, sem semântica COBOL no core,
com owner, binding e refs de uso; só registrar em analysis-ir após demonstrar
que as relações existentes não satisfazem A1–A6. Não implementar sidecar que o
consumer precise abrir junto com SP, nem serializar semântica em provenance.

## Seis exemplos bilaterais mínimos

Notação conceitual: `u/f` é conector com owner, `r` é registro, `s` site e `o`
origem. Campos abaixo indicam obrigações; não são nomes de API aprovados.

| ID / entrada manual SP | AIR mínimo necessário / situação atual | Resultado esperado independente |
| --- | --- | --- |
| A1: unit U; SELECT F→DD; FD F; REC R; sem statements | Resource literal + associação U/F/R e origens SELECT/FD; sem invoke. Codec/owner bloqueiam transporte atual | uma declaração, alvo DD exato, zero usos/arestas operacionais |
| A2: U/F/R; WRITE R FROM X | invoke(write,file,DD); ligação s→U/F; leitura X/transferência conforme regra; origem derivada | uso de F, não de X; record explica binding; CALL vazio |
| A3: U1/F→DD e U2/F→DD | dois owners/conectores mesmo alvo; sem parsing de localId; D-AIR | declarações distintas; agregação conserva ambos os owners, não duplica entidade no mesmo owner |
| A4: U/F, ASSIGN computado X sem prova | conector conservado; ComputedTarget + unknown tipado no ponto; D-AIR para estrutura | site FILE conhecido, zero candidatos, remainder true; não inventar DD nem apagar F |
| A5: X='a'; OPEN F; X='b'; READ F; CLOSE; OPEN F | captura sintética no OPEN usando assign/read/storage gerais; uso lê estado capturado; join conservador | primeira conexão a; segunda b; abertura ambígua preserva conjunto e remainder correto |
| A6: SORT S USING A B GIVING C | SD estrutural; usos derivados com papéis input A/B, output C, work S e mesma origem; fases apenas se provadas | três alvos fornecidos, sem DD inventado para S; procedimento local não cria CALL |

W1 transforma A1–A4 e A6 em Publication/model→validator→codec→consumer executáveis
antes do slice produtivo. A5 tem smoke estrutural em W1 e oráculo de valores em W7.
Incompatibilidade vira repro mínimo + decisão, não extensão especulativa em H.

## Cinco outcomes manuais obrigatórios de W3/W4

Modelar conclusão de invoke e resultado semântico separadamente. `Normal` não
significa status 00. Status/branch explícitos são a preferência se suportados;
outcome de extensão só com interpreter registrado/negociado (preparação usa
registry vazio hoje). Buffers B, destino INTO D e variável disjunta X são distintos.

| ID | Sucessor/efeito esperado a testar |
| --- | --- |
| O1 sucesso | status antes do teste/handler; B alterado no intervalo provado; cópia INTO após leitura; X preservado |
| O2 EOF | ramo AT END; nenhuma cópia INTO de sucesso; validade de B não presumida; CALL no handler inventariado |
| O3 invalid key | ramo INVALID KEY próprio; status visível; não equiparar EOF |
| O4 outro erro | handler USE/continuação conforme perfil ou controle aberto explícito; não cair em sucesso por default |
| O5 outcome aberto | união conservadora/unknown localizado; preservar suportes independentes, sem kill global nem sucesso inventado |

Tabela concreta de bytes/outcomes exige autoridade W3/W4. Até lá referências de
arquivo podem ser exatas com efeitos/controle PARTIAL; isso não é perfil N completo.

## JSON e readers observados

`DependencyJson` produz 1.1.0 ou 1.2.0 (partial), sites/edges somente programas.
`scripts/project/dependency_wire.py:validate` fecha chaves e versões; acrescentar
seção sob essas versões **é incompatível**. Os scripts E2E importam esse reader;
`carddemo_metrics.py` usa projeções de sites/candidates. Tests `W1dDependencyTest`,
`PartialDependencyTest`, `test_dependency_wire.py` e leitores de corpus são a
fronteira conhecida. Busca no site local mostrou apresentação de SP/AIR/CFG,
sem reader de dependency identificado; não modificá-lo por associação. Consumers
externos fora do workspace são desconhecidos, portanto não alegar compatibilidade universal.

W1 publica versão nova negociada e seção tipada `fileDependencies`, com
`analysisBoundary = COBOL_SOURCE_ONLY`; top-level sites/edges mantêm significado
CALL. Decisão do número e eventual projeção legada em D-WIRE. Não relaxar o reader
antigo para aceitar qualquer campo/versão. Testar rejeição, reader novo e projeção
CALL sem perder supports/remainders; o rótulo valuesProfile deve refletir perfil real.

Forma conceitual mínima: declarations(owner, logicalFile, FD/SD, target, origins),
sites(owner, operation, role, declarationRef, valuePoint, candidates/supports,
remainder, reachability, effects/control status, origins), edges derivadas dos
sites e inventários conhecidos/indisponíveis. `artifacts` continua proveniência.
Para DD literal, ausência de DSNAME não cria campo/reason code algum.
IDs/refs devem fechar no documento; ordenação determinística na mesma revisão.

## Decisoes abertas

| ID | Decisão / dono | Quando fecha; bloqueia |
| --- | --- | --- |
| D-AIR | associação tipada mínima; lower + AIR + consumer | W1 antes de alteração normativa/codec; não bloqueia W0 SP |
| D-WIRE | número da versão nova e modo/projeção de reader legado; CFG/adapters | W1 antes de emissão; inspecionar todos os readers listados |
| D-EFFECT | tabela bytes/ordem/status/validade por outcome e opções; frontend/lower | W3 antes de MUST; W4 antes de fechar USE/handlers |
| D-DYNAMIC | captura/joins/ciclos de conexão no IR geral; lower/values | W7; sem solver separado |
| D-D-AUTH | revisão 3.2 exata e assinaturas/efeitos do catálogo D-v1; frontend/lower | W7 para DYNAMIC, W10 para APIs; catálogo finito já selecionado |

Não estão abertas: scope source-only, CICS incluído, inventário separado de uso,
reutilização do motor geral, ausência de execução W em H, e revisão humana em H4.
Multi-unit seguirá composição selecionada; se captures exigirem outra estratégia,
documentar contraexemplo antes de mudar, sem bloquear declaração local W0.

# Evals — WORK-CFG-002

## O que prova corretude

Compilação e teste contra o JAR real do `air-java`; identidade nominal de
`Publication`; delegação ao `AirValidator`; preservação dos três status de validação
e dos diagnostics; ausência de transporte/modelo/validator paralelo no kernel.

## Classes positivas

Uma publicação estrutural mínima construída apenas com classes `air-java` atravessa
o preflight e produz `STRUCTURALLY_VALID`.

## Classes negativas

Publicação com referência estrutural pendente produz `INVALID_IR`. Capability
requerida desconhecida produz `INCOMPLETE_VALIDATION`. Remover `air-java` impede a
compilação do smoke. Java/POM sem testes ou dependência proibida falham no gate.

## Classes ambíguas

`SEMANTIC_OBLIGATION` pode coexistir com estrutura válida; o issue deve sobreviver
sem ser promovido a evidência de verdade ou conformidade do consumer.

## Casos adversariais

Expected anterior à configuração: uma classe de produção que referencia
`java.nio.file.Path` deve fazer o gate arquitetural falhar. Outro sentinel com nome
AIR local deve ser rejeitado antes do build. Ambos serão exercitados em cópia/edição
temporária e não permanecerão no produto.

## Casos de regressão

O harness continua falsificando lifecycle, source lock, binding DRAFT, fase e gates.
Surefire falha com zero testes. O gate inspeciona classes compiladas, POM/dependency
tree e source inventory do kernel.

## Propriedades/relações metamórficas

Para a mesma publicação, o resultado do preflight é igual ao resultado direto do
`AirValidator`; o consumer não modifica a publicação nem seus inventários.

## Expectativas de escala

Não há algoritmo CFG neste checkpoint. O preflight adiciona somente uma delegação;
limites e estatísticas continuam sob responsabilidade do validator upstream.

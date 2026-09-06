# O que foi reaproveitado — e o que mudou

## Padrões mantidos do proleap-poc

AGENTS curto com roteamento; separação entre arquitetura/domínio/engenharia/evals;
ADRs e invariantes com IDs; backlog sem autorização implícita; work items com spec,
plan, eval e state; gates nomeados; lifecycle hygiene; dados de performance como
evidência algorítmica; challenge adversarial; oracle independente; proibida promoção
de unknown para certeza. Fontes fixadas no [índice](index.md).

## Adaptações ao CFG

Autoridade semântica é Analysis IR, não documentação COBOL. Não copiar grammar,
parser, symbol tables, resolver, snapshots HTML, testes de corpus ou dependências
ANTLR/Node. Proteger Clean Architecture e via em memória desde o primeiro slice.
Reconhecer que Sequence já fixa fronteiras de transferência; sem leader detection
necessário. Distinguir projeção plana e matching contextual. Incorporar equivalência
arquivo/memória e congelamento de revisão no catálogo de testes.

Após o primeiro discovery, o modelo/validator compartilhado é o `air-java` fixado,
o consumer usa Java 21 e o primeiro slice é CFG-FIRST em memória. O frontend
continua Java 17 e termina no COBOL Semantic Product; `cobol-lower` é boundary
upstream planejada. Binding JSON pertence ao `analysis-ir`, não ao layout Java.

Os cinco arquivos de work item são mantidos conceitualmente, mas o manifesto usa
`work-item.json` em vez de YAML. Isso permite validar estritamente o formato com a
biblioteca padrão Python sem instalar parser e sem implementar YAML parcial.
`status` e `authorization` são explícitos. O estado humano curto não é substituído
por logs completos.

O gate docs é executável antes de existir Java. Os gates de produto não simulam
sucesso: enquanto ausentes, retornam UNAVAILABLE. Testes negativos exercitam o
validador do harness, não um CFG fictício. Não transportar retrospectivamente o
status “verde” dos testes upstream para este repositório.

## Limites conscientes

Harness ajuda a tornar regras e falhas verificáveis; não garante que agente nunca
erre. Gating documental não prova soundness, compliance de bytecode, autorização
humana real ou completude semântica. Essas provas entram nas fases correspondentes.
Os scripts não consultam rede por padrão, não fazem merge e não mudam o código.

# Review do harness — 05/09/2026

## Escopo

Revisão realizada antes da primeira implementação Java, confrontando o harness com:

- objetivo de um consumidor CFG que conhece somente Analysis IR;
- futura integração em monólito modular Maven;
- troca de driving adapter arquivo → memória sem alteração do core;
- evolução progressiva de controle até `dispatch`, loops, controle local e indireto;
- práticas do harness do `proleap-poc`;
- Analysis IR 1.0.0 fixada em `docs/sources/sources.lock.json`.

## Parecer

O desenho é adequado para iniciar o projeto. As fronteiras mais importantes estão
protegidas documentalmente antes do código: Publication é a única entrada semântica;
I/O fica em adapters; o core não interpreta COBOL, JSON ou ordem física de sequences;
capabilities da IR são o eixo de evolução; incompletude não vira ausência; e gates de
produto inexistentes não podem aparecer como verdes.

## Ajustes feitos nesta revisão

1. **MVP reduzido até IF/ELSE.** O primeiro marco observável agora cobre fluxo
   linear, `jump`, `branch`, `return` e `halt`, com arquivo e memória usando a mesma
   porta. `invoke` virou slice posterior. Isso reduz risco e fecha cedo a prova
   arquitetural pedida.
2. **Backlog reordenado.** O fechamento arquivo/memória não depende de `invoke`; a
   qualificação `AIR-STRUCTURE@1` continua dependendo de `invoke`, seleção/ciclos e
   controle aberto.
3. **Estado do repositório atualizado.** README/START_HERE deixam de tratar o
   harness como ZIP ainda não instalado.
4. **Extensibilidade mantida por capability, não por COBOL.** `PERFORM`, `GO TO`,
   `EVALUATE` e `NEXT SENTENCE` continuam motivadores de lowering; o CFG evolui por
   `control.local@1`, `control.indirect@1` e primitives do core.

## Pontos fortes preservados

- `Sequence` já possui terminador; o MVP não precisa de leader finder nem de
  reconstrução por proximidade textual.
- Clean/Hexagonal é verificável: kernel sem filesystem/serialização/framework e
  adapters externos substituíveis.
- controle local é tratado como problema contextual, não simples adjacência; o
  backlog exige discovery/literatura antes de selecionar algoritmo.
- TDD nasce de regra/oracle e inclui adversariais, metamorfismo, challenge pass e
  proibição de expected gerado pelo próprio builder.
- algoritmos exatos/canônicos têm precedência; aproximações conservadoras precisam
  declarar soundness, limites, terminação e complexidade.
- contrato IR, exemplos/oráculos e contexto upstream estão pinados por revisão.

## Riscos que continuam deliberadamente abertos

- ownership físico do modelo Java da Analysis IR e suas coordenadas Maven;
- formato técnico/versionado da fixture em arquivo;
- representação precisa de consultas contextuais para `control.local@1`;
- estratégia de fronteira simbólica para `ControlScope` aberto;
- eventual biblioteca de grafo e forma física do produto CFG.

Esses pontos pertencem a work items específicos. Nenhum justifica antecipar uma
framework genérica, duplicar a IR ou ensinar semântica COBOL ao consumer.

## Decisão de readiness

**Harness pronto para o discovery `WORK-CFG-001`; Java ainda não autorizado.**
O próximo passo correto é fechar ownership do modelo IR, módulos, porta concreta e
fixture técnica; depois promover um único slice de implementação por vez.

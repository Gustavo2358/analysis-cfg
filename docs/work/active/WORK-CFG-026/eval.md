# eval

## O que prova corretude
EVAL-CFG-031: fixture upstream estática, golden CFG manual anterior ao writer, decode real, build real, topologia/correlações manuais, PARTIAL/KNOWN_SUBSET, equivalência com Publication em memória e bytes determinísticos.

## Casos adversariais
Arquivo ausente, limite max+1, BOM/UTF-8/versão/AIR inválida, forma válida fora do codec, recusa real do kernel, output impossível, serialização limitada/inválida preservando destino, args sem build. Writer cobre Entry/Sequence/NormalExit/HaltExit e seis kinds, IDs escapados e namespaces distintos.

## Casos de regressão
102 testes kernel, semantic 84 métodos, harness 41 testes e gates exatos preservados.

## Propriedades/relações metamórficas
Duas execuções sobre mesmos bytes geram o mesmo golden. Sem metadata de máquina. Challenges dos 13 atalhos exigidos e ausência/skip/duplicação da suíte devem ficar RED, restaurar fontes byte a byte e obter segundo GREEN.

## Expectativas de escala
Limites operacionais de input/output, sem benchmark nem claim de performance. Testes planejados ainda não são evidência executada.

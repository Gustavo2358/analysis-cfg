# Relações metamórficas do CFG

Cada relação declara precondições e o que permanece equivalente. Não exigir
isomorfismo literal quando a transformação muda a granularidade do grafo.

| Relação | Precondição | Observação preservada |
| --- | --- | --- |
| MR-CFG-01: permutar sequences | labels/transferências/origens iguais | mesmas transições correlacionadas |
| MR-CFG-02: renomear IDs | bijeção por domínio e namespace, referências atualizadas | mesmos comportamentos sob correlação |
| MR-CFG-03: alterar display | metadata sem papel semântico | mesmo CFG e precisão |
| MR-CFG-04: split de Sequence | inserir jump e label fresco sem alterar operações originais | mesmos caminhos entre pontos originais, modulo passos auxiliares |
| MR-CFG-05: negar predicado e trocar targets | predicado puro, tipo bool, mesmas leituras | comportamento equivalente com TRUE/FALSE remapeados |
| MR-CFG-06: acrescentar região desconectada | não é Entry, não é target e não existe scope aberto que a alcance | subgrafo anterior inalterado; inventário total aumenta |
| MR-CFG-07: via arquivo/memória | mesmo modelo e mesmas opções | mesmo resultado semântico |
| MR-CFG-08: permutar cases | dispatch válido sem literais duplicados | mesmas alternativas por valor e mesmo default |

Mudança de literal de dados não é invariância universal: pode mudar um predicado ou
controle indireto. Aplicar só onde controle independe comprovadamente desse valor e
a consulta é estrutural sem refinamento. Mutação Java não é relação metamórfica.

MR-CFG-01 está executável no domínio CFG-FIRST por EVAL-CFG-025, junto com
permutação de Units/Entries e teste de inventário órfão. Isso não implementa o
EVAL-CFG-014 inteiro. O mutante Return → próxima Sequence física foi morto pelo
oracle independente.

WORK-CFG-022/EVAL-CFG-028 executa MR-CFG-01/02/03/04 no slice linear: IDs
correlacionados, display/origin presentation preservados e split com Jump, sem
exigir isomorfismo literal. EVAL-CFG-014 continua planned por obrigações além do slice.
Os mutantes Jump→vizinho, Return→vizinho, Halt→NormalExit, Halt→vizinho e perda/
reordenação de instructions foram mortos por expected independente, sem a guarda
tipada de endpoints. As mutações foram restauradas antes dos gates.

Mutantes prioritários posteriores: remover default, unir IDs de units, pular opaco, criar C1→resume direto, buscar porta abaixo do
topo, ignorar underflow, descartar remaining ControlScope. A suíte deve matar cada
mutante focalizado ou registrar uma lacuna, não maquiar score.

## Branch — EVAL-CFG-029

MR-BRANCH-1 (MR-CFG-01): permutation física adversarial do diamond mantém nós,
IDs CFG e transições. MR-BRANCH-2 (MR-CFG-02): alpha rename de Unit/Entry/Label/
Operation/Operand usa correlação explícita, conservando origem e valor publicado.
MR-BRANCH-3 (MR-CFG-03): display/origin presentation enganosos não selecionam target;
referências de provenance e operações continuam observáveis.
MR-BRANCH-4 (MR-CFG-04): split da Sequence que termina em Branch insere Jump e
conserva as mesmas ocorrências/pontos originais na continuação, sem exigir
isomorfismo literal nem apagar IDs/origins.

Mutantes A–G foram mortos em WORK-CFG-006: TRUE/FALSE invertidos, unknown sem
FALSE, target físico, destinos iguais colapsados, Halt reconvergente, literal true
com pruning e nested usando join externo. Cada execução teve exit 1, uma assertion
failure e zero errors; o expected independente detectou o defeito com a guarda de
endpoints temporariamente desativada. Fontes/guardas restauradas antes dos gates.

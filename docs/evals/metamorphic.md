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

Mutantes prioritários posteriores: remover FALSE, remover default, adicionar fallthrough após
halt, unir IDs de units, pular opaco, criar C1→resume direto, buscar porta abaixo do
topo, ignorar underflow, descartar remaining ControlScope. A suíte deve matar cada
mutante focalizado ou registrar uma lacuna, não maquiar score.

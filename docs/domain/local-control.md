# Controle local e contextos de retorno

## Contrato já existente

A extensão `control.local@1` da Analysis IR §05.7 tem uma pilha semântica de frames:
site invocador, resume e completion ports. Ela compartilha a ativação de memória,
não instancia outra unit. O produtor só a usa quando a disciplina de conclusão
corresponde à regra do contrato. Fonte fixada: [fontes](../sources/index.md).

| Operação | Regra |
| --- | --- |
| `local.invoke(entry, ports, resume)` | Push do frame e transferência à entry; nenhuma execução direta para resume |
| `local.boundary(port, default)` | Match só no topo: pop/retorno se casar; caso contrário default sem alterar pilha |
| `local.resume()` | Pop/retorno; pilha vazia é `invalid_local_return` |
| `local.unwind(n,dest)` | Remove exatamente n frames; excesso é `invalid_local_unwind` |
| `jump` | Não remove frames |

Mais de uma boundary pode sinalizar a mesma porta; porta e ocorrência têm IDs
diferentes. Não procurar frame externo atravessando o topo.

## Consequência para arquitetura

A semântica pode ser representada como regras de transição condicionadas pelo
contexto. Materializar todos os contextos não é obrigatório, e pode não ser finito
com recursão. Um `Map<Node,List<Node>>` é apenas projeção: não garante caminhos
realizáveis nem matching preciso de chamada/retorno.

BACKLOG-CFG-013 compara representação pushdown/simbólica, consultas contextuais,
resumos aplicáveis e aproximações declaradas. Não transformar esse estudo em um
projeto de dataflow. Nenhum algoritmo é selecionado só por “ter pilha”.
Documentar domínio, soundness, terminação, custo e contraexemplos; fontes acadêmicas
em [algoritmos](../engineering/semantic-policy.md).

Adicionar campo `callSiteId` ao edge sem impor sua compatibilidade durante consultas
não resolve o problema. O-56–O-60 precisam provar retornos correspondentes.
Um consumer por fallback pode ser conservador, não preciso para AIR-LOCAL-CONTROL.

## Motivação PERFORM/THRU

Um trecho curto pode concluir na porta intermediária; um longo ignora essa porta
pelo default e conclui na última. Entrada ordinária com pilha vazia percorre defaults.
Esses casos existem no exemplo X-24 upstream e orientam [oráculos locais](../evals/local-control.md).

Isso não certifica todas as variações COBOL: ranges cruzados, saídas não locais,
EXIT variants e combinações dependentes de dialeto exigem validação de lowering.
Não mapear `EXIT PARAGRAPH` indiscriminadamente para unwind, nem mudar a regra de
topo do consumidor para acomodar um programa-fonte. Tal problema pertence ao
produtor/contrato; o CFG deve continuar independente de COBOL.

## Limite inicial histórico

O MVP anuncia `control.local@1` como não suportado. O seam e os oráculos já constam
do harness; implementá-los não é pré-requisito para demonstrar diamond em arquivo.
Essa postergação não autoriza tratar local.invoke como invoke externo normal.

## Implementação stage 5 (2026-09-30)

O limite inicial acima é histórico. `CoreProjection` admite as quatro operações e
publica uma regra tipada por ocorrência, mantendo os corpos compartilhados.
`ContextView.Point` seleciona Entry, nó e pilha de retornos. A travessia aplica as
regras da tabela; a API antiga que recebe somente um nó recusa grafos com controle
local. Retornos dinâmicos não integram a lista de arestas ordinárias.

O solver explora contextos finitos, mantendo raízes de estado separadas por pilha.
Consultas a um corpo compartilhado executam as instruções de cada contexto antes
de juntar os resultados; juntar antes do replay inventaria combinações em domínios
não distributivos. O consumidor de dependências não interpreta os frames.

Uma invocação já ativa encontrada novamente recusa a exploração com
`LocalControlRules.RecursiveActivation`. Não há truncamento por profundidade nem
resultado estável parcial nessa situação. Recursão geral exige outro algoritmo;
o lowering mantém suas fronteiras e especializações existentes para esses casos.
Loops que concluem uma chamada antes de repetir continuam admitidos.

CFG JSON v5 publica `localControl` separadamente das transições ordinárias. O
consumidor precisa manter a pilha descrita aqui para calcular caminhos realizáveis.
Ver [contrato de transporte](cfg-local-wire.md) e [qualificação](../work/shared-routine-bodies.md).

## Guarda explícita de ativação

`control.local.reentry_guard@1` é interpretada junto com control.local@1. Antes
do push, uma chave lógica na mesma Unit é procurada em todos os frames pendentes.
Uma correspondência segue o destino publicado da guarda sem alterar a pilha.
Caso contrário, a invocação registra a chave no frame. Resume, boundary e unwind
liberam a chave com o frame; jump a conserva. Invocações sem guarda mantêm a
recusa de recursão do perfil finito existente.

A projeção conserva nós compartilhados e uma regra por operação. Os oracles
GuardedLocalControlTest e LocalControlWireTest cobrem identidade lógica em
operações físicas distintas, correspondência abaixo do topo, retorno/unwind,
reentrada direta, fechamento do wire e valores separados por chamador.
Não foi substituído o solver: enumerar pilhas ainda pode ser combinatório.
O formato guardado está documentado em [CFG local wire](cfg-local-wire.md).

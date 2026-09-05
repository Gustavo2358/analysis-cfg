# Controle local: preparar agora, implementar em slice próprio

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

## Limite inicial

O MVP anuncia `control.local@1` como não suportado. O seam e os oráculos já constam
do harness; implementá-los não é pré-requisito para demonstrar diamond em arquivo.
Essa postergação não autoriza tratar local.invoke como invoke externo normal.

# Extensibilidade sem framework especulativo

## Dois eixos diferentes

**Infraestrutura:** arquivos, memória, CLI e cloud. Varia por adapters externos;
sem alterar significado de Publication ou BuildCfg.
**Semântica:** operações/capabilities da IR. Varia por extensão versionada,
contrato de controle e novos oráculos. Não é resolvida adicionando um decoder JSON.

Os perfis do consumidor sob o core V2 usam `@2`; isso não renomeia as extensões
`memory.regions@1`, `control.local@1` ou `control.indirect@1`. Versão de perfil e
versão de capability são eixos distintos.

## Primitives existentes

Um novo construct COBOL que baixa para `jump`, `branch` ou `dispatch` já suportados
não modifica o CFG. Não criar `PerformHandler`, `EvaluateHandler`, flags de dialeto
ou testes de prefixos no core. Evoluir por `(capability, versão)` da IR.
O switch tipado do núcleo fechado pode ser simples; OCP não exige eliminar todos os
switches. O registro de extensões semânticas é separado e explícito.

## Seam antes dos handlers avançados

BACKLOG-CFG-003 deve provar o seam sem implementar controle local real. O builder
coordena; a interpretação de um terminador produz alternativas de controle tipadas:
destino local, saída, fronteira aberta e, quando aplicável, ação/condição contextual.
Não congelar o resultado de todo terminador como `List<LabelId>` incondicional.

Composition root injeta intérpretes compatíveis. Registro duplicado/conflitante é
erro, não prioridade por ordem de inserção. Falta de intérprete usa envelope válido
ou retorna incompatibilidade explícita; nunca `nop` nem lista vazia.
Não carregar classes por texto vindo da fixture. Sem ServiceLoader dentro do core.

A forma concreta da interface aguarda discovery: o teste arquitetural exigido é
adicionar uma capability sintética de teste por extensão/registro sem modificar o
orquestrador nem regras antigas. A sintética não vira capability normativa upstream.

## Evolução conservadora

Compatibilidade estrutural, semântica, conservadora, de precisão e de transporte
são diferentes. Consumidor antigo pode aceitar uma extensão pelo envelope e perder
precisão. Não prometer “zero quebra” apenas por adicionar um campo.
Versionar contratos e correlacionar resultados com a publicação original.
Nova semântica exige contrato aprovado; o agente não altera a IR unilateralmente.

## Controle local futuro

Um grafo finito de nós/transições pode carregar regras de push/pop/guard e uma
consulta contextual. Isso não torna todo caminho da projeção plana realizável.
Precisão para recursão não pode depender de enumerar infinitas pilhas. A escolha de
representação/algoritmo é discovery separado com literatura, adversariais e limite
operacional explícito. Registrar metadado de frame sem usá-lo nas consultas não
satisfaz matching preciso. Ver [controle local](../domain/local-control.md).

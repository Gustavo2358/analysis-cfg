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

O modelo de `air-java` usa sealed types. Uma variante AIR precisa nova pode exigir
nova versão da biblioteca compartilhada e migração explícita do consumer. Um
consumer que ainda não conhece a variante segue o contrato de reduction, envelope
ou incompatibilidade; não cria `Map<String,Object>`, opcode textual ou hierarquia
AIR paralela dentro do CFG para contornar o fechamento.

## Seam antes dos handlers avançados

BACKLOG-CFG-003 implementa `SemanticInterpreter` como a identidade mínima de um
intérprete por `Capabilities.Capability` e `SemanticInterpreterRegistry` como
composição explícita, imutável e determinística. O coordinator consulta esse
registry para capabilities requeridas antes da projeção core. A
interface ainda não possui operação semântica: seu contrato de interpretação
depende de um slice concreto de extensão, ainda posterior.

CFG-FIRST interpreta Return diretamente no core e usa transições tipadas
ENTRY/RETURN; Return não é capability nem handler do registry. Registro identifica
presença, não implementa interpretação: mesmo uma capability reconhecida pelo
validator e registrada é recusada por UNSUPPORTED_INPUT se exigir semântica fora
do slice. Destino local, fronteira aberta e ações contextuais de extensões continuam
posteriores; o contrato não está congelado como `List<LabelId>` incondicional.

Composition root injeta intérpretes compatíveis. Registro duplicado/conflitante da
mesma capability/version lança erro; versões distintas coexistem e a ordem de
inserção não cria prioridade. Falta de intérprete retorna
`UNSUPPORTED_CAPABILITY`; nenhum fallback está implementado neste checkpoint.
Capability registrada não apaga diagnostics do `AirValidator`. Nunca `nop` nem
lista vazia. Não carregar classes por texto vindo da fixture. Sem ServiceLoader
dentro do core.

EVAL-CFG-027 registra uma capability sintética somente em teste, injeta o registry
no mesmo coordinator e prova duplicata e ausência de suporte. A sintética não vira
constante de produção nem capability normativa upstream.

EVAL-CFG-009 permanece planejado para o slice que puder provar interpretação ou
fallback único e uma representação executável sob seus oráculos upstream.

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

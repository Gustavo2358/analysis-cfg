# Extensibilidade sem framework especulativo

## Dois eixos diferentes

**Infraestrutura:** arquivos, memória, CLI e cloud. Varia por adapters externos;
sem alterar significado de Publication ou BuildCfg.
**Semântica:** operações/capabilities da IR. Varia por extensão versionada,
contrato de controle e novos oráculos. Não é resolvida adicionando um decoder JSON.

Alguns perfis históricos do consumidor sob o core V2 usam `@2` conforme suas
próprias autoridades; isso não exige incrementar perfis locais nunca publicados
corrigidos sob a política pré-release abaixo. Também não renomeia as extensões
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
Versionar fronteiras que precisem detectar snapshots incompatíveis e correlacionar
resultados com a publicação original. Correções locais pré-release seguem a
política abaixo.
Nova semântica exige contrato aprovado; o agente não altera a IR unilateralmente.

## Política de versionamento pré-release

Decisão explícita do responsável pelo produto em R2-REV-F2, 16/09/2026.
O produto está em **PRE-RELEASE**: nunca foi usado em produção, não tem consumidor
externo, contrato de compatibilidade publicado ou versão instalada no banco a
preservar. O Git é o ambiente de desenvolvimento. A primeira entrega efetiva ao
banco será um snapshot da main e constituirá o primeiro baseline publicado.

Enquanto não houver baseline de produção ou consumidor externo, bugs semânticos
de contratos/profiles locais são corrigidos **in-place**. Não criar nova versão,
novo profile, compatibility shim ou legacy mode apenas para preservar comportamento
incorreto que nunca foi publicado. Uma mudança no Git, inclusive merge em main,
não equivale a uma entrega de produção. Documentação canônica, implementação e
oráculos devem registrar a correção juntos, mantendo a evidência RED histórica.

A partir do primeiro baseline efetivamente entregue ao banco, sua semântica fica
congelada para compatibilidade futura; mudanças incompatíveis posteriores podem
exigir nova versão. Registre o snapshot e a entrega quando ocorrerem. Esta decisão
não afirma que tal baseline já existe nem redefine retrospectivamente versões de
outros contratos.

**Exceção:** wires entre repositórios independentes, como SP/AIR/storage, podem
precisar de versões durante o desenvolvimento para detectar snapshots incompatíveis
entre produtor e consumidor. Pins e validação dessas fronteiras continuam
obrigatórios. Essa exceção não justifica versionar uma política local interna só
porque seu comportamento foi corrigido antes do release; também não autoriza
alterar a AIR unilateralmente.

Aplicação: a [política CALL](../domain/cp6-call-name-policy.md) mantém
`cobol-zos-dynamic-call-minimal@1` ao aceitar `$` inicial. Não há novo identificador
nem modo legado para a rejeição incorreta. A auditoria posterior do incidente real
mede o recall recuperado; não condiciona a autorização desse requisito de produto.

## Controle local futuro

Um grafo finito de nós/transições pode carregar regras de push/pop/guard e uma
consulta contextual. Isso não torna todo caminho da projeção plana realizável.
Precisão para recursão não pode depender de enumerar infinitas pilhas. A escolha de
representação/algoritmo é discovery separado com literatura, adversariais e limite
operacional explícito. Registrar metadado de frame sem usá-lo nas consultas não
satisfaz matching preciso. Ver [controle local](../domain/local-control.md).

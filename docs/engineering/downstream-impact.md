# Findings e impacto downstream

Cada finding deve identificar regra, publicação/capability, evidência, escopo e
classe primária: `CONTRACT_GAP`, `PRODUCER_GAP`, `CONSUMER_GAP`, `INFRASTRUCTURE`,
`PRECISION_LIMIT`, `INVALID_IR` ou `UNASSESSED`. Esta classificação é local ao harness,
não enumeração da IR. Não confundir impacto, severidade e autorização de remediação.

`UNASSESSED` exige condição explícita de reavaliação. Exemplo: não sabemos se uma
forma COBOL satisfaz o matching topo-only; reavaliar quando houver regra de lowering
com dialeto/precondições e fixtures adversariais. Não alterar o CFG por hipótese.

O finding deve responder: falta fato upstream? Falta primitive no contrato? O consumer
não implementou uma primitive existente? O adapter perdeu campo? Ou o programa é
representado corretamente, mas o método escolheu aproximação?

Registrar origem, contraexemplo, efeito no MVP/perfil, testes afetados e proposta.
Fato observado independente pode ser preservado, mas não autoriza completar uma
consulta cuja região de influência é desconhecida. Corrigir no menor domínio que
é autoridade da regra, não necessariamente no arquivo onde o erro foi percebido.

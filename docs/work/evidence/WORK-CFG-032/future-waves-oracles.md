# Plano futuro — requer nova autorização

**DISCOVERY / NOT IMPLEMENTED.** Nenhum teste RED, fixture de produto, API ou
implementação abaixo foi criado. [Relatório](../../../architecture/cp6-call-dependency-discovery.md)
e [gaps factuais](gap-matrix.md) fundamentam o plano.

## Fronteira anterior à primeira wave

Review humano deve escolher explicitamente: transporte com assessment I-56 sem
quebrar a API estrita existente; fitting/nome para PIC X(8) com autoridade de dialeto;
perfil conservador de effects/outcomes e recusa de controle aberto não suportado;
produto de dependency separado do resultado W5. Não é uma wave de implementação
escondida. Até a autorização seguinte, tudo permanece NOT_AUTHORIZED.

## W1 proposta — primeira vertical singleton + controle literal

| Campo | Proposta |
| --- | --- |
| Repos | proleap-poc, cobol-lower, air-java, analysis-cfg; artefatos-e2e somente para orchestration/evidence autorizada separadamente |
| Escopo produtivo | Pure COBOL, uma entrada, MOVE literal scalar text ajustado, CALL literal ou WS inteiro, continuação explícita ao GOBACK; sem USING/RETURNING/handlers; Invoke neutro e perfil conservador |
| Contratos alterados | SP CallFact/target/access/continuation + prova de fitting; domínio lower; cobertura AIR JSON e assessment explícito; CFG outcomes/index/snapshot se publicado; perfil values de effects; dependency output separado |
| Contratos preservados | AIR 2.0 normativa; modelo Invoke já existente; solver/lattice genéricos; W4 SPI; DefaultValuePlan e result-v1 W5 compatíveis |
| RED principal | Arquivo COBOL exato X(8) deve atravessar CLI SP→AIR→CFG→PossibleValues→Dependency e obter PROGA com support MOVE; hoje lower recusa |
| RED controle | CALL literal deve atravessar o mesmo pipeline e preservar target/origin, sem solver values desnecessário; hoje SP perde nome |
| RED de fronteira | Codec Invoke estruturalmente válido com I-56; receiver conserva obligation; INVALID_IR/resource failure nunca aceitos. Round-trip preserva IDs/expressão/origins/signature/effects/outcomes |
| RED soundness | Chamada anterior + backedge, e Invoke antes de outra observação, abrem valores afetados; noop não pode passar. No USING não autoriza NoMemory sem prova |
| Definition of done | E2Es fonte real, zero injeção pós-lower; supports/origins e três remainders corretos; admissões negativas explícitas; gates por repo + W1–W5 existentes verdes; receipts exatos cross-repo |
| Dependências | Review das decisões; contratos SP/transport/publicação coerentes antes do consumo; perfil de efeitos antes de admitir Invoke em análise de valores |
| Human review boundary | PRs focais por repo sob autorização nova; revisar contratos e provas de efeitos; nenhuma expansão automática para USING ou wave seguinte |

Sequência mínima dentro da vertical, com ownership:

1. **proleap-poc** publica target literal/data distinto, identidade/acesso e successor
   do CALL; extensão semanticamente demonstrada do MOVE X8. Publica restrições e
   evidência que sustentam assinatura parcial/efeitos/outcomes; não simula runtime values.
2. **air-java** transporta o subconjunto neutro necessário, incluindo Read e eventual
   expressão de interpretação de nome, effects/outcomes e signatures. Resolve a
   política I-56 somente conforme decisão revisada. Testa literal e computed round-trip.
3. **cobol-lower** preserva payload no domínio, aplica prova de fitting, emite Assign
   real e Invoke real com continuação/outcomes, target e origins. Rejeita features
   fora da slice com diagnósticos independentes; não filtra CALL desconhecido.
4. **analysis-cfg** projeta Invoke/outcomes e indexa o site; estende perfil genérico
   de efeitos; registra consumer e reachability; serializa produto de dependency.
   Gate/inventário só evolui junto do código autorizado e provas correspondentes.
5. **artefatos-e2e** roda a fonte até dependency, registra SHAs/trees/hashes/outputs
   e verifica os oráculos independentes. Não marcar PASS em boundary não exercitada.

Passos 1 e 2 podem ter desenvolvimento independente após autorização, mas o aceite
é vertical. Não implementar toda AIR invocation, todos os modos ou todas as
expressões antes do primeiro CALL real. O subset deve ser transitivamente suficiente
para a forma emitida e conservador nas formas restantes.

## W2 proposta — IF real, multi-candidate e remainder aberto

| Campo | Proposta |
| --- | --- |
| Repos | proleap-poc, cobol-lower, air-java, analysis-cfg; artefatos-e2e conforme autorização |
| Escopo produtivo | IF simples com dois ramos MOVE, merge e CALL posterior; branch conservadora tipada quando condição não é resolvida; variante com caminho sem definição |
| Contratos alterados | SP controle de filhos/successors e predicado bool conservador; domínio/assembler IF lower; codec Branch/Jump/predicado usado; consumer mantém contrato W1 |
| RED principal | PROGA/PROGB de MOVEs distintos chegam ao CALL real com supports separados, independentemente da ordem de serialização |
| RED open | Um ramo define PROGA, outro conserva entrada desconhecida: PROGA + model open; nenhum nome inventado |
| Definition of done | Diamond COBOL real, joins e supports corretos; unknown-only e unreachable distinguíveis; W1 e CP5 continuam verdes; sem redefinir lattice/solver |
| Dependências | W1 revisada; fatos de controle SP suficientes; efeitos/outcomes W1 preservados |
| Human review boundary | Review da normalização IF/unknown predicate e prova de source remainder; encerrar sem iniciar parâmetros/interprocedural |

É possível entregar singleton/literal antes de ampliar IF porque o gap do segundo
é upstream/control transport. O diamond e joins já são suportados pelo CFG/values
atual; isso evita uma wave artificial de solver. Unknown-source MOVE arbitrário
requer seu próprio lowering/havoc genérico e permanece fora caso ultrapasse o IF
simples. O oracle de valor aberto abaixo continua obrigatório para o contrato.

## Oráculos propostos, ainda não executáveis como CP6

### Controle literal

```cobol
IDENTIFICATION DIVISION.
PROGRAM-ID. CALLER.
PROCEDURE DIVISION.
    CALL 'PROGA'.
    GOBACK.
```

Esperado: CALL vira Invoke real com LiteralTarget program/reference namespace,
nome interpretado PROGA e origin source-derived. Site alcançável no modelo produz
um candidato; support do literal CALL, nenhum Assign fabricado. Values solver runs
para esse consumer = 0; reachability ainda verificada. Nenhum source de PROGA exigido.
Mesma família de output da variante dinâmica.

### Dinâmico singleton — manter X(8)

```cobol
IDENTIFICATION DIVISION.
PROGRAM-ID. CALLER.
DATA DIVISION.
WORKING-STORAGE SECTION.
01 WS-PGM PIC X(8).
PROCEDURE DIVISION.
    MOVE 'PROGA' TO WS-PGM.
    CALL WS-PGM.
    GOBACK.
```

Esperado após W1 autorizada:

- SP preserva CALL, DataItemId e acesso inteiro; lower emite ObjectId correspondente.
- Assign deriva do MOVE real e representa o resultado ajustado `PROGA   ` sem
  falsificar o conteúdo de X(8). Target conserva leitura do mesmo objeto.
- Query usa `BEFORE(entry, invokeOperationId)`, não o último Assign nem AFTER Invoke.
- Raw value candidato é `PROGA   `; interpretação de nome aprovada deriva referência
  `PROGA`. Se o target expression já realiza trim neutro, seu resultado é PROGA,
  conservando a ligação ao raw value e ao Assign. Decisão de interpretação deve
  aparecer no receipt/contract e na origem derivada.
- Support aponta Assign/statement MOVE real, com provenance original/expanded do
  source; site e target possuem suas próprias origins.
- Model remainder **false** no BEFORE linear sem chamada anterior; source remainder
  **conforme evidência real** (pode ser true); effective = model OR source. Não retirar
  a lacuna runtime/contract apenas para obter false/false/false.
- Output caller CALLER → candidate PROGA é referência possível, sem linkage/runtime
  lookup. O E2E usa o lower real: nenhuma Publication/value/dependency manual após ele.

### Dinâmico multi-candidate

Usar a declaração X(8) e condição simples sobre FLAG com binding real:

```cobol
IF FLAG = 'Y'
    MOVE 'PROGA' TO WS-PGM
ELSE
    MOVE 'PROGB' TO WS-PGM
END-IF
CALL WS-PGM.
```

Esperado: THEN/ELSE → merge → Invoke real, sem ordenar control por IDs/linhas.
BEFORE contém raw PROGA/PROGB ajustados, nomes PROGA/PROGB, model remainder false
se ambos caminhos definem e não há efeito anterior que o abra; source remainder
independente. **PROGA → Assign A; PROGB → Assign B**. Inverter ordem física das
Sequences ou requests não troca nem globaliza supports. W4 compartilha run por key.

### Open remainder e unknown-only

Caso contratual `MOVE unknown-source TO WS-PGM; CALL WS-PGM`: se o profile/lower
futuro admite esse write como unknown, candidates vazio, model remainder true,
nenhum programa inventado. No baseline tal MOVE pode ser recusado; não confundir
UNSUPPORTED com empty/closed.

Primeiro E2E aberto menor: IF define PROGA em um ramo e no outro não define WS-PGM,
cujo estado inicial é desconhecido; CALL depois. Esperado known candidates {PROGA},
model open=true, support somente do MOVE PROGA, effective open=true. Source open
mantém suas próprias razões. Graph export emite edge possível PROGA associado ao
site open; preserva registro do site mesmo sem nenhum candidato.

### Efeitos e loop

Propor probes/testes AIR genéricos autorizados de may-write scope e must-overwrite
com prova, incluindo backedge, aliases admitidos e cells fora/dentro de escopo.
Aplicar effects no fixpoint. Para CALL USING, a primeira slice deve recusar antes
de apresentar valor otimista. Para no USING com limite desconhecido, o perfil
conservador deve abrir todos os cells possivelmente afetados. Um fixture com
efeitos conhecidos none só é controle válido com autoridade/evidência explícitas.

Se o source de loop/PERFORM ainda não atravessar o lower na W1, reportar teste de
efeito como AIR/model-level, não como COBOL E2E. A ausência desse lower não autoriza
deixar o transfer otimista para grafos AIR já recebidos.

### Unreachable, rejeições e determinismo

Site estrutural órfão: output diagnóstico UNREACHABLE_IN_MODEL, sem graph edge.
Ausência de candidatos por unknown: OPEN_TARGET, distinta do órfão. Unsupported
expression/policy/ASG/lower: diagnóstico e remainder indisponível, sem zero exato.
Testar todos com invariância a ordem de units/sequences, requests repetidos e
dois consumidores que pedem o mesmo ponto. Preservar origin IDs e candidate-specific
supports por round-trip do novo output; não depender de toString/nomes Java como wire.

## Fora dessas waves

Tracing USING #1/#N, RETURNING, ON/NOT ON EXCEPTION, nested lookup, efeitos por
outcome mais precisos, corpos de callee, interprocedural propagation, CICS, IMS,
DB2, GRBE, arquivos, tabelas SQL, linking/deployment, corpus resolution, CSV/Neptune
persistência e UI. São possibilidades do modelo consumer-driven, não promessa de
entrega ou autorização implícita.

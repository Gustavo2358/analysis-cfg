# Estado

## Onde estamos

CP1a de sincronização contratual executado; work item segue `active`, autorização
`discovery`. Branch para review; Java/POM/BACKLOG-CFG-002 não autorizados.

## Verde conhecido

Analysis IR 2.0.0 pinada no merge `0b2fbce7046010b22b32efa8cbc3e75ccba09442`;
blobs verificados. `check-fast` e 30 testes do harness passam. Gates de produto
permanecem `UNAVAILABLE`; nenhum perfil AIR foi implementado.

## Restante

Human review do PR; fechar ownership do modelo IR, coordenadas Maven, binding das
fixtures e contrato físico da porta. Não iniciar BACKLOG-CFG-002 automaticamente.

## Descobertas que afetam o plano

V2 preserva a topologia Sequence/terminador e exige `TypeRef`, `sameDomain` e
`DomainProofScope`. `unknown(known(bool))` admite branch; `unknown_type` não.
Perfis passam a `@2`; extensões local/indirect permanecem `@1`.

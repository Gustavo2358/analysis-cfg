# Validação desta entrega

Executada em 06/09/2026, no repositório local. Esta é evidência do **harness**, não de
uma implementação Java, da IR ou do CFG.

| Verificação | Resultado |
| --- | --- |
| check-docs.sh | PASS, exit 0 |
| check-harness.sh | PASS, 38 testes, exit 0 |
| check-fast.sh | PASS, exit 0 |
| architecture / semantic / performance / integration / full | UNAVAILABLE, exit 3, como previsto |
| JSONs alterados / `git diff --check` | válidos, exit 0 |
| Sintaxe Bash dos oito entrypoints | válida |
| Execução dos entrypoints fora da raiz do repositório | verificada a partir de /tmp |
| Inventário Java/POM na entrega | nenhum arquivo |
| Metadados de perfis implementados | coleção vazia, como previsto |

## Falsificações testadas

Links inválidos/fora da raiz; invariantes desconhecidos/duplicados; ciclos e
referências pendentes no backlog; registry divergente; work item concluído deixado
em active; arquivos extras no work item; sentinelas de arquivos Java/POM na fase
docs-only; falso gate implementado; perfil falsamente declarado; obrigação removida;
commit móvel em fonte; JSON com chave duplicada; mudança de fase sem autorização
correlacionada; contexto obrigatório ausente; gate indisponível que tenta passar;
eval/work item não resolvido; backlog concluído sem evidência; SHA móvel ou baseline
AIR divergente no `air-java`; Java diferente de 21; owner incorreto do binding JSON;
commit/API fabricado para `cobol-lower` planejado; SHA de `proleap-poc/main`
divergente da última revisão mergeada registrada.

Testes locais também verificaram cache de referência por hash Git, rejeição de
conteúdo divergente, bloqueio de path traversal, publicação somente após validação
e detecção de cache alterado. Usaram fontes sintéticas de teste, não rede.

## Limitações

Não há Java, POM, parser/codec de fixtures IR, solver de CFG ou suíte semântica de
produto. Os 26 evals são planejados. Os oráculos AIR ainda não foram executados por
uma implementação de CFG. Os 38 testes validam somente o harness.

Na inspeção externa deste checkpoint, `air-java ./scripts/check.sh` e `mvn verify`
passaram com 94 checks; o CI remoto do SHA fixado também estava verde. Isso é
evidência do modelo/validator upstream, não do CFG. A importação local do cache e sua
integridade foram testadas; os 21 documentos IR integrais não estão embutidos.

A validação offline não autentica autorização humana, confirma merge de PR, verifica
URLs remotas ou prova soundness. Scripts, políticas, testes futuros e review são
camadas complementares, não garantia absoluta contra erro de agente.

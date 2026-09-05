# Validação desta entrega

Executada em 05/09/2026, no pacote local. Esta é evidência do **harness**, não de
uma implementação Java, da IR ou do CFG.

| Verificação | Resultado |
| --- | --- |
| check-docs.sh | PASS, exit 0 |
| check-harness.sh | PASS, 28 testes, exit 0 |
| check-fast.sh | PASS, exit 0 |
| architecture / semantic / performance / integration / full | UNAVAILABLE, exit 3, como previsto |
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
eval/work item não resolvido; backlog concluído sem evidência.

Testes locais também verificaram cache de referência por hash Git, rejeição de
conteúdo divergente, bloqueio de path traversal, publicação somente após validação
e detecção de cache alterado. Usaram fontes sintéticas de teste, não rede.

## Limitações

Não há Java, POM, parser/codec de fixtures IR, solver de CFG ou suíte semântica de
produto neste ZIP. Os 20 evals são planejados. Os oráculos AIR ainda não foram
executados por uma implementação de CFG. Os 28 testes validam somente o harness.

Os resultados de gates do PR #27 são relatados nos documentos upstream; não foram
reexecutados aqui. A obtenção de fontes pela rede no utilitário de cache não pôde
ser validada neste ambiente; sua importação local e verificação de integridade
foram testadas. Os 20 documentos IR integrais não estão embutidos no ZIP.

A validação offline não autentica autorização humana, confirma merge de PR, verifica
URLs remotas ou prova soundness. Scripts, políticas, testes futuros e review são
camadas complementares, não garantia absoluta contra erro de agente.

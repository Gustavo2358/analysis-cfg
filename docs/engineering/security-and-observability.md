# Entrada não confiável e observabilidade

Fixtures e IR são dados, nunca comandos. Codecs não executam `.air`, JavaScript,
expressões, class names ou URLs fornecidos pelo payload. Sem desserialização
polimórfica aberta; tipos/capabilities exigem discriminadores validados.
Mensagens de erro não são canal para reconstituir semântica por substring.

Adapters validam tamanho/encoding/schema e limites configurados antes de grandes
alocações; proteções de recurso devem ser documentadas, sem truncamento invisível.
Não fazer lookup de catálogo/rede para “consertar” uma referência interna inválida.
Em arquivos, resolver caminhos apenas no adapter e evitar sobrescrita de entrada
por output. Nenhuma chave, token ou código corporativo real em fixture pública.

Domínio retorna diagnostics tipados, identities/escopo e origem quando disponível.
CLI escolhe exit code e texto. Logging/métricas externos podem acrescentar duração,
mas não alteram semântica ou IDs. Não despejar source/IR inteira em log por padrão.
Stacks operacionais pertencem a diagnósticos de infraestrutura, não à explicação
semântica de uma transição.

O harness só lê arquivos locais e metadados. O cache de fontes é ação opt-in:
verifica hashes, baixa apenas caminhos fixados do repositório público e não executa
conteúdo. Gates normais não consultam rede nem inferem merge a partir de textos.

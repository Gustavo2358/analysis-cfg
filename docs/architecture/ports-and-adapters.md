# Porta estável, transportes substituíveis

## Porta de entrada

Contrato conceitual, não código Java nem API congelada:

```text
BuildCfg(Publication, BuildOptions) → CfgBuildResult
```

Publication pertence ao modelo semântico IR compartilhado. BuildOptions declara
escopo de entries, capacidades/modos de precisão e limites. Resultado é tipado e
imutável. Não contém handles de arquivo, conexões ou callbacks para completar fatos.
IDs inteiros/strings não viram chaves globais sem namespace.

A implementação recebe dependências por construção; não escolhe adapter consultando
ambiente. O caller pode ser um teste, CLI ou módulo de um monólito. A porta é a mesma.

## Arquivos agora

CLI/driver recebe path → adapter lê bytes → codec valida representação de fixture →
materializa Publication → caso de uso valida fechamento/semântica estrutural → CFG →
adapter exporta resultado. Erro de arquivo/encoding/JSON é `INPUT_ERROR` no adapter;
IR com label pendente é `INVALID_IR`; capability legítima não implementada é
`UNSUPPORTED_CAPABILITY`. Não trocar essas classes para disfarçar falha.

O formato de fixture será um contrato técnico versionado e pequeno, decidido antes
do Java. A notação `.air` dos exemplos upstream é informativa, **não parser/esquema
oficial**. Não implementar parser ad hoc de exemplos. Não presumir que
`semantic-product.json` é Analysis IR.

## Memória depois — e nos testes desde o início

CobolLower fornece Publication diretamente à mesma porta. A chamada é Java normal,
sem escrever temporários, abrir rede ou converter para JSON e voltar.
Testes de domínio já seguem esse caminho desde a primeira implementação.
O adapter em memória pode ser simplesmente o caller: não inventar `MemoryReader`
ou repository para passar um objeto pronto.

## Portas de saída

Construir CFG não exige persistência: devolver CfgBuildResult basta. Exportador de
JSON/DOT é adapter que consome esse resultado fora do core. Se futuramente um caso
de uso exigir publicar/armazenar resultados, definir uma porta de saída **orientada
à necessidade** (`PublishCfg`, por exemplo), implementada pela infraestrutura.
Não introduzir storage obrigatório só para desenhar um hexágono.

## Prova de substituição obrigatória

Uma Publication deve chegar por fixture decodificada e por construção em memória.
Com mesmas opções/identidades, os resultados semânticos devem ser equivalentes.
Compare nós, outcomes, gaps, origens e precisão; não comparar texto de console.
A mesma prova será reutilizada no monólito modular. Mudam adapters e wiring,
**não a porta nem o algoritmo** (INV-CFG-002; EVAL-CFG-008).

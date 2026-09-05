# Primeira sessão no repositório

## 1. Validar a baseline

O repositório já contém o harness revisado. Antes de qualquer trabalho, confirme que
a cópia local está íntegra e que nenhum Java/POM apareceu sem autorização.

## 2. Validar o harness

```bash
bash scripts/harness/check-fast.sh
```

Execute a partir de qualquer diretório: os entrypoints localizam a raiz por seu
próprio caminho. Resultado esperado nesta fase: `PASS` em docs e harness. Um gate
Java chamado agora deve terminar com código 3 e `UNAVAILABLE`, não com sucesso.

## 3. Abrir a rota de conhecimento

Leia [AGENTS.md](AGENTS.md), o [mapa arquitetural](ARCHITECTURE.md) e o
[work item inicial](docs/work/active/WORK-CFG-001/spec.md).
As fontes fixadas estão no [índice de fontes](docs/sources/index.md).
Para cache offline opcional, com uma cópia local do contrato:

```bash
python3 scripts/harness/cache_ir.py --from-dir ../analysis-ir
```

Ou, com acesso à rede, `python3 scripts/harness/cache_ir.py --download`.
Os arquivos são verificados pelo hash Git do blob, não apenas pelo nome.
A criação desse cache não autoriza mudança da versão IR.

## 4. Autorizar somente o discovery

Use o [prompt inicial](docs/prompts/start-discovery.md). Ele libera apenas o
planejamento executivo: ownership do modelo IR, fronteiras Maven, transporte de
fixture e critérios de aceitação. O agente deve registrar autorização no work item,
atualizar os documentos, rodar `fast` e parar para review.

Não é necessário terminar frontend ou lowering para começar testes do consumidor.
Mas fixtures do CFG devem ser **publicações IR**, não `semantic-product.json`.
A diferença está no [estado upstream](docs/sources/upstream-state.md).

Depois do review, promova o primeiro item de implementação do backlog para um novo
work item com paths e testes concretos. Não autorize “implementar todo o backlog”.

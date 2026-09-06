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
[índice de trabalho](docs/work/index.md). `WORK-CFG-001` está concluído e seu
[resumo](docs/work/history/WORK-CFG-001.md) registra a baseline pré-Java.
As fontes fixadas estão no [índice de fontes](docs/sources/index.md).
Para cache offline opcional, com uma cópia local do contrato:

```bash
python3 scripts/harness/cache_ir.py --from-dir ../analysis-ir
```

Ou, com acesso à rede, `python3 scripts/harness/cache_ir.py --download`.
Os arquivos são verificados pelo hash Git do blob, não apenas pelo nome.
A criação desse cache não autoriza mudança da versão IR.

## 4. Próxima autorização

O próximo item é `BACKLOG-CFG-002`: bootstrap Java 21/Maven, dependência no
`air-java` e validação da boundary. O backlog não o autoriza. Crie novo work item
somente após autorização explícita; não reutilize a autorização de discovery.

Não é necessário terminar frontend ou lowering para começar testes do consumidor.
Entradas de teste do CFG são **`air-java Publication`**, não
`semantic-product.json`. JSON/arquivo é adapter posterior; `CFG-FIRST` começa em
memória.
A diferença está no [estado upstream](docs/sources/upstream-state.md).

Depois do review, promova o primeiro item de implementação do backlog para um novo
work item com paths e testes concretos. Não autorize “implementar todo o backlog”.

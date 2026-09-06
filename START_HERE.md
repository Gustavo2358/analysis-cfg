# Primeira sessão no repositório

## 1. Validar a baseline

O repositório contém um harness revisado, a fundação Java/Maven de WORK-CFG-002 e a
porta/seam de WORK-CFG-003 e o CFG-FIRST de WORK-CFG-005. Antes de qualquer
trabalho, confirme que a cópia local está íntegra
e siga o work item ativo ou o próximo backlog explicitamente autorizado.

## 2. Validar o harness

```bash
bash scripts/harness/check-fast.sh
```

Execute a partir de qualquer diretório: os entrypoints localizam a raiz por seu
próprio caminho. Resultado esperado: `PASS` em docs e harness. Depois de instalar o
`air-java` fixado no repositório Maven usado pelo consumer, execute também:

```bash
bash scripts/harness/check-architecture.sh
bash scripts/harness/check-semantic.sh
```

Architecture executa 38 testes Java e verifica bytecode/dependências reais.
Semantic seleciona os 20 testes obrigatórios de EVAL-CFG-025 e verifica seus nomes
e resultados, falhando com ausência ou skip.

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

## 4. Porta disponível e próxima autorização

`BuildCfg` recebe diretamente `io.github.gustavo2358.air.model.Publication` e
`BuildOptions`; o coordinator delega o preflight a
`io.github.gustavo2358.air.validation.AirValidator` e retorna `CfgBuildResult`.
No slice suportado, `CFG_BUILT` contém Entry/Sequence/NormalExit e transições
ENTRY/RETURN; falhas não contêm grafo. Consulte a
[estratégia do SNAPSHOT](docs/engineering/toolchain-and-modules.md) antes de rodar
Maven em um checkout limpo.

Não é necessário terminar frontend ou lowering para começar testes do consumidor.
Entradas de teste do CFG são **`air-java Publication`**, não
`semantic-product.json`. JSON/arquivo é adapter posterior; `CFG-FIRST` começa em
memória.
A diferença está no [estado upstream](docs/sources/upstream-state.md).

CFG-FIRST implementa apenas Entry/Return. BACKLOG-CFG-022 (linear/jump/halt)
continua planejado e exige autorização própria; não avance automaticamente.

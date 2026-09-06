# Fontes, autoridade e leitura sob demanda

## Autoridade

Requisito explícito do usuário governa produto/arquitetura. Semântica de operação é
a **Analysis IR 2.0.0 fixada**. Se uma solicitação exige comportamento incompatível,
registrar conflito e propor evolução; não reinterpretar a operação silenciosamente.
ADRs/contratos locais refinam desenho sem contrariar IR. Código e testes são evidência,
não autoridade normativa isolada. História/conversa anterior não vence contrato.

## IR normativa — commit 0b2fbce7046010b22b32efa8cbc3e75ccba09442

O commit é o merge canônico do PR upstream #1. Não usar o antigo head do PR nem
`main` flutuante como autoridade; hashes de blobs estão no lock.

- [README.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/README.md)
- [REFERENCIAS.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/REFERENCIAS.md)
- [conformidade/01-invariantes.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/conformidade/01-invariantes.md)
- [conformidade/02-oraculos.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/conformidade/02-oraculos.md)
- [especificacao/00-escopo-e-convencoes.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/00-escopo-e-convencoes.md)
- [especificacao/01-modelo-e-identidades.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/01-modelo-e-identidades.md)
- [especificacao/02-tipos-valores-e-operandos.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/02-tipos-valores-e-operandos.md)
- [especificacao/03-memoria-e-aliases.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/03-memoria-e-aliases.md)
- [especificacao/04-operacoes.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/04-operacoes.md)
- [especificacao/05-controle-e-invocacoes.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/05-controle-e-invocacoes.md)
- [especificacao/06-incompletude-e-proveniencia.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/06-incompletude-e-proveniencia.md)
- [especificacao/07-contrato-de-produtores.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/07-contrato-de-produtores.md)
- [especificacao/08-contrato-de-consumidores.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/08-contrato-de-consumidores.md)
- [especificacao/09-extensibilidade-e-compatibilidade.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/09-extensibilidade-e-compatibilidade.md)
- [especificacao/10-perfis-de-conformidade.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/10-perfis-de-conformidade.md)
- [especificacao/11-rastreabilidade-bilateral.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/especificacao/11-rastreabilidade-bilateral.md)
- [exemplos/00-notacao.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/exemplos/00-notacao.md)
- [exemplos/01-fluxo-e-valores.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/exemplos/01-fluxo-e-valores.md)
- [exemplos/02-memoria-e-chamadas.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/exemplos/02-memoria-e-chamadas.md)
- [exemplos/03-extensoes-e-parcialidade.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/exemplos/03-extensoes-e-parcialidade.md)
- [exemplos/04-conhecimento-de-tipo.md](https://github.com/Gustavo2358/analysis-ir/blob/0b2fbce7046010b22b32efa8cbc3e75ccba09442/exemplos/04-conhecimento-de-tipo.md)

`§01/04/05/06/08/09/10` designam capítulos pelo prefixo do arquivo.
O-01–O-85 são oráculos upstream; X-01–X-40 são exemplos informativos.
A sintaxe desses exemplos **não é schema de arquivo**.

Leitura para MVP: §01; `TypeRef`, `sameDomain` e `DomainProofScope` pertinentes de
§02; operações do subset em §04; §05.1–6, §06, §08.1–2 e §10; exemplos X-01–X-04,
X-33/X-34 e oráculos correlatos. Controle local acrescenta §05.7, X-23/X-24 e
O-56–O-60. Não carregar capítulos de memória inteira para implementar um jump.

## Inspiração e contexto

[AGENTS.md upstream](https://github.com/Gustavo2358/proleap-poc/blob/3972c669fe187004769becbd6bff00af90d6c9dd/AGENTS.md),
[protocolo](https://github.com/Gustavo2358/proleap-poc/blob/3972c669fe187004769becbd6bff00af90d6c9dd/docs/engineering/work-item-protocol.md),
[política semântica](https://github.com/Gustavo2358/proleap-poc/blob/3972c669fe187004769becbd6bff00af90d6c9dd/docs/engineering/semantic-analysis-policy.md),
[testes](https://github.com/Gustavo2358/proleap-poc/blob/3972c669fe187004769becbd6bff00af90d6c9dd/docs/engineering/semantic-testing.md),
[gates](https://github.com/Gustavo2358/proleap-poc/blob/3972c669fe187004769becbd6bff00af90d6c9dd/docs/engineering/gates.md),
[performance](https://github.com/Gustavo2358/proleap-poc/blob/3972c669fe187004769becbd6bff00af90d6c9dd/docs/engineering/performance-policy.md).

O PR #27 foi lido no head `9c53948089a6040d1666a5df812ba993ea3ca50b`, **aberto**, não presumido mergeado.
[Estado do Semantic Product](https://github.com/Gustavo2358/proleap-poc/blob/9c53948089a6040d1666a5df812ba993ea3ca50b/docs/work/active/WORK-SEMANTIC-PRODUCT-002/state.md).
[Resumo aplicado](upstream-state.md) e [o que foi adaptado](harness-adaptation.md).

## Bibliografia de engenharia

[Robert C. Martin — The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html): dependências internas não conhecem mecanismos externos.
[Alistair Cockburn — Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture): mesma aplicação acionada por adapters diferentes e testada isoladamente.
[LLVM Language Reference](https://www.llvm.org/docs/LangRef.html): exemplo primário de controle explícito por terminadores; não importar SSA ou sua regra especial de entry.
[Maven — Multiple Modules](https://maven.apache.org/guides/mini/guide-multiple-modules.html): reactor ordena dependências entre módulos; não dita desenho do domínio.
[Reps, Horwitz, Sagiv — POPL 1995](https://research.cs.wisc.edu/wpis/abstracts/popl95.abs.html): referência para caminhos realizáveis e análise interprocedural; o abstract foi consultado, não uma validação de aplicabilidade de IFDS ao domínio inteiro. O discovery deve ler trabalho completo e precondições antes de selecionar algoritmo.

Essas fontes públicas foram verificadas em 05/09/2026. Não são dependências de runtime.
Não houve revisão exaustiva de toda literatura nem benchmark nesta preparação.

## Atualização e cache

[sources.lock.json](sources.lock.json) é o registro de revisões e hashes.
O ZIP não contém os 20 documentos IR integrais. O [cache opcional](../../scripts/harness/cache_ir.py)
importa uma cópia local ou baixa o snapshot verificando os hashes Git; falha em
qualquer divergência. Nunca usa a branch móvel como fallback. Documentação e gates
básicos são offline; sem fonte normativa disponível, não implementar semântica
nova a partir apenas deste resumo.

Atualizar o lock exige work item com diff semântico, migração e regressão. A
verificação local não confirma estado remoto de PR nem disponibilidade futura de URLs.

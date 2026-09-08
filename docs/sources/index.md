# Fontes, autoridade e leitura sob demanda

## Autoridade

Requisito explícito do usuário governa produto/arquitetura. Semântica de operação é
a **Analysis IR 2.0.0 fixada**. Se uma solicitação exige comportamento incompatível,
registrar conflito e propor evolução; não reinterpretar a operação silenciosamente.
ADRs/contratos locais refinam desenho sem contrariar IR. Código e testes são evidência,
não autoridade normativa isolada. História/conversa anterior não vence contrato.

O owner físico do modelo/validator Java compartilhado é `Gustavo2358/air-java`,
também fixado por SHA. Ele implementa a AIR; não substitui a autoridade semântica do
`analysis-ir`. Versão da biblioteca e versão semântica da IR são eixos distintos.

## IR normativa — commit 122ce54e1b9ef9b00646f93ece409ca8b63bc933

O commit é o merge canônico do PR upstream #2. Não usar o antigo head do PR nem
`main` flutuante como autoridade; hashes de blobs estão no lock.

- [README.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/README.md)
- [REFERENCIAS.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/REFERENCIAS.md)
- [bindings/json-v1.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/bindings/json-v1.md)
- [bindings/revisao-json-v1.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/bindings/revisao-json-v1.md)
- [conformidade/01-invariantes.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/conformidade/01-invariantes.md)
- [conformidade/02-oraculos.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/conformidade/02-oraculos.md)
- [especificacao/00-escopo-e-convencoes.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/00-escopo-e-convencoes.md)
- [especificacao/01-modelo-e-identidades.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/01-modelo-e-identidades.md)
- [especificacao/02-tipos-valores-e-operandos.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/02-tipos-valores-e-operandos.md)
- [especificacao/03-memoria-e-aliases.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/03-memoria-e-aliases.md)
- [especificacao/04-operacoes.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/04-operacoes.md)
- [especificacao/05-controle-e-invocacoes.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/05-controle-e-invocacoes.md)
- [especificacao/06-incompletude-e-proveniencia.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/06-incompletude-e-proveniencia.md)
- [especificacao/07-contrato-de-produtores.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/07-contrato-de-produtores.md)
- [especificacao/08-contrato-de-consumidores.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/08-contrato-de-consumidores.md)
- [especificacao/09-extensibilidade-e-compatibilidade.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/09-extensibilidade-e-compatibilidade.md)
- [especificacao/10-perfis-de-conformidade.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/10-perfis-de-conformidade.md)
- [especificacao/11-rastreabilidade-bilateral.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/especificacao/11-rastreabilidade-bilateral.md)
- [exemplos/00-notacao.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/exemplos/00-notacao.md)
- [exemplos/01-fluxo-e-valores.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/exemplos/01-fluxo-e-valores.md)
- [exemplos/02-memoria-e-chamadas.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/exemplos/02-memoria-e-chamadas.md)
- [exemplos/03-extensoes-e-parcialidade.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/exemplos/03-extensoes-e-parcialidade.md)
- [exemplos/04-conhecimento-de-tipo.md](https://github.com/Gustavo2358/analysis-ir/blob/122ce54e1b9ef9b00646f93ece409ca8b63bc933/exemplos/04-conhecimento-de-tipo.md)

`§01/04/05/06/08/09/10` designam capítulos pelo prefixo do arquivo.
O-01–O-91 são oráculos upstream; X-01–X-41 são exemplos informativos.
A sintaxe desses exemplos **não é schema de arquivo**.

Leitura para MVP: §01; `TypeRef`, `sameDomain` e `DomainProofScope` pertinentes de
§02; operações do subset em §04; §05.1–6, §06, §08.1–2 e §10; exemplos X-01–X-04,
X-33/X-34 e oráculos correlatos. Controle local acrescenta §05.7, X-23/X-24 e
O-56–O-60. Não carregar capítulos de memória inteira para implementar um jump.

O merge acrescentou I-55–I-61 e O-86–O-91. Para esta boundary, as regras centrais
são: contrato materializado no `invoke` sem inventário `Publication.contracts`,
targets executáveis somente internal/literal/computed, `ArtifactRelationId`,
`disjoint_storage` universal, assinatura e outcomes com formas próprias, owners de
operandos completos, naturais sem teto de runtime e proveniência sem fabricar
linha/coluna quando há somente offsets.

O **Analysis IR JSON Binding 1.0.0** existe nesse commit, **targets AIR 2.0.0** e
permanece **DRAFT**. É contrato de transporte separado da versão semântica,
implementado exclusivamente pelo shared air-json e consumido por cfg-adapters em
WORK-CFG-026. A decisão humana libera o experimento sem promoção; não autoriza
gerar codec a partir dos records de air-java.

## Implementação Java e codec compartilhado — commit b78f4068d8a479f48eb048b8d76fa60a0997dc4a

- [README](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/README.md)
- [POM](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/pom.xml)
- [Publication](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Publication.java)
- [Sequence](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Sequence.java)
- [Terminator](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Terminator.java)
- [Operations](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Operations.java)
- [Interactions](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Interactions.java)
- [Control](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Control.java)
- [Origins](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Origins.java)
- [Proofs](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/model/Proofs.java)
- [AirValidator](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/src/main/java/io/github/gustavo2358/air/validation/AirValidator.java)
- [Reconciliação AIR 2.0](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/docs/reconciliation-air-2.md)
- [Cobertura e limites](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/docs/implementation-status.md)
- [CI verificado](https://github.com/Gustavo2358/air-java/actions/runs/34163367232/job/101869534574)

Coordenadas declaradas: `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT`, Java 21.
O parent é `air-java-parent`; `air-model/` publica o artefato `air-java` e
`air-json/` publica o codec compartilhado de cobertura 1A. O kernel continua dependendo
somente do modelo; WORK-CFG-026 acrescenta air-json apenas em cfg-adapters.

- [POM do modelo](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-model/pom.xml)
- [POM do codec](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-json/pom.xml)
- [AirJson](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/air-json/src/main/java/io/github/gustavo2358/air/json/AirJson.java)
- [Cobertura 1A](https://github.com/Gustavo2358/air-java/blob/b78f4068d8a479f48eb048b8d76fa60a0997dc4a/docs/engineering/air-json.md)

Não foi observada tag/release. O modelo reconciliado não possui `contracts[]`; usa
`ContractRef` como valor e materializa assinatura externa, effects e outcomes no
`invoke`. O check remoto `contracts` do SHA fixado terminou com `success`; isso é
evidência upstream, não resultado dos gates do `analysis-cfg`.

## Frontend COBOL e contexto

[AGENTS.md upstream](https://github.com/Gustavo2358/proleap-poc/blob/7a376f33f55127f53c63b86d3228671b9c6a348d/AGENTS.md),
[arquitetura](https://github.com/Gustavo2358/proleap-poc/blob/7a376f33f55127f53c63b86d3228671b9c6a348d/ARCHITECTURE.md),
[ADR da boundary](https://github.com/Gustavo2358/proleap-poc/blob/7a376f33f55127f53c63b86d3228671b9c6a348d/docs/architecture/decisions/0013-cobol-semantic-product-precedes-language-neutral-lowering.md),
[audit AIR V2](https://github.com/Gustavo2358/proleap-poc/blob/7a376f33f55127f53c63b86d3228671b9c6a348d/docs/architecture/semantic-product-air-v2-audit.md) e
[encerramento do audit](https://github.com/Gustavo2358/proleap-poc/blob/7a376f33f55127f53c63b86d3228671b9c6a348d/docs/work/history/WORK-SEMANTIC-PRODUCT-003.md).

Os PRs #27, #29 e #30 foram verificados como mergeados; `main` está em
`7a376f33f55127f53c63b86d3228671b9c6a348d`. O repo continua Java 17 e produz
`cobol-semantic-product`, não AIR. O PR #30 reforçou a ownership cross-repo;
`cobol-lower` permanece componente planejado e sem repositório/API observável.
[Resumo aplicado](upstream-state.md) e [o que foi adaptado](harness-adaptation.md).

## Bibliografia de engenharia

[Robert C. Martin — The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html): dependências internas não conhecem mecanismos externos.
[Alistair Cockburn — Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture): mesma aplicação acionada por adapters diferentes e testada isoladamente.
[LLVM Language Reference](https://www.llvm.org/docs/LangRef.html): exemplo primário de controle explícito por terminadores; não importar SSA ou sua regra especial de entry.
[Maven — Multiple Modules](https://maven.apache.org/guides/mini/guide-multiple-modules.html): reactor ordena dependências entre módulos; não dita desenho do domínio.
[Reps, Horwitz, Sagiv — POPL 1995](https://research.cs.wisc.edu/wpis/abstracts/popl95.abs.html): referência para caminhos realizáveis e análise interprocedural; o abstract foi consultado, não uma validação de aplicabilidade de IFDS ao domínio inteiro. O discovery deve ler trabalho completo e precondições antes de selecionar algoritmo.

Essas fontes públicas foram verificadas em 06/09/2026. Não são dependências de runtime.
Não houve revisão exaustiva de toda literatura nem benchmark nesta preparação.

## Atualização e cache

[sources.lock.json](sources.lock.json) é o registro de revisões e hashes.
O repositório não contém os 23 documentos IR integrais. O [cache opcional](../../scripts/harness/cache_ir.py)
importa uma cópia local ou baixa o snapshot verificando os hashes Git; falha em
qualquer divergência. Nunca usa a branch móvel como fallback. Documentação e gates
básicos são offline; sem fonte normativa disponível, não implementar semântica
nova a partir apenas deste resumo.

Atualizar o lock exige work item com diff semântico, migração e regressão. A
verificação local não confirma estado remoto de PR nem disponibilidade futura de URLs.

# Capability brief — programa → arquivo

## Scope lock normativo da campanha

**analysis boundary = COBOL_SOURCE_ONLY. SOURCE-CLOSED / INTRAPROGRAM.**
A pergunta é: quais dependências de arquivo são provadas pela semântica dos
fontes COBOL fornecidos, incluindo COPY/preprocessamento já autorizados?

`SELECT CUSTOMER-FILE ASSIGN TO CUSTOMERDD` fornece
`logicalFile = CUSTOMER-FILE` e `externalFileName = CUSTOMERDD`, derivado da
`assignment-name`. Esse nome externo é identidade terminal válida, assim como
`CALL 'XPTO123'` termina em programa → XPTO123. Declaração sozinha não cria uso.

No perfil IBM, o nome pode ser utilizado pelo runtime como ddname ou nome de
variável de ambiente; o fonte não prova qual mecanismo será usado. O contrato
conceitual usa `namespace = cobol.external-file-name`, `name = CUSTOMERDD`,
`sourceKind = ASSIGNMENT_NAME`; preserva a grafia original e a interpretação do
perfil. A apresentação pode dizer “assignment-name / DDNAME externo”, sem
afirmar allocation. Não usar `zos.ddname` como fato universal nem adicionar
`bindingMechanism`, sequer `bindingMechanism = UNKNOWN`. A pergunta não é modelada.
Autoridade e revisão: [perfis](profiles.md).

**Fora do domínio**, inclusive do schema: DDNAME → DSNAME, JCL, JOB/STEP/STEPLIB,
allocation, catálogos z/OS, datasets reais, filesystem, recurso físico, runtime,
resolução de ambiente e integração COBOL/JCL. Não criar campos para uma futura
resolução externa. O conteúdo de ASSIGN não autoriza abrir o arquivo referido.

**Ausência de DSNAME não é erro, PARTIAL, UNKNOWN, gap ou perda de confiança.**
Não existe uma dimensão de completude externa. Consultar manuais de linguagem
para implementar o analisador é permitido; consultar ambiente para resolver um
programa analisado é proibido.

## Modelo e glossário

| Conceito | Significado / separação |
| --- | --- |
| Owner | identidade canônica da unidade; PROGRAM-ID textual não basta |
| Conector / logical file | entidade FILE local que une SELECT e FD/SD; homônimos têm escopos |
| Alvo source-level | external file name derivado de assignment-name, literal de perfil ou nome CICS FILE |
| Registro / buffer | memória associada ao conector; alias não une arquivos |
| Declaração | inventário estrutural; sem I/O fictício |
| Site / uso | operação + owner + papel + origem; inclui CLOSE/START e I/O implícito |
| Candidate | valor com suporte individual e política de nomes do recurso |
| Remainder | possibilidades intraprograma que a prova não fechou |
| PARTIAL | dimensão intraprograma incompleta: inventário, binding nominal, valores, efeitos ou controle; nunca mecanismo externo |

Um alvo literal conhecido, um conjunto fechado `{A,B}`, `{A,B}+unknown remainder`
e desconhecido sem candidatos são quatro resultados distintos. Um conjunto vazio
conhecido não equivale a inventário indisponível. Não fechar conjuntos por cutoff.
Nomes calculados usam apenas evidência intraprograma; prova insuficiente conserva
expressão, candidatos comprovados e desconhecimento. Literais independem de solver.

## Invariantes de arquitetura e produto

1. Frontend reconhece/binda COBOL; lower traduz facts públicos; AIR representa;
   CFG projeta controle; providers gerais resolvem valores; consumer agrega;
   serializer transporta. Nenhuma camada downstream reinterpreta texto COBOL.
2. Reutilizar símbolos FILE, storage, aliases, efeitos e possible-values existentes.
   Nenhum lowerer separado só para arquivos; nenhum solver exclusivo de filenames.
3. Inventário de declarações e usos é separado de arestas alcançáveis. Uso visto
   sobrevive a CFG parcial; unreachable no modelo não significa não observado.
4. CALL e FILE são capabilities independentes. Efeitos/CFG/dataflow/serialization
   novos preservam evidências e resultados CALL, salvo correção justificada por
   novo efeito de I/O com oráculo independente. Proibido perder suporte disjunto
   ou conservar singleton antigo após sobrescrita provada.
5. SD não ganha nome externo persistente inventado. WRITE/REWRITE usam record ownership;
   FROM não cria leitura do arquivo dono do buffer fonte. SORT/MERGE preservam
   todos os papéis e procedimentos locais, sem CALL externo fictício.
6. Captura de nome dinâmico respeita a vida da conexão; CICS FILE usa o ponto do
   comando. Não reler a variável no READ para mudar retroativamente o OPEN.
7. Não confundir namespaces CICS FILE, external file name e conector por igualdade de grafia.
   Sem dependência transitiva pai → arquivo usado apenas por programa chamado.
8. Reusar AIR existente; extensão normativa só com produtor, consumer, informação
   indispensável e teste de perda. `analysis-ir` não muda preventivamente.

## Guardrail de revisão

Rejeitar diffs que introduzam lookup externo, campo/estado de resolução física,
normalização de path pela máquina, parsing downstream, perda de CALL, declaração
transformada em execução, assignment-name promovida a mecanismo DD comprovado,
ou promoção de UNKNOWN obrigatório a implementação
completa. A matriz e os negativos do [catálogo](test-catalog.md) tornam isso testável.
Qualquer proposta incompatível exige **outro produto e nova autorização**; não
uma opção oculta ou um backlog que condicione esta capability.

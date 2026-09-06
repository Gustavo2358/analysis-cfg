# WORK-CFG-002 — Bootstrap Java 21/Maven e boundary air-java

Status: `completed`. Data de conclusão: 06/09/2026. Autorização executada:
`implementation`, exclusivamente para BACKLOG-CFG-002. Base da `main`:
`4685dca32bf4f31a0e699ec6a3927018ab1b1d84`. Branch:
`codex/feat/cfg-java-air-boundary`.

## Resultado

O repositório agora possui parent Maven e somente o módulo `cfg-kernel`. A única
classe de produção é `CfgPreflight`, cuja boundary compilada é:

```text
io.github.gustavo2358.air.model.Publication
    → io.github.gustavo2358.air.validation.AirValidator.validate
    → io.github.gustavo2358.air.validation.ValidationResult
```

O preflight devolve diretamente o resultado upstream. Não repara `INVALID_IR`,
não oculta `INCOMPLETE_VALIDATION`/unsupported e mantém
`SEMANTIC_OBLIGATION` como obrigação, nunca como fato comprovado. EVAL-CFG-007 e
EVAL-CFG-024 foram implementados para esta fundação. EVAL-CFG-001 foi apenas
exercitado por um caso inválido representativo e continua `planned`.

## Upstreams e SNAPSHOT

- `analysis-ir/main`: `122ce54e1b9ef9b00646f93ece409ca8b63bc933`, AIR
  2.0.0, confirmado diretamente; 23/23 blob hashes conferidos;
- Analysis IR JSON Binding 1.0.0: presente, **DRAFT**, targets AIR 2.0.0 e não
  implementado neste consumer;
- `air-java/main`: `6a4091e5394fc22b3d2ada9abbdb530eb3572a58`,
  confirmado diretamente e reconciliado com a revisão AIR acima;
- coordenada: `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT`;
- CI upstream `contracts`: `completed/success` no mesmo SHA; execução local do
  checkout exato: 172 checks determinísticos, todos passando.

A resolução temporária faz checkout do source SHA completo, verifica `HEAD` e roda
`mvn clean install` a partir da raiz de `air-java`, em repositório Maven vazio e
isolado. O consumer usa depois o mesmo repositório. O workflow reproduz essa cadeia
em JDK 21 e o gate cruza o SHA do workflow com `sources.lock.json`. Nenhuma fonte,
JAR ou classe AIR foi vendorizada; release versionada ou reactor futuro poderá
substituir a preparação sem alterar o core.

## Testes e falsificações

Quatro testes JUnit reais foram executados, sem skip:

1. `Publication` e `AirValidator` carregados do JAR real atravessam a boundary;
2. referência estrutural inválida permanece `INVALID_IR`, sem reparo;
3. capability requerida desconhecida permanece `INCOMPLETE_VALIDATION`;
4. `SEMANTIC_OBLIGATION` permanece visível mesmo com status estrutural válido.

Evidência RED → GREEN:

- remoção temporária da dependência `air-java` fez `mvn clean test` falhar com seis
  erros de compilação nos packages/símbolos AIR; a restauração passou 4/4;
- referência temporária a `java.nio.file.Path` em `CfgPreflight` fez o gate final
  falhar com dependência bytecode inesperada; removida a mutação, o gate passou;
- após o hardening final, um `import java.nio.file.Path` não usado também falhou
  com exit 1 na guarda complementar; removido, o gate voltou a passar;
- `-Dtest=NoSuchBoundaryTest` falhou com exit 1 e “No tests matching pattern”,
  provando que suíte obrigatória vazia não produz falso verde;
- fixtures internas do gate rejeitam representantes das dependências proibidas e
  uma classe paralela, sem persistir fontes artificiais.

## Validação de encerramento

- refs remotos dos dois upstreams: confirmados nos SHAs fixados, exit 0;
- `air-java mvn -B -ntp clean install`, em checkout/SHA e Maven repo limpos: PASS,
  172 checks, exit 0;
- `analysis-cfg mvn -B -ntp clean verify`, em checkout limpo e mesmo Maven repo:
  PASS, 4 testes, 0 failures/errors/skips, exit 0;
- dependency tree compile: somente
  `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT`, exit 0;
- gate `architecture`: PASS; um classfile de produção, major 65/minor 0,
  `java.base`, assinatura/delegação exatas e source pin conferido, exit 0;
- gate `fast`: PASS, 41 testes do harness, exit 0;
- `docs` e `harness`: PASS, exit 0;
- `semantic`, `performance`, `integration` e `full`: `UNAVAILABLE`, exit 3, sem
  mascaramento;
- JSONs e `git diff --check`: PASS.

A máquina local dispõe de Temurin 25 e compilou com `--release 21`; major 65 e
ausência de preview foram inspecionados. A execução efetiva com Temurin 21 pertence
ao workflow do PR e deve ser tratada como evidência remota, não como execução local
antecipada.

## Escopo negativo e handoff

Nenhum `BuildCfg`, tipo/nó/aresta CFG, interpretação de `Return`, branch, invoke,
matching, controle aberto/local/indireto, JSON, arquivo, CLI, DOT, filesystem,
COBOL, lowerer, dataflow ou publicação upstream foi implementado. Nenhum perfil AIR
foi declarado implementado e nenhum upstream foi alterado.

**BACKLOG-CFG-002 concluído. BACKLOG-CFG-003 NÃO iniciado. CFG algorithm NÃO
implementado.** O trabalho para em PR aberto, sem merge nem auto-merge, aguardando
review humano.

# Fixture AIR GOBACK independente

Repository: Gustavo2358/air-java
Commit: b78f4068d8a479f48eb048b8d76fa60a0997dc4a
Path: air-json/src/test/resources/goback.canonical.json
Git blob SHA-1: 8f852589b7e2286beee2c8569e348d2b56301dab
SHA-256: fa299c2e5f3fae75afe365363b9f16925f0cfea591f631768ace82f0fb9a1075

Capturado por git show do commit, byte a byte. Golden canônico manual upstream,
não bytes de cobol-lower/2A. Nenhum setup chama AirJson.encode para produzir input.
Binding analysis-ir-json 1.0.0 / AIR 2.0.0, DRAFT pinado 122ce54e1b9ef9b00646f93ece409ca8b63bc933.

## Scalar Assign — 4B / 4D

[Proveniência escalar](scalar-assign.provenance.json): repository Gustavo2358/air-java,
merge ce530a7e17ab12b23c48f29425f503ff920b09fb,
upstream air-json/src/test/resources/scalar-assign.canonical.json,
blob b37ff744819132cd0bfe115ce9a4382b19a29060.
SHA-256 40b9cec1bcc1c1e40cf3b9e3c48e834835e478e1d84bccafa575d63497ef3b60, 14554 bytes.
Local scalar-assign.canonical.json byte-identical ao blob extraído por git show do merge,
sem AirJson.encode e sem output 4C. Guardas conferem SHA-256, Git blob e bytes upstream.
A proveniência GOBACK acima permanece histórica; seus bytes também foram conferidos
no merge 4B e o golden CFG original não mudou.

# WORK-CFG-025 — Proveniência 1A reconciliada

Status: `completed`; autorização: `discovery`. Encerrado em 2026-09-07 pelo merge real do [PR #9](https://github.com/Gustavo2358/analysis-cfg/pull/9).
Merge `b614712fda55fef12639cbe18fd90793faa1fb3b`, em `2026-09-07T22:47:42Z`.
Head do PR: `eb98b887627f5b01d0b5f32331e219e3a5ccb1c6`.
Consulta `gh pr view 9 --json state,mergeCommit,mergedAt,headRefOid,reviews,statusCheckRollup` confirmou MERGED e CI SUCCESS; reviews retornou lista vazia. Não se infere aprovação/review humano.
`git cat-file -t` confirmou commit e `git merge-base --is-ancestor` confirmou ancestralidade da main atual, cujo baseline limpo após fetch/pull --ff-only é o próprio merge.

Pin preservado: air-java b78f4068d8a479f48eb048b8d76fa60a0997dc4a e AIR 2.0.0 em analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933. Evidência original em [validation](../evidence/WORK-CFG-025/validation.md); cinco arquivos ativos preservados no merge acima antes do arquivamento. Não houve produto 2A/2B nesse item.
Nova autorização implementation para 2B pertence exclusivamente a WORK-CFG-026.

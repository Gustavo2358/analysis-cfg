# WORK-CFG-025 — Plano

## Fatiamento

CP0 único: confirmar merge/fontes, atualizar proveniência, verificar paths/URLs e história, executar gates e build atuais, revisar diff integral, certificar quando aplicável, commit/push e PR próprio. Após CI verde no head, parar para review humano. Nenhum próximo checkpoint iniciado.

## Dependências

Baseline main `141b8270f54558a24ee561281598e53c48a0ff6b`; upstream autorizado `b78f4068d8a479f48eb048b8d76fa60a0997dc4a`; norma `122ce54e1b9ef9b00646f93ece409ca8b63bc933`. Instalar o reactor upstream completo em Maven repo temporário isolado com Java 21, sem editar o upstream. A CI continua construindo da raiz upstream; o modelo mantém suas coordenadas Maven.

## Artefatos

Source lock, documentação de proveniência e pacote de trabalho/evidência. No CFG, também os dois pins do checkout CI. Preservar todos os arquivos de implementação.

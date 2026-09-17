# D-EFFECT — autoridade e plano de prova W3/W4

MEMÓRIA W3 CLOSED / CONTROLE W4 IN_PROGRESS. Esta tabela fixou as regras
antes de produção W3; O1–O5, CALL-X pertinentes, Q-SHARED e E-SELECTED passaram. Não confundir retorno Normal AIR com
status 00 de COBOL. AIR existente admite status/selector e branches explícitos;
nenhuma extensão de outcome ou interpretation por string é necessária.

Autoridade: IBM Enterprise COBOL for z/OS6.4, SC27-8713-03, atualização
2026-04-28, PDF SHA256
22b5b8875041300484ac48cd16d8db6191fe4f17424cfe2f3a93392fb2aac0f6.
Seções lidas: FILE STATUS p153; SAME RECORD AREA p157; CLOSE pp328–329;
DELETE pp332–333; OPEN pp413–417; READ pp429–434; REWRITE pp437–439;
START pp460–461; WRITE pp476–481. AIR pin fb153ae5: §4.7, §5 e memória.

## Regras LANGUAGE_GUARANTEED

| Operação / caminho | Ordem e regiões | Força permitida |
| --- | --- | --- |
| READ sucesso | Record torna-se disponível; tamanho/validade seguem RECORD; depois cópia INTO; endereço INTO (subscritos/ref-mod) avaliado depois da leitura | Conteúdo externo desconhecido; escrita localizada no buffer. Não presumir MUST de toda a área pela largura da maior descrição |
| READ comprimento conflitante / variável | Só intervalo corrente substituído; cauda além do record indefinida; status00/04 depende de VLR | Não inferir comprimento runtime, VLR nem strong kill da cauda a partir do FD. MAY/unknown local |
| READ EOF | Status antes de AT END; sem cópia INTO; conteúdo do buffer indefinido; acesso pode gerar exceção | Buffer unknown local; nada de valor antigo exato ou MUST físico sem prova |
| READ invalid key / outro erro | Caminho distinto de EOF; READ unsuccessful não faz implied MOVE | Status/validade conservadores; sem sucesso default |
| READ INTO | Implied MOVE após READ bem-sucedido; destino não pode compartilhar área com record | Prova de MOVE/bytes e disjunção permite escrita forte do destino exato; alias não provado preserva gap/efeito local |
| READ relativo sequencial | Relative key recebe número do registro disponibilizado | Destino publicado; valor externo desconhecido, caminho próprio |
| WRITE/REWRITE FROM | MOVE FROM→record antes da operação I/O | Captura e escrita usam motor de MOVE existente; FROM nunca uso FILE. Mesma área fonte/destino é proibida pela linguagem; não assumir disjunção |
| WRITE | Record deixa de estar disponível após operação, salvo SAME RECORD AREA; FROM continua disponível | Validade/buffer separados da leitura do record; não matar origem FROM disjunta |
| REWRITE sucesso | Record deixa de estar disponível salvo SAME RECORD AREA | Unknown local; não strong kill arbitrário |
| REWRITE invalid key | Atualização não ocorre, conteúdo do record não é alterado por I/O | MOVE FROM anterior permanece relevante; não aplicar invalidação do sucesso nesse caminho |
| DELETE RECORD | Exclusão lógica no arquivo; área de record não é modificada por DELETE | Não inventar escrita no buffer nem remoção de recurso físico; status próprio |
| START | Compara chave e posiciona; não lê conteúdo de novo record | Leitura da chave/atualização de status; nenhum buffer recebido inventado |
| OPEN | Disponibiliza área sem obter primeiro record | Não inicializa conteúdo nem infere runtime. Status e LINAGE conforme cláusulas |
| CLOSE | Área deixa de estar disponível; falha deixa disponibilidade indefinida | Unknown localizado, sem exposição/global kill por conveniência |
| FILE STATUS | Destino primário atualizado após cada I/O, antes de handler/teste; adicional tem regras distintas, inclusive indefinido no sucesso | Não equiparar status primário/adicional nem assumir toda operação status00 |
| LINAGE / RECORD DEPENDING | Contadores/tamanho publicados conforme cláusula, caminho e ordem | Sem campo sintético que finja ter origem em fonte; W3 conserva footprint, W6 completa cláusulas auxiliares |

## Storage / algoritmo previsto

Descrições01 do mesmo FD compartilham área (READ p431). SAME RECORD AREA une
bases de arquivos por identidade canônica, sem unir ResourceId/FILE. REDEFINES e
RENAMES mantêm vistas e offsets existentes. Área desconhecida não invalida
independência de WORKING-STORAGE provadamente separado. Visibilidade EXTERNAL /
GLOBAL sem prova conserva incerteza (W9 fecha escopos). Nenhum limite por contagem.

Derivar fatos canônicos de efeito depois de binding/storage e usá-los tanto no
inventário de mutação quanto no SP; não gerar referências AST fictícias no
projector. Lower normaliza ordem dos efeitos/copias/outcomes a operações AIR
gerais. Outcome de I/O e seleção do handler são separados; handlers complexos e
USE ficam com controle aberto explícito até W4. Não mapear EOF para sucesso nem
usar Normal como código00. Prova de bytes/alias é pré-condição de MUST.

Classe C3/C4: fronteira SP e storage compartilhado. Oráculos antes da produção:
FD layouts/alias/negativos, O1–O5 AIR manual, CALL-X1–6; focais F-STORAGE/L-INPUT,
C-VALUES/C-DEP, B-AIR/E-SELECTED, FAST fixos e Q-SHARED obrigatório W3. Corpus
amplo permanece W11 salvo falha que invalide a fronteira prevista.

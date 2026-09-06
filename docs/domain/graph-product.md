# Produto CFG e observabilidade

## Produto próprio

O CFG é resultado derivado imutável vinculado à publicação IR, versão, entries,
capabilities, premissas e política de precisão usadas. Não gravar successors,
alcançabilidade ou caches mutáveis na IR. IDs CFG são próprios, correlacionados com
identidades IR; não reutilizar source span como identidade de nó.

Conceitos necessários, ainda sem classes Java congeladas:

- inventário de nós/sequences e seus operations/program points;
- transições conhecidas com outcome, predicado/case/tag quando aplicáveis;
- entradas distintas, saídas normais/excepcionais, halt/diverge e fronteiras abertas;
- correlação para IR/provenance, gaps, capabilities e limites da alegação.

No `CFG-FIRST`, `Return` produz saída normal da ativação da `Unit`, não uma
continuação para a próxima Sequence. O resultado preserva `UnitId` e, quando o
build/consulta é scoped a uma Entry, o `EntryId` relevante. Um nó/objeto sintético
de saída pode pertencer ao CFG, mas sua identidade/correlação não funde entradas
silenciosamente nem cria um único exit global da Publication.

Divergência é comportamento sem próximo estado observável: sua representação não
cria caminho artificial até uma saída normal. Uma saída sintética é convenção do
produto, não statement-fonte. Não fabricar linhas para nós sintéticos.

## Consultas

Sucessores/predecessores estruturais podem ser índices materializados. Para controle
local, o contrato conceitual é `successors(point, context)`; a API concreta pode
separar projeção plana e consulta contextual. Projeção plana deve identificar sua
sobreaproximação. Não entregar lista supostamente exaustiva se existe ControlScope
aberto que admite destinos adicionais.

Não exigir um único entry/exit para toda publicação. Um super-entry sintético é
permitido só preservando a escolha de Entry e seu escopo, sem fundir inicializações.

## Retenção e ciclo de vida

O resultado pode reter referências ao modelo `air-java` imutável e copiar somente
fatos próprios/mínimos. Não deep-copiar toda a Publication por padrão nem manter
acesso preguiçoso a serviço/produtor para completar semântica. Registrar sempre
PublicationId, revisão/versão e correlações utilizadas. Índices derivados pertencem
ao CFG e não alteram a AIR. Publicação grande exige medir memória; a API de navegação
não força cópia O(N) a cada chamada.

## Saídas humanas e máquina

DOT/HTML são apresentação, não oracle de semântica. Exportar JSON é adapter próprio,
com schema próprio se necessário. Testes comparam o produto estruturado, não strings
de console nem layout de renderização. Relatórios sempre distinguem `INVALID_IR`,
`UNSUPPORTED_CAPABILITY`, resultado parcial e resultado conforme ao escopo declarado.

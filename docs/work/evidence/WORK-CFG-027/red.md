# RED antes do pin

Baseline analysis-cfg 2b4df46d53ce5b21a5c315d3f691b183cb6bd124, produção byte-identical. Upstream b78f4068d8a479f48eb048b8d76fa60a0997dc4a extraído por git archive em /tmp/cfg-4d/air-old; clean install em Maven repo isolado inicialmente vazio /tmp/cfg-4d/m2-old, Temurin 21.0.12.1.

`JAVA_HOME=/tmp/cfg-2b/jdk21 PATH=/tmp/cfg-2b/jdk21/bin:$PATH MAVEN_OPTS=-Dmaven.repo.local=/tmp/cfg-4d/m2-old mvn -B -ntp clean test` passou (133 testes). Oracle Java compilou exit 0, esperava CFG_BUILT e falhou exit 1 por IMPLEMENTATION_LIMIT em $.publication.storage via AirJsonFileReader.read → AirJson.decode. [Comandos/saída exatos](baseline-red.json), [oracle](ScalarBaselineOracle.java), [baseline](baseline-test.log.gz), [upstream](upstream-old-install.log.gz).

Input extraído diretamente do merge 4B para /tmp antes de criar fixture local; [hash/blob](provenance.json). A tentativa inicial de Maven sem acesso de rede falhou por DNS e NÃO é RED; execução concluída com dependências resolvidas está nos logs acima.

Logs brutos preservados em gzip sem transformação de bytes.

package io.github.gustavo2358.analysis.adapters;

import java.util.*;
import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent AIR oracle; source aggregation has no graph/dataflow inputs. */
final class SourceDependencyTest {
    private static Publication publication(String kind,String authority,String resolution,int count,boolean nested) {
        var p=ResourceBindingOracle.publication("A1");var uid=p.units().getFirst().id();
        var artifacts=new ArrayList<>(p.artifacts());var owner=new ArtifactId(p.id(),"source-owner");var outer=new ArtifactId(p.id(),"outer");
        artifacts.add(new Origins.Artifact(owner,nested?"A.cpy":"program.cbl",Optional.empty()));artifacts.add(new Origins.Artifact(outer,"program.cbl",Optional.empty()));
        var origins=new ArrayList<>(p.origins());var resources=new ArrayList<>(p.resources());
        resources.add(new Interactions.Resource(new ResourceId(p.id(),"source-inventory"),new Interactions.LocalResource("source-dependency-inventory"),ResourceBindingOracle.ORIGIN,
            Optional.of(new Interactions.ResourceDeclaration(uid,"source-dependencies@1","source.KNOWN","source.inventory@1",List.of(),List.of()))));
        for(int i=0;i<count;i++) {
            var id=new OriginId(p.id(),"source-"+i);var position=new Origins.Position(BigInteger.valueOf(i+5),BigInteger.valueOf(7));
            origins.add(new Origins.Written(id,owner,Optional.of(new Origins.LineColumns(new Origins.Span(position,position,BigInteger.ONE,BigInteger.ZERO,Origins.ColumnUnit.UNICODE_SCALAR,false))),
                nested?List.of(new Origins.IncludeFrame(outer,owner,"A",Optional.empty())):List.of(),true));
            resources.add(new Interactions.Resource(new ResourceId(p.id(),"source-occurrence-"+i),new Interactions.LiteralTarget(kind,"source-member","MEMBER",Interactions.ExactName.INSTANCE,id),id,
                Optional.of(new Interactions.ResourceDeclaration(uid,"member.cpy",resolution,authority,List.of(),List.of()))));
        }
        return new Publication(p.id(),p.airVersion(),p.capabilities(),artifacts,p.units(),p.storage(),resources,p.artifactRelations(),origins,p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void deduplicatesWithoutDiscardingOriginalOwnersAndSupports() throws Exception {
        var p=publication("source-copybook","source.COPY_SYNTAX@1","source.RESOLVED",2,true);
        var codec=new AirJson();assertEquals(p,codec.decode(codec.encode(p)));
        var result=new SourceDependencyAnalysis().prepare(p);assertEquals(2,result.occurrences());assertEquals(1,result.dependencies().size());
        var d=result.dependencies().getFirst();assertEquals("MEMBER",d.name());assertEquals(2,d.supports().size());
        assertTrue(d.supports().stream().allMatch(s->s.transitive()&&s.sourceOwner().localId().equals("source-owner")));assertFalse(d.remainder());
        var product=new DependencyAnalysis().prepare(p);assertEquals(1,product.fileDependencies().declarations().size());assertEquals(result,product.sourceDependencies());
        var a=new ByteArrayOutputStream();var b=new ByteArrayOutputStream();new DependencyJson().write(product,a);new DependencyJson().write(new DependencyAnalysis().prepare(p),b);assertArrayEquals(a.toByteArray(),b.toByteArray());
        var output=java.nio.file.Path.of("target/source-dependencies-w3");java.nio.file.Files.createDirectories(output);
        java.nio.file.Files.write(output.resolve("dependencies.json"),a.toByteArray());
        assertEquals(1L,product.metrics().get("logicalOnlyMode"));assertEquals(0L,product.metrics().get("physicalGroupsApplied"));
    }
    @Test void retainsNominalUnresolvedAndUnknownSqlIncludes() {
        var result=new SourceDependencyAnalysis().prepare(publication("source-sql_include","source.UNKNOWN@1","source.UNRESOLVED",1,false));
        assertTrue(result.partial());assertEquals(SourceDependencyResult.Kind.SQL_INCLUDE,result.dependencies().getFirst().kind());assertEquals("MEMBER",result.dependencies().getFirst().name());
        assertTrue(result.gapCodes().contains("SQL_INCLUDE_CLASSIFICATION_UNKNOWN"));
    }
    @Test void requiresPositiveDclgenAuthorityAndClosedProfile() {
        var valid=new SourceDependencyAnalysis().prepare(publication("source-dclgen","source.CONFIGURED_DCLGEN@1","source.RESOLVED",1,false));
        assertEquals(SourceDependencyResult.Kind.DCLGEN,valid.dependencies().getFirst().kind());
        assertThrows(IllegalArgumentException.class,()->new SourceDependencyAnalysis().prepare(publication("source-dclgen","source.UNKNOWN@1","source.RESOLVED",1,false)));
        assertThrows(IllegalArgumentException.class,()->new SourceDependencyAnalysis().prepare(publication("source-table","source.CONFIGURED_DCLGEN@1","source.RESOLVED",1,false)));
        assertThrows(IllegalArgumentException.class,()->new SourceDependencyAnalysis().prepare(publication("source-copybook","source.COPY_SYNTAX@1","source.UNKNOWN",1,false)));
        assertFalse(new SourceDependencyAnalysis().prepare(ResourceBindingOracle.publication("A1")).available());
        var source=publication("source-copybook","source.COPY_SYNTAX@1","source.RESOLVED",1,false);
        var two=ResourceBindingOracle.publication("A3");
        var mixed=new Publication(source.id(),source.airVersion(),source.capabilities(),source.artifacts(),two.units(),two.storage(),source.resources(),source.artifactRelations(),source.origins(),source.coverage(),source.uncertainties(),source.premises());
        var partial=new SourceDependencyAnalysis().prepare(mixed);
        assertTrue(partial.partial());assertTrue(partial.gapCodes().contains("SOURCE_UNIT_INVENTORY_UNAVAILABLE"));
    }
    @Test void aggregatesThousandOccurrencesWithoutRuntimeAnalysis() {
        var result=new SourceDependencyAnalysis().prepare(publication("source-copybook","source.COPY_SYNTAX@1","source.RESOLVED",1000,false));
        assertEquals(1000,result.occurrences());assertEquals(1,result.dependencies().size());assertEquals(1000,result.dependencies().getFirst().supports().size());
    }
    @Test void db2UsageRemainsOnSupportsWithoutNominalSolver() throws Exception {
        var p=publication("source-db2_table","source.STATIC_SQL_SELECT_READ@1","source.NOT_APPLICABLE",2,true);
        var resources=new ArrayList<>(p.resources());
        for(int i=0;i<resources.size();i++) {
            var r=resources.get(i);if(!r.id().localId().equals("source-occurrence-1"))continue;
            var d=r.declaration().orElseThrow();resources.set(i,new Interactions.Resource(r.id(),r.description(),r.origin(),Optional.of(new Interactions.ResourceDeclaration(d.owner(),d.name(),d.classification(),"source.STATIC_SQL_UPDATE_WRITE@1",d.objects(),d.uses()))));
        }
        var mixed=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),p.units(),p.storage(),resources,p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var facts=new SourceDependencyAnalysis().prepare(mixed);assertEquals(1,facts.dependencies().size());assertFalse(facts.partial());
        var supports=facts.dependencies().getFirst().supports();assertEquals(Set.of("SELECT","UPDATE"),new HashSet<>(supports.stream().map(s->s.operation().name()).toList()));assertTrue(supports.stream().allMatch(SourceDependencyResult.Support::transitive));
        assertEquals(mixed,new AirJson().decode(new AirJson().encode(mixed)));
        assertThrows(IllegalArgumentException.class,()->new SourceDependencyAnalysis().prepare(publication("source-db2_table","source.STATIC_SQL_SELECT_WRITE@1","source.NOT_APPLICABLE",1,false)));
        assertThrows(IllegalArgumentException.class,()->new SourceDependencyAnalysis().prepare(publication("source-db2_table","source.UNKNOWN@1","source.NOT_APPLICABLE",1,false)));
    }
}

package io.github.gustavo2358.analysis.adapters;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;

final class PreparedSourceEvidenceTest {
    private QualifiedSourceDependencies read(String path)throws Exception {
        try(var input=getClass().getResourceAsStream(path)) {
            return new QualifiedSourceJson().decode(Objects.requireNonNull(input).readAllBytes());
        }
    }
    @Test void preparedNativeAndProgramFactsKeepAuthorityAndAreReusableWithoutReinterpretation()throws Exception {
        for(var fixture:List.of("/qualified-source-r9/conditional.source.json","/source-possibility/unknown-native.source.json")) {
            var evidence=read(fixture);var prepared=SourceQualifiedDependencyResult.admit(evidence);
            var occurrences=prepared.occurrences();var files=prepared.nativeFiles();
            for(int i=0;i<32;i++){assertSame(occurrences,prepared.occurrences());assertSame(files,prepared.nativeFiles());}
            assertEquals(prepared,new SourceQualifiedDependencyResult(evidence,occurrences));
            assertEquals(prepared.hashCode(),new SourceQualifiedDependencyResult(evidence,occurrences).hashCode());
            if(!occurrences.isEmpty())assertThrows(IllegalArgumentException.class,()->new SourceQualifiedDependencyResult(evidence,List.of()));
            if(fixture.contains("unknown-native")) {
                assertEquals(Set.of("FIRSTDD","SECONDDD"),new HashSet<>(files.stream().flatMap(f->f.candidates().stream()).map(SourceQualifiedDependencyResult.Candidate::referenceName).toList()));
                for(var file:files){assertTrue(file.remainder());assertEquals(SourceQualifiedDependencyResult.Status.POSSIBLE_UNDER_UNKNOWN_CONTROL,file.status());}
            }
        }
    }
    @Test void admittedInputRetainsOneSourceCertificateAndImmutableCorrelatedInventory()throws Exception {
        var publication=DependencyPreservationTest.linear(true,false);var original=read("/qualified-source-r9/computed.source.json");
        var evidence=new QualifiedSourceDependencies(original.schema(),original.version(),original.producer(),original.source(),List.of(new QualifiedSourceDependencies.AirCorrelation(publication.id().localId(),"0".repeat(64))),original.units());
        var site=new DependencyAnalysis().prepare(publication).sites().getFirst();
        var link=new DependencyInput.StatementCorrelation(evidence.units().getFirst().occurrences().getFirst().id(),site.operation(),site.sequence(),site.siteOrigin());
        var input=new DependencyInput(publication,Optional.of(evidence),List.of(link));
        var occurrences=input.occurrences();var certificate=input.preparedSource().orElseThrow();
        assertEquals(1,occurrences.size());assertEquals(List.of(site.operation()),occurrences.getFirst().executableOperations());
        assertEquals(evidence.units().getFirst().occurrences().getFirst().qualifications(),occurrences.getFirst().qualifications());
        for(int i=0;i<32;i++){assertSame(occurrences,input.occurrences());assertSame(certificate,input.preparedSource().orElseThrow());}
        var result=new DependencyAnalysis().prepare(input);
        assertSame(certificate,result.sourceQualifiedDependencies().orElseThrow());
        assertEquals(List.of("PROGA"),result.programDependencies().getFirst().candidates().stream().map(TargetResolver.Candidate::referenceName).toList());
        assertEquals(input,new DependencyInput(publication,Optional.of(evidence),List.of(link)));
        assertThrows(UnsupportedOperationException.class,()->occurrences.clear());
    }
    @Test void indexedCorrelationsRetainEverySortedSiteAndRejectForeignCertificates()throws Exception {
        int n=512;
        var publication=W1dModelTest.model(1,u->{
            var sequences=new ArrayList<io.github.gustavo2358.air.model.Sequence>();
            for(int j=n-1;j>=0;j--) {
                String label=j==0?"start":"s"+j;
                sequences.add(new io.github.gustavo2358.air.model.Sequence(new io.github.gustavo2358.air.model.Ids.LabelId(u,label),List.of(),W1dModelTest.call(u,"call-"+j,"end",new io.github.gustavo2358.air.model.Ids.ObjectId(u,"object-0"),false),ResultFixtures.origin(u.publication())));
            }
            sequences.add(ResultFixtures.returning(u,"end",List.of()));return sequences;
        });
        var original=read("/qualified-source-r9/computed.source.json");
        var evidence=new QualifiedSourceDependencies(original.schema(),original.version(),original.producer(),original.source(),List.of(new QualifiedSourceDependencies.AirCorrelation(publication.id().localId(),"0".repeat(64))),original.units());
        var statement=evidence.units().getFirst().occurrences().getFirst().id();
        var links=new ArrayList<DependencyInput.StatementCorrelation>();var expected=new ArrayList<io.github.gustavo2358.air.model.Ids.OperationId>();
        for(var sequence:publication.units().getFirst().sequences())if(sequence.terminator() instanceof io.github.gustavo2358.air.model.Operations.Invoke invoke) {
            expected.add(invoke.header().id());links.add(new DependencyInput.StatementCorrelation(statement,invoke.header().id(),sequence.label(),invoke.header().origin()));
        }
        expected.sort(Comparator.comparing(io.github.gustavo2358.air.model.Ids.OperationId::localId));
        var input=new DependencyInput(publication,Optional.of(evidence),links);
        assertEquals(1,input.occurrences().size());assertEquals(expected,input.occurrences().getFirst().executableOperations());
        Collections.reverse(links);assertEquals(input.occurrences(),new DependencyInput(publication,Optional.of(evidence),links).occurrences());
        var partial=new DependencyInput(publication,Optional.of(evidence),links.subList(0,n-1));
        assertEquals(2,partial.occurrences().size());assertEquals(n-1,partial.occurrences().getFirst().executableOperations().size());
        assertTrue(partial.occurrences().getLast().source().isEmpty());assertEquals(List.of(links.getLast().operation()),partial.occurrences().getLast().executableOperations());
        var result=new DependencyAnalysis().prepare(DependencyPreservationTest.linear(true,false));
        var foreign=new QualifiedSourceDependencies(evidence.schema(),evidence.version(),evidence.producer(),evidence.source(),List.of(new QualifiedSourceDependencies.AirCorrelation("foreign","0".repeat(64))),evidence.units());
        assertThrows(IllegalArgumentException.class,()->result.withSourceEvidence(SourceQualifiedDependencyResult.admit(foreign)));
    }
    @Test void deepSharedOriginsPreserveEveryCorrelatedSiteAndRejectASibling()throws Exception {
        int depth=1024,n=512;
        var base=W1dModelTest.model(1,u->{
            var sequences=new ArrayList<io.github.gustavo2358.air.model.Sequence>();
            for(int i=0;i<n;i++) {
                var invoke=W1dModelTest.call(u,"deep-call-"+i,"end",new io.github.gustavo2358.air.model.Ids.ObjectId(u,"object-0"),false);
                sequences.add(new io.github.gustavo2358.air.model.Sequence(new io.github.gustavo2358.air.model.Ids.LabelId(u,i==0?"start":"deep-s"+i),List.of(),invoke,ResultFixtures.origin(u.publication())));
            }
            sequences.add(ResultFixtures.returning(u,"end",List.of()));return sequences;
        });
        var origins=new ArrayList<>(base.origins());var anchor=ResultFixtures.origin(base.id());var previous=anchor;
        for(int i=1;i<=depth;i++) {
            var id=new io.github.gustavo2358.air.model.Ids.OriginId(base.id(),"derived-"+i);
            origins.add(new io.github.gustavo2358.air.model.Origins.Derived(id,List.of(previous),"synthetic-step"));previous=id;
        }
        var sibling=new io.github.gustavo2358.air.model.Ids.OriginId(base.id(),"sibling");origins.add(new io.github.gustavo2358.air.model.Origins.Unavailable(sibling,"independent negative"));
        var unit=base.units().getFirst();var sequences=new ArrayList<io.github.gustavo2358.air.model.Sequence>();
        for(var sequence:unit.sequences()) {
            if(sequence.terminator() instanceof io.github.gustavo2358.air.model.Operations.Invoke invoke) {
                var h=invoke.header();var header=new io.github.gustavo2358.air.model.Operations.Header(h.id(),previous,h.coverage(),h.precision(),h.uncertainties());
                var derived=new io.github.gustavo2358.air.model.Operations.Invoke(header,invoke.action(),invoke.target(),invoke.arguments(),invoke.results(),invoke.signature(),invoke.effectOperands(),invoke.effectBound(),invoke.outcomes(),invoke.contract());
                sequences.add(new io.github.gustavo2358.air.model.Sequence(sequence.label(),sequence.instructions(),derived,previous));
            }else sequences.add(sequence);
        }
        var derivedUnit=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),unit.entries(),sequences,unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var publication=new io.github.gustavo2358.air.model.Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(derivedUnit),base.storage(),base.resources(),base.artifactRelations(),origins,base.coverage(),base.uncertainties(),base.premises());
        assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.CFG_BUILT,W1dBoundaryTest.build(publication).status());
        var original=read("/qualified-source-r9/computed.source.json");
        var evidence=new QualifiedSourceDependencies(original.schema(),original.version(),original.producer(),original.source(),List.of(new QualifiedSourceDependencies.AirCorrelation(publication.id().localId(),"0".repeat(64))),original.units());
        var statement=evidence.units().getFirst().occurrences().getFirst().id();var links=new ArrayList<DependencyInput.StatementCorrelation>();
        for(var sequence:publication.units().getFirst().sequences())if(sequence.terminator() instanceof io.github.gustavo2358.air.model.Operations.Invoke invoke)
            links.add(new DependencyInput.StatementCorrelation(statement,invoke.header().id(),sequence.label(),anchor));
        var input=new DependencyInput(publication,Optional.of(evidence),links);assertEquals(n,input.occurrences().getFirst().executableOperations().size());
        Collections.reverse(links);assertEquals(input.occurrences(),new DependencyInput(publication,Optional.of(evidence),links).occurrences());
        var first=links.getFirst();links.set(0,new DependencyInput.StatementCorrelation(first.source(),first.operation(),first.label(),sibling));
        assertThrows(IllegalArgumentException.class,()->new DependencyInput(publication,Optional.of(evidence),links));
    }

}

package io.github.gustavo2358.analysis.dependencies.source;
import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Source-only value evidence; no AIR object, operation or executable memory claim. */
public record NominalValueEvidence(NominalValues facts,List<Declaration> declarations,List<Seed> seeds,
        List<Branch> branches,List<Uncertainty> uncertainties) {
    public record Declaration(String node,Provenance provenance) {
        public Declaration {text(node);Objects.requireNonNull(provenance);}
    }
    public record Seed(String node,String value,String authority,Provenance provenance) {
        public Seed {text(node);Objects.requireNonNull(value);text(authority);Objects.requireNonNull(provenance);require(Set.of("DECLARATIVE_POSSIBILITY","DECLARATIVE_INVARIANT","DECLARATIVE_VALUE").contains(authority),"source seed authority");}
    }
    public record Branch(String derivation,boolean whenTrue) { public Branch {text(derivation);} }
    public record Uncertainty(String id,String kind,Provenance provenance) {
        public Uncertainty {text(id);require(Set.of("MISSING_COPY","OPAQUE_INCLUDE","UNLOCATED_INPUT").contains(kind),"source uncertainty kind");Objects.requireNonNull(provenance);}
    }
    public NominalValueEvidence {
        Objects.requireNonNull(facts);declarations=List.copyOf(declarations);seeds=List.copyOf(seeds);branches=List.copyOf(branches);uncertainties=List.copyOf(uncertainties);
        var nodes=new HashSet<String>();for(var s:facts.symbols())nodes.add(s.node());
        var declared=new HashSet<String>();for(var d:declarations)require(nodes.contains(d.node())&&declared.add(d.node()),"nominal declaration identity");require(declared.equals(nodes),"complete nominal declaration provenance");
        var seedNodes=new HashSet<String>();for(var s:seeds)require(nodes.contains(s.node())&&seedNodes.add(s.node()),"nominal initial value identity");
        var roles=new HashSet<String>();for(var branch:branches)require(roles.add(branch.derivation()),"duplicate nominal branch role");
        var missing=new HashSet<String>();for(var u:uncertainties)require(missing.add(u.id()),"duplicate nominal uncertainty");
    }
    public void validate(List<Statement> statements,List<Occurrence> occurrences,List<Node> nodes,List<Derivation> derivations) {
        var ss=new HashSet<String>();statements.forEach(s->ss.add(s.id().handle()));
        facts.validate(declarations.stream().map(Declaration::node).collect(java.util.stream.Collectors.toSet()),ss);
        var ds=new HashMap<String,Derivation>();derivations.forEach(d->ds.put(d.id(),d));
        var ns=new HashMap<String,Node>();nodes.forEach(n->ns.put(n.id(),n));
        var predicates=new HashSet<String>();facts.conditions().forEach(c->predicates.add(c.statement()));
        for(var b:branches){var d=ds.get(b.derivation());require(d!=null&&d.source().size()==1&&d.selection().isEmpty()&&d.callerPremise().isEmpty(),"nominal branch derivation");require(predicates.contains(ns.get(d.source().get(0)).location()),"nominal branch predicate owner");}
        var queries=new HashMap<String,Occurrence>();occurrences.forEach(o->queries.put(o.id().handle(),o));
        for(var q:facts.queries())require(queries.containsKey(q.statement())&&queries.get(q.statement()).targetKind().equals("COMPUTED"),"nominal query computed occurrence");
    }
    private static void text(String x){require(x!=null&&!x.isBlank(),"source nominal identity");}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
}

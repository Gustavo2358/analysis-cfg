package io.github.gustavo2358.analysis.dependencies.source;
import java.util.*;

/** Nominal source facts. These do not assert storage allocation or executable control. */
public record NominalValues(String authority,List<Symbol> symbols,List<Assignment> assignments,
        List<Condition> conditions,List<Query> queries) {
    public record Symbol(String node,int extent) {
        public Symbol { text(node);require(extent>0,"positive nominal text extent"); }
    }
    public record Term(String kind,String value) {
        public Term { Objects.requireNonNull(value);require(Set.of("READ","LITERAL","SPACES","LOW_VALUES","HIGH_VALUES","UNKNOWN").contains(kind),"nominal term kind");if(kind.equals("READ"))text(value);else if(!kind.equals("LITERAL"))require(value.isEmpty(),"nonliteral payload"); }
    }
    public record Assignment(String statement,String target,Term source) {
        public Assignment { text(statement);text(target);Objects.requireNonNull(source); }
    }
    public record Predicate(String kind,List<Term> terms,List<Predicate> children) {
        public Predicate {
            terms=List.copyOf(terms);children=List.copyOf(children);
            require(switch(kind){case "EQ"->terms.size()==2&&children.isEmpty();case "NOT"->terms.isEmpty()&&children.size()==1;case "AND","OR"->terms.isEmpty()&&children.size()>=2;default->false;},"nominal predicate shape");
        }
    }
    public record Condition(String statement,Predicate predicate) {
        public Condition {text(statement);Objects.requireNonNull(predicate);}
    }
    public record Query(String statement,String node) {
        public Query {text(statement);text(node);}
    }
    public NominalValues {
        require("NOMINAL_TEXT_SOURCE_V1".equals(authority),"nominal value authority");
        symbols=List.copyOf(symbols);assignments=List.copyOf(assignments);conditions=List.copyOf(conditions);queries=List.copyOf(queries);
        var nodes=new HashSet<String>();for(var s:symbols)require(nodes.add(s.node()),"duplicate nominal symbol");
        var writes=new HashSet<String>();for(var a:assignments){require(nodes.contains(a.target()),"nominal receiver reference");term(a.source(),nodes);require(writes.add(a.statement()+"/"+a.target()),"duplicate nominal assignment");}
        var branches=new HashSet<String>();for(var c:conditions){require(branches.add(c.statement()),"duplicate nominal condition");var todo=new ArrayDeque<Predicate>();todo.add(c.predicate());while(!todo.isEmpty()){var p=todo.removeFirst();p.terms().forEach(t->term(t,nodes));todo.addAll(p.children());}}
        var sinks=new HashSet<String>();for(var q:queries)require(nodes.contains(q.node())&&sinks.add(q.statement()),"nominal query reference/identity");
    }
    /** Validate references in every typed consumer, including the in-memory port. */
    public void validate(Set<String> nodes,Set<String> statements) {
        for(var s:symbols)require(nodes.contains(s.node()),"nominal symbol belongs to source storage inventory");
        for(var a:assignments)require(statements.contains(a.statement()),"nominal assignment owner");
        for(var c:conditions)require(statements.contains(c.statement()),"nominal condition owner");
        for(var q:queries)require(statements.contains(q.statement()),"nominal query owner");
    }
    private static void term(Term t,Set<String> nodes){if(t.kind().equals("READ"))require(nodes.contains(t.value()),"nominal read reference");}
    private static void text(String x){require(x!=null&&!x.isBlank(),"nominal identity");}
    private static void require(boolean yes,String message){if(!yes)throw new IllegalArgumentException(message);}
}

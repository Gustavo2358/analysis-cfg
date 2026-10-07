package io.github.gustavo2358.analysis.dependencies.source;
import java.util.*;

/** Nominal source facts. These do not assert storage allocation or executable control. */
public record NominalValues(String authority,List<Symbol> symbols,List<Assignment> assignments,
        List<Condition> conditions,List<Query> queries,List<TableField> tableFields) {
    public NominalValues(String authority,List<Symbol> symbols,List<Assignment> assignments,List<Condition> conditions,List<Query> queries){this(authority,symbols,assignments,conditions,queries,List.of());}
    public record Initial(String origin,String value){public Initial{text(origin);Objects.requireNonNull(value);}}
    public record TableField(String node,List<Initial> initial){public TableField{text(node);initial=List.copyOf(initial);require(new HashSet<>(initial).size()==initial.size(),"duplicate table initializer");}}
    public record Symbol(String node,int extent,boolean modelAssumed) {
        public Symbol(String node,int extent){this(node,extent,false);}
        public Symbol { text(node);require(extent>0,"positive nominal text extent"); }
    }
    public record Term(String kind,String value,List<Term> arguments) {
        public Term(String kind,String value){this(kind,value,List.of());}
        public Term {
            Objects.requireNonNull(value);arguments=arguments==null?List.of():List.copyOf(arguments);
            boolean unary=Set.of("UPPER_ASCII","TRIM_SPACES","TRIM_LEADING_SPACES","TRIM_TRAILING_SPACES").contains(kind);
            require(kind.equals("CHOICE")?arguments.size()>=2:unary?arguments.size()==1:arguments.isEmpty(),"nominal expression arity");
            require(kind.equals("CHOICE")||unary||Set.of("READ","LITERAL","SPACES","LOW_VALUES","HIGH_VALUES","UNKNOWN").contains(kind),"nominal term kind");
            if(kind.equals("READ"))text(value);else if(!kind.equals("LITERAL"))require(value.isEmpty(),"nonliteral payload");
        }
        public boolean extended(){return !arguments.isEmpty();}
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
        require(Set.of("NOMINAL_TEXT_SOURCE_V1","NOMINAL_TEXT_SOURCE_V2","NOMINAL_TEXT_SOURCE_V3","NOMINAL_TEXT_SOURCE_V4").contains(authority),"nominal value authority");
        require(!authority.equals("NOMINAL_TEXT_SOURCE_V1")||symbols.stream().noneMatch(Symbol::modelAssumed),"model marker requires V2");
        symbols=List.copyOf(symbols);assignments=List.copyOf(assignments);conditions=List.copyOf(conditions);queries=List.copyOf(queries);tableFields=tableFields==null?List.of():List.copyOf(tableFields);
        require(authority.equals("NOMINAL_TEXT_SOURCE_V4")||tableFields.isEmpty(),"table fields require V4");
        var nodes=new HashSet<String>();for(var s:symbols)require(nodes.add(s.node()),"duplicate nominal symbol");
        var tables=new HashSet<String>();for(var f:tableFields){require(nodes.contains(f.node())&&tables.add(f.node()),"table field identity");for(var i:f.initial())require(nodes.contains(i.origin()),"table initializer origin");}
        if(!authority.equals("NOMINAL_TEXT_SOURCE_V4")){for(var a:assignments)noChoice(a.source());var pending=new ArrayDeque<Predicate>();conditions.forEach(c->pending.add(c.predicate()));while(!pending.isEmpty()){var p=pending.removeFirst();p.terms().forEach(NominalValues::noChoice);pending.addAll(p.children());}}
        record WriteIdentity(String statement,String target) { }
        var writes=new HashSet<WriteIdentity>();for(var a:assignments){require(nodes.contains(a.target()),"nominal receiver reference");term(a.source(),nodes);require(Set.of("NOMINAL_TEXT_SOURCE_V3","NOMINAL_TEXT_SOURCE_V4").contains(authority)||!a.source().extended(),"expression requires V3");require(writes.add(new WriteIdentity(a.statement(),a.target())),"duplicate nominal assignment");}
        var branches=new HashSet<String>();for(var c:conditions){require(branches.add(c.statement()),"duplicate nominal condition");var todo=new ArrayDeque<Predicate>();todo.add(c.predicate());while(!todo.isEmpty()){var p=todo.removeFirst();p.terms().forEach(t->{term(t,nodes);require(Set.of("NOMINAL_TEXT_SOURCE_V3","NOMINAL_TEXT_SOURCE_V4").contains(authority)||!t.extended(),"expression requires V3");});todo.addAll(p.children());}}
        var sinks=new HashSet<String>();for(var q:queries)require(nodes.contains(q.node())&&sinks.add(q.statement()),"nominal query reference/identity");
    }
    /** Validate references in every typed consumer, including the in-memory port. */
    public void validate(Set<String> nodes,Set<String> statements) {
        for(var s:symbols)require(nodes.contains(s.node()),"nominal symbol belongs to source storage inventory");
        for(var a:assignments)require(statements.contains(a.statement()),"nominal assignment owner");
        for(var c:conditions)require(statements.contains(c.statement()),"nominal condition owner");
        for(var q:queries)require(statements.contains(q.statement()),"nominal query owner");
    }
    private static void term(Term root,Set<String> nodes){
        var pending=new ArrayDeque<Term>();pending.add(root);
        while(!pending.isEmpty()){var t=pending.removeFirst();if(t.kind().equals("READ"))require(nodes.contains(t.value()),"nominal read reference");pending.addAll(t.arguments());}
    }
    private static void noChoice(Term t){require(!t.kind().equals("CHOICE"),"choice requires V4");t.arguments().forEach(NominalValues::noChoice);}
    private static void text(String x){require(x!=null&&!x.isBlank(),"nominal identity");}
    private static void require(boolean yes,String message){if(!yes)throw new IllegalArgumentException(message);}
}

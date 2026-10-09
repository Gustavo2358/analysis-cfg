package io.github.gustavo2358.analysis.dependencies.source;
import java.util.*;

/** Nominal source facts. These do not assert storage allocation or executable control. */
public record NominalValues(String authority,List<Symbol> symbols,List<Assignment> assignments,
        List<Condition> conditions,List<Query> queries,List<TableField> tableFields) {
    public NominalValues(String authority,List<Symbol> symbols,List<Assignment> assignments,List<Condition> conditions,List<Query> queries){this(authority,symbols,assignments,conditions,queries,List.of());}
    /** The callback applies to this construction only, never subsequent model access. */
    public NominalValues(String authority,List<Symbol> symbols,List<Assignment> assignments,List<Condition> conditions,List<Query> queries,List<TableField> tableFields,Runnable progress){this(authority,SourceAdmission.input(symbols,progress),assignments,conditions,queries,tableFields);}
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
        var progress=SourceAdmission.progress(symbols);progress.run();
        symbols=SourceAdmission.input(List.copyOf(symbols),progress);
        assignments=SourceAdmission.input(List.copyOf(SourceAdmission.input(assignments,progress)),progress);
        conditions=SourceAdmission.input(List.copyOf(SourceAdmission.input(conditions,progress)),progress);
        queries=SourceAdmission.input(List.copyOf(SourceAdmission.input(queries,progress)),progress);
        tableFields=SourceAdmission.input(tableFields==null?List.of():List.copyOf(SourceAdmission.input(tableFields,progress)),progress);
        require(Set.of("NOMINAL_TEXT_SOURCE_V1","NOMINAL_TEXT_SOURCE_V2","NOMINAL_TEXT_SOURCE_V3","NOMINAL_TEXT_SOURCE_V4").contains(authority),"nominal value authority");
        if(authority.equals("NOMINAL_TEXT_SOURCE_V1"))for(var symbol:symbols)require(!symbol.modelAssumed(),"model marker requires V2");
        require(authority.equals("NOMINAL_TEXT_SOURCE_V4")||tableFields.isEmpty(),"table fields require V4");
        var nodes=new HashSet<String>();for(var s:symbols)require(nodes.add(s.node()),"duplicate nominal symbol");
        var tables=new HashSet<String>();for(var f:tableFields){require(nodes.contains(f.node())&&tables.add(f.node()),"table field identity");for(var i:f.initial()){progress.run();require(nodes.contains(i.origin()),"table initializer origin");}}
        // Identity, not record equality/hash: shared AST subgraphs may have exponentially many
        // paths and deeply nested record hashCode/recursive walkers are not a traversal algorithm.
        if(!authority.equals("NOMINAL_TEXT_SOURCE_V4")) {
            var checkedTerms=new IdentityHashMap<Term,Boolean>();
            for(var assignment:assignments)noChoice(assignment.source(),checkedTerms,progress);
            var checkedPredicates=new IdentityHashMap<Predicate,Boolean>();var pending=new ArrayDeque<Predicate>();
            for(var condition:conditions) {
                if(checkedPredicates.put(condition.predicate(),Boolean.TRUE)==null)pending.add(condition.predicate());
                while(!pending.isEmpty()) {
                    progress.run();var predicate=pending.removeFirst();for(var term:predicate.terms())noChoice(term,checkedTerms,progress);
                    for(var child:predicate.children()){progress.run();if(checkedPredicates.put(child,Boolean.TRUE)==null)pending.addLast(child);}
                }
            }
        }
        record WriteIdentity(String statement,String target) { }
        var writes=new HashSet<WriteIdentity>();var checkedTerms=new IdentityHashMap<Term,Boolean>();
        boolean expressions=authority.equals("NOMINAL_TEXT_SOURCE_V3")||authority.equals("NOMINAL_TEXT_SOURCE_V4");
        for(var assignment:assignments) {
            require(nodes.contains(assignment.target()),"nominal receiver reference");term(assignment.source(),nodes,checkedTerms,progress);
            require(expressions||!assignment.source().extended(),"expression requires V3");
            require(writes.add(new WriteIdentity(assignment.statement(),assignment.target())),"duplicate nominal assignment");
        }
        var branches=new HashSet<String>();var checkedPredicates=new IdentityHashMap<Predicate,Boolean>();
        for(var condition:conditions) {
            require(branches.add(condition.statement()),"duplicate nominal condition");var pending=new ArrayDeque<Predicate>();
            if(checkedPredicates.put(condition.predicate(),Boolean.TRUE)==null)pending.add(condition.predicate());
            while(!pending.isEmpty()) {
                progress.run();var predicate=pending.removeFirst();
                for(var operand:predicate.terms()){term(operand,nodes,checkedTerms,progress);require(expressions||!operand.extended(),"expression requires V3");}
                for(var child:predicate.children()){progress.run();if(checkedPredicates.put(child,Boolean.TRUE)==null)pending.addLast(child);}
            }
        }
        var sinks=new HashSet<String>();for(var q:queries)require(nodes.contains(q.node())&&sinks.add(q.statement()),"nominal query reference/identity");
        symbols=SourceAdmission.owned(symbols);assignments=SourceAdmission.owned(assignments);
        conditions=SourceAdmission.owned(conditions);queries=SourceAdmission.owned(queries);tableFields=SourceAdmission.owned(tableFields);
    }
    /** Validate references in every typed consumer, including the in-memory port. */
    public void validate(Set<String> nodes,Set<String> statements) {
        validate(nodes,statements,()->{});
    }
    void validate(Set<String> nodes,Set<String> statements,Runnable progress) {
        for(var s:symbols){progress.run();require(nodes.contains(s.node()),"nominal symbol belongs to source storage inventory");}
        for(var a:assignments){progress.run();require(statements.contains(a.statement()),"nominal assignment owner");}
        for(var c:conditions){progress.run();require(statements.contains(c.statement()),"nominal condition owner");}
        for(var q:queries){progress.run();require(statements.contains(q.statement()),"nominal query owner");}
    }
    private static void term(Term root,Set<String> nodes,IdentityHashMap<Term,Boolean> checked,Runnable progress) {
        progress.run();
        if(checked.put(root,Boolean.TRUE)!=null)return;
        var pending=new ArrayDeque<Term>();pending.add(root);
        while(!pending.isEmpty()) {
            progress.run();var term=pending.removeFirst();if(term.kind().equals("READ"))require(nodes.contains(term.value()),"nominal read reference");
            for(var child:term.arguments()){progress.run();if(checked.put(child,Boolean.TRUE)==null)pending.addLast(child);}
        }
    }
    private static void noChoice(Term root,IdentityHashMap<Term,Boolean> checked,Runnable progress) {
        progress.run();
        if(checked.put(root,Boolean.TRUE)!=null)return;
        var pending=new ArrayDeque<Term>();pending.add(root);
        while(!pending.isEmpty()) {
            progress.run();var term=pending.removeFirst();require(!term.kind().equals("CHOICE"),"choice requires V4");
            for(var child:term.arguments()){progress.run();if(checked.put(child,Boolean.TRUE)==null)pending.addLast(child);}
        }
    }
    private static void text(String x){require(x!=null&&!x.isBlank(),"nominal identity");}
    private static void require(boolean yes,String message){if(!yes)throw new IllegalArgumentException(message);}
}

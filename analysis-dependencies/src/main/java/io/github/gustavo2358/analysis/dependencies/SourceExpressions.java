package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.NominalValues;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.util.*;

/**
 * Source expression program shared by all predecessor evaluations. The supported unary maps
 * commute and are idempotent (ASCII upper, leading/trailing U+0020 trim); their composition has
 * eight masks. Each distributes over CHOICE, including open/model/table flags and proof union.
 * Compilation therefore specializes at most eight contexts per syntax identity, never paths.
 * Primitive runtime rows/argument vectors are canonical; input String/syntax indexes remain an
 * explicit resident bridge. No AIR control or scalar CONCAT law is inferred by this program.
 */
final class SourceExpressions implements AutoCloseable {
    static final int READ=1,LITERAL=2,UNKNOWN=3,CHOICE=4,UPPER=1,LEADING=2,TRAILING=4;
    private record Leaf(int kind,int transforms,String payload) { }
    private static final class Choice {
        final int[] children;final int hash;
        Choice(int[] children){this.children=children;hash=Arrays.hashCode(children);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof Choice c&&Arrays.equals(children,c.children);}
    }
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private final Map<Leaf,Integer> leaves;
    private final Map<Choice,Integer> choices;
    private final IdentityHashMap<NominalValues.Term,int[]> memo;
    private int[] kinds,transforms,starts,counts,arguments;
    private String[] payloads;
    private int size,argumentCount;
    private boolean closed;

    SourceExpressions(AnalysisResources resources) {
        this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,4096,AnalysisResources.Phase.DOMAIN);
        try {
            leaves=new HashMap<>();choices=new HashMap<>();memo=new IdentityHashMap<>();
            kinds=new int[8];transforms=new int[8];starts=new int[8];counts=new int[8];arguments=new int[16];payloads=new String[8];
        } catch(RuntimeException|Error failure){resident.close();throw failure;}
    }
    private int known(NominalValues.Term term,int context) {
        if(term.arguments().isEmpty())return leaf(term,context);
        var contexts=memo.get(term);return contexts==null?0:contexts[context];
    }
    private int leaf(NominalValues.Term term,int context) {
        int kind=switch(term.kind()){case "READ"->READ;case "LITERAL","SPACES"->LITERAL;default->UNKNOWN;};
        String payload=term.kind().equals("SPACES")?" ":kind==UNKNOWN?"":term.value();
        if(kind==UNKNOWN)context=0;
        var key=new Leaf(kind,context,payload);Integer existing=leaves.get(key);if(existing!=null)return existing;
        resident.grow(256,AnalysisResources.Phase.DOMAIN);int node=node(kind,context,payload,0,0);leaves.put(key,node);return node;
    }
    private int node(int kind,int context,String payload,int start,int count) {
        if(size+1==kinds.length) {
            int capacity=Math.multiplyExact(kinds.length,2);resident.grow(40L*capacity,AnalysisResources.Phase.DOMAIN);
            kinds=Arrays.copyOf(kinds,capacity);transforms=Arrays.copyOf(transforms,capacity);
            starts=Arrays.copyOf(starts,capacity);counts=Arrays.copyOf(counts,capacity);payloads=Arrays.copyOf(payloads,capacity);
        }
        int node=++size;kinds[node]=kind;transforms[node]=context;payloads[node]=payload;starts[node]=start;counts[node]=count;return node;
    }
    private static int mask(String kind) {
        return switch(kind){case "UPPER_ASCII"->UPPER;case "TRIM_LEADING_SPACES"->LEADING;
            case "TRIM_TRAILING_SPACES"->TRAILING;case "TRIM_SPACES"->LEADING|TRAILING;
            default->throw new IllegalArgumentException("source unary program operator");};
    }
    private static final class Frame {
        final NominalValues.Term term;final int context;final int[] children;int next;
        Frame(NominalValues.Term term,int context){this.term=term;this.context=context;children=new int[term.arguments().size()];}
    }
    int compile(NominalValues.Term root) {
        if(closed)throw new IllegalStateException("source expression program closed");
        int known=known(root,0);if(known!=0)return known;
        try(var scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.DOMAIN)) {
            var stack=new ArrayDeque<Frame>();push(stack,root,0,scratch);int result=0;
            while(!stack.isEmpty()) {
                var frame=stack.peek();boolean choice=frame.term.kind().equals("CHOICE");
                if(frame.next<frame.children.length) {
                    var child=frame.term.arguments().get(frame.next);int context=choice?frame.context:frame.context|mask(frame.term.kind());
                    int childNode=known(child,context);
                    if(childNode==0){push(stack,child,context,scratch);continue;}
                    frame.children[frame.next++]=childNode;continue;
                }
                if(choice)scratch.grow(128+4L*frame.children.length,AnalysisResources.Phase.DOMAIN);
                result=choice?choice(frame.children):frame.children[0];
                var contexts=memo.get(frame.term);
                if(contexts==null){resident.grow(256,AnalysisResources.Phase.DOMAIN);contexts=new int[8];memo.put(frame.term,contexts);}
                contexts[frame.context]=result;stack.pop();
                if(!stack.isEmpty()){var parent=stack.peek();parent.children[parent.next++]=result;}
            }
            return result;
        }
    }
    private static void push(ArrayDeque<Frame> stack,NominalValues.Term term,int context,AnalysisResources.Reservation scratch) {
        scratch.grow(192+4L*term.arguments().size(),AnalysisResources.Phase.DOMAIN);stack.push(new Frame(term,context));
    }
    private int choice(int[] children) {
        Arrays.sort(children);int unique=0;
        for(int child:children)if(unique==0||children[unique-1]!=child)children[unique++]=child;
        if(unique==1)return children[0];
        var key=new Choice(Arrays.copyOf(children,unique));Integer existing=choices.get(key);if(existing!=null)return existing;
        resident.grow(256+8L*unique,AnalysisResources.Phase.DOMAIN);
        int needed=Math.addExact(argumentCount,unique);
        if(needed>arguments.length) {
            int capacity=arguments.length;while(capacity<needed)capacity=Math.multiplyExact(capacity,2);
            resident.grow(8L*capacity,AnalysisResources.Phase.DOMAIN);arguments=Arrays.copyOf(arguments,capacity);
        }
        int start=argumentCount;System.arraycopy(key.children,0,arguments,start,unique);argumentCount=needed;
        int result=node(CHOICE,0,"",start,unique);choices.put(key,result);return result;
    }
    int kind(int node){return kinds[node];}
    int transforms(int node){return transforms[node];}
    String payload(int node){return payloads[node];}
    int count(int node){return counts[node];}
    int argument(int node,int ordinal){return arguments[starts[node]+ordinal];}
    @Override public void close(){if(closed)return;closed=true;leaves.clear();choices.clear();memo.clear();
        kinds=null;transforms=null;starts=null;counts=null;arguments=null;payloads=null;resident.close();}
}

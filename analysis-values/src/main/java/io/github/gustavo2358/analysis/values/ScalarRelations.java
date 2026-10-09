package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Expressions;
import io.github.gustavo2358.air.model.Ids.ObjectId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.analysis.solver.DataflowResult;
import java.util.*;
import java.util.function.Function;

/**
 * Exact finite relations for scalar cells that participate in a multi-read expression.
 * Pointwise facts remain the public projection; these roots preserve which cell values
 * actually coexist at control-flow joins so expression transfer cannot invent a product.
 */
final class ScalarRelations {
    /**
     * Binary-carry union forest. Incremental fan-in combines equally sized
     * chunks instead of copying the growing top-level edge map for every new
     * predecessor. Materialization is exact and cached only when an operation
     * must inspect or transform the relation.
     */
    private static final class Relation {
        final List<FactorizedAlternatives.Node<Candidates>> bins;
        FactorizedAlternatives.Node<Candidates> materialized;
        Relation(FactorizedAlternatives.Node<Candidates> root){bins=List.of(root);materialized=root;}
        Relation(List<FactorizedAlternatives.Node<Candidates>> bins){this.bins=Collections.unmodifiableList(new ArrayList<>(bins));}
        FactorizedAlternatives.Node<Candidates> materialize(FactorizedAlternatives<Candidates> domain) {
            if(materialized!=null)return materialized;
            FactorizedAlternatives.Node<Candidates> result=null;
            for(int rank=bins.size()-1;rank>=0;rank--)if(bins.get(rank)!=null)result=domain.union(result,bins.get(rank));
            if(result==null)throw new IllegalStateException("empty scalar relation forest");
            return materialized=result;
        }
        void roots(Collection<FactorizedAlternatives.Node<Candidates>> target) {
            for(var root:bins)if(root!=null)target.add(root);if(materialized!=null)target.add(materialized);
        }
    }
    static final class Roots {
        final List<Relation> values;
        Roots(List<Relation> values){this.values=List.copyOf(values);}
    }
    record Assignment(Candidates projection,Roots roots) { }
    private final FactorizedAlternatives<Candidates> domain;
    private final int[] groupOf;
    private final List<int[]> cells;

    private ScalarRelations(int[] groupOf,List<int[]> cells,Runnable progress){this.groupOf=groupOf;this.cells=List.copyOf(cells);domain=new FactorizedAlternatives<>(progress);}

    static ScalarRelations create(Collection<TextProfile.Location> selected,Collection<TextProfile.Write> writes,
            Map<ObjectId,TextProfile.Location> subjects) {
        return create(selected,writes,subjects,()->{});
    }
    static ScalarRelations create(Collection<TextProfile.Location> selected,Collection<TextProfile.Write> writes,
            Map<ObjectId,TextProfile.Location> subjects,Runnable progress) {
        int count=subjects.values().stream().mapToInt(TextProfile.Location::ordinal).max().orElse(-1)+1;
        int[] parent=new int[count];boolean[] included=new boolean[count];
        Arrays.setAll(parent,i->i);for(var location:selected)included[location.ordinal()]=true;
        var seeds=new HashSet<Integer>();
        for(var write:writes) {
            progress.run();
            int target=write.location().ordinal();if(!included[target])continue;
            var sourceSet=new LinkedHashSet<Integer>();
            if(write instanceof TextProfile.CopyWrite copy)sourceSet.add(copy.source().ordinal());
            else if(write instanceof TextProfile.ExpressionWrite expression)
                for(var object:TextExpressions.reads(expression.operation().value())) {
                    var location=subjects.get(object);if(location!=null&&included[location.ordinal()])sourceSet.add(location.ordinal());
                }
            var sources=List.copyOf(sourceSet);boolean multi=sources.size()>1;
            for(int source:sources)union(parent,target,source);
            if(multi){seeds.add(target);seeds.addAll(sources);}
        }
        var seededRoots=new HashSet<Integer>();for(int seed:seeds)seededRoots.add(find(parent,seed));
        var components=new TreeMap<Integer,List<Integer>>();
        for(int cell=0;cell<count;cell++)if(included[cell]&&seededRoots.contains(find(parent,cell)))
            components.computeIfAbsent(find(parent,cell),ignored->new ArrayList<>()).add(cell);
        var ordered=new ArrayList<>(components.values());ordered.sort(Comparator.comparingInt(v->v.getFirst()));
        int[] groupOf=new int[count];Arrays.fill(groupOf,-1);var groups=new ArrayList<int[]>();
        for(int group=0;group<ordered.size();group++) {
            int[] members=ordered.get(group).stream().mapToInt(Integer::intValue).sorted().toArray();groups.add(members);
            for(int member:members)groupOf[member]=group;
        }
        return new ScalarRelations(groupOf,groups,progress);
    }

    boolean active(){return !cells.isEmpty();}
    Map<String,Long> metrics() {
        var result=new LinkedHashMap<String,Long>();
        result.put("scalarRelationGroups",(long)cells.size());
        result.put("scalarRelationCells",cells.stream().mapToLong(group->group.length).sum());
        result.put("scalarRelationMaxWidth",cells.stream().mapToLong(group->group.length).max().orElse(0));
        var factorized=domain.metrics();
        result.put("scalarRelationInternedNodes",factorized.get("internedNodes"));
        result.put("scalarRelationInternedEdges",factorized.get("internedAlternatives"));
        result.put("scalarRelationAllocatedNodes",factorized.get("allocatedNodes"));
        result.put("scalarRelationAllocatedEdges",factorized.get("allocatedAlternatives"));
        result.put("scalarRelationRetiredNodes",factorized.get("retiredNodes"));
        result.put("scalarRelationRetiredEdges",factorized.get("retiredAlternatives"));
        result.put("scalarRelationUnionPairs",factorized.get("relationUnionPairs"));
        result.put("scalarRelationProjectedRows",factorized.get("projectedAlternatives"));
        return Map.copyOf(result);
    }
    void retain(DataflowResult<PossibleValuesState> result) {
        var roots=new ArrayList<FactorizedAlternatives.Node<Candidates>>();
        result.forEachRetainedState(state->{
            if(state.relations==this&&state.relational!=null)state.relational.values.forEach(relation->relation.roots(roots));
        });
        domain.retain(roots);
    }
    Roots attach(PossibleValuesState state,ValuesWork work) {
        var roots=new ArrayList<Relation>(cells.size());
        for(var group:cells) {
            var tuple=new TreeMap<Integer,Candidates>();for(int cell:group)tuple.put(cell,state.value(cell,work));
            roots.add(new Relation(domain.singleton(tuple)));
        }
        return new Roots(roots);
    }
    Roots strong(Roots roots,int cell,Candidates value){return update(roots,cell,ignored->value,false);}
    Roots weak(Roots roots,int cell,Candidates value){return update(roots,cell,ignored->value,true);}
    Roots widen(Roots roots,int cell,ValuesWork work){return update(roots,cell,current->current.withOpen(work),false);}
    private Roots update(Roots roots,int cell,java.util.function.UnaryOperator<Candidates> change,boolean retainOld) {
        int group=group(cell);if(group<0)return roots;var old=roots.values.get(group);
        var original=old.materialize(domain);var changed=domain.update(original,Map.of(cell,change));if(retainOld)changed=domain.union(original,changed);
        return replace(roots,group,changed);
    }
    Roots join(Roots left,Roots right) {
        var joined=new ArrayList<Relation>(cells.size());
        for(int i=0;i<cells.size();i++)joined.add(union(left.values.get(i),right.values.get(i)));
        return new Roots(joined);
    }
    private Relation union(Relation left,Relation right) {
        if(left==right)return left;
        if(left.materialized!=null&&left.materialized==right.materialized)return left;
        var bins=new ArrayList<FactorizedAlternatives.Node<Candidates>>(left.bins);FactorizedAlternatives.Node<Candidates> carry;
        for(int rank=0;rank<right.bins.size();rank++) {
            carry=right.bins.get(rank);if(carry==null)continue;int slot=rank;
            while(true) {
                while(bins.size()<=slot)bins.add(null);
                var existing=bins.get(slot);
                if(existing==null){bins.set(slot,carry);break;}
                bins.set(slot,null);carry=domain.union(existing,carry);slot++;
            }
        }
        while(bins.size()>1&&bins.getLast()==null)bins.removeLast();
        return new Relation(bins);
    }
    boolean equivalent(Roots left,Roots right) {
        if(left==right)return true;for(int i=0;i<cells.size();i++)if(left.values.get(i).materialize(domain)!=right.values.get(i).materialize(domain))return false;return true;
    }
    long fingerprint(Roots roots) {
        long result=0x6a09e667f3bcc909L;for(var root:roots.values)result=Long.rotateLeft(result,11)^domain.fingerprint(root.materialize(domain));return result;
    }
    Assignment copy(PossibleValuesState state,int target,int source,ValuesWork work) {
        return image(state,target,Set.of(source),row->{var value=row.get(source);if(value==null)throw new IllegalStateException("missing correlated copy source");return value;},work);
    }
    Assignment expression(PossibleValuesState state,int target,Operations.Assign operation,
            Map<ObjectId,TextProfile.Location> subjects,ValueUniverse universe,ValuesWork work) {
        var selected=new LinkedHashSet<Integer>();
        for(var object:TextExpressions.reads(operation.value()))selected.add(subjects.get(object).ordinal());
        return image(state,target,selected,row->TextExpressions.evaluateTuple(operation,row,subjects,universe,work),work);
    }
    private Assignment image(PossibleValuesState state,int target,Set<Integer> selected,
            Function<Map<Integer,Candidates>,Candidates> transfer,ValuesWork work) {
        int group=group(target);if(group<0)return null;
        for(int source:selected)if(group(source)!=group)throw new IllegalStateException("correlated scalar source split across groups");
        var original=state.relational.values.get(group).materialize(domain);var projected=domain.project(original,selected);
        Relation result=null;Candidates pointwise=null;
        for(var row:domain.selections(projected)) {
            var supplied=transfer.apply(row);pointwise=pointwise==null?supplied:pointwise.join(supplied,work);
            var restricted=domain.restrict(original,row);
            var contribution=new Relation(domain.update(restricted,Map.of(target,ignored->supplied)));
            result=result==null?contribution:union(result,contribution);
        }
        if(result==null||pointwise==null)throw new IllegalStateException("reached scalar relation has no alternatives");
        return new Assignment(pointwise,replace(state.relational,group,result.materialize(domain)));
    }
    private Roots replace(Roots roots,int group,FactorizedAlternatives.Node<Candidates> value) {
        if(roots.values.get(group).materialized==value)return roots;var copy=new ArrayList<>(roots.values);copy.set(group,new Relation(value));return new Roots(copy);
    }
    private int group(int cell){return cell>=0&&cell<groupOf.length?groupOf[cell]:-1;}
    private static int find(int[] parent,int value){int root=value;while(parent[root]!=root)root=parent[root];while(parent[value]!=value){int next=parent[value];parent[value]=root;value=next;}return root;}
    private static void union(int[] parent,int left,int right){int a=find(parent,left),b=find(parent,right);if(a==b)return;if(a<b)parent[b]=a;else parent[a]=b;}
}

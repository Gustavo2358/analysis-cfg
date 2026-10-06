package io.github.gustavo2358.analysis.structure;
import java.util.*;
/** Exact finite caller closure via iterative SCC condensation; see activation-memory.md. */
final class CallerSupport {
 private CallerSupport() { }
 static BitSet[] compute(int[] variables,int[][] parents) {
  int n=variables.length;var children=new ArrayList<List<Integer>>(n);
  for(int i=0;i<n;i++)children.add(new ArrayList<>());
  for(int child=0;child<n;child++)for(int parent:parents[child])children.get(parent).add(child);
  boolean[] seen=new boolean[n];int[] stack=new int[n],cursor=new int[n],order=new int[n];int used=0;
  for(int root=0;root<n;root++)if(!seen[root]) {
   int depth=0;stack[depth++]=root;seen[root]=true;
   while(depth>0) {
    int v=stack[depth-1];var edges=children.get(v);
    if(cursor[v]<edges.size()){int next=edges.get(cursor[v]++);if(!seen[next]){seen[next]=true;stack[depth++]=next;}}
    else {depth--;order[used++]=v;}
   }
  }
  int[] component=new int[n];Arrays.fill(component,-1);int count=0;
  for(int pos=n-1;pos>=0;pos--)if(component[order[pos]]<0) {
   int depth=0;stack[depth++]=order[pos];component[order[pos]]=count;
   while(depth>0)for(int parent:parents[stack[--depth]])if(component[parent]<0){component[parent]=count;stack[depth++]=parent;}
   count++;
  }
  var support=new BitSet[count];var outgoing=new ArrayList<Set<Integer>>(count);int[] indegrees=new int[count];
  for(int i=0;i<count;i++){support[i]=new BitSet();outgoing.add(new HashSet<>());}
  for(int child=0;child<n;child++) {
   support[component[child]].set(variables[child]);
   for(int parent:parents[child])if(component[parent]!=component[child]&&outgoing.get(component[parent]).add(component[child]))indegrees[component[child]]++;
  }
  var pending=new ArrayDeque<Integer>();for(int i=0;i<count;i++)if(indegrees[i]==0)pending.add(i);
  while(!pending.isEmpty()) {int from=pending.removeFirst();for(int to:outgoing.get(from)){support[to].or(support[from]);if(--indegrees[to]==0)pending.addLast(to);}}
  var result=new BitSet[n];for(int i=0;i<n;i++)result[i]=(BitSet)support[component[i]].clone();return result;
 }
}

package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.values.TextPredicate;
import java.util.Objects;
import java.util.function.LongPredicate;

/**
 * Internal complete conditional nominal evaluation on one admitted SOURCE unit.
 * It neither synthesizes executable AIR/CFG facts nor substitutes for target
 * resolution. Immutable SOURCE and control facts are borrowed; all variable
 * metadata, demand, graph, evaluator state, values, text and proofs are paged.
 * Successful construction completes the selected query set; operational failure
 * retires derived owners and never publishes a capped/partial nominal result.
 */
final class SourceNominalSnapshot extends SourceAdmissionSnapshot.Dependent {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DOMAIN;
    private static final int SYMBOL=1,STATEMENT=2,NODE=3,QUERY=4,CONDITION=5,BRANCH=6,DECLARATION=7;
    private static final long DEMAND=8,NEEDED=1;
    private volatile SourceAdmissionSnapshot admission;
    private SourceSnapshot source;
    private PageStore pages;
    private AnalysisResources resources;
    private SourceControlSnapshot controlFacts;
    private SourceAdmissionDirectory metadata;
    private SourceNominalDemand demand;
    private SourceSnapshotExpressions expressions;
    private SourceLiteralView literal;
    private SourceValueStore values;
    private PagedLongArray requested,outputs,proofRows,assignments,waiting,roots,tokens,observations,tails;
    private PagedWorklist work;
    private AnalysisResources.Reservation control;
    private long unit,unitOrdinal,scope,nominal,facts,queryCount,activeQueries,proofCount,waitingCount,observedCount,workItems;
    private long[] transferRoots,transferResults,transferLocations;
    private byte[] transferModes;
    private volatile boolean closed,ready;
    private SourceNominalSnapshot(SourceAdmissionSnapshot admission,AnalysisResources.Reservation capacity){this.admission=admission;control=capacity;}
    static SourceNominalSnapshot prepare(SourceAdmissionSnapshot admission,long unitOrdinal,LongPredicate queries,SourceControlSnapshot controlFacts,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(queries);Objects.requireNonNull(controlFacts);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        admission.requireContext(pages,resources);controlFacts.requireAdmission(admission);
        if(unitOrdinal<0||unitOrdinal>=admission.unitCount())throw new IndexOutOfBoundsException("nominal SOURCE unit ordinal");
        long unit=admission.unit(unitOrdinal),nominal=admission.snapshot().field(unit,SourceShape.UNIT_EVIDENCE,12);
        if(nominal==0)throw new IllegalArgumentException("nominal SOURCE evidence unavailable");
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,4096,PHASE);SourceNominalSnapshot result=null;
        try {
            result=new SourceNominalSnapshot(admission,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.unitOrdinal=unitOrdinal;result.unit=unit;result.nominal=nominal;result.controlFacts=controlFacts;result.initialize(queries,pages,resources);
            admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure) {
            if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            throw failure;
        }
    }
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("nominal SOURCE result expired");return value;}
    private void open(){if(closed||!ready)throw new IllegalStateException("nominal SOURCE result expired or incomplete");parent().snapshot();}
    private long field(long row,SourceShape shape,int column){return source.field(row,shape,column);}
    private long size(long list,SourceShape shape){return list==0?0:source.size(list,shape);}
    private long item(long list,SourceShape shape,long index){return source.element(list,shape,index);}
    private PagedLongArray array(){return new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);}
    private long lookup(int namespace,long reference){long row=metadata.find(scope,namespace,reference,0);if(row==0)throw new IllegalStateException("admitted nominal SOURCE reference missing");return row;}
    private long intern(int namespace,long key,long reference,long ordinal){return metadata.intern(scope,namespace,key,0,reference,ordinal);}
    private void initialize(LongPredicate queries,PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("nominal SOURCE result expired before initialization");this.pages=pages;this.resources=resources;source=parent().snapshot();scope=field(unit,SourceShape.UNIT_EVIDENCE,0);facts=field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,0);
        long queryList=field(facts,SourceShape.NOMINAL_VALUES,4);queryCount=size(queryList,SourceShape.QUERY);requested=array();
        for(long i=0;i<queryCount;i++)if(queries.test(i)){requested.set(i,1);activeQueries=Math.incrementExact(activeQueries);}
        if(activeQueries!=0) {
            metadata=new SourceAdmissionDirectory(pages,resources);outputs=array();proofRows=array();assignments=array();waiting=array();roots=array();tokens=array();observations=array();tails=array();work=new PagedWorklist(pages,resources,PHASE);
            transferRoots=new long[64];transferResults=new long[64];transferLocations=new long[64];transferModes=new byte[64];
            values=new SourceValueStore(pages,resources);expressions=SourceSnapshotExpressions.prepare(parent(),pages,resources);literal=SourceLiteralView.prepare(parent(),pages,resources);demand=SourceNominalDemand.prepare(parent(),unitOrdinal,pages,resources);
            index(queryList);backward();prepareFacts();evaluate();project();retireStates();values.collect();
        }
        controlFacts.requireAdmission(parent());parent().snapshot();if(closed)throw new IllegalStateException("nominal SOURCE result expired before completion");ready=true;
    }
    private void index(long queries) {
        long symbols=field(facts,SourceShape.NOMINAL_VALUES,1);demand.begin();
        for(long i=0,n=size(symbols,SourceShape.SYMBOL);i<n;i++) {
            long symbol=item(symbols,SourceShape.SYMBOL,i),row=intern(SYMBOL,field(symbol,SourceShape.SYMBOL,0),symbol,i),model=field(symbol,SourceShape.SYMBOL,2);
            metadata.state(row,0,model!=0&&source.bool(model)?SourceValueStore.MODEL:0);
        }
        for(long i=0;i<queryCount;i++)if(requested.get(i)!=0) {
            long query=item(queries,SourceShape.QUERY,i);intern(QUERY,field(query,SourceShape.QUERY,0),query,i);demand.requestReference(field(query,SourceShape.QUERY,1));
        }demand.finish();
        for(long i=0,n=size(symbols,SourceShape.SYMBOL);i<n;i++)if(demand.contains(i)){long row=lookup(SYMBOL,field(item(symbols,SourceShape.SYMBOL,i),SourceShape.SYMBOL,0));metadata.state(row,0,metadata.state(row,0)|DEMAND);}
        long statements=field(unit,SourceShape.UNIT_EVIDENCE,2),nodes=field(unit,SourceShape.UNIT_EVIDENCE,5);
        for(long i=0,n=size(statements,SourceShape.STATEMENT);i<n;i++){long statement=item(statements,SourceShape.STATEMENT,i),id=field(statement,SourceShape.STATEMENT,0);intern(STATEMENT,field(id,SourceShape.STATEMENT_ID,1),statement,i);}
        for(long i=0,n=size(nodes,SourceShape.NODE);i<n;i++) {
            long node=item(nodes,SourceShape.NODE,i),row=intern(NODE,field(node,SourceShape.NODE,0),node,i),query=metadata.find(scope,QUERY,field(node,SourceShape.NODE,2),0);
            if(query!=0) {
                markNeeded(row);long at=Math.multiplyExact(++observedCount,2);observations.set(at,row);observations.set(at+1,metadata.state(query,0));metadata.state(query,0,observedCount);
            }
        }
    }
    private void markNeeded(long row){if((metadata.state(row,0)&NEEDED)==0){metadata.state(row,0,NEEDED);work.add(row);}}
    private void backward() {
        long steps=field(unit,SourceShape.UNIT_EVIDENCE,6);
        try(var incoming=array()) {
            for(long i=0,n=size(steps,SourceShape.DERIVATION);i<n;i++) {
                long step=item(steps,SourceShape.DERIVATION,i),row=lookup(NODE,field(step,SourceShape.DERIVATION,2)),at=Math.multiplyExact(i+1,2);
                incoming.set(at,step);incoming.set(at+1,metadata.state(row,1));metadata.state(row,1,i+1);
            }
            while(work.size()!=0) {
                long node=work.remove();
                for(long e=metadata.state(node,1);e!=0;e=incoming.get(e*2+1)) {
                    long step=incoming.get(e*2),sources=field(step,SourceShape.DERIVATION,1),callers=field(step,SourceShape.DERIVATION,3);
                    for(long i=0,n=size(sources,SourceShape.TEXT);i<n;i++)markNeeded(lookup(NODE,item(sources,SourceShape.TEXT,i)));
                    for(long i=0,n=size(callers,SourceShape.TEXT);i<n;i++)markNeeded(lookup(NODE,item(callers,SourceShape.TEXT,i)));
                }
            }
        }
        long nodes=field(unit,SourceShape.UNIT_EVIDENCE,5);for(long i=0,n=size(nodes,SourceShape.NODE);i<n;i++)metadata.state(lookup(NODE,field(item(nodes,SourceShape.NODE,i),SourceShape.NODE,0)),1,0);
    }
    private boolean demanded(long name){return (metadata.state(lookup(SYMBOL,name),0)&DEMAND)!=0;}
    private boolean modeled(long name){return (metadata.state(lookup(SYMBOL,name),0)&SourceValueStore.MODEL)!=0;}
    private boolean table(long name){return (metadata.state(lookup(SYMBOL,name),0)&SourceValueStore.TABLE)!=0;}
    private int extent(long name){return source.integer(field(metadata.reference(lookup(SYMBOL,name)),SourceShape.SYMBOL,1));}
    private long proof(int kind,long owner,long detail,long provenance) {
        long before=metadata.size(),row=metadata.intern(scope,200+kind,owner,detail,provenance,proofCount);
        if(metadata.size()!=before) {
            long atom=++proofCount,at=Math.multiplyExact(atom,4);proofRows.set(at,kind+1);proofRows.set(at+1,owner);proofRows.set(at+2,detail);proofRows.set(at+3,provenance);
        }return metadata.ordinal(row)+1;
    }
    private void prepareFacts() {
        long declarations=field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,1),conditions=field(facts,SourceShape.NOMINAL_VALUES,3),branches=field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,3),tables=field(facts,SourceShape.NOMINAL_VALUES,5),writes=field(facts,SourceShape.NOMINAL_VALUES,2);
        for(long i=0,n=size(declarations,SourceShape.DECLARATION);i<n;i++){long row=item(declarations,SourceShape.DECLARATION,i);intern(DECLARATION,field(row,SourceShape.DECLARATION,0),field(row,SourceShape.DECLARATION,1),i);}
        for(long i=0,n=size(conditions,SourceShape.CONDITION);i<n;i++){long row=item(conditions,SourceShape.CONDITION,i);intern(CONDITION,field(row,SourceShape.CONDITION,0),field(row,SourceShape.CONDITION,1),i);}
        for(long i=0,n=size(branches,SourceShape.BRANCH);i<n;i++){long row=item(branches,SourceShape.BRANCH,i);intern(BRANCH,field(row,SourceShape.BRANCH,0),field(row,SourceShape.BRANCH,1),i);}
        for(long i=0,n=size(tables,SourceShape.TABLE_FIELD);i<n;i++){long name=field(item(tables,SourceShape.TABLE_FIELD,i),SourceShape.TABLE_FIELD,0),symbol=lookup(SYMBOL,name);metadata.state(symbol,0,metadata.state(symbol,0)|SourceValueStore.TABLE);}
        for(long i=0,n=size(writes,SourceShape.ASSIGNMENT);i<n;i++) {
            long write=item(writes,SourceShape.ASSIGNMENT,i),target=field(write,SourceShape.ASSIGNMENT,1);if(!demanded(target))continue;
            long owner=field(write,SourceShape.ASSIGNMENT,0),statement=lookup(STATEMENT,owner),origin=field(metadata.reference(statement),SourceShape.STATEMENT,1),at=(i+1)*3;
            assignments.set(at,write);assignments.set(at+1,proof(2,owner,target,origin));assignments.set(at+2,metadata.state(statement,0));metadata.state(statement,0,i+1);
        }
    }
    private long get(long state,long name){long symbol=lookup(SYMBOL,name);return (metadata.state(symbol,0)&DEMAND)==0?values.unknown(false):values.get(state,metadata.ordinal(symbol)+1,modeled(name));}
    private long put(long state,long name,long value){long symbol=lookup(SYMBOL,name);return value==values.unknown(modeled(name))?values.remove(state,metadata.ordinal(symbol)+1):values.put(state,metadata.ordinal(symbol)+1,value);}
    private long initial() {
        long result=values.emptyState(),seeds=field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,2),tables=field(facts,SourceShape.NOMINAL_VALUES,5);
        for(long i=0,n=size(seeds,SourceShape.SEED);i<n;i++) {
            long seed=item(seeds,SourceShape.SEED,i),name=field(seed,SourceShape.SEED,0);if(!demanded(name)||modeled(name))continue;
            literal.selectSource(field(seed,SourceShape.SEED,1),0,extent(name));result=put(result,name,values.literal(literal,0,proof(0,name,0,field(seed,SourceShape.SEED,3))));
        }
        for(long i=0,n=size(tables,SourceShape.TABLE_FIELD);i<n;i++) {
            long table=item(tables,SourceShape.TABLE_FIELD,i),name=field(table,SourceShape.TABLE_FIELD,0),seedsOf=field(table,SourceShape.TABLE_FIELD,1);if(!demanded(name)||modeled(name))continue;
            for(long j=0,m=size(seedsOf,SourceShape.INITIAL);j<m;j++) {
                long seed=item(seedsOf,SourceShape.INITIAL,j),origin=field(seed,SourceShape.INITIAL,0);if(modeled(origin))continue;
                literal.selectSource(field(seed,SourceShape.INITIAL,1),0,extent(name));long atom=proof(1,name,origin,metadata.reference(lookup(DECLARATION,origin)));
                result=put(result,name,values.join(get(result,name),values.literal(literal,SourceValueStore.OPEN|SourceValueStore.TABLE,atom)));
            }
        }return result;
    }
    private void enqueue(long node,long step,long destination) {
        long edge=++waitingCount,at=edge*3,ordinal=metadata.ordinal(node),last=tails.get(ordinal);waiting.set(at,step);waiting.set(at+1,destination);waiting.set(at+2,0);
        if(last==0)metadata.state(node,1,edge);else waiting.set(last*3+2,edge);tails.set(ordinal,edge);
    }
    private void evaluate() {
        long initial=initial(),steps=field(unit,SourceShape.UNIT_EVIDENCE,6);
        for(long i=0,n=size(steps,SourceShape.DERIVATION);i<n;i++) {
            long step=item(steps,SourceShape.DERIVATION,i),destination=lookup(NODE,field(step,SourceShape.DERIVATION,2));if((metadata.state(destination,0)&NEEDED)==0)continue;
            long sources=field(step,SourceShape.DERIVATION,1),callers=field(step,SourceShape.DERIVATION,3),a=size(sources,SourceShape.TEXT)==0?0:item(sources,SourceShape.TEXT,0),b=size(callers,SourceShape.TEXT)==0?0:item(callers,SourceShape.TEXT,0);
            if(a==0&&b==0)join(destination,initial);else {if(a!=0)enqueue(lookup(NODE,a),step,destination);if(b!=0&&b!=a)enqueue(lookup(NODE,b),step,destination);}
        }
        long collectAt=4096;
        while(work.size()!=0) {
            resources.work(1,PHASE);workItems=Math.incrementExact(workItems);long node=work.remove();
            for(long edge=metadata.state(node,1);edge!=0;edge=waiting.get(edge*3+2))propagate(waiting.get(edge*3),waiting.get(edge*3+1));
            if(values.records()>collectAt){clearTransfers();values.collect();collectAt=Math.max(4096,Math.multiplyExact(values.records(),2));}
        }
    }
    private void join(long node,long incoming) {
        long ordinal=metadata.ordinal(node),previous=roots.get(ordinal),joined=previous==0?incoming:values.joinStates(previous,incoming);if(joined==previous)return;
        long token=values.retain(joined),old=tokens.get(ordinal);roots.set(ordinal,joined);tokens.set(ordinal,token);if(old!=0)values.release(old);work.add(node);
    }
    private void propagate(long step,long destination) {
        long sources=field(step,SourceShape.DERIVATION,1);if(size(sources,SourceShape.TEXT)==0)return;
        long node=lookup(NODE,item(sources,SourceShape.TEXT,0)),state=roots.get(metadata.ordinal(node));if(state==0)return;
        long callers=field(step,SourceShape.DERIVATION,3);for(long i=0,n=size(callers,SourceShape.TEXT);i<n;i++)if(roots.get(metadata.ordinal(lookup(NODE,item(callers,SourceShape.TEXT,i))))==0)return;
        long location=field(metadata.reference(node),SourceShape.NODE,2),branch=metadata.find(scope,BRANCH,field(step,SourceShape.DERIVATION,0),0);
        byte mode=(byte)((branch==0?0:source.bool(metadata.reference(branch))?2:1)+(size(field(step,SourceShape.DERIVATION,6),SourceShape.TEXT)==0?4:0));
        long hash=state*0x9e3779b97f4a7c15L+location*31L+mode;hash^=hash>>>33;int slot=(int)hash&63;long result;
        if(transferRoots[slot]==state&&transferLocations[slot]==location&&transferModes[slot]==mode)result=transferResults[slot];
        else {result=transfer(state,location,mode);transferRoots[slot]=state;transferLocations[slot]=location;transferModes[slot]=mode;transferResults[slot]=result;}
        if(result!=0)join(destination,result);
    }
    private void clearTransfers(){java.util.Arrays.fill(transferRoots,0);java.util.Arrays.fill(transferResults,0);java.util.Arrays.fill(transferLocations,0);}
    private long transfer(long state,long location,byte mode) {
        int branch=mode&3;if(branch!=0){long predicate=metadata.find(scope,CONDITION,location,0);if(predicate!=0)state=filter(state,metadata.reference(predicate),branch==2);if(state==0)return 0;}
        long statement=metadata.find(scope,STATEMENT,location,0);if((mode&4)==0||statement==0||metadata.state(statement,0)==0)return state;
        long changed=state;try(var evaluator=new Evaluator(state,null)) {
            for(long write=metadata.state(statement,0);write!=0;write=assignments.get(write*3+2))changed=put(changed,field(assignments.get(write*3),SourceShape.ASSIGNMENT,1),assigned(write,state,evaluator));
        }return changed;
    }
    private long assigned(long ordinal,long state,Evaluator evaluator) {
        long write=assignments.get(ordinal*3),target=field(write,SourceShape.ASSIGNMENT,1),input=values.addSupport(evaluator.term(field(write,SourceShape.ASSIGNMENT,2)),assignments.get(ordinal*3+1));int flags=values.flags(input);boolean model=modeled(target)||(flags&SourceValueStore.MODEL)!=0;
        if(model)flags|=SourceValueStore.OPEN|SourceValueStore.MODEL;if(table(target))flags|=SourceValueStore.TABLE;long result=values.emptyValue(flags);
        try(var candidates=values.candidates(input)) {while(candidates.advance()){literal.selectValue(values,candidates.key(),0,extent(target));result=values.addCandidate(result,literal,candidates.value());if(model){literal.selectValue(values,candidates.key(),0,-1);result=values.addCandidate(result,literal,candidates.value());}}}
        return model||table(target)?values.join(get(state,target),result):result;
    }
    private final class Evaluator implements AutoCloseable {
        private final long state;
        private final Evaluator parent;
        private PagedLongArray results,inputs,frames,predicateFrames;
        private PagedLongIndex truths;
        Evaluator(long state,Evaluator parent) {
            this.state=state;this.parent=parent;
            try{results=array();inputs=array();frames=array();predicateFrames=array();truths=new PagedLongIndex(pages,resources,PHASE);}
            catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;}
        }
        private long leaf(long node) {
            int kind=expressions.kind(node);long payload=expressions.payload(node),input;
            if(kind==SourceSnapshotExpressions.READ)input=get(state,payload);
            else if(kind==SourceSnapshotExpressions.LITERAL){if(payload==-1)literal.selectSpace(0,-1);else literal.selectSource(payload,0,-1);input=values.literal(literal,0,0);}
            else input=values.unknown(false);
            long output=parent!=null&&parent.inputs.get(node)==input?parent.results.get(node):0;
            if(output==0)output=expressions.transforms(node)==0?input:transform(expressions.transforms(node),input);inputs.set(node,input);results.set(node,output);return output;
        }
        long term(long reference) {
            long root=expressions.compile(reference),known=results.get(root);if(known!=0)return known;if(expressions.kind(root)!=SourceSnapshotExpressions.CHOICE)return leaf(root);
            long top=0;frames.set(0,root);frames.set(1,0);frames.set(2,0);long result=0;
            while(top>=0) {
                long at=top*3,node=frames.get(at),next=frames.get(at+1),accumulated=frames.get(at+2);
                if(next<expressions.argumentCount(node)) {
                    long child=expressions.argument(node,next),value=results.get(child);if(value==0&&expressions.kind(child)!=SourceSnapshotExpressions.CHOICE)value=leaf(child);
                    if(value==0){top++;frames.set(top*3,child);frames.set(top*3+1,0);frames.set(top*3+2,0);continue;}
                    frames.set(at+2,accumulated==0?value:values.join(accumulated,value));frames.set(at+1,next+1);
                }else {result=accumulated;results.set(node,result);inputs.set(node,0);top--;if(top>=0){long parentAt=top*3,old=frames.get(parentAt+2);frames.set(parentAt+2,old==0?result:values.join(old,result));frames.set(parentAt+1,frames.get(parentAt+1)+1);}}
            }return result;
        }
        private long transform(int mask,long input) {
            int flags=values.flags(input);long result=values.emptyValue(flags);
            try(var candidates=values.candidates(input)) {while(candidates.advance()){if(!literal.selectValue(values,candidates.key(),mask,-1)){flags|=SourceValueStore.OPEN;continue;}result=values.addCandidate(result,literal,candidates.value());}}
            return values.withFlags(result,flags);
        }
        int truth(long root) {
            long existing=truths.find(root);if(existing!=0)return Math.toIntExact(existing);long top=0;predicateFrames.set(0,root);predicateFrames.set(1,0);
            while(top>=0) {
                long at=top*3,predicate=predicateFrames.get(at),next=predicateFrames.get(at+1),children=field(predicate,SourceShape.PREDICATE,2);
                if(next<size(children,SourceShape.PREDICATE)) {
                    long child=item(children,SourceShape.PREDICATE,next);predicateFrames.set(at+1,next+1);if(truths.find(child)==0){top++;predicateFrames.set(top*3,child);predicateFrames.set(top*3+1,0);}continue;
                }
                long kind=field(predicate,SourceShape.PREDICATE,0);int result;
                if(source.matches(kind,"NOT"))result=TextPredicate.negate(Math.toIntExact(truths.find(item(children,SourceShape.PREDICATE,0))));
                else if(source.matches(kind,"AND")||source.matches(kind,"OR")){boolean and=source.matches(kind,"AND");result=and?TextPredicate.TRUE:TextPredicate.FALSE;for(long i=0,n=size(children,SourceShape.PREDICATE);i<n;i++)result=TextPredicate.combine(and,result,Math.toIntExact(truths.find(item(children,SourceShape.PREDICATE,i))));}
                else {long terms=field(predicate,SourceShape.PREDICATE,1),left=item(terms,SourceShape.TERM,0),right=item(terms,SourceShape.TERM,1),a=term(left),b=term(right);result=figurative(right)?values.figurativeEquality(a):figurative(left)?values.figurativeEquality(b):values.equality(a,b);}
                truths.intern(predicate,result);top--;
            }return Math.toIntExact(truths.find(root));
        }
        @Override public void close(){closeOwned(truths,predicateFrames,frames,inputs,results);truths=null;predicateFrames=frames=inputs=results=null;}
    }
    private boolean figurative(long term){long kind=field(term,SourceShape.TERM,0);return source.matches(kind,"LOW_VALUES")||source.matches(kind,"HIGH_VALUES");}
    private long filter(long state,long predicate,boolean whenTrue) {
        try(var reads=new SourceAdmissionDirectory(pages,resources);var pending=array();var readNames=array()) {
            long tail=1,readCount=0;pending.set(0,predicate);reads.intern(0,1,predicate,0,predicate,0);
            for(long head=0;head<tail;head++) {
                long row=pending.get(head);SourceShape shape=source.shape(row);
                if(shape==SourceShape.PREDICATE) {
                    long children=field(row,shape,2),terms=field(row,shape,1);
                    for(long i=0,n=size(children,SourceShape.PREDICATE);i<n;i++){long child=item(children,SourceShape.PREDICATE,i),before=reads.size();reads.intern(0,1,child,0,child,0);if(reads.size()!=before)pending.set(tail++,child);}
                    for(long i=0,n=size(terms,SourceShape.TERM);i<n;i++){long child=item(terms,SourceShape.TERM,i),before=reads.size();reads.intern(0,2,child,0,child,0);if(reads.size()!=before)pending.set(tail++,child);}
                }else {
                    if(source.matches(field(row,SourceShape.TERM,0),"READ")){long name=field(row,SourceShape.TERM,1),before=reads.size();reads.intern(0,3,name,0,name,0);if(reads.size()!=before)readNames.set(readCount++,name);if(table(name)||(values.flags(get(state,name))&SourceValueStore.MODEL)!=0)return state;}
                    long children=field(row,SourceShape.TERM,2);for(long i=0,n=size(children,SourceShape.TERM);i<n;i++){long child=item(children,SourceShape.TERM,i),before=reads.size();reads.intern(0,2,child,0,child,0);if(reads.size()!=before)pending.set(tail++,child);}
                }
            }
            int wanted=whenTrue?TextPredicate.TRUE:TextPredicate.FALSE;
            try(var baseline=new Evaluator(state,null)) {
                if((baseline.truth(predicate)&wanted)==0)return 0;long result=state;
                for(long i=0;i<readCount;i++) {
                    long name=readNames.get(i);if(!demanded(name))continue;
                    long old=get(state,name),kept=values.emptyValue(values.flags(old));boolean any=false;
                    try(var candidates=values.candidates(old)) {
                        while(candidates.advance()) {
                            long snapshot=put(state,name,values.oneCandidate(old,candidates.key(),candidates.value(),literal));boolean allowed=snapshot==state;
                            if(!allowed)try(var evaluator=new Evaluator(snapshot,baseline)){allowed=(evaluator.truth(predicate)&wanted)!=0;}
                            if(allowed){literal.selectValue(values,candidates.key(),0,-1);kept=values.addCandidate(kept,literal,candidates.value());any=true;}
                        }
                    }if(!any&&(values.flags(old)&SourceValueStore.OPEN)==0)return 0;result=put(result,name,kept);
                }return result;
            }
        }
    }
    private void project() {
        long queries=field(facts,SourceShape.NOMINAL_VALUES,4);
        for(long i=0;i<queryCount;i++)if(requested.get(i)!=0) {
            long query=item(queries,SourceShape.QUERY,i),row=lookup(QUERY,field(query,SourceShape.QUERY,0)),name=field(query,SourceShape.QUERY,1),combined=0;boolean affected=false;
            for(long observation=metadata.state(row,0);observation!=0;observation=observations.get(observation*2+1)) {
                long node=observations.get(observation*2),ordinal=metadata.ordinal(node);affected|=controlFacts.affected(unitOrdinal,ordinal);long state=roots.get(ordinal);
                if(state!=0){long value=get(state,name);combined=combined==0?value:values.join(combined,value);}
            }long at=i*3;outputs.set(at,combined);outputs.set(at+1,combined==0?0:values.retain(combined));outputs.set(at+2,(affected?1:0)|(table(name)?2:0));
        }
    }
    private void retireStates() {
        clearTransfers();long nodes=field(unit,SourceShape.UNIT_EVIDENCE,5);for(long i=0,n=size(nodes,SourceShape.NODE);i<n;i++){long token=tokens.get(i);if(token!=0)values.release(token);}
        closeOwned(work,tails,observations,tokens,roots,waiting,assignments,demand,expressions,literal);work=null;tails=observations=tokens=roots=waiting=assignments=null;demand=null;expressions=null;literal=null;
    }
    private void query(long ordinal){if(ordinal<0||ordinal>=queryCount)throw new IndexOutOfBoundsException("nominal SOURCE query ordinal");}
    long value(long ordinal){open();try{query(ordinal);return outputs==null?0:outputs.get(ordinal*3);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    boolean affected(long ordinal){open();try{query(ordinal);return outputs!=null&&(outputs.get(ordinal*3+2)&1)!=0;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    boolean summary(long ordinal){open();try{query(ordinal);return outputs!=null&&(outputs.get(ordinal*3+2)&2)!=0;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    SourceValueStore values(){open();if(values==null)throw new IllegalStateException("no nominal query evaluated");return values;}
    long nominalEvidence(){open();return nominal;}
    long activeQueries(){open();return activeQueries;}
    long workItems(){open();return workItems;}
    long retainedMetadata(){open();try{return metadata==null?0:metadata.size();}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private long proofField(long atom,int column){open();try{if(atom<=0||atom>proofCount)throw new IllegalArgumentException("nominal proof atom");return proofRows.get(atom*4+column);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    int proofKind(long atom){return Math.toIntExact(proofField(atom,0))-1;}
    long proofOwner(long atom){return proofField(atom,1);}
    long proofDetail(long atom){return proofField(atom,2);}
    long proofProvenance(long atom){return proofField(atom,3);}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    private static void closeOwned(AutoCloseable... owned) {
        Throwable failure=null;for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("nominal SOURCE cleanup",failure);
    }
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){if(closed)return;closed=true;ready=false;owned=new AutoCloseable[]{work,tails,observations,tokens,roots,waiting,assignments,demand,expressions,literal,values,proofRows,outputs,requested,metadata,control};work=null;tails=observations=tokens=roots=waiting=assignments=proofRows=outputs=requested=null;demand=null;expressions=null;literal=null;values=null;metadata=null;control=null;source=null;admission=null;controlFacts=null;pages=null;resources=null;transferRoots=transferResults=transferLocations=null;transferModes=null;}
        var parent=owner;if(parent!=null)parent.release(this);closeOwned(owned);
    }
}

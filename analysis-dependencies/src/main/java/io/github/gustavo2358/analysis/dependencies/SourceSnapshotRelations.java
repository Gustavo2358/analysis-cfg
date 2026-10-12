package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Borrowed complete semantic check. This class cannot issue or represent admission. */
final class SourceSnapshotRelations implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int UNIT=1,STATEMENT=2,HANDLE=3,OCCURRENCE=4,OCCURRENCE_HANDLE=5,
            TARGET=6,NODE=7,DERIVATION=8,SELECTION=9,EVENT=10,GUARD=11,PROOF=12,
            LOCATION=13,REGISTRATION=14,SYMBOL=15,DECLARATION=16,SEED=17,CONDITION=18,
            QUERY=19,BRANCH=20,UNCERTAINTY=21,TABLE=22,WRITE=23,NATIVE=24,REFERENCE=25,
            TERM=26,PREDICATE=27;
    private SourceSnapshot source;
    private final AnalysisResources resources;
    private AnalysisResources.Reservation control;
    private SourceAdmissionDirectory directory;
    private PagedLongArray edges,queue;
    private char[] prefix;
    private long unit,referencePass,edgeCount,read,write;
    private int minor;
    private boolean closed;

    static void check(SourceSnapshot source,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(source);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        SourceSnapshotConstraints.check(source,pages,resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,1280,PHASE);
        try(var check=new SourceSnapshotRelations(source,pages,resources,capacity)){check.all();}
    }
    private SourceSnapshotRelations(SourceSnapshot source,PageStore pages,AnalysisResources resources,AnalysisResources.Reservation capacity) {
        this.source=source;this.resources=resources;control=capacity;
        try {
            prefix=new char[6];directory=new SourceAdmissionDirectory(pages,resources);
            edges=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            queue=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            control.onOwnerClose(this::close);
        }catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    private static void require(boolean yes,String message){if(!yes)throw new IllegalArgumentException(message);}
    private void work(){if(closed)throw new IllegalStateException("source relation check expired");resources.work(1,PHASE);}
    private long field(long ref,int column){return source.field(ref,source.shape(ref),column);}
    private long size(long ref,int column){var shape=source.shape(ref);long list=source.field(ref,shape,column);return list==0?0:source.size(list,shape.field(column).element());}
    private long item(long ref,int column,long ordinal){var shape=source.shape(ref);return source.element(source.field(ref,shape,column),shape.field(column).element(),ordinal);}
    private boolean is(long ref,int column,String text){return source.matches(field(ref,column),text);}
    private boolean flag(long ref,int column){return source.bool(field(ref,column));}
    private long find(int namespace,long key){return directory.find(unit,namespace,key,0);}
    private long record(int namespace,long key){long row=find(namespace,key);require(row!=0,"missing source reference");return directory.reference(row);}
    private long unique(int namespace,long first,long second,long ref,long ordinal) {
        long next=Math.incrementExact(directory.size()),row=directory.intern(unit,namespace,first,second,ref,ordinal);
        require(row==next,"duplicate source identity");return row;
    }
    private void index(long owner,int column,int namespace) {
        for(long i=0,n=size(owner,column);i<n;i++){work();long ref=item(owner,column,i);unique(namespace,field(ref,0),0,ref,i);}
    }
    private void refs(long owner,int column,int namespace) {
        long list=field(owner,column),pass=referencePass=Math.incrementExact(referencePass);
        for(long i=0,n=size(owner,column);i<n;i++) {
            work();long key=item(owner,column,i);require(find(namespace,key)!=0,"invalid source reference");
            long row=directory.intern(unit,REFERENCE,list,key,key,i);
            require(directory.state(row,0)!=pass,"duplicate source reference");directory.state(row,0,pass);
        }
    }
    private void all() {
        long root=source.root();minor=-1;
        for(int i=0;i<=6;i++)if(is(root,1,"1."+i+".0")){minor=i;break;}
        require(minor>=0,"unsupported source contract");
        unit=0;
        for(long i=0,n=size(root,5);i<n;i++){work();long evidence=item(root,5,i);unique(UNIT,field(evidence,0),0,evidence,i);}
        for(long i=0,n=size(root,5);i<n;i++){work();long evidence=item(root,5,i);unit=field(evidence,0);checkUnit(evidence);}
        source.root();
    }
    private void checkUnit(long evidence) {
        index(evidence,2,STATEMENT);index(evidence,3,OCCURRENCE);index(evidence,4,TARGET);
        index(evidence,5,NODE);index(evidence,6,DERIVATION);index(evidence,7,SELECTION);
        index(evidence,8,EVENT);index(evidence,9,GUARD);index(evidence,10,PROOF);
        for(long i=0,n=size(evidence,2);i<n;i++) {
            work();long ref=item(evidence,2,i),id=field(ref,0);require(field(id,0)==unit,"statement unit");
            unique(HANDLE,field(id,1),0,ref,i);
        }
        for(long i=0,n=size(evidence,3);i<n;i++){work();long ref=item(evidence,3,i);unique(OCCURRENCE_HANDLE,field(field(ref,0),1),0,ref,i);}
        for(long i=0,n=size(evidence,5);i<n;i++) {
            work();long node=item(evidence,5,i),location=field(node,2);
            long row=directory.intern(unit,LOCATION,location,0,location,0);
            directory.state(row,0,Math.incrementExact(directory.state(row,0)));
            require(minor==6||size(field(node,3),4)==0,"condition state requires version 1.6.0");
            require(minor>=2||!is(field(node,3),3,"SOURCE_REENTRY_UNDEFINED"),"source reentry requires version 1.2.0");
        }
        for(long i=0,n=size(evidence,4);i<n;i++){work();target(item(evidence,4,i));}
        for(long i=0,n=size(evidence,10);i<n;i++) {
            work();long proof=item(evidence,10,i);refs(proof,4,PROOF);
            require(minor>=1||!is(proof,1,"CONTROL_POSSIBILITY"),"source possibility requires version 1.1.0");
            require(minor==6||!is(proof,2,"cics-pgmiderr-condition-event")&&!is(proof,2,"cics-condition-registration"),"condition proof version");
        }
        proofCycles(evidence);
        for(long i=0,n=size(evidence,5);i<n;i++){work();support(field(item(evidence,5,i),3));}
        for(long i=0,n=size(evidence,9);i<n;i++){work();record(EVENT,field(item(evidence,9,i),1));}
        for(long i=0,n=size(evidence,8);i<n;i++){work();event(item(evidence,8,i));}
        for(long i=0,n=size(evidence,7);i<n;i++){work();selection(item(evidence,7,i));}
        grounding(evidence);
        for(long i=0,n=size(evidence,7);i<n;i++) {
            work();long ref=item(evidence,7,i);require(size(ref,5)==0||directory.state(find(SELECTION,field(ref,0)),0)!=0,"missing selection derivation");
        }
        for(long i=0,n=size(evidence,3);i<n;i++) {
            work();long ref=item(evidence,3,i),id=field(ref,0);record(STATEMENT,id);alternatives(ref,9,field(id,1));
        }
        for(long i=0,n=size(evidence,11);i<n;i++){work();long ref=item(evidence,11,i);record(NODE,field(ref,0));refs(ref,3,PROOF);}
        require(flag(evidence,1)||size(evidence,5)==0&&size(evidence,6)==0&&size(evidence,7)==0&&size(evidence,8)==0&&size(evidence,10)==0&&size(evidence,11)==0,"unavailable control has no authority");
        require(minor>=1||size(evidence,13)==0,"native files require version 1.1.0");
        for(long i=0,n=size(evidence,13);i<n;i++) {
            work();long ref=item(evidence,13,i),id=field(ref,0);require(field(id,0)==unit,"native file statement unit");record(STATEMENT,id);
            unique(NATIVE,id,source.integer(field(ref,1)),ref,i);alternatives(ref,7,field(ref,2));
            require(flag(evidence,1)||size(ref,7)==0,"native file control availability");
        }
        long nominal=field(evidence,12);if(nominal!=0)nominal(nominal);
    }
    private void target(long target) {
        refs(target,2,STATEMENT);
        for(long i=0,n=size(target,3);i<n;i++) {
            work();long registration=item(target,3,i),sid=field(registration,0),statement=record(STATEMENT,sid);
            unique(REGISTRATION,field(target,0),sid,registration,i);
            require(field(statement,1)==field(registration,1),"registration provenance");
        }
        for(long i=0,n=size(target,4);i<n;i++) {
            work();long operand=item(target,4,i),sid=field(field(operand,0),0);
            require(directory.find(unit,REGISTRATION,field(target,0),sid)!=0,"program operand owner");
        }
        require(size(target,5)==0||is(target,1,"PROGRAM_LITERAL")&&size(target,4)>0,"program literal authority");
    }
    private void support(long support) {
        for(long i=0,n=size(support,4);i<n;i++) {
            work();long condition=item(support,4,i),statement=record(STATEMENT,field(condition,1));refs(condition,3,PROOF);boolean qualified=false;
            for(long j=0,m=size(condition,3);j<m;j++) {
                work();long proof=record(PROOF,item(condition,3,j));
                if(is(proof,1,"LOCAL_GRAMMAR")&&is(proof,2,"cics-condition-registration")&&field(proof,3)==field(statement,1))qualified=true;
            }
            require(qualified,"condition registration proof/origin");
        }
        refs(support,1,TARGET);refs(support,2,STATEMENT);
        if(size(support,1)>0)require(directory.find(unit,REGISTRATION,item(support,1,0),item(support,2,0))!=0,"support activation/target");
    }
    private boolean authority(long owner,int column,String rule) {
        for(long i=0,n=size(owner,column);i<n;i++){work();long proof=record(PROOF,item(owner,column,i));if(is(proof,1,"LOCAL_GRAMMAR")&&is(proof,2,rule))return true;}
        return false;
    }
    private void event(long event) {
        record(STATEMENT,field(event,1));refs(event,7,GUARD);refs(event,8,PROOF);
        require(minor==6||!is(event,2,"LINK_PGMIDERR"),"condition event version");
        for(long i=0,n=size(event,7);i<n;i++){work();require(field(record(GUARD,item(event,7,i)),1)==field(event,0),"guard owner");}
        if(is(event,2,"EXPLICIT_ABEND"))require(size(event,7)==0,"explicit event has no guards");
        else require(size(event,7)==2&&is(record(GUARD,item(event,7,0)),2,"CONDITION_RAISED")&&is(record(GUARD,item(event,7,1)),2,"DEFAULT_DISPOSITION_APPLIES")&&is(event,4,"HANDLER_ELIGIBLE"),"origin guard correlation");
        require(authority(event,8,is(event,2,"EXPLICIT_ABEND")?"cics-explicit-abend-event":is(event,2,"LINK_PGMIDERR")?"cics-link-pgmiderr-default-abend-event":"cics-xctl-pgmiderr-default-abend-event"),"event proof authority");
    }
    private boolean supportEquals(long first,long second) {
        if(first==second)return true;
        for(int i=0;i<4;i++)if(field(first,i)!=field(second,i))return false;
        long n=size(first,4);if(n!=size(second,4))return false;
        for(long i=0;i<n;i++){work();if(item(first,4,i)!=item(second,4,i))return false;}
        return true; // absent conditions and [] are semantically equal, not physically identical.
    }
    private void selection(long selection) {
        long event=record(EVENT,field(selection,1)),node=record(NODE,field(selection,2));
        refs(selection,3,TARGET);refs(selection,5,NODE);refs(selection,6,GUARD);refs(selection,7,PROOF);
        require(field(node,2)==field(field(event,1),1)&&field(field(event,1),0)==unit,"selection source event");
        require(field(selection,6)==field(event,7)&&field(selection,7)==field(event,8),"selection event qualification");
        require(flag(selection,11)==is(event,4,"HANDLERS_BYPASSED"),"bypass qualification");
        if(size(selection,3)>0) {
            long entry=item(selection,4,0),support=field(node,3);support(entry);
            require(!flag(selection,11)&&is(support,0,"ACTIVE")&&field(support,1)==field(selection,3),"selected ACTIVE target");
            require(is(entry,0,"DEACTIVATED")&&field(entry,1)==field(selection,3)&&field(entry,2)==field(support,2),"deactivated entry support");
            if(size(selection,5)>0) {
                long local=record(NODE,item(selection,5,0)),target=record(TARGET,item(selection,3,0));
                require(supportEquals(field(local,3),entry)&&is(target,1,"LABEL_LOCAL")&&size(target,2)==1&&field(local,2)==field(item(target,2,0),1),"local entry correlation");
            }
        }else require(size(selection,5)==0,"unselected local entry");
        if(flag(selection,11))require(size(selection,3)==0&&!flag(selection,8)&&!flag(selection,9)&&!flag(selection,10),"bypassed remainder");
    }
    private void alternatives(long owner,int column,long location) {
        refs(owner,column,NODE);long row=find(LOCATION,location),expected=row==0?0:directory.state(row,0);
        require(size(owner,column)==expected,"complete source alternatives");
        for(long i=0,n=size(owner,column);i<n;i++){work();require(field(record(NODE,item(owner,column,i)),2)==location,"qualification location owner");}
    }
    private void resetWork(){read=write=edgeCount=0;}
    private void enqueue(long row){queue.set(write,row);write=Math.incrementExact(write);}
    private void edge(long prerequisite,long dependent) {
        long ordinal=edgeCount=Math.incrementExact(edgeCount),at=Math.multiplyExact(ordinal,2);
        edges.set(at,dependent);edges.set(at+1,directory.state(prerequisite,1));directory.state(prerequisite,1,ordinal);
    }
    private void proofCycles(long evidence) {
        resetWork();
        for(long i=0,n=size(evidence,10);i<n;i++) {
            work();long proof=item(evidence,10,i),row=find(PROOF,field(proof,0));directory.state(row,0,size(proof,4));
            if(size(proof,4)==0)enqueue(row);
            for(long j=0,m=size(proof,4);j<m;j++){work();edge(find(PROOF,item(proof,4,j)),row);}
        }
        while(read<write) {
            work();long row=queue.get(read++);
            for(long edge=directory.state(row,1);edge!=0;) {
                work();long at=Math.multiplyExact(edge,2),destination=edges.get(at),pending=directory.state(destination,0);
                require(pending>0,"proof worklist invariant");directory.state(destination,0,pending-1);if(pending==1)enqueue(destination);
                edge=edges.get(at+1);
            }
        }
        require(write==size(evidence,10),"proof cycle");
    }
    private void reach(long row) {if(directory.state(row,0)==0){directory.state(row,0,1);enqueue(row);}}
    private boolean starts(long text,String expected) {
        if(source.characterCount(text)<expected.length())return false;
        source.readCharacters(text,0,prefix,0,expected.length());resources.work(expected.length(),PHASE);
        for(int i=0;i<expected.length();i++)if(prefix[i]!=expected.charAt(i))return false;return true;
    }
    private void grounding(long evidence) {
        resetWork();
        for(long i=0,n=size(evidence,6);i<n;i++) {
            work();long derivation=item(evidence,6,i),row=find(DERIVATION,field(derivation,0));
            refs(derivation,1,NODE);refs(derivation,3,NODE);refs(derivation,6,SELECTION);refs(derivation,5,PROOF);
            long destination=find(NODE,field(derivation,2));require(destination!=0,"derivation destination");
            require(minor>=5||!is(derivation,4,"ALTERNATE_ENTRY"),"alternate roots require version 1.5.0");
            if(size(derivation,1)==0) {
                require(size(derivation,3)==0&&size(derivation,6)==0&&(is(derivation,4,"PRIMARY_ENTRY")||is(derivation,4,"ALTERNATE_ENTRY")),"ordinary root authority");
                if(is(derivation,4,"ALTERNATE_ENTRY"))require(authority(derivation,5,"alternate-entry-start")&&starts(field(directory.reference(destination),1),"ENTRY/"),"alternate root proof/context");
            }
            if(size(derivation,6)>0) {
                long selected=find(SELECTION,item(derivation,6,0)),selection=directory.reference(selected);
                require(size(derivation,1)==1&&item(derivation,1,0)==field(selection,2)&&size(derivation,3)==0&&size(selection,5)==1&&item(selection,5,0)==field(derivation,2)&&field(derivation,5)==field(selection,7),"selection derivation correlation");
                directory.state(selected,0,1);
            }
            long source=size(derivation,1)==0?0:find(NODE,item(derivation,1,0));
            long caller=size(derivation,3)==0?0:find(NODE,item(derivation,3,0)),pending=0;
            if(source!=0){edge(source,row);pending++;}if(caller!=0&&caller!=source){edge(caller,row);pending++;}
            directory.state(row,0,pending);directory.state(row,1,destination);if(pending==0)reach(destination);
        }
        while(read<write) {
            work();long row=queue.get(read++);
            for(long edge=directory.state(row,1);edge!=0;) {
                work();long at=Math.multiplyExact(edge,2),derivation=edges.get(at),pending=directory.state(derivation,0);
                require(pending>0,"AND/OR worklist invariant");directory.state(derivation,0,pending-1);if(pending==1)reach(directory.state(derivation,1));
                edge=edges.get(at+1);
            }
        }
        require(write==size(evidence,5),"ungrounded qualification node");
    }
    private void nominal(long evidence) {
        long facts=field(evidence,0);
        require(minor>=4||!is(facts,0,"NOMINAL_TEXT_SOURCE_V4"),"source tables require version 1.4.0");
        require(minor>=3||!is(facts,0,"NOMINAL_TEXT_SOURCE_V3"),"source expressions require version 1.3.0");
        index(facts,1,SYMBOL);index(facts,3,CONDITION);index(facts,4,QUERY);index(facts,5,TABLE);
        index(evidence,1,DECLARATION);index(evidence,2,SEED);index(evidence,3,BRANCH);index(evidence,4,UNCERTAINTY);
        require(size(evidence,1)==size(facts,1),"complete nominal declaration provenance");
        for(long i=0,n=size(evidence,1);i<n;i++){work();record(SYMBOL,field(item(evidence,1,i),0));}
        for(long i=0,n=size(evidence,2);i<n;i++){work();record(SYMBOL,field(item(evidence,2,i),0));}
        for(long i=0,n=size(facts,5);i<n;i++) {
            work();long table=item(facts,5,i);record(SYMBOL,field(table,0));
            for(long j=0,m=size(table,1);j<m;j++){work();record(SYMBOL,field(item(table,1,j),0));}
        }
        resetWork();
        for(long i=0,n=size(facts,2);i<n;i++) {
            work();long assignment=item(facts,2,i);record(HANDLE,field(assignment,0));record(SYMBOL,field(assignment,1));
            unique(WRITE,field(assignment,0),field(assignment,1),assignment,i);nominalPush(field(assignment,2));
        }
        for(long i=0,n=size(facts,3);i<n;i++){work();long condition=item(facts,3,i);record(HANDLE,field(condition,0));nominalPush(field(condition,1));}
        nominalWalk();
        for(long i=0,n=size(facts,4);i<n;i++) {
            work();long query=item(facts,4,i);record(HANDLE,field(query,0));record(SYMBOL,field(query,1));
            require(is(record(OCCURRENCE_HANDLE,field(query,0)),5,"COMPUTED"),"nominal query computed occurrence");
        }
        for(long i=0,n=size(evidence,3);i<n;i++) {
            work();long branch=item(evidence,3,i),derivation=record(DERIVATION,field(branch,0));
            require(size(derivation,1)==1&&size(derivation,6)==0&&size(derivation,3)==0,"nominal branch derivation");
            require(find(CONDITION,field(record(NODE,item(derivation,1,0)),2))!=0,"nominal branch predicate owner");
        }
    }
    private void nominalPush(long ref) {
        int namespace=source.shape(ref)==SourceShape.TERM?TERM:PREDICATE;
        long next=Math.incrementExact(directory.size());if(directory.intern(unit,namespace,ref,0,ref,0)==next)enqueue(ref);
    }
    private void nominalWalk() {
        while(read<write) {
            work();long ref=queue.get(read++);
            if(source.shape(ref)==SourceShape.TERM) {
                if(is(ref,0,"READ"))record(SYMBOL,field(ref,1));
                for(long i=0,n=size(ref,2);i<n;i++){work();nominalPush(item(ref,2,i));}
            }else {
                for(long i=0,n=size(ref,1);i<n;i++){work();nominalPush(item(ref,1,i));}
                for(long i=0,n=size(ref,2);i<n;i++){work();nominalPush(item(ref,2,i));}
            }
        }
    }
    private void cleanup(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public void close() {
        if(closed)return;closed=true;source=null;prefix=null;unit=referencePass=edgeCount=read=write=0;Throwable failure=null;
        for(AutoCloseable owner:new AutoCloseable[]{queue,edges,directory,control})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        queue=null;edges=null;directory=null;control=null;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("source relations cleanup",failure);
    }
}

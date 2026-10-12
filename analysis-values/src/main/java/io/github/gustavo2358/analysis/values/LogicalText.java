package io.github.gustavo2358.analysis.values;

import java.util.*;

/** Canonical runs of logical Unicode scalars. Unknown positions are -1, never padding. */
final class LogicalText {
    record Run(int scalar,int length) { }
    private final List<Run> runs;
    private final int length;
    private final boolean complete;
    private final int hash;
    private final LogicalTextArena owner;
    private LogicalText(List<Run> runs,int length) {
        this.runs=List.copyOf(runs);this.length=length;
        complete=runs.stream().noneMatch(r->r.scalar()<0);hash=this.runs.hashCode();owner=null;
    }
    private LogicalText(List<Run> runs,int length,LogicalTextArena owner,int hash,boolean complete){this.runs=runs;this.length=length;this.owner=owner;this.hash=hash;this.complete=complete;}
    /** The arena publishes only canonical immutable run views; do not copy them into heap. */
    static LogicalText stored(List<Run> runs,int length,LogicalTextArena owner,int hash,boolean complete){return new LogicalText(runs,length,owner,hash,complete);}
    private static void append(List<Run> runs,int scalar,int length) {
        if(length==0)return;
        if(!runs.isEmpty()&&runs.getLast().scalar()==scalar) {
            var last=runs.removeLast();length=Math.addExact(length,last.length());
        }
        runs.add(new Run(scalar,length));
    }
    static LogicalText of(String value) {
        var runs=new ArrayList<Run>();int count=0;
        for(int at=0;at<value.length();count++) {
            int scalar=value.codePointAt(at);at+=Character.charCount(scalar);append(runs,scalar,1);
        }
        return new LogicalText(runs,count);
    }
    static LogicalText unknown(int length) {
        if(length<0)throw new IllegalArgumentException("negative text length");
        return new LogicalText(length==0?List.of():List.of(new Run(-1,length)),length);
    }
    int length(){return length;}
    int runCount(){return runs.size();}
    List<Run> runs(){return runs;}
    boolean complete(){return complete;}
    /** Materialization is only for an explicitly requested result, proportional to its output size. */
    String text() {
        if(!complete)throw new IllegalStateException("partial text cannot be published");
        var output=new StringBuilder();
        for(var run:runs)for(int i=0;i<run.length();i++)output.appendCodePoint(run.scalar());
        return output.toString();
    }
    LogicalText fill(int count) {
        if(length!=1||count<0)throw new IllegalArgumentException("fill requires one scalar and a natural count");
        if(owner!=null){var result=owner.builder();result.append(runs.getFirst().scalar(),count);return result.finish();}
        return new LogicalText(count==0?List.of():List.of(new Run(runs.getFirst().scalar(),count)),count);
    }
    LogicalText fit(int length,int pad) {
        if(length<0)throw new IllegalArgumentException("negative text length");
        if(length<=this.length)return slice(0,length);
        if(owner!=null){var result=owner.builder();result.append(this);result.append(pad,length-this.length);return result.finish();}
        var result=new ArrayList<>(runs);append(result,pad,length-this.length);return new LogicalText(result,length);
    }
    LogicalText slice(int start,int length) {
        if(start<0||length<0||start>this.length-length)throw new IllegalArgumentException("logical slice outside text");
        if(start==0&&length==this.length)return this;
        if(owner!=null){
            var result=owner.builder();int cursor=0,end=start+length;
            for(var run:runs){int next=cursor+run.length();if(next>start&&cursor<end)result.append(run.scalar(),Math.min(next,end)-Math.max(cursor,start));cursor=next;if(cursor>=end)break;}
            return result.finish();
        }
        var result=new ArrayList<Run>();int cursor=0,end=start+length;
        for(var run:runs) {
            int next=cursor+run.length();
            if(next>start&&cursor<end)append(result,run.scalar(),Math.min(next,end)-Math.max(cursor,start));
            cursor=next;if(cursor>=end)break;
        }
        return new LogicalText(result,length);
    }
    LogicalText concat(LogicalText right) {
        int combined=Math.addExact(length,right.length);
        var selected=owner!=null?owner:right.owner;
        if(selected!=null){var result=selected.builder();result.append(this);result.append(right);return result.finish();}
        var result=new ArrayList<>(runs);for(var run:right.runs)append(result,run.scalar(),run.length());
        return new LogicalText(result,combined);
    }
    @Override public boolean equals(Object o){return this==o||o instanceof LogicalText t&&length==t.length&&runs.equals(t.runs);}
    @Override public int hashCode(){return hash;}
}

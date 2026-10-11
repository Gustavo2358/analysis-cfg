package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/**
 * Single scoped borrowed nominal text view. A fixed 256 UTF-16-unit cache serves
 * complete SOURCE/dictionary text; trim, ASCII upper and code-point fit do not
 * allocate whole Strings. Non-ASCII upper is explicitly unsupported before fit,
 * preserving the existing OPEN/skip law instead of inventing an empty candidate.
 * Re-selection invalidates the previous sequence; dictionary intern borrows it
 * only during its call. Closing the view never closes either borrowed text owner.
 */
final class SourceLiteralView extends SourceAdmissionSnapshot.Dependent implements CharSequence {
    private volatile SourceAdmissionSnapshot admission;
    private SourceSnapshot source;
    private SourceValueStore values;
    private PageStore pages;
    private AnalysisResources resources;
    private AnalysisResources.Reservation control;
    private char[] cache;
    private long reference;
    private int rawLength,start,end,length,transforms,cachedStart=-1,cachedCount;
    private boolean space,selected;
    private volatile boolean closed;
    private SourceLiteralView(SourceAdmissionSnapshot admission,AnalysisResources.Reservation capacity){this.admission=admission;control=capacity;}
    static SourceLiteralView prepare(SourceAdmissionSnapshot admission,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(pages);Objects.requireNonNull(resources);admission.requireContext(pages,resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,AnalysisResources.Phase.DOMAIN);SourceLiteralView result=null;
        try {
            result=new SourceLiteralView(admission,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.pages=pages;result.resources=resources;result.source=admission.snapshot();result.cache=new char[256];admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure) {
            if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            throw failure;
        }
    }
    private void open(){var owner=admission;if(closed||owner==null)throw new IllegalStateException("nominal literal view expired");owner.snapshot();}
    private void query(){open();if(!selected)throw new IllegalStateException("nominal literal view not selected or transformation unsupported");}
    boolean selectSource(long reference,int mask,int extent) {
        open();try {
            selected=false;values=null;space=false;this.reference=reference;cachedStart=-1;
            if(source.shape(reference)!=SourceShape.TEXT)throw new IllegalArgumentException("nominal literal SOURCE text shape");
            rawLength=Math.toIntExact(source.characterCount(reference));return select(mask,extent);
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    boolean selectSpace(int mask,int extent) {
        open();try{selected=false;values=null;space=true;reference=0;cachedStart=-1;rawLength=1;return select(mask,extent);}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    boolean selectValue(SourceValueStore values,long reference,int mask,int extent) {
        open();try {
            selected=false;Objects.requireNonNull(values).requireContext(pages,resources);this.values=values;space=false;this.reference=reference;cachedStart=-1;
            rawLength=values.characterCount(reference);return select(mask,extent);
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private boolean select(int mask,int extent) {
        if(mask<0||mask>7||extent< -1)throw new IllegalArgumentException("nominal literal transform/extent");transforms=mask;start=0;end=rawLength;
        if((mask&2)!=0)while(start<end&&rawAt(start)==' ')start++;
        if((mask&4)!=0)while(end>start&&rawAt(end-1)==' ')end--;
        if((mask&1)!=0)for(int i=start;i<end;i++)if(rawAt(i)>127){open();return false;}
        int retained=end-start,padding=0;
        if(extent>=0) {
            int at=start,points=0;
            while(at<end&&points<extent) {
                char first=rawAt(at++);if(Character.isHighSurrogate(first)&&at<end&&Character.isLowSurrogate(rawAt(at)))at++;points++;
            }
            end=at;retained=end-start;padding=extent-points;
        }
        length=Math.addExact(retained,padding);open();selected=true;return true;
    }
    private char rawAt(int index) {
        if(space)return ' ';
        if(cachedStart<0||index<cachedStart||index>=cachedStart+cachedCount) {
            cachedStart=index/256*256;cachedCount=Math.min(256,rawLength-cachedStart);
            if(values==null)source.readCharacters(reference,cachedStart,cache,0,cachedCount);else values.readCharacters(reference,cachedStart,cache,0,cachedCount);
        }return cache[index-cachedStart];
    }
    @Override public int length(){query();return length;}
    @Override public char charAt(int index) {
        query();try {
            Objects.checkIndex(index,length);char value=index<end-start?rawAt(start+index):' ';
            return (transforms&1)!=0&&value>='a'&&value<='z'?(char)(value-'a'+'A'):value;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    @Override public CharSequence subSequence(int start,int end){throw new UnsupportedOperationException("scoped nominal view cannot create detached slices");}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AnalysisResources.Reservation capacity;
        synchronized(this){if(closed)return;closed=true;selected=false;capacity=control;control=null;admission=null;source=null;values=null;cache=null;pages=null;resources=null;}
        var parent=owner;if(parent!=null)parent.release(this);if(capacity!=null)capacity.close();
    }
}

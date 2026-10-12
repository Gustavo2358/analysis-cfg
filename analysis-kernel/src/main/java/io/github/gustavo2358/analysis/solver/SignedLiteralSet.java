package io.github.gustavo2358.analysis.solver;

/** Canonical Patricia sets of signed nonnegative int keys. Root parity complements
 * every literal; branch parity is normalized to its first child. Zero is empty.
 * Union returns -1 for opposing literals. The caller retains underlying root >>> 1.
 * Storage and root ownership belong to the borrowed arena: six structural fields,
 * optional56 sampled words and an optional unsigned-support reference. */
final class SignedLiteralSet implements AutoCloseable {
    private static final long LEAF=4,BRANCH=5;
    private final CanonicalTupleArena arena;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation metadata;
    private final boolean sampled,supported;
    private long[] tuple,path,joinA,joinB,firstResult,otherA,otherB;
    private byte[] sides,joinState;
    private int[] joinBit;
    private int depth;
    private long visits,copies,sampleWords;
    private boolean closed,failed;

    SignedLiteralSet(CanonicalTupleArena arena,AnalysisResources resources) {
        int arity=arena.arity();supported=arity==63;sampled=arity==62||supported;
        if(arity!=6&&!sampled)throw new IllegalArgumentException("literal set needs six structural fields and optional complete sample pair");
        for(int n=0;n<arity;n++)if(arena.referenceColumn(n)!=(n==2||n==4||n==5||supported&&n==62))throw new IllegalArgumentException("literal reference schema mismatch");
        this.arena=arena;this.resources=resources;metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,4096+8L*(arity-6),AnalysisResources.Phase.CONTROL);
        try {
            tuple=new long[arity];path=new long[64];sides=new byte[64];
            joinA=new long[64];joinB=new long[64];firstResult=new long[64];otherA=new long[64];otherB=new long[64];joinState=new byte[64];joinBit=new int[64];
        }catch(RuntimeException|Error error){metadata.close();throw error;}
    }
    private void open(){if(closed||failed)throw new IllegalStateException("literal set unavailable");}
    long visits(){return visits;}
    long copies(){return copies;}
    long sampleWordsCalculated(){open();return sampleWords;}
    private long conjunctionSample(long root,boolean inverted,int word) {
        if(root==0)return -1;
        int variant=(int)(root&1)^(inverted?1:0);
        return field(root,6+variant*PagedBooleanCircuit.SAMPLE_WORDS+word);
    }
    long sample(long root,boolean union,int word) {
        open();if(!sampled)throw new IllegalStateException("literal arena has no sample metadata");
        if(word<0||word>=PagedBooleanCircuit.SAMPLE_WORDS)throw new IllegalArgumentException("foreign sample channel");
        try{return union?~conjunctionSample(root,true,word):conjunctionSample(root,false,word);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    /** Canonical all-positive keys. This is exact support of every proper literal
     * junction, independent of signs; it is not syntactic support of arbitrary logic. */
    long unsigned(long root) {
        open();if(!supported)throw new IllegalStateException("literal arena has no unsigned support metadata");
        try {
            if(root==0)return 0;long normalized=root&~1L;
            return positiveCount(normalized)==size(normalized)?normalized:field(normalized,62)<<1;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private long field(long root,int column){visits++;return arena.field(root>>>1,column);}
    private boolean leaf(long root){return (field(root,0)&255)==LEAF;}
    private int bit(long root){return (int)(field(root,1)&63);}
    long size(long root){open();return root==0?0:field(root,3)>>>1;}
    int firstKey(long root){open();if(root==0)throw new IllegalArgumentException("empty literal set");return (int)(field(root,1)>>>6);}
    long positiveCount(long root){open();if(root==0)return 0;long count=field(root,0)>>>8;return (root&1)==0?count:size(root)-count;}
    int firstPolarity(long root,boolean negative) {
        open();if(root==0||count(root,negative)==0)return -1;
        while(!leaf(root)){long left=child(root,0);root=count(left,negative)!=0?left:child(root,1);}
        return firstKey(root);
    }
    private long count(long root,boolean negative){long positive=positiveCount(root);return negative?size(root)-positive:positive;}
    long child(long root,int side) {
        long flip=root&1;
        if(side==1)flip^=field(root,3)&1;
        return (field(root,side==0?2:4)<<1)|flip;
    }
    int polarity(long root,int key) {
        open();if(key<0)throw new IllegalArgumentException("negative literal key");
        try {
            while(root!=0&&!leaf(root))root=child(root,(key>>>(31-bit(root)))&1);
            return root==0||firstKey(root)!=key?0:(int)(root&1)+1;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    private long encoded(long handle,long flip) {
        if(handle>Long.MAX_VALUE/2)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"literal handle space exhausted");
        return (handle<<1)|flip;
    }
    private long leaf(int key,boolean negative) {
        tuple[0]=(1L<<8)|LEAF;tuple[1]=((long)key<<6)|32;tuple[2]=0;tuple[3]=2;tuple[4]=0;tuple[5]=0;if(supported)tuple[62]=0;
        if(sampled) {
            PagedBooleanCircuit.writePrimarySamples(key,tuple,6);
            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++)tuple[6+PagedBooleanCircuit.SAMPLE_WORDS+word]=~tuple[6+word];
            sampleWords+=2L*PagedBooleanCircuit.SAMPLE_WORDS;
        }
        return encoded(arena.intern(tuple),negative?1:0);
    }
    private long branch(int bit,long left,long right) {
        long count=Math.addExact(size(left),size(right));long flip=left&1;
        long positive=Math.addExact(positiveCount(left^flip),positiveCount(right^flip));
        // At most one nested call: its children are all-positive, so it creates no
        // further support skeleton. Compute before writing the shared staging tuple.
        long support=supported&&positive!=count?branch(bit,unsigned(left),unsigned(right)):0;
        tuple[0]=(positive<<8)|BRANCH;tuple[1]=((long)firstKey(left)<<6)|bit;tuple[2]=left>>>1;
        tuple[3]=(count<<1)|((right&1)^flip);tuple[4]=right>>>1;tuple[5]=0;if(supported)tuple[62]=support>>>1;
        if(sampled) {
            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++) {
                tuple[6+word]=conjunctionSample(left^flip,false,word)&conjunctionSample(right^flip,false,word);
                tuple[6+PagedBooleanCircuit.SAMPLE_WORDS+word]=conjunctionSample(left^flip,true,word)&conjunctionSample(right^flip,true,word);
            }
            sampleWords+=2L*PagedBooleanCircuit.SAMPLE_WORDS;
        }
        copies++;return encoded(arena.intern(tuple),flip);
    }
    private long descend(long root,int key) {
        depth=0;
        while(root!=0&&!leaf(root)) {
            path[depth]=root;int side=(key>>>(31-bit(root)))&1;sides[depth++]=(byte)side;root=child(root,side);
        }
        return root;
    }
    private long rebuild(long root,int count) {
        while(count!=0) {
            int i=--count;long old=path[i],other=child(old,1-sides[i]);
            root=sides[i]==0?branch(bit(old),root,other):branch(bit(old),other,root);
        }
        return root;
    }
    long put(long root,int key,boolean negative) {
        open();if(key<0)throw new IllegalArgumentException("negative literal key");
        try {
            long old=descend(root,key),replacement=leaf(key,negative);
            if(old==0)return replacement;
            int previous=firstKey(old);if(previous==key)return old==replacement?root:rebuild(replacement,depth);
            int differing=Integer.numberOfLeadingZeros(previous^key),cut=0;
            while(cut<depth&&bit(path[cut])<differing)cut++;
            long subtree=cut==depth?old:path[cut];
            long merged=((key>>>(31-differing))&1)==0?branch(differing,replacement,subtree):branch(differing,subtree,replacement);
            return rebuild(merged,cut);
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    long remove(long root,int key) {
        open();if(key<0)throw new IllegalArgumentException("negative literal key");
        try {
            long old=descend(root,key);if(old==0||firstKey(old)!=key)return root;
            return depth==0?0:rebuild(child(path[depth-1],1-sides[depth-1]),depth-1);
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    /** Structural set union skips shared prefix/suffix subtrees; no literal product. */
    long union(long left,long right) {
        open();
        try {
            int top=0;joinA[0]=left;joinB[0]=right;joinState[0]=0;long result=0;boolean returned=false;
            while(top>=0) {
                if(returned) {
                    if(result==-1){top--;continue;}
                    byte state=joinState[top];
                    if(state==1) {
                        firstResult[top]=result;joinState[top]=2;
                        long a=otherA[top],b=otherB[top];top++;joinA[top]=a;joinB[top]=b;joinState[top]=0;returned=false;continue;
                    }
                    result=state==2?branch(joinBit[top],firstResult[top],result)
                        :state==3?branch(joinBit[top],result,otherA[top]):branch(joinBit[top],otherA[top],result);
                    top--;continue;
                }
                long a=joinA[top],b=joinB[top];visits++;
                if(a==0||b==0){result=a==0?b:a;top--;returned=true;continue;}
                if((a>>>1)==(b>>>1)){result=a==b?a:-1;top--;returned=true;continue;}
                boolean aLeaf=leaf(a),bLeaf=leaf(b);
                if(aLeaf||bLeaf) {
                    long single=aLeaf?a:b,tree=aLeaf?b:a;int key=firstKey(single),old=polarity(tree,key),supplied=(int)(single&1)+1;
                    result=old!=0&&old!=supplied?-1:put(tree,key,supplied==2);top--;returned=true;continue;
                }
                int aBit=bit(a),bBit=bit(b),aKey=firstKey(a),bKey=firstKey(b),difference=Integer.numberOfLeadingZeros(aKey^bKey);
                if(difference<Math.min(aBit,bBit)) {
                    result=aKey<bKey?branch(difference,a,b):branch(difference,b,a);top--;returned=true;continue;
                }
                if(aBit==bBit) {
                    joinBit[top]=aBit;joinState[top]=1;otherA[top]=child(a,1);otherB[top]=child(b,1);
                    long l=child(a,0),r=child(b,0);top++;joinA[top]=l;joinB[top]=r;joinState[top]=0;
                }else {
                    if(aBit>bBit){long swap=a;a=b;b=swap;aBit=bBit;bKey=aKey;}
                    int side=(bKey>>>(31-aBit))&1;joinBit[top]=aBit;joinState[top]=(byte)(side==0?3:4);otherA[top]=child(a,1-side);
                    long l=child(a,side);top++;joinA[top]=l;joinB[top]=b;joinState[top]=0;
                }
            }
            return result;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    /** Signed set intersection/difference, independent of Boolean truth binding.
     * Shared Patricia subtrees are accepted/rejected without enumerating keys.
     * The fixed frontier is bounded by the nonnegative-int key width. */
    long intersection(long left,long right){return partition(left,right,false);}
    long without(long left,long right){return partition(left,right,true);}
    private long combinePartition(long original,int split,long left,long right){
        if(left==0)return right;if(right==0)return left;
        if(left==child(original,0)&&right==child(original,1))return original;
        return branch(split,left,right);
    }
    private long partition(long left,long right,boolean subtract){
        open();int top=0;joinA[0]=left;joinB[0]=right;joinState[0]=0;long result=0;boolean returned=false;
        try{
            while(top>=0){
                if(returned){
                    byte state=joinState[top];
                    if(state==1){
                        firstResult[top]=result;joinState[top]=2;
                        long a=otherA[top],b=otherB[top];top++;joinA[top]=a;joinB[top]=b;joinState[top]=0;returned=false;continue;
                    }
                    result=state==2?combinePartition(joinA[top],joinBit[top],firstResult[top],result)
                        :state==3?combinePartition(joinA[top],joinBit[top],result,otherA[top])
                        :combinePartition(joinA[top],joinBit[top],otherA[top],result);
                    top--;continue;
                }
                long a=joinA[top],b=joinB[top];visits++;
                if(a==0||a==b){result=subtract?0:a;top--;returned=true;continue;}
                if(b==0||(a>>>1)==(b>>>1)){result=subtract?a:0;top--;returned=true;continue;}
                if(leaf(a)){
                    boolean same=polarity(b,firstKey(a))==(int)(a&1)+1;
                    result=same!=subtract?a:0;top--;returned=true;continue;
                }
                int aBit=bit(a),bBit=bit(b),difference=Integer.numberOfLeadingZeros(firstKey(a)^firstKey(b));
                if(difference<Math.min(aBit,bBit)){result=subtract?a:0;top--;returned=true;continue;}
                if(aBit>bBit){joinB[top]=child(b,(firstKey(a)>>>(31-bBit))&1);continue;}
                joinBit[top]=aBit;
                if(aBit==bBit){
                    joinState[top]=1;otherA[top]=child(a,1);otherB[top]=child(b,1);
                    long firstA=child(a,0),firstB=child(b,0);top++;joinA[top]=firstA;joinB[top]=firstB;joinState[top]=0;
                }else{
                    int side=(firstKey(b)>>>(31-aBit))&1;joinState[top]=(byte)(side==0?3:4);otherA[top]=subtract?child(a,1-side):0;
                    long first=child(a,side);top++;joinA[top]=first;joinB[top]=b;joinState[top]=0;
                }
            }
            return result;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    /** Signed containment skips identical subtrees and rejects incompatible prefixes. */
    boolean includes(long whole,long part) {
        open();int top=0;joinA[0]=whole;joinB[0]=part;
        try {
            while(top>=0) {
                long a=joinA[top],b=joinB[top--];visits++;
                if(b==0||a==b)continue;if(a==0||(a>>>1)==(b>>>1))return false;
                if(leaf(b)){if(polarity(a,firstKey(b))!=(int)(b&1)+1)return false;continue;}
                if(leaf(a))return false;
                int aBit=bit(a),bBit=bit(b),difference=Integer.numberOfLeadingZeros(firstKey(a)^firstKey(b));
                if(difference<Math.min(aBit,bBit)||aBit>bBit)return false;
                if(aBit==bBit) {
                    joinA[++top]=child(a,1);joinB[top]=child(b,1);
                    joinA[++top]=child(a,0);joinB[top]=child(b,0);
                }else {joinA[++top]=child(a,(firstKey(b)>>>(31-aBit))&1);joinB[top]=b;}
            }
            return true;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    boolean intersectsSame(long left,long right) {
        open();int top=0;joinA[0]=left;joinB[0]=right;
        try {
            while(top>=0) {
                long a=joinA[top],b=joinB[top--];visits++;
                if(a==0||b==0)continue;if(a==b)return true;if((a>>>1)==(b>>>1))continue;
                if(leaf(a)||leaf(b)) {
                    long single=leaf(a)?a:b,tree=single==a?b:a;
                    if(polarity(tree,firstKey(single))==(int)(single&1)+1)return true;continue;
                }
                int aBit=bit(a),bBit=bit(b),difference=Integer.numberOfLeadingZeros(firstKey(a)^firstKey(b));
                if(difference<Math.min(aBit,bBit))continue;
                if(aBit==bBit) {
                    joinA[++top]=child(a,1);joinB[top]=child(b,1);
                    joinA[++top]=child(a,0);joinB[top]=child(b,0);
                }else {
                    if(aBit>bBit){long swap=a;a=b;b=swap;aBit=bBit;}
                    joinA[++top]=child(a,(firstKey(b)>>>(31-aBit))&1);joinB[top]=b;
                }
            }
            return false;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    /** Bind every disallowed key to absent. -1 denotes the absorbing truth value. */
    @SuppressWarnings("try") // The reservation accounts for the fixed frontier's lifetime.
    long restrict(long root,boolean union,java.util.function.IntPredicate allowed) {
        open();
        try(var lease=resources.reserve(AnalysisResources.Pool.SCRATCH,2048,AnalysisResources.Phase.CONTROL)) {
            long[] stack=new long[64],leftResult=new long[64];byte[] phase=new byte[64];
            int top=0;stack[0]=root;long result=0;boolean returned=false;
            while(top>=0) {
                if(returned) {
                    if(result==-1){top--;continue;}
                    if(phase[top]==1) {
                        leftResult[top]=result;phase[top]=2;
                        long right=child(stack[top],1);stack[++top]=right;phase[top]=0;returned=false;continue;
                    }
                    long original=stack[top],left=leftResult[top];
                    if(left==0) { /* the right result is already canonical */ }
                    else if(result==0)result=left;
                    else if(left==child(original,0)&&result==child(original,1))result=original;
                    else result=branch(bit(original),left,result);
                    top--;continue;
                }
                long current=stack[top];
                if(current==0){result=0;top--;returned=true;continue;}
                if(leaf(current)) {
                    result=allowed.test(firstKey(current))?current:((current&1)!=0)==union?-1:0;
                    top--;returned=true;continue;
                }
                phase[top]=1;long left=child(current,0);stack[++top]=left;phase[top]=0;
            }
            return result;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    boolean test(long root,boolean union,java.util.BitSet assignment) {
        open();return testLiterals(root,union,assignment::get);
    }
    boolean test(long root,boolean union,PersistentLongMap assignment,long word) {
        open();long positives=positiveCount(root),negatives=size(root)-positives,words=assignment.size(word);
        if(union&&negatives>words)return true;
        if(!union&&positives>words)return false;
        if(size(root)<=words)return testLiterals(root,union,key->assignment.contains(word,key));
        long matchedPositive=0,matchedNegative=0;
        try(var cursor=assignment.cursor(word)) {
            while(cursor.advance()) {
                long key=cursor.key();if(key<0||key>Integer.MAX_VALUE)continue;
                int polarity=polarity(root,(int)key);
                if(polarity==1){if(union)return true;matchedPositive++;}
                else if(polarity==2){if(!union)return false;matchedNegative++;}
            }
        }
        return union?negatives>matchedNegative:positives==matchedPositive;
    }
    private boolean testLiterals(long root,boolean union,java.util.function.IntPredicate assignment) {
        if(root==0)return !union;int top=0;path[0]=root;
        while(top>=0) {
            long current=path[top--];
            if(leaf(current)){boolean truth=assignment.test(firstKey(current))!=((current&1)!=0);if(truth==union)return union;}
            else {path[++top]=child(current,1);path[++top]=child(current,0);}
        }
        return !union;
    }
    @Override public void close() {
        if(closed)return;closed=true;metadata.close();tuple=null;path=null;sides=null;joinA=null;joinB=null;firstResult=null;otherA=null;otherB=null;joinState=null;joinBit=null;
    }
}

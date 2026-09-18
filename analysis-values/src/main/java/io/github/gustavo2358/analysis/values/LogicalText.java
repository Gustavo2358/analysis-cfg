package io.github.gustavo2358.analysis.values;

import java.util.*;

/** Finite logical characters, with unknown positions. Never bytes or an encoding. */
final class LogicalText {
    private final int[] characters;
    private LogicalText(int[] characters){this.characters=characters;}
    static LogicalText of(String value){return new LogicalText(value.codePoints().toArray());}
    static LogicalText unknown(int length){var chars=new int[length];Arrays.fill(chars,-1);return new LogicalText(chars);}
    int length(){return characters.length;}
    boolean complete(){for(int c:characters)if(c<0)return false;return true;}
    String text(){if(!complete())throw new IllegalStateException("partial text cannot be published");return new String(characters,0,characters.length);}
    LogicalText fit(int length,int pad){var result=Arrays.copyOf(characters,length);if(length>characters.length)Arrays.fill(result,characters.length,length,pad);return new LogicalText(result);}
    LogicalText slice(int start,int length){if(start<0||length<0||start>characters.length-length)throw new IllegalArgumentException("logical slice outside text");return new LogicalText(Arrays.copyOfRange(characters,start,start+length));}
    LogicalText concat(LogicalText right){var result=Arrays.copyOf(characters,Math.addExact(characters.length,right.characters.length));System.arraycopy(right.characters,0,result,characters.length,right.characters.length);return new LogicalText(result);}
    @Override public boolean equals(Object o){return o instanceof LogicalText t&&Arrays.equals(characters,t.characters);}
    @Override public int hashCode(){return Arrays.hashCode(characters);}
}

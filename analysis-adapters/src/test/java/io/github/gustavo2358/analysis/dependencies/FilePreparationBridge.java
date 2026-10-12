package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Instruction;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.lang.reflect.*;
import java.util.*;

/** Test-only access to FILE inventory fallback and legal bulk-read instrumentation. */
public final class FilePreparationBridge {
    private FilePreparationBridge() { }
    public static FileDependencyResult prepare(ProgramStore.Structural program,int[] bulkReads){
        var tracked=proxy(ProgramStore.Structural.class,program,(method,args)->{
            if(method.getName().equals("units"))return program.units().stream().map(unit->
                proxy(ProgramStore.UnitView.class,unit,(unitMethod,unitArgs)->{
                    if(unitMethod.getName().equals("sequences"))return unit.sequences().stream().map(sequence->
                        proxy(ProgramStore.SequenceView.class,sequence,(sequenceMethod,sequenceArgs)->{
                            if(sequenceMethod.getName().equals("instructions"))return counted(sequence.instructions(),bulkReads);
                            return invoke(sequenceMethod,sequence,sequenceArgs);
                        })).toList();
                    return invoke(unitMethod,unit,unitArgs);
                })).toList();
            return invoke(method,program,args);
        });
        return FileDependencyAnalysis.prepare(tracked,null,null,"SYNTHETIC_UNAVAILABLE",StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
    }
    private static List<Instruction> counted(List<Instruction> source,int[] bulkReads){
        return new AbstractList<>(){
            @Override public int size(){return source.size();}
            @Override public Instruction get(int at){return source.get(at);}
            @Override public Object[] toArray(){bulkReads[0]++;return super.toArray();}
            @Override public <T>T[] toArray(T[] destination){bulkReads[0]++;return super.toArray(destination);}
        };
    }
    @FunctionalInterface private interface Call {Object call(Method method,Object[] args)throws Throwable;}
    private static <T>T proxy(Class<T> type,T target,Call call){
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->call.call(method,args)));
    }
    private static Object invoke(Method method,Object owner,Object[] args)throws Throwable{
        try{return method.invoke(owner,args);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
}

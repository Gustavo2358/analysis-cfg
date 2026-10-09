package io.github.gustavo2358.analysis.dependencies.source;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/** Construction-only carrier for a record's additional progress argument.
 * Every constructor still performs its complete admission. Carriers are removed
 * before the canonical constructor assigns fields; no finished model owns a callback.
 */
final class SourceAdmission {
    private static final Runnable NONE=()->{};
    private SourceAdmission() { }
    static Runnable progress(List<?> input) {return input instanceof Input<?> checked?checked.progress:NONE;}
    static <T> List<T> input(List<T> values,Runnable progress) {
        Objects.requireNonNull(progress).run();Objects.requireNonNull(values);
        return progress==NONE?values:new Input<>(values,progress);
    }
    static <T> List<T> owned(List<T> values) {return values instanceof Input<T> checked?checked.values:values;}
    private static final class Input<T> extends AbstractList<T> implements RandomAccess {
        private final List<T> values;private final Runnable progress;
        Input(List<T> values,Runnable progress){this.values=values;this.progress=progress;}
        @Override public int size(){return values.size();}
        @Override public T get(int index){progress.run();return values.get(index);}
    }
}

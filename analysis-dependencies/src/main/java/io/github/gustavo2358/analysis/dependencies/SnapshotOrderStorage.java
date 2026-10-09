package io.github.gustavo2358.analysis.dependencies;

import java.util.ArrayList;
import java.util.Objects;

/** External-order port for canonical snapshot scans. Production adapters must bound residency. */
public interface SnapshotOrderStorage extends AutoCloseable {
    @FunctionalInterface interface Order { int compare(long first,long second); }
    interface Index extends AutoCloseable {
        void add(long handle);
        Cursor cursor();
        @Override void close();
    }
    interface Cursor extends AutoCloseable {
        boolean advance();
        long handle();
        @Override void close();
    }
    Index open(Order order);
    @Override void close();

    /** Explicit compatibility backend for small in-memory callers. */
    static SnapshotOrderStorage resident(){return new Resident();}

    final class Resident implements SnapshotOrderStorage {
        private boolean closed;
        @Override public Index open(Order order) {
            if(closed)throw new IllegalStateException("snapshot order storage is closed");
            Objects.requireNonNull(order);
            return new Index(){private ArrayList<Long> values=new ArrayList<>();private boolean ended;
                @Override public void add(long handle){if(ended)throw new IllegalStateException("snapshot order index is closed");values.add(handle);}
                @Override public Cursor cursor(){if(ended)throw new IllegalStateException("snapshot order index is closed");values.sort((a,b)->order.compare(a,b));return new Cursor(){private int at=-1;private boolean cursorClosed;
                    @Override public boolean advance(){if(cursorClosed)return false;return ++at<values.size();}
                    @Override public long handle(){if(cursorClosed||at<0||at>=values.size())throw new IllegalStateException("snapshot order cursor is not positioned");return values.get(at);}
                    @Override public void close(){cursorClosed=true;}
                };}
                @Override public void close(){if(ended)return;ended=true;values.clear();values=null;}
            };
        }
        @Override public void close(){closed=true;}
    }
}

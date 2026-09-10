package io.github.gustavo2358.analysis.solver;

/** Package-private scheduling seam. Implementations must remove every accepted item fairly. */
interface IntWorklist {
    void add(int point);
    int remove();
    int size();

    final class Queue implements IntWorklist {
        private int[] items;
        private int head, size;
        Queue(int points) { items = new int[Math.max(1, points)]; }
        public int size() { return size; }
        public void add(int point) {
            if (size == items.length) {
                int[] grown = new int[Math.multiplyExact(items.length, 2)];
                for (int i = 0; i < size; i++) grown[i] = items[position(i)];
                items = grown; head = 0;
            }
            items[position(size)] = point; size = Math.incrementExact(size);
        }
        private int position(int offset) {
            return offset < items.length - head ? head + offset : offset - (items.length - head);
        }
        public int remove() {
            if (size == 0) throw new java.util.NoSuchElementException("empty worklist");
            int point = items[head]; head = head + 1 == items.length ? 0 : head + 1; size--;
            return point;
        }
    }
}

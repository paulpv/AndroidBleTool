package com.github.paulpv.androidbletool.collections;

import com.github.paulpv.androidbletool.Utils;

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

public class SortableSet<E> {
    private static final String TAG = Utils.Companion.TAG(SortableSet.class);

    private TreeSet<E> mBackingTreeSet;

    public SortableSet() {
        this(null);
    }

    public SortableSet(Comparator<? super E> comparator) {
        mBackingTreeSet = new TreeSet<>(comparator);
    }

    public void setSortByValueComparator(Comparator<? super E> comparator) {
        TreeSet<E> newTreeSet = new TreeSet<>(comparator);
        newTreeSet.addAll(mBackingTreeSet);
        mBackingTreeSet = newTreeSet;
    }

    /**
     * @param object
     * @return the positive index of the updated element, or the {@code -index - 1} index of the added element.
     */
    public int add(E object) {
        //PbLog.e(TAG, "add(" + PbStringUtils.quote(object) + ')');
        boolean added = mBackingTreeSet.add(object);
        int index = indexOfKnownExisting(object);
        return added ? (-index - 1) : index;
    }

    public void clear() {
        mBackingTreeSet.clear();
    }

    public boolean contains(E object) {
        return indexOf(object) >= 0;
    }

    public E getAt(int index) {
        //PbLog.e(TAG, "getAt(" + index + ')');
        return getIteratorAt(index).next();
    }

    private Iterator<E> getIteratorAt(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must be >= 0");
        }
        if (index >= size()) {
            throw new IndexOutOfBoundsException("index must be < size()");
        }

        int size = size();
        int count;

        Iterator<E> it;

        if (index < size / 2) {
            it = mBackingTreeSet.iterator();
            count = index;
        } else {
            it = mBackingTreeSet.descendingIterator();
            count = size - 1 - index;
        }

        while (count > 0) {
            it.next();
            --count;
        }

        return it;
    }

    public int indexOf(E object) {
        return mBackingTreeSet.contains(object) ? indexOfKnownExisting(object) : -1;
    }

    private int indexOfKnownExisting(E object) {
        return mBackingTreeSet.headSet(object).size();
    }

    public boolean isEmpty() {
        return mBackingTreeSet.isEmpty();
    }

    public int remove(E object) {
        //
        // TODO:(pv) Use a removal strategy depending on the sort type
        //  Example: Knowing that we are tracking BLE devices, removals are typically going to happen because
        //  a device went out of range. IN GENERAL, devices that go out of range have weak RSSI values.
        //  If we are sorting by RSSI, then a good strategy to find the object to be removed faster would be to
        //  iterate the collection from weakest to strongest.
        //  If we are sorting by Device Address, the weakest RSSIs are randomly distributed, so a good strategy
        //  to find the object to be removed would be a binarySearch.
        //
        //  To do this, when setting the comparator, the caller could also provide a custom iterator.
        //  This is just an *IDEA* for optimizing the remove operation; for now we just iterate beginning to end...
        //

        int i = 0;
        Iterator<E> it = mBackingTreeSet.iterator();
        while (it.hasNext()) {
            if (it.next() == object) {
                it.remove();

                return i;
            }

            ++i;
        }
        return -1;
    }

    public E removeAt(int index) {
        Iterator<E> it = getIteratorAt(index);

        E object = it.next();

        it.remove();

        return object;
    }

    public int size() {
        return mBackingTreeSet.size();
    }
}

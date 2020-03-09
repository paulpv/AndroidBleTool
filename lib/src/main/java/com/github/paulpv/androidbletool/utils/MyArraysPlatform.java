package com.github.paulpv.androidbletool.utils;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;

public class MyArraysPlatform {
    private MyArraysPlatform() {
    }

    public static boolean isNullOrEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    public static boolean equals(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    public static void copy(byte[] source, int sourceOffset, byte[] destination, int destinationOffset, int count) {
        System.arraycopy(source, sourceOffset, destination, destinationOffset, count);
    }

    public static byte[] copy(byte[] source, int offset, int count) {
        byte[] destination = new byte[count];
        System.arraycopy(source, offset, destination, 0, count);
        return destination;
    }

    public static void fill(byte[] array, byte element, int offset, int length) {
        //Arrays.fill(array, element, offset, length);
        for (int i = offset; i < length; i++) {
            array[i] = element;
        }
    }

    public static void sort(Object[] values, MyComparatorPlatform comparator) {
        Arrays.sort(values, comparator);
    }

    public static void sort(Vector vector, MyComparatorPlatform comparator) {
        Object[] temp = vector.toArray();
        sort(temp, comparator);

        int i = 0;
        Enumeration enumumeration = vector.elements();
        while (enumumeration.hasMoreElements()) {
            enumumeration.nextElement();
            vector.setElementAt(temp[i], i++);
        }
    }
}

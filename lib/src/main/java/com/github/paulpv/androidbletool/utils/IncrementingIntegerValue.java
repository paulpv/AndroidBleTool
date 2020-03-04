package com.github.paulpv.androidbletool.utils;

public class IncrementingIntegerValue {
    private int mNextMessageCode = 0;

    public int getNextMessageCode() {
        return mNextMessageCode++;
    }
}

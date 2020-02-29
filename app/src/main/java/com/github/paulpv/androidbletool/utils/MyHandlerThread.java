package com.github.paulpv.androidbletool.utils;

import android.os.HandlerThread;
import android.util.Log;

/**
 * Replacement for {@link HandlerThread} to fix start-up race conditions.
 */
public class MyHandlerThread
        extends HandlerThread
{
    private static final String TAG = Utils.TAG(MyHandlerThread.class);

    /*
    private final Object mSyncLock = new Object();
    private int mPriority;
    int mTid = -1;
    Looper mLooper;
    */

    public MyHandlerThread(String threadName)
    {
        super(threadName);
    }

    public MyHandlerThread(String threadName, int priority)
    {
        super(threadName, priority);
    }

    @Override
    protected void onLooperPrepared()
    {
        String threadName = getName();
        Log.v(TAG, threadName + " +onLooperPrepared()");
        super.onLooperPrepared();
        Log.v(TAG, threadName + " -onLooperPrepared()");
    }

    @Override
    public void run()
    {
        String threadName = getName();
        Log.v(TAG, threadName + " +run()");

        super.run();

        /*
        mTid = android.os.Process.myTid();

        Log.v(TAG, threadName + " run: +Looper.prepare()");
        Looper.prepare();
        Log.v(TAG, threadName + " run: -Looper.prepare()");

        Log.v(TAG, threadName + " run: +synchronized (this)");
        synchronized (this)
        {
            Log.v(TAG, threadName + " run: synchronized (this)");

            mLooper = Looper.myLooper();

            Log.v(TAG, threadName + " run: this.notifyAll()");
            notifyAll();
        }
        Log.v(TAG, threadName + " run: -synchronized (this)");

        Process.setThreadPriority(mPriority);

        onLooperPrepared();

        Log.v(TAG, threadName + " run: +Looper.loop()");
        Looper.loop();
        Log.v(TAG, threadName + " run: -Looper.loop()");

        mTid = -1;
        */

        Log.v(TAG, threadName + " -run()");
    }

    /*
    public Looper getLooper()
    {
        if (!isAlive())
        {
            return null;
        }

        // If the thread has been started, wait until the looper has been created.
        synchronized (this)
        {
            while (isAlive() && mLooper == null)
            {
                try
                {
                    wait();
                }
                catch (InterruptedException e)
                {
                    // ignore
                }
            }
        }
        return mLooper;
    }

    public boolean quit()
    {
        Looper looper = getLooper();
        if (looper != null)
        {
            looper.quit();
            return true;
        }
        return false;
    }

    public boolean quitSafely()
    {
        Looper looper = getLooper();
        if (looper != null)
        {
            looper.quitSafely();
            return true;
        }
        return false;
    }

    public int getThreadId()
    {
        return mTid;
    }
    */
}

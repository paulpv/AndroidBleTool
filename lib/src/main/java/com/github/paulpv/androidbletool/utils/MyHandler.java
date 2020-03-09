package com.github.paulpv.androidbletool.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class MyHandler
        extends Handler {
    private final IncrementingIntegerValue mMessageCodes;

    public int getNextMessageCode() {
        return mMessageCodes.getNextMessageCode();
    }

    /**
     * Default constructor associates this handler with the {@link Looper} for the
     * main thread.
     */
    public MyHandler() {
        this((Callback) null);
    }

    /**
     * Constructor associates this handler with the {@link Looper} for the
     * main thread and takes a callback interface in which you can handle
     * messages.
     *
     * @param callback The callback interface in which to handle messages, or null.
     */
    public MyHandler(Callback callback) {
        this(null, Looper.getMainLooper(), callback);
    }

    /**
     * Use the provided {@link Looper} instead of the default one.
     *
     * @param looper The looper, must not be null.
     */
    public MyHandler(Looper looper) {
        this(null, looper, null);
    }

    /**
     * Use the provided {@link Looper} instead of the default one and take a callback
     * interface in which to handle messages.
     *
     * @param looper   The looper, must not be null.
     * @param callback The callback interface in which to handle messages, or null.
     */
    public MyHandler(Looper looper, Callback callback) {
        this(null, looper, callback);
    }

    public MyHandler(IncrementingIntegerValue messageCodes, Callback callback) {
        this(messageCodes, Looper.getMainLooper(), callback);
    }

    public MyHandler(IncrementingIntegerValue messageCodes, Looper looper, Callback callback) {
        super(looper, callback);
        if (messageCodes == null) {
            messageCodes = new IncrementingIntegerValue();
        }
        mMessageCodes = messageCodes;
    }

    @Override
    public String toString() {
        String infoKey = "getLooper()";
        String infoValue = null;

        Looper looper = getLooper();
        if (looper != null) {
            infoKey += ".getThread().getName()";
            infoValue = looper.getThread().getName();
        }
        return getClass().getSimpleName() + '@' + Integer.toHexString(hashCode()) +
                " { " +
                infoKey + '=' + Utils.quote(infoValue) +
                " }";
    }

    public Message obtainAndSendMessage(int what, Object obj) {
        return obtainAndSendMessage(what, 0, 0, obj);
    }

    public Message obtainAndSendMessage(int what, int arg1, Object obj) {
        return obtainAndSendMessage(what, arg1, 0, obj);
    }

    public Message obtainAndSendMessage(int what, int arg1, int arg2) {
        return obtainAndSendMessage(what, arg1, arg2, null);
    }

    public Message obtainAndSendMessage(int what, int arg1, int arg2, Object obj) {
        return obtainAndSendMessageDelayed(what, arg1, arg2, obj, 0);
    }

    public Message obtainAndSendMessageDelayed(int what, Object obj, long delayMillis) {
        return obtainAndSendMessageDelayed(what, 0, 0, obj, delayMillis);
    }

    public Message obtainAndSendMessageDelayed(int what, int arg1, Object obj, long delayMillis) {
        return obtainAndSendMessageDelayed(what, arg1, 0, obj, delayMillis);
    }

    public Message obtainAndSendMessageDelayed(int what, int arg1, int arg2, long delayMillis) {
        return obtainAndSendMessageDelayed(what, arg1, arg2, null, delayMillis);
    }

    public Message obtainAndSendMessageDelayed(int what, int arg1, int arg2, Object obj, long delayMillis) {
        Message message = obtainMessage(what, arg1, arg2, obj);
        sendMessageDelayed(message, delayMillis);
        return message;
    }

    public boolean post(Runnable r, Object token) {
        /*
        if (VERSION.SDK_INT >= 28)
        {
            return super.postDelayed(r, token, 0);
        }
        else
        {
        */
        return supportPostDelayed(r, token, 0);
        /*
        }
        */
    }

    /**
     * Avoid "Fatal Exception: java.lang.LinkageError Method boolean com.pebblebee.common.os.PbHandler.postDelayed(java.lang.Runnable, java.lang.Object, long) overrides final method in class Landroid/os/Handler; (declaration of 'com.pebblebee.common.os.PbHandler' appears in base.apk)"
     *
     * @param r
     * @param token
     * @param delayMillis
     * @return
     */
    public boolean supportPostDelayed(Runnable r, Object token, long delayMillis) {
        return sendMessageDelayed(getPostMessage(r, token), delayMillis);
    }

    private Message getPostMessage(Runnable r, Object token) {
        Message m = Message.obtain(this, r);
        m.obj = token;
        return m;
    }
}
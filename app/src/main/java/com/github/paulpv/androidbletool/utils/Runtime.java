package com.github.paulpv.androidbletool.utils;

import android.content.Context;

import androidx.annotation.NonNull;

@SuppressWarnings({"WeakerAccess", "unused"})
public class Runtime {
    private Runtime() {
    }

    @NonNull
    public static <T> T throwIllegalArgumentExceptionIfNull(T paramValue,
                                                            @NonNull String paramName) {
        throwIllegalArgumentExceptionIfNullOrEmpty(paramName, "paramName");
        if (paramValue == null) {
            throw new IllegalArgumentException(paramName + " must not be null");
        }
        return paramValue;
    }

    @NonNull
    public static String throwIllegalArgumentExceptionIfNullOrEmpty(String paramValue,
                                                                    @NonNull String paramName) {
        if (Utils.isNullOrEmpty(paramName)) {
            throw new IllegalArgumentException("paramName must not be null/\"\"");
        }
        if (Utils.isNullOrEmpty(paramValue)) {
            throw new IllegalArgumentException(paramName + " must not be null/\"\"");
        }
        return paramValue;
    }

    /**
     * Alias for {@link #throwIllegalArgumentExceptionIfNull(Object, String)}
     *
     * @param paramValue paramValue
     * @param paramName  paramName
     * @param <T>        type
     * @return paramValue
     */
    @NonNull
    public static <T> T toNonNull(T paramValue,
                                  @NonNull String paramName) {
        throwIllegalArgumentExceptionIfNull(paramValue, paramName);
        return paramValue;
    }

    /**
     * Alias for {@link #throwIllegalArgumentExceptionIfNullOrEmpty(String, String)}
     *
     * @param paramValue paramValue
     * @param paramName  paramName
     * @return paramValue
     */
    @NonNull
    public static String toNonNullNonEmpty(String paramValue,
                                           @NonNull String paramName) {
        throwIllegalArgumentExceptionIfNullOrEmpty(paramValue, paramName);
        return paramValue;
    }

    // TODO:(pv) Move to Platform package...
    @NonNull
    public static Context getContext(@NonNull Context context) {
        return toNonNull(context, "context");
    }
}

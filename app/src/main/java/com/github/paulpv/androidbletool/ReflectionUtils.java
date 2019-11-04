package com.github.paulpv.androidbletool;

import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;

@SuppressWarnings({"WeakerAccess", "unused"})
public class ReflectionUtils {
    private static final String TAG = Utils.Companion.TAG(ReflectionUtils.class);

    private ReflectionUtils() {
    }

    public static <T> Class<T> getClass(T o) {
        //noinspection unchecked
        return (Class<T>) ((o instanceof Class<?>) ? o : (o != null ? o.getClass() : null));
    }

    public static String getCanonicalName(Object o) {
        return o != null ? getClass(o).getCanonicalName() : null;
    }

    public static String getClassName(Object o) {
        return getClassName(getClass(o));
    }

    public static String getClassName(Class c) {
        return getClassName(c == null ? null : c.getName(), true);
    }

    public static String getClassName(String className, boolean shortClassName) {
        if (Utils.Companion.isNullOrEmpty(className)) {
            className = "null";
        }
        if (shortClassName) {
            className = className.substring(className.lastIndexOf('.') + 1);
            className = className.substring(className.lastIndexOf('$') + 1);
        }
        return className;
    }

    public static String getShortClassName(String className) {
        return getClassName(className, true);
    }

    public static String getShortClassName(Object o) {
        return getShortClassName(getClass(o));
    }

    public static String getShortClassName(Class c) {
        String className = (c == null) ? null : c.getName();
        return getShortClassName(className);
    }

    public static String getMethodName(String methodName) {
        if (methodName == null) {
            methodName = "()";
        }
        if (methodName.compareTo("()") != 0) {
            methodName = "." + methodName;
        }
        return methodName;
    }

    public static String getShortClassAndMethodName(Object o, String methodName) {
        return getShortClassName(o) + getMethodName(methodName);
    }

    public static <T> String getInstanceSignature(@NonNull T instance) {
        Runtime.throwIllegalArgumentExceptionIfNull(instance, "instance");

        StringBuilder sb = new StringBuilder();

        Class<?> instanceClass = getClass(instance);

        Class<?>[] instanceSubclasses = instanceClass.getClasses();
        if (instanceSubclasses.length > 0) {
            sb.append(" extends");
            Class<?> instanceSubclass;
            for (int i = 0; i < instanceSubclasses.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                instanceSubclass = instanceSubclasses[i];
                sb.append(' ').append(instanceSubclass);
            }
        }

        Class<?>[] instanceInterfaces = instanceClass.getInterfaces();
        if (instanceInterfaces.length > 0) {
            sb.append(" implements");
            Class<?> instanceInterface;
            for (int i = 0; i < instanceInterfaces.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                instanceInterface = instanceInterfaces[i];
                sb.append(' ').append(instanceInterface);
            }
        }

        return sb.toString().trim();
    }

    public static boolean isAssignableFrom(Object instanceExpected, Object instanceActual) {
        Runtime.throwIllegalArgumentExceptionIfNull(instanceExpected, "instanceExpected");

        if (instanceActual == null) {
            return false;
        }

        Class<?> expectedInstanceClass = getClass(instanceExpected);

        Class<?> actualInstanceClass = getClass(instanceActual);

        //
        // Verify that actualInstanceClass is an instance of all subclasses and interfaces of expectedClass…
        //

        if (!expectedInstanceClass.isInterface()) {
            Class<?>[] expectedSubclasses = expectedInstanceClass.getClasses();
            for (Class<?> expectedSubclass : expectedSubclasses) {
                if (!expectedSubclass.isAssignableFrom(actualInstanceClass)) {
                    return false;
                }
            }
        }

        Class<?>[] expectedInterfaces = expectedInstanceClass.getInterfaces();
        for (Class<?> expectedInterface : expectedInterfaces) {
            if (!expectedInterface.isAssignableFrom(actualInstanceClass)) {
                return false;
            }
        }

        return true;
    }

    /*
    public static Object getFieldValue(String className, String fieldName)
    {
        Class<?> c;
        try
        {
            c = Class.forName(className);
        }
        catch (ClassNotFoundException e)
        {
            PbLog.w(TAG, "getField: forName", e);
            c = null;
        }

        return getField(c, fieldName);
    }
    */

    public static Object getFieldValue(Object o, String fieldName) {
        Object fieldValue = null;

        Class<?> c = getClass(o);
        if (c != null) {
            try {
                Field field = c.getField(fieldName);

                try {
                    fieldValue = field.get(c);
                    //PbLog.v(TAG, "getFieldValue: fieldValue == " + fieldValue);
                } catch (IllegalAccessException e) {
                    Log.w(TAG, "getFieldValue: get", e);
                }
            } catch (NoSuchFieldException e) {
                Log.w(TAG, "getFieldValue: getField", e);
            }
        }

        return fieldValue;
    }

    public static String getFieldValueString(Object o, String fieldName) {
        return (String) getFieldValue(o, fieldName);
    }

    /*
    public static Map<String, Object> getFieldValues(Object o, String fieldNameStartsWith)
    {
        Map<String, Object> fieldValues = new LinkedHashMap<>();

        Class<?> c = getClass(o);
        if (c != null)
        {
            Field[] fields = c.getFields();
            for (Field field : fields)
            {
                String fieldName = field.getName();
                if (fieldName.startsWith(fieldNameStartsWith))
                {
                    try
                    {
                        Object fieldValue = field.get(c);
                        fieldValues.put(fieldName, fieldValue);
                    }
                    catch (IllegalAccessException e)
                    {
                        PbLog.w(TAG, "getFieldValues: get", e);
                    }
                }
            }
        }

        return fieldValues;
    }
    */
}

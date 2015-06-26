package com.rometools.rome.feed.impl.beans;

import com.sun.beans.TypeResolver;
import com.sun.beans.WeakCache;
import com.sun.beans.finder.MethodFinder;
import sun.reflect.misc.ReflectUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class Introspector {

    static final String ADD_PREFIX = "add";
    static final String REMOVE_PREFIX = "remove";
    static final String GET_PREFIX = "get";
    static final String SET_PREFIX = "set";
    static final String IS_PREFIX = "is";

    // Static Caches to speed up introspection.
    private static final WeakCache<Class<?>, Method[]> declaredMethodCache = new WeakCache<Class<?>, Method[]>();

    /**
     * Utility method to take a string and convert it to normal Java variable
     * name capitalization.  This normally means converting the first
     * character from upper case to lower case, but in the (unusual) special
     * case when there is more than one character and both the first and
     * second characters are upper case, we leave it alone.
     * <p>
     * Thus "FooBah" becomes "fooBah" and "X" becomes "x", but "URL" stays
     * as "URL".
     *
     * @param  name The string to be decapitalized.
     * @return  The decapitalized version of the string.
     */
    public static String decapitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
                Character.isUpperCase(name.charAt(0))){
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Find a target methodName with specific parameter list on a given class.
     * <p>
     * Used in the contructors of the EventSetDescriptor,
     * PropertyDescriptor and the IndexedPropertyDescriptor.
     * <p>
     * @param cls The Class object on which to retrieve the method.
     * @param methodName Name of the method.
     * @param argCount Number of arguments for the desired method.
     * @param args Array of argument types for the method.
     * @return the method or null if not found
     */
    static Method findMethod(Class<?> cls, String methodName, int argCount,
                             Class args[]) {
        if (methodName == null) {
            return null;
        }
        return internalFindMethod(cls, methodName, argCount, args);
    }

    /**
     * Find a target methodName on a given class.
     */
    static Method findMethod(Class<?> cls, String methodName, int argCount) {
        return findMethod(cls, methodName, argCount, null);
    }

    //======================================================================
    // Package private support methods.
    //======================================================================

    /**
     * Internal support for finding a target methodName with a given
     * parameter list on a given class.
     */
    private static Method internalFindMethod(Class<?> start, String methodName,
                                             int argCount, Class args[]) {
        // For overriden methods we need to find the most derived version.
        // So we start with the given class and walk up the superclass chain.

        Method method = null;

        for (Class<?> cl = start; cl != null; cl = cl.getSuperclass()) {
            Method methods[] = getPublicDeclaredMethods(cl);
            for (int i = 0; i < methods.length; i++) {
                method = methods[i];
                if (method == null) {
                    continue;
                }

                // make sure method signature matches.
                if (method.getName().equals(methodName)) {
                    Type[] params = method.getGenericParameterTypes();
                    if (params.length == argCount) {
                        if (args != null) {
                            boolean different = false;
                            if (argCount > 0) {
                                for (int j = 0; j < argCount; j++) {
                                    if (TypeResolver.erase(TypeResolver.resolveInClass(start, params[j])) != args[j]) {
                                        different = true;
                                        continue;
                                    }
                                }
                                if (different) {
                                    continue;
                                }
                            }
                        }
                        return method;
                    }
                }
            }
        }
        method = null;

        // Now check any inherited interfaces.  This is necessary both when
        // the argument class is itself an interface, and when the argument
        // class is an abstract class.
        Class ifcs[] = start.getInterfaces();
        for (int i = 0 ; i < ifcs.length; i++) {
            // Note: The original implementation had both methods calling
            // the 3 arg method. This is preserved but perhaps it should
            // pass the args array instead of null.
            method = internalFindMethod(ifcs[i], methodName, argCount, null);
            if (method != null) {
                break;
            }
        }
        return method;
    }

    /*
     * Internal method to return *public* methods within a class.
     */
    private static Method[] getPublicDeclaredMethods(Class<?> clz) {
        // Looking up Class.getDeclaredMethods is relatively expensive,
        // so we cache the results.
        if (!ReflectUtil.isPackageAccessible(clz)) {
            return new Method[0];
        }
        synchronized (declaredMethodCache) {
            Method[] result = declaredMethodCache.get(clz);
            if (result == null) {
                result = clz.getMethods();
                for (int i = 0; i < result.length; i++) {
                    Method method = result[i];
                    if (!method.getDeclaringClass().equals(clz)) {
                        result[i] = null; // ignore methods declared elsewhere
                    }
                    else {
                        try {
                            method = MethodFinder.findAccessibleMethod(method);
                            Class<?> type = method.getDeclaringClass();
                            result[i] = type.equals(clz) || type.isInterface()
                                    ? method
                                    : null; // ignore methods from superclasses
                        }
                        catch (NoSuchMethodException exception) {
                            // commented out because of 6976577
                            // result[i] = null; // ignore inaccessible methods
                        }
                    }
                }
                declaredMethodCache.put(clz, result);
            }
            return result;
        }
    }
}

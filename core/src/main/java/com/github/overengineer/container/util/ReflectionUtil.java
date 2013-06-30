package com.github.overengineer.container.util;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 */
public class ReflectionUtil {

    public static boolean isPublicSetter(Method method) {
        return method.getName().startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType() == Void.TYPE;
    }

    public static boolean isPropertyType(Class cls) {
        return
                cls.isPrimitive()
                || String.class.isAssignableFrom(cls)
                || Number.class.isAssignableFrom(cls)
                || Boolean.class.isAssignableFrom(cls);
    }

    public static Set<Class<?>> getAllInterfaces(Class<?> cls) {
        Set<Class<?>> interfaces = new HashSet<Class<?>>();
        getAllInterfaces(cls, interfaces);
        return interfaces;
    }

    public static void getAllInterfaces(Class<?> cls, Set<Class<?>> interfacesFound) {
        getAllClasses(cls, interfacesFound, false);
    }

    public static Set<Class<?>> getAllClasses(Class<?> cls) {
        Set<Class<?>> interfaces = new HashSet<Class<?>>();
        getAllClasses(cls, interfaces, true);
        return interfaces;
    }

    public static void getAllClasses(Class<?> cls, Set<Class<?>> classesFound, boolean addClass) {
        while (cls != null) {
            if (addClass) {
                classesFound.add(cls);
            }
            Class<?>[] interfaces = cls.getInterfaces();
            for (Class<?> i : interfaces) {
                if (classesFound.add(i)) {
                    getAllClasses(i, classesFound, addClass);
                }
            }
            cls = cls.getSuperclass();
        }
    }

}
/*
 * Copyright (c) 2013.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gunmetal.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author rees.byars
 */
class InjectorFactoryImpl implements InjectorFactory {

    private final AnnotationResolver<Qualifier> qualifierResolver;
    private final ConstructorResolver constructorResolver;
    private final InjectionResolver injectionResolver;
    private final Linkers linkers;

    InjectorFactoryImpl(AnnotationResolver<Qualifier> qualifierResolver,
                        ConstructorResolver constructorResolver,
                        InjectionResolver injectionResolver,
                        Linkers linkers) {
        this.qualifierResolver = qualifierResolver;
        this.constructorResolver = constructorResolver;
        this.injectionResolver = injectionResolver;
        this.linkers = linkers;
    }
    
    @Override public <T> StaticInjector<T> staticInjector(final Method method, final ComponentMetadata componentMetadata) {
        final ParameterizedFunctionInvoker<T> invoker = eagerInvoker(
                new MethodFunction(method),
                componentMetadata);
        return new StaticInjector<T>() {
            @Override public T inject(InternalProvider internalProvider, ResolutionContext resolutionContext) {
                return invoker.invoke(null, internalProvider, resolutionContext);
            }
            @Override public Collection<Dependency<?>> dependencies() {
                return invoker.dependencies();
            }
        };
    }

    @Override public <T> Injector<T> compositeInjector(final ComponentMetadata<Class<?>> componentMetadata) {
        final List<Injector<T>> injectors = new ArrayList<Injector<T>>();
        new ClassWalker().walk(componentMetadata.provider(), new ClassWalker.MemberVisitor() {
            @Override public void visit(final Field field) {
                if (!injectionResolver.shouldInject(field)) {
                    return;
                }
                final Dependency<?> dependency = new Dependency<Object>() {
                    Qualifier qualifier = qualifierResolver.resolve(field);
                    TypeKey<Object> typeKey = Types.typeKey(field.getGenericType());

                    @Override Qualifier qualifier() {
                        return qualifier;
                    }

                    @Override TypeKey<Object> typeKey() {
                        return typeKey;
                    }
                };
                injectors.add(new Injector<T>() {
                    ProvisionStrategy<?> provisionStrategy;

                    {
                        field.setAccessible(true);
                        linkers.add(new Linker() {
                            @Override public void link(InternalProvider internalProvider,
                                                       ResolutionContext linkingContext) {
                                provisionStrategy = internalProvider.getProvisionStrategy(
                                        DependencyRequest.Factory.create(componentMetadata, dependency));
                            }
                        }, LinkingPhase.POST_WIRING);
                    }

                    @Override public Object inject(T target, InternalProvider internalProvider,
                                                   ResolutionContext resolutionContext) {
                        try {
                            field.set(target, provisionStrategy.get(internalProvider, resolutionContext));
                            return null;
                        } catch (IllegalAccessException e) {
                            throw Smithy.<RuntimeException>cloak(e);
                        }
                    }

                    @Override public Collection<Dependency<?>> dependencies() {
                        return Collections.<Dependency<?>>singleton(dependency);
                    }
                });
            }
            @Override public void visit(Method method) {
                if (!injectionResolver.shouldInject(method)) {
                    return;
                }
                final ParameterizedFunctionInvoker<T> invoker = eagerInvoker(
                        new MethodFunction(method),
                        componentMetadata);
                injectors.add(new Injector<T>() {
                    @Override public Object inject(T target, InternalProvider internalProvider,
                                                   ResolutionContext resolutionContext) {
                        return invoker.invoke(target, internalProvider, resolutionContext);
                    }

                    @Override public Collection<Dependency<?>> dependencies() {
                        return invoker.dependencies();
                    }
                });
            }
        });
        return new Injector<T>() {
            @Override public Object inject(T target, InternalProvider internalProvider,
                                           ResolutionContext resolutionContext) {
                for (Injector<T> injector : injectors) {
                    injector.inject(target, internalProvider, resolutionContext);
                }
                return null;
            }
            @Override public Collection<Dependency<?>> dependencies() {
                List<Dependency<?>> dependencies = new LinkedList<Dependency<?>>();
                for (Injector<T> injector : injectors) {
                    dependencies.addAll(injector.dependencies());
                }
                return dependencies;
            }
        };
    }

    @Override public <T> Injector<T> lazyCompositeInjector(final ComponentMetadata<?> componentMetadata) {
        return new Injector<T>() {
            volatile List<Injector<T>> injectors;
            void init(Class<?> targetClass,
                      final InternalProvider internalProvider) {
                new ClassWalker().walk(targetClass, new ClassWalker.MemberVisitor() {
                    @Override public void visit(final Field field) {
                        if (!injectionResolver.shouldInject(field)) {
                            return;
                        }
                        final Dependency<?> dependency = new Dependency<Object>() {
                            Qualifier qualifier = qualifierResolver.resolve(field);
                            TypeKey<Object> typeKey = Types.typeKey(field.getGenericType());
                            @Override Qualifier qualifier() {
                                return qualifier;
                            }
                            @Override TypeKey<Object> typeKey() {
                                return typeKey;
                            }
                        };
                        injectors.add(new Injector<T>() {
                            ProvisionStrategy<?> provisionStrategy = internalProvider.getProvisionStrategy(
                                    DependencyRequest.Factory.create(componentMetadata, dependency));
                            @Override public Object inject(T target, InternalProvider internalProvider,
                                                           ResolutionContext resolutionContext) {
                                try {
                                    field.set(target, provisionStrategy.get(internalProvider, resolutionContext));
                                    return null;
                                } catch (IllegalAccessException e) {
                                    throw Smithy.<RuntimeException>cloak(e);
                                }
                            }
                            @Override public Collection<Dependency<?>> dependencies() {
                                return Collections.<Dependency<?>>singleton(dependency);
                            }
                        });
                    }
                    @Override public void visit(Method method) {
                        if (!injectionResolver.shouldInject(method)) {
                            return;
                        }
                        final ParameterizedFunctionInvoker<T> invoker = lazyInvoker(
                                new MethodFunction(method),
                                componentMetadata,
                                internalProvider);
                        injectors.add(new Injector<T>() {
                            @Override public Object inject(T target, InternalProvider internalProvider,
                                                           ResolutionContext resolutionContext) {
                                return invoker.invoke(target, internalProvider, resolutionContext);
                            }
                            @Override public Collection<Dependency<?>> dependencies() {
                                return invoker.dependencies();
                            }
                        });
                    }
                });
            }
            @Override public Object inject(T target, InternalProvider internalProvider,
                                           ResolutionContext resolutionContext) {
                if (injectors == null) {
                    synchronized (this) {
                        if (injectors == null) {
                            init(target.getClass(), internalProvider);
                        }
                    }
                }
                for (Injector<T> injector : injectors) {
                    injector.inject(target, internalProvider, resolutionContext);
                }
                return null;
            }
            @Override public Collection<Dependency<?>> dependencies() {
                if (injectors == null) {
                    throw new IllegalStateException("The component [" + componentMetadata.toString()
                        + "] cannot have it's dependencies queried before it has been initialized.");
                }
                List<Dependency<?>> dependencies = new LinkedList<Dependency<?>>();
                for (Injector<T> injector : injectors) {
                    dependencies.addAll(injector.dependencies());
                }
                return dependencies;
            }
        };
    }

    @Override public <T> Instantiator<T> constructorInstantiator(ComponentMetadata<Class<?>> componentMetadata) {
        final ParameterizedFunctionInvoker<T> invoker = eagerInvoker(
                new ConstructorFunction(constructorResolver.resolve(componentMetadata.provider())),
                componentMetadata);
        return new Instantiator<T>() {
            @Override public T newInstance(InternalProvider provider, ResolutionContext resolutionContext) {
                return invoker.invoke(null, provider, resolutionContext);
            }
            @Override public Collection<Dependency<?>> dependencies() {
                return invoker.dependencies();
            }
        };
    }

    @Override public <T> Instantiator<T> methodInstantiator(final ComponentMetadata<Method> componentMetadata) {
        return new Instantiator<T>() {
            StaticInjector<T> staticInjector = staticInjector(componentMetadata.provider(), componentMetadata);
            @Override public T newInstance(InternalProvider provider, ResolutionContext resolutionContext) {
                return staticInjector.inject(provider, resolutionContext);
            }
            @Override public Collection<Dependency<?>> dependencies() {
                return staticInjector.dependencies();
            }
        };
    }

    private <T> ParameterizedFunctionInvoker<T> eagerInvoker(final ParameterizedFunction function,
                                                        final ComponentMetadata<?> metadata) {
        final Dependency<?>[] dependencies = new Dependency[function.getParameterTypes().length];
        for (int i = 0; i < dependencies.length; i++) {
            dependencies[i] = new Parameter(function, i).asDependency();
        }
        return new ParameterizedFunctionInvoker<T>() {
            ProvisionStrategy<?>[] provisionStrategies = new ProvisionStrategy[dependencies.length];
            {
                linkers.add(new Linker() {
                    @Override public void link(InternalProvider internalProvider, ResolutionContext linkingContext) {
                        for (int i = 0; i < dependencies.length; i++) {
                            provisionStrategies[i] = internalProvider.getProvisionStrategy(
                                    DependencyRequest.Factory.create(metadata, dependencies[i]));
                        }
                    }
                }, LinkingPhase.POST_WIRING);
            }
            @Override public T invoke(Object onInstance, InternalProvider internalProvider, ResolutionContext resolutionContext) {
                Object[] parameters = new Object[provisionStrategies.length];
                for (int i = 0; i < parameters.length; i++) {
                    parameters[i] = provisionStrategies[i].get(internalProvider, resolutionContext);
                }
                try {
                    return Smithy.cloak(function.invoke(onInstance, parameters));
                } catch (IllegalAccessException e) {
                    throw Smithy.<RuntimeException>cloak(e);
                } catch (InvocationTargetException e) {
                    throw Smithy.<RuntimeException>cloak(e);
                } catch (InstantiationException e) {
                    throw Smithy.<RuntimeException>cloak(e);
                }
            }
            @Override public Collection<Dependency<?>> dependencies() {
                return Arrays.asList(dependencies);
            }
        };
    }

    private <T> ParameterizedFunctionInvoker<T> lazyInvoker(final ParameterizedFunction function,
                                                             final ComponentMetadata<?> metadata,
                                                             final InternalProvider internalProvider) {
        final Dependency<?>[] dependencies = new Dependency[function.getParameterTypes().length];
        for (int i = 0; i < dependencies.length; i++) {
            dependencies[i] = new Parameter(function, i).asDependency();
        }
        return new ParameterizedFunctionInvoker<T>() {
            ProvisionStrategy<?>[] provisionStrategies = new ProvisionStrategy[dependencies.length];
            {
                for (int i = 0; i < dependencies.length; i++) {
                    provisionStrategies[i] = internalProvider.getProvisionStrategy(
                            DependencyRequest.Factory.create(metadata, dependencies[i]));
                }
            }
            @Override public T invoke(Object onInstance, InternalProvider internalProvider, ResolutionContext resolutionContext) {
                Object[] parameters = new Object[provisionStrategies.length];
                for (int i = 0; i < parameters.length; i++) {
                    parameters[i] = provisionStrategies[i].get(internalProvider, resolutionContext);
                }
                try {
                    return Smithy.cloak(function.invoke(onInstance, parameters));
                } catch (IllegalAccessException e) {
                    throw Smithy.<RuntimeException>cloak(e);
                } catch (InvocationTargetException e) {
                    throw Smithy.<RuntimeException>cloak(e);
                } catch (InstantiationException e) {
                    throw Smithy.<RuntimeException>cloak(e);
                }
            }
            @Override public Collection<Dependency<?>> dependencies() {
                return Arrays.asList(dependencies);
            }
        };
    }

    private static interface ParameterizedFunctionInvoker<T> extends Dependent {
        T invoke(Object onInstance, InternalProvider internalProvider, ResolutionContext resolutionContext);
    }

    private interface ParameterizedFunction<T> {
        T invoke(Object onInstance, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException;
        Type[] getParameterTypes();
        Annotation[][] getParameterAnnotations();
    }

    private static class MethodFunction implements ParameterizedFunction {
        final Method method;
        MethodFunction(Method method) {
            method.setAccessible(true);
            this.method = method;
        }
        @Override public Object invoke(Object onInstance, Object[] params) throws InvocationTargetException, IllegalAccessException {
            return method.invoke(onInstance, params);
        }
        @Override public Type[] getParameterTypes() {
            return method.getGenericParameterTypes();
        }
        @Override public Annotation[][] getParameterAnnotations() {
            return method.getParameterAnnotations();
        }
    }

    private static class ConstructorFunction implements ParameterizedFunction {
        final Constructor<?> constructor;
        ConstructorFunction(Constructor<?> constructor) {
            constructor.setAccessible(true);
            this.constructor = constructor;
        }
        @Override public Object invoke(Object onInstance, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException {
            return constructor.newInstance(params);
        }
        @Override public Type[] getParameterTypes() {
            return constructor.getGenericParameterTypes();
        }
        @Override public Annotation[][] getParameterAnnotations() {
            return constructor.getParameterAnnotations();
        }
    }

    private class Parameter<T> implements AnnotatedElement {

        final Annotation[] annotations;
        final Type type;

        Parameter(ParameterizedFunction parameterizedFunction, int index) {
            annotations = parameterizedFunction.getParameterAnnotations()[index];
            type = parameterizedFunction.getParameterTypes()[index];
        }

        @Override public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            for (Annotation annotation : annotations) {
                if (annotationClass.isInstance(annotation)) {
                    return true;
                }
            }
            return false;
        }

        @Override public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            for (Annotation annotation : annotations) {
                if (annotationClass.isInstance(annotation)) {
                    return Smithy.cloak(annotation);
                }
            }
            return null;
        }

        @Override public Annotation[] getAnnotations() {
            return annotations;
        }

        @Override public Annotation[] getDeclaredAnnotations() {
            return annotations;
        }

        Dependency<T> asDependency() {
            return new Dependency<T>() {
                Qualifier qualifier = qualifierResolver.resolve(Parameter.this);
                @Override Qualifier qualifier() {
                    return qualifier;
                }
                @Override TypeKey<T> typeKey() {
                    return Types.typeKey(type);
                }
            };
        }

    }

    static class ClassWalker {
        interface MemberVisitor {
            void visit(Field field);
            void visit(Method method);
        }
        void walk(Class<?> classToWalk, MemberVisitor memberVisitor) {
            for (Class<?> cls = classToWalk; cls != Object.class; cls = cls.getSuperclass()) {
                for (final Field field : cls.getDeclaredFields()) {
                    memberVisitor.visit(field);
                }
                for (final Method method : cls.getDeclaredMethods()) {
                    memberVisitor.visit(method);
                }
            }
        }
    }

}
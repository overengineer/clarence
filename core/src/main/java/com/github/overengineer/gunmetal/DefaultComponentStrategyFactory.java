package com.github.overengineer.gunmetal;

import com.github.overengineer.gunmetal.inject.ComponentInjector;
import com.github.overengineer.gunmetal.inject.InjectorFactory;
import com.github.overengineer.gunmetal.inject.MethodInjector;
import com.github.overengineer.gunmetal.instantiate.Instantiator;
import com.github.overengineer.gunmetal.instantiate.InstantiatorFactory;
import com.github.overengineer.gunmetal.metadata.MetadataAdapter;
import com.github.overengineer.gunmetal.scope.Scope;
import com.github.overengineer.gunmetal.scope.Scopes;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author rees.byars
 */
public class DefaultComponentStrategyFactory implements ComponentStrategyFactory {

    private final MetadataAdapter metadataAdapter;
    private final InjectorFactory injectorFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final List<ComponentInitializationListener> initializationListeners;

    public DefaultComponentStrategyFactory(MetadataAdapter metadataAdapter, InjectorFactory injectorFactory, InstantiatorFactory instantiatorFactory, List<ComponentInitializationListener> initializationListeners) {
        this.metadataAdapter = metadataAdapter;
        this.injectorFactory = injectorFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.initializationListeners = initializationListeners;
    }

    @Override
    public <T> ComponentStrategy<T> create(Class<T> implementationType, Object qualifier, Scope scope) {
        ComponentInjector<T> injector = injectorFactory.create(implementationType);
        Instantiator<T> instantiator = instantiatorFactory.create(implementationType);
        Scope theScope = metadataAdapter.getScope(implementationType);
        if (theScope == null) {
            theScope = scope;
        }
        if (Scopes.PROTOTYPE.equals(theScope)) {
            return new CircularPrototypeComponentStrategy<T>(injector, instantiator, qualifier, initializationListeners);
        } else if (Scopes.SINGLETON.equals(theScope)) {
            return new SingletonComponentStrategy<T>(new CircularPrototypeComponentStrategy<T>(injector, instantiator, qualifier, initializationListeners));
        } else {
            return metadataAdapter.getStrategyProvider(theScope).get(implementationType, qualifier, new CircularPrototypeComponentStrategy<T>(injector, instantiator, qualifier, initializationListeners));
        }
    }

    @Override
    public <T> ComponentStrategy<T> createInstanceStrategy(T implementation, Object qualifier) {
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) implementation.getClass();
        ComponentInjector<T> injector = injectorFactory.create(clazz);
        return new InstanceStrategy<T>(implementation, injector, qualifier, initializationListeners);
    }

    @Override
    public <T> ComponentStrategy<T> createCustomStrategy(ComponentStrategy providerStrategy, Object qualifier) {
        Method providerMethod = metadataAdapter.getCustomProviderMethod(providerStrategy.getComponentType());
        @SuppressWarnings("unchecked")
        MethodInjector<T> methodInjector = injectorFactory.create(providerStrategy.getComponentType(), providerMethod);
        return new CustomComponentStrategy<T>(providerStrategy, methodInjector, providerMethod.getReturnType(), qualifier);
    }

}

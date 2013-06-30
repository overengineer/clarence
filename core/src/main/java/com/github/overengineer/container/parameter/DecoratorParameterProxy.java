package com.github.overengineer.container.parameter;

import com.github.overengineer.container.ComponentStrategy;
import com.github.overengineer.container.Provider;
import com.github.overengineer.container.SelectionAdvisor;
import com.github.overengineer.container.key.Key;

/**
 * @author rees.byars
 */
public class DecoratorParameterProxy<T> implements ParameterProxy<T> {

    private final Key<T> key;
    private final Class<?> injectionTarget;

    DecoratorParameterProxy(Key<T> key, Class<?> injectionTarget) {
        this.key = key;
        this.injectionTarget = injectionTarget;
    }

    @Override
    public T get(Provider provider) {
        return provider.get(key, new SelectionAdvisor() {
            @Override
            public boolean validSelection(ComponentStrategy<?> candidateStrategy) {
                return candidateStrategy.getComponentType() != injectionTarget; //TODO this prevents self injection.  OK??
            }
        });
    }

}
package io.gunmetal.internal;

import io.gunmetal.spi.Config;
import io.gunmetal.spi.Dependency;
import io.gunmetal.spi.DependencyRequest;
import io.gunmetal.spi.InternalProvider;
import io.gunmetal.spi.Linkers;
import io.gunmetal.spi.ProvisionStrategy;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
* @author rees.byars
*/
class InternalProviderImpl implements InternalProvider {

    private final Config config;
    private final HandlerFactory handlerFactory;
    private final HandlerCache handlerCache;
    private final Linkers linkers;

    InternalProviderImpl(Config config,
                         HandlerFactory handlerFactory,
                         HandlerCache handlerCache,
                         Linkers linkers) {
        this.config = config;
        this.handlerFactory = handlerFactory;
        this.handlerCache = handlerCache;
        this.linkers = linkers;
    }

    @Override public <T> ProvisionStrategy<? extends T> getProvisionStrategy(final DependencyRequest<T> dependencyRequest) {
        final Dependency<T> dependency = dependencyRequest.dependency();
        DependencyRequestHandler<? extends T> requestHandler = handlerCache.get(dependency);
        if (requestHandler != null) {
            return requestHandler
                    .handle(dependencyRequest)
                    .validateResponse()
                    .getProvisionStrategy();
        }
        if (config.isProvider(dependency)) {
            requestHandler = createProviderHandler(dependencyRequest, config, this, handlerCache);
            if (requestHandler != null) {
                handlerCache.put(dependency, requestHandler);
                return requestHandler
                        .handle(dependencyRequest)
                        .validateResponse()
                        .getProvisionStrategy();
            }
        }
        requestHandler = handlerFactory.attemptToCreateHandlerFor(dependencyRequest, linkers);
        if (requestHandler != null) {
            handlerCache.put(dependency, requestHandler);
            return requestHandler
                    .handle(dependencyRequest)
                    .validateResponse()
                    .getProvisionStrategy();
        }
        throw new DependencyException("missing dependency " + dependency.toString()); // TODO
    }

    private <T, C> DependencyRequestHandler<T> createProviderHandler(
            final DependencyRequest<T> providerRequest,
            final Config config,
            final InternalProvider internalProvider,
            final HandlerCache handlerCache) {
        Dependency<T> providerDependency = providerRequest.dependency();
        Type providedType = ((ParameterizedType) providerDependency.typeKey().type()).getActualTypeArguments()[0];
        final Dependency<C> componentDependency = Dependency.from(providerDependency.qualifier(), providedType);
        final DependencyRequestHandler<? extends C> componentHandler = handlerCache.get(componentDependency);
        if (componentHandler == null) {
            return null;
        }
        ProviderStrategyFactory providerStrategyFactory = new ProviderStrategyFactory(config);
        ProvisionStrategy<? extends C> componentStrategy = componentHandler.force();
        final ProvisionStrategy<T> providerStrategy =
                providerStrategyFactory.create(componentStrategy, internalProvider);
        return new ProviderRequestHandler<>(
                providerRequest,
                providerStrategy,
                providerStrategyFactory,
                componentHandler,
                componentDependency);

    }

}
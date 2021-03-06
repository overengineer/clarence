package io.gunmetal.internal;

import io.gunmetal.MultiBind;
import io.gunmetal.Ref;
import io.gunmetal.spi.Converter;
import io.gunmetal.spi.ConverterSupplier;
import io.gunmetal.spi.Dependency;
import io.gunmetal.spi.DependencyRequest;
import io.gunmetal.spi.DependencySupplier;
import io.gunmetal.spi.ProvisionStrategy;
import io.gunmetal.spi.SupplierAdapter;
import io.gunmetal.spi.TypeKey;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Instances of the supplier should not be accessed concurrently.
 *
 * @author rees.byars
 */
class ComponentDependencySupplier implements DependencySupplier {

    private final SupplierAdapter supplierAdapter;
    private final ResourceAccessorFactory resourceAccessorFactory;
    private final ConverterSupplier converterSupplier;
    private final ComponentGraph componentGraph;
    private final ComponentContext context;
    private final boolean requireInterfaces;

    ComponentDependencySupplier(SupplierAdapter supplierAdapter,
                                ResourceAccessorFactory resourceAccessorFactory,
                                ConverterSupplier converterSupplier,
                                ComponentGraph componentGraph,
                                ComponentContext context,
                                boolean requireInterfaces) {
        this.supplierAdapter = supplierAdapter;
        this.resourceAccessorFactory = resourceAccessorFactory;
        this.converterSupplier = converterSupplier;
        this.componentGraph = componentGraph;
        this.context = context;
        this.requireInterfaces = requireInterfaces;
    }

    @Override public synchronized ProvisionStrategy supply(DependencyRequest dependencyRequest) {

        Dependency dependency = dependencyRequest.dependency();

        // try cached strategy
        ProvisionStrategy strategy = getCachedProvisionStrategy(dependencyRequest);
        if (strategy != null) {
            return strategy;
        }

        // try jit constructor ResourceAccessor strategy
        ResourceAccessor resourceAccessor = resourceAccessorFactory.createJit(dependencyRequest, context);
        if (resourceAccessor != null) {
            componentGraph.put(dependencyRequest.dependency(), resourceAccessor, context.errors());
            return resourceAccessor.process(dependencyRequest, context.errors());
        }

        // try conversion strategy
        TypeKey typeKey = dependency.typeKey();
        for (Converter converter : converterSupplier.convertersForType(typeKey)) {
            for (Class<?> fromType : converter.supportedFromTypes()) {
                resourceAccessor = createConversionResourceAccessor(converter, fromType, dependency);
                if (resourceAccessor != null) {
                    componentGraph.put(dependency, resourceAccessor, context.errors());
                    return resourceAccessor.process(dependencyRequest, context.errors());
                }
            }
        }

        // try jit local factory method ResourceAccessor
        List<ResourceAccessor> factoryResourceAccessorsForRequest =
                resourceAccessorFactory.createJitFactoryRequest(dependencyRequest, context);
        componentGraph.putAll(factoryResourceAccessorsForRequest, context.errors());
        strategy = getCachedProvisionStrategy(dependencyRequest);
        if (strategy != null) {
            return strategy;
        }

        // all attempts to serve request have failed
        context.errors().add(
                dependencyRequest.sourceProvision(),
                "There is no provider defined for a dependency -> " + dependencyRequest.dependency());

        return (p, c) -> {
            context.errors().throwIfNotEmpty();
            return null;
        };

    }

    private synchronized ProvisionStrategy getCachedProvisionStrategy(final DependencyRequest dependencyRequest) {

        final Dependency dependency = dependencyRequest.dependency();

        if (requireInterfaces &&
                !(dependency.typeKey().raw().isInterface()
                        || dependencyRequest.sourceProvision().overrides().allowNonInterface())) {
            context.errors().add(
                    dependencyRequest.sourceProvision(),
                    "Dependency is not an interface -> " + dependency);
        }

        ResourceAccessor resourceAccessor = componentGraph.get(dependency);
        if (resourceAccessor != null) {
            return resourceAccessor.process(dependencyRequest, context.errors());
        }

        // TODO totally gross
        if (supplierAdapter.isSupplier(dependency)) {
            resourceAccessor = createReferenceResourceAccessor(
                    dependencyRequest, () -> new SupplierStrategyFactory(supplierAdapter));
            if (resourceAccessor != null) {
                componentGraph.put(dependency, resourceAccessor, context.errors());
                return resourceAccessor.process(dependencyRequest, context.errors());
                // support empty multi-bind request
                // TODO should not know about MultiBind here -> should be included in above mentioned DependencyMetadata
            } else if (Arrays.stream(dependency.qualifier().qualifiers()).anyMatch(q -> q instanceof MultiBind)) {
                return (supplier, resolutionContext) -> supplierAdapter.supplier(ArrayList::new);
            }
        }

        if (dependency.typeKey().raw() == Ref.class) {
            resourceAccessor = createReferenceResourceAccessor(dependencyRequest, RefStrategyFactory::new);
            if (resourceAccessor != null) {
                componentGraph.put(dependency, resourceAccessor, context.errors());
                return resourceAccessor.process(dependencyRequest, context.errors());
            }  else {
                // support empty multi-bind request
                // TODO should not know about MultiBind here -> should be included in above mentioned DependencyMetadata
                if (Arrays.stream(dependency.qualifier().qualifiers()).anyMatch(q -> q instanceof MultiBind)) {
                    return (supplier, resolutionContext) -> (Ref<Object>) ArrayList::new;
                }
            }
        }

        // support empty multi-bind request
        // TODO should not know about MultiBind here -> should be included in above mentioned DependencyMetadata
        if (Arrays.stream(dependency.qualifier().qualifiers()).anyMatch(q -> q instanceof MultiBind)) {
            return (supplier, resolutionContext) -> new ArrayList<>();
        }

        return null;
    }

    private ResourceAccessor createReferenceResourceAccessor(
            final DependencyRequest refRequest, Supplier<ReferenceStrategyFactory> factorySupplier) {
        Dependency referenceDependency = refRequest.dependency();
        Type providedType = ((ParameterizedType) referenceDependency.typeKey().type()).getActualTypeArguments()[0];
        final Dependency provisionDependency = Dependency.from(referenceDependency.qualifier(), providedType);
        ResourceAccessor provisionResourceAccessor = componentGraph.get(provisionDependency);
        if (provisionResourceAccessor == null) {
            // TODO gross, whole class gross
            // try jit constructor ResourceAccessor strategy
            provisionResourceAccessor =
                    resourceAccessorFactory.createJit(
                            DependencyRequest.create(refRequest, provisionDependency), context);
            if (provisionResourceAccessor == null) {
                return null;
            }
            // TODO jit injections by a parent can cause new children to
            // TODO have provision override errors.  does it matter?
            componentGraph.put(provisionDependency, provisionResourceAccessor, context.errors());
        }
        ProvisionStrategy provisionStrategy = provisionResourceAccessor.force();
        ReferenceStrategyFactory strategyFactory = factorySupplier.get();
        final ProvisionStrategy referenceStrategy = strategyFactory.create(provisionStrategy, this, context);
        return resourceAccessorFactory.createForReference(
                refRequest,
                provisionResourceAccessor,
                provisionDependency,
                referenceStrategy,
                strategyFactory,
                context);
    }

    private ResourceAccessor createConversionResourceAccessor(
            Converter converter, Class<?> fromType, Dependency to) {
        Dependency from = Dependency.from(to.qualifier(), fromType);
        ResourceAccessor fromResourceAccessor = componentGraph.get(from);
        if (fromResourceAccessor != null) {
            return resourceAccessorFactory.createForConversion(fromResourceAccessor, converter, from, to);
        }
        return null;
    }

}

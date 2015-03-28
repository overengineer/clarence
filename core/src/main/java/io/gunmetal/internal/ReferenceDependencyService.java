package io.gunmetal.internal;

import io.gunmetal.spi.Dependency;
import io.gunmetal.spi.DependencyRequest;
import io.gunmetal.spi.Errors;
import io.gunmetal.spi.DependencySupplier;
import io.gunmetal.spi.Linkers;
import io.gunmetal.spi.ProvisionStrategy;
import io.gunmetal.spi.ResolutionContext;

import java.util.Collections;

/**
 * @author rees.byars
 */
class ReferenceDependencyService implements DependencyService {

    private final DependencyRequest referenceRequest;
    private final DependencyService provisionService;
    private final Dependency provisionDependency;
    private final ProvisionStrategy referenceStrategy;
    private final ReferenceStrategyFactory referenceStrategyFactory;
    private final Binding binding;

    ReferenceDependencyService(
            DependencyRequest referenceRequest,
            DependencyService provisionService,
            Dependency provisionDependency,
            ProvisionStrategy referenceStrategy,
            ReferenceStrategyFactory referenceStrategyFactory) {
        this.referenceRequest = referenceRequest;
        this.provisionService = provisionService;
        this.provisionDependency = provisionDependency;
        this.referenceStrategy = referenceStrategy;
        this.referenceStrategyFactory = referenceStrategyFactory;
        binding = new BindingImpl(
                provisionService.binding().resource(),
                Collections.singletonList(referenceRequest.dependency()));
    }

    @Override public DependencyService replicateWith(ComponentContext context) {
        return new ReferenceDependencyService(
                referenceRequest,
                provisionService.replicateWith(context),
                provisionDependency,
                new DelegatingProvisionStrategy(context.linkers()),
                referenceStrategyFactory);
    }

    @Override public Binding binding() {
        return binding;
    }

    @Override public DependencyResponse service(DependencyRequest dependencyRequest, Errors errors) {
        provisionService.service(DependencyRequest.create(referenceRequest, provisionDependency), errors);
        return this::force;
    }

    @Override public ProvisionStrategy force() {
        return referenceStrategy;
    }

    private class DelegatingProvisionStrategy implements ProvisionStrategy {

        ProvisionStrategy delegateStrategy;

        DelegatingProvisionStrategy(Linkers linkers) {
            linkers.addWiringLinker((reference, context) -> {
                ProvisionStrategy provisionStrategy =
                        reference.getProvisionStrategy(DependencyRequest.create(referenceRequest, provisionDependency));
                delegateStrategy = referenceStrategyFactory.create(provisionStrategy, reference);
            });
        }

        @Override public Object get(DependencySupplier dependencySupplier, ResolutionContext resolutionContext) {
            return delegateStrategy.get(dependencySupplier, resolutionContext);
        }

    }

}

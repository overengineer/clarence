package io.gunmetal.internal;

import java.util.LinkedList;
import java.util.List;

/**
 * @author rees.byars
 */
interface DependencyRequest {

    Class<?> sourceOrigin();

    Qualifier sourceQualifier();

    ModuleAdapter sourceModule();

    Dependency<?> dependency();

    List<String> errors();

    DependencyRequest addError(String errorMessage);

    final class Factory {

        private Factory() { }

        static DependencyRequest create(final ComponentMetadata requestingComponent, final Dependency<?> dependency) {

            return new DependencyRequest() {

                List<String> errors;

                @Override
                public Class<?> sourceOrigin() {
                    return requestingComponent.providerClass();
                }

                @Override
                public Qualifier sourceQualifier() {
                    return requestingComponent.qualifier();
                }

                @Override
                public ModuleAdapter sourceModule() {
                    return requestingComponent.moduleAdapter();
                }

                @Override
                public Dependency<?> dependency() {
                    return dependency;
                }

                @Override
                public List<String> errors() {
                    return errors;
                }

                @Override
                public DependencyRequest addError(String errorMessage) {
                    if (errors == null) {
                        errors = new LinkedList<String>();
                    }
                    errors.add(errorMessage);
                    return this;
                }

            };

        }

    }

}
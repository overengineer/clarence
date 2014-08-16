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

package io.gunmetal.spi;

import java.lang.reflect.AnnotatedElement;

/**
 * @author rees.byars
 */
public interface DependencyRequest<T> {

    // TODO cleanup duplication between methods here and those on the provision metadata

    AnnotatedElement source();

    Class<?> sourceOrigin();

    Qualifier sourceQualifier();

    Scope sourceScope();

    ModuleMetadata sourceModule();

    ProvisionMetadata<?> sourceProvision();

    Dependency<T> dependency();

    static <T> DependencyRequest<T> create(final ProvisionMetadata<?> requestingProvision, final Dependency<T> dependency) {

        return new DependencyRequest<T>() {

            @Override public AnnotatedElement source() {
                return requestingProvision.provider();
            }

            @Override public Class<?> sourceOrigin() {
                return requestingProvision.providerClass();
            }

            @Override public Qualifier sourceQualifier() {
                return requestingProvision.qualifier();
            }

            @Override public Scope sourceScope() {
                return requestingProvision.scope();
            }

            @Override public ModuleMetadata sourceModule() {
                return requestingProvision.moduleMetadata();
            }

            @Override public ProvisionMetadata<?> sourceProvision() {
                return requestingProvision;
            }

            @Override public Dependency<T> dependency() {
                return dependency;
            }

        };

    }

    static <T> DependencyRequest<T> create(final DependencyRequest<?> dependencyRequest,
                                                  final Dependency<T> dependency) {

        return new DependencyRequest<T>() {

            @Override public AnnotatedElement source() {
                return dependencyRequest.source();
            }

            @Override public Class<?> sourceOrigin() {
                return dependencyRequest.sourceOrigin();
            }

            @Override public Qualifier sourceQualifier() {
                return dependencyRequest.sourceQualifier();
            }

            @Override public Scope sourceScope() {
                return dependencyRequest.sourceScope();
            }

            @Override public ModuleMetadata sourceModule() {
                return dependencyRequest.sourceModule();
            }

            @Override public ProvisionMetadata<?> sourceProvision() {
                return dependencyRequest.sourceProvision();
            }

            @Override public Dependency<T> dependency() {
                return dependency;
            }

        };

    }

}

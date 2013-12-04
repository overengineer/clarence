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

import io.gunmetal.internal.Smithy;

import java.util.HashMap;
import java.util.Map;

/**
 * @author rees.byars
 */
public interface ResolutionContext {

    <T> ProvisionContext<T> getProvisionContext(ProvisionStrategy<T> strategy);

    interface States  {
        byte NEW = 0;
        byte PRE_INSTANTIATION = 1;
        byte PRE_INJECTION = 2;
    }

    class ProvisionContext<T> {
        byte state = States.NEW;
        T component;
    }

    class Factory {

        private static class ResolutionContextImpl implements ResolutionContext {

            private final Map<ProvisionStrategy, ProvisionContext> contextMap = new HashMap<ProvisionStrategy, ProvisionContext>();

            @Override
            public <T> ProvisionContext<T> getProvisionContext(ProvisionStrategy<T> strategy) {

                ProvisionContext<T> strategyContext = Smithy.cloak(contextMap.get(strategy));

                if (strategyContext == null) {
                    strategyContext = new ProvisionContext<T>();
                    contextMap.put(strategy, strategyContext);
                }

                return strategyContext;
            }
        }

        public static ResolutionContext create() {
            return new ResolutionContextImpl();
        }

    }
}
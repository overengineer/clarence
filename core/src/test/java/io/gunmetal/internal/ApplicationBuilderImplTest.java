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

import com.github.overengineer.gunmetal.testmocks.A;
import io.gunmetal.ApplicationContainer;
import io.gunmetal.ApplicationModule;
import io.gunmetal.Gunmetal;
import io.gunmetal.Module;
import io.gunmetal.Prototype;
import io.gunmetal.Provider;
import org.junit.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author rees.byars
 */
public class ApplicationBuilderImplTest {

    @ApplicationModule(modules = TestModule.class)
    static class Application { }

    @Retention(RetentionPolicy.RUNTIME)
    @io.gunmetal.Qualifier
    public @interface Main {}


    @Module
    static class TestModule {

        @Main static ApplicationBuilderImplTest test(ApplicationBuilderImplTest test) {
            return new ApplicationBuilderImplTest();
        }

        static Object test2(Provider<ApplicationBuilderImplTest> test) {
            System.out.println(test.get());
            System.out.println(test.get());
            System.out.println(test.get());
            System.out.println(test.get());
            return test.get();
        }

        @Prototype static ApplicationBuilderImplTest testy() {
            return new ApplicationBuilderImplTest();
        }

    }

    @Test
    public void testBuild() {

        ApplicationContainer app = new ApplicationBuilderImpl().build(Application.class);

        @Main
        class Dep implements io.gunmetal.Dependency<ApplicationBuilderImplTest> { }

        ApplicationBuilderImplTest test = app.get(Dep.class);

        assert test != this;

        class Dep2 implements io.gunmetal.Dependency<A> { }

        app = Gunmetal.create(NewGunmetalBenchMarkModule.class);

        A a = app.get(Dep2.class);

        assert a != app.get(Dep2.class);
    }

}
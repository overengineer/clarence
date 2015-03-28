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

package io.gunmetal.sandbox;

import io.gunmetal.Component;
import io.gunmetal.FromModule;
import io.gunmetal.Inject;
import io.gunmetal.Lazy;
import io.gunmetal.Module;
import io.gunmetal.MultiBind;
import io.gunmetal.Overrides;
import io.gunmetal.Param;
import io.gunmetal.Provider;
import io.gunmetal.Provides;
import io.gunmetal.Ref;
import io.gunmetal.Singleton;
import io.gunmetal.internal.ComponentBuilder;
import io.gunmetal.sandbox.testmocks.A;
import io.gunmetal.sandbox.testmocks.AA;
import io.gunmetal.sandbox.testmocks.F;
import io.gunmetal.sandbox.testmocks.N;
import io.gunmetal.sandbox.testmocks.NewGunmetalBenchMarkModule;
import io.gunmetal.spi.Converter;
import io.gunmetal.spi.ProvisionStrategyDecorator;
import io.gunmetal.spi.Scope;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author rees.byars
 */
public class SandboxIntegrationTest {



    @Retention(RetentionPolicy.RUNTIME)
    @io.gunmetal.Qualifier
    public @interface Main {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @io.gunmetal.Qualifier
    public @interface Stateful {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @io.gunmetal.Scope
    public @interface TestScope {
    }

    interface Bad {
    }

    static class Circ implements Bad {
        @Inject Bad providedCirc;
        @Inject SandboxIntegrationTest sandboxIntegrationTest;
    }

    @Module(notAccessibleFrom = TestModule.BlackList.class, dependsOn = StatefulModule.class)
    static class TestModule {

        @Provides @Singleton @Overrides(allowCycle = true) static Bad providedCirc(Provider<Circ> circProvider) {
            return circProvider.get();
        }

        @Provides @Overrides(allowCycle = true) static Circ circ() {
            return new Circ();
        }

        @io.gunmetal.BlackList.Modules(M.class)
        static class BlackList implements io.gunmetal.BlackList {

            @Inject
            TestModule testModule;


        }


        @Provides @Singleton static Class<Void> routine(BlackList blackList) {
            System.out.println("routineeee" + blackList);
            return Void.TYPE;
        }


        @Provides @Singleton @Overrides(allowMappingOverride = true) static TestModule tm(ArrayList<Integer> integers) {
            return new TestModule();
        }

        @Provides @Singleton static TestModule tmO(@Param String name) {
            System.out.println(name);
            return new TestModule();
        }

        @Provides @Singleton @Main static SandboxIntegrationTest test(SandboxIntegrationTest test) {
            return new SandboxIntegrationTest();
        }

        @Provides @Singleton static Object test2(Provider<SandboxIntegrationTest> test, BlackList blackList) {
            System.out.println(test.get());
            System.out.println(test.get());

            System.out.println(blackList);
            return test.get();
        }

        @Provides static SandboxIntegrationTest testy() {
            return new SandboxIntegrationTest();
        }

        @Provides @Singleton @MultiBind static String s1(@Stateful String name) {
            return name;
        }

        @Provides @Singleton @MultiBind static String s2() {
            return "2";
        }

        @Provides @Singleton @MultiBind static String s3() {
            return "3";
        }

        @Provides @Singleton @Main static InputStream printStrings(@MultiBind List<String> strings) {
            for (String s : strings) {
                System.out.println(s);
            }
            return System.in;
        }

        @Provides @Singleton @Lazy static List<ProvisionStrategyDecorator> decorators() {
            return Collections.singletonList((resourceMetadata, delegateStrategy, linkers) -> delegateStrategy);
        }

        @Provides @Singleton @Lazy static Map<? extends Scope, ? extends ProvisionStrategyDecorator> scopeDecorators() {
            return Collections.singletonMap(
                    CustomScopes.TEST,
                    (resourceMetadata, delegateStrategy, linkers) -> delegateStrategy);
        }

    }

    @Module(stateful = true)
    @Stateful
    @Singleton
    static class StatefulModule {

        String name;

        StatefulModule(String name) {
            this.name = name;
        }

        @Provides @Singleton String name(SandboxIntegrationTest test) {
            return name + test.getClass().getName();
        }

        @Provides @Singleton List<StatefulModule> statefulModules(@FromModule Ref<StatefulModule> statefulModuleRef) {
            assert statefulModuleRef.get() == statefulModuleRef.get();
            return Collections.singletonList(statefulModuleRef.get());
        }

    }

    @Module
    static class M {
        @Provides @Singleton @Lazy static M m(SandboxIntegrationTest test) {
            return new M();
        }
    }

    enum CustomScopes implements Scope {
        TEST;

        @Override public boolean canInject(Scope scope) {
            return scope.equals(scope);
        }
    }

    @Module(dependsOn = TestModule.class)
    public interface TestComponent {

        void inject(Object o);

        ComponentBuilder plus();

        public interface Factory {
            TestComponent create(StatefulModule statefulModule);
        }

    }

    @Module(dependsOn = NewGunmetalBenchMarkModule.class)
    public interface GComponent {

        void inject(Object o);

        public interface Factory {
            GComponent create();
        }

    }

    @Test
    public void testBuild() {

        TestComponent.Factory templateGraph = Component.builder()
                .requireAcyclic()
                .build(TestComponent.Factory.class);
        templateGraph.create(new StatefulModule("rees"));

        TestComponent app = templateGraph.create(new StatefulModule("rees"));

        class Dep {
            @Inject @Main SandboxIntegrationTest test;
        }


        Dep dep = new Dep();
        app.inject(dep);
        SandboxIntegrationTest test = dep.test;


        class BadDep {
            @Inject Circ circ;
        }

        BadDep badDep = new BadDep();
        BadDep badDep2 = new BadDep();
        BadDep badDep3 = new BadDep();
        BadDep badDep4 = new BadDep();
        app.inject(badDep);
        app.inject(badDep2);
        app.inject(badDep3);
        app.inject(badDep4);

        assert badDep.circ != badDep4.circ;

        BadDep badDep5 = new BadDep();
        app.inject(badDep5);
        assert (badDep.circ).providedCirc != badDep5.circ;

        assert test != this;

        class Dep2 {
            @Inject A a;
        }

        Dep2 dep2 = new Dep2();

        GComponent gApp = Component
                .builder()
                .build(GComponent.Factory.class)
                .create();

        gApp.inject(dep2);
        A a = dep2.a;

        gApp.inject(dep2);
        assert a != dep2.a;

        class InjectTest {
            @Inject
            F f;
        }

        class InjectTest2 {
            @Inject
            F f;
        }

        InjectTest injectTest = new InjectTest();

        gApp.inject(injectTest);

        gApp.inject(new InjectTest2());

        assert injectTest.f != null;

    }

    @Module(dependsOn = {TestModule.class, M.class})
    public interface BadComponent {

        public interface Factory {
            BadComponent create();
        }

    }

    @Test(expected = RuntimeException.class)
    public void testBlackList() {
        new ComponentBuilder().build(BadComponent.Factory.class);
    }

    @Module(subsumes = MyLibrary.class)
    @Main
    static class PlusModule implements Cheese {

        @Inject SandboxIntegrationTest sandboxIntegrationTest;

        @Provides @Singleton static PlusModule plusModule() {
            return new PlusModule();
        }

        @Provides @Singleton static Cheese cheese() {
            return new PlusModule();
        }

        @Provides @Singleton static OutputStream r(@FromModule Cheese cheese, @Main Lib myLibrary) {
            System.out.println("sup");
            return System.out;
        }

    }

    interface Lib {
    }

    @Module(lib = true)
    static class MyLibrary implements Lib {

        @Provides @Singleton static Lib huh(@FromModule Cheese cheese) {
            System.out.println("sup library");
            return new MyLibrary();
        }

    }

    interface Cheese {
    }

    @Module(dependsOn = PlusModule.class)
    public interface PlusComponent {

        void inject(Object o);

        public interface Factory {
            PlusComponent create();
        }

    }

    @Test
    public void testPlus() {


        class Dep {
            @Inject @Main PlusModule plusModule;
        }

        Dep dep = new Dep();

        TestComponent parent = Component.builder()
                .build(TestComponent.Factory.class)
                .create(new StatefulModule("plus"));

        PlusComponent.Factory childTemplate = parent.plus().build(PlusComponent.Factory.class);
        PlusComponent child = childTemplate.create();

        child.inject(dep);
        PlusModule p = dep.plusModule;

        assert p.sandboxIntegrationTest != null;

        dep.plusModule = null;
        parent.inject(dep);
        assert dep.plusModule != null;

        class InjectTest {
            @Inject
            F f;
        }

        class InjectTest2 {
            @Inject
            F f;
        }

        InjectTest injectTest = new InjectTest();

        child.inject(injectTest);

        child.inject(new InjectTest2());

        assert injectTest.f != null;

        PlusComponent childCopy = childTemplate.create();
        Dep dep2 = new Dep();
        child.inject(dep);
        childCopy.inject(dep2);
        assert dep.plusModule != dep2.plusModule;

        child.inject(dep2);
        assert dep.plusModule == dep2.plusModule;

        childCopy.inject(injectTest);

    }

    @Test
    public void testMore() {
        class ProviderDep {
            @Inject Provider<N> nProvider;
        }
        ProviderDep p =  new ProviderDep();
        APPLICATION_CONTAINER.inject(p);
        newGunmetalProvider = p.nProvider;
        newGunmetalStandup(10000);
    }

    io.gunmetal.Provider<N> newGunmetalProvider;
    static final GComponent APPLICATION_CONTAINER = Component.builder().build(GComponent.Factory.class).create();

    static class AaHolder {
        @Inject AA aa;
    }

    long newGunmetalStandup(int reps) {
        int dummy = 0;
        for (long i = 0; i < reps; i++) {
            dummy |= newGunmetalProvider.get().hashCode();
        }
        return dummy;
    }

    @Module static class ConversionModule {

        @Inject Long numberLong;

        @Provides static String numberString() {
            return "3425";
        }

    }

    @Module(dependsOn = ConversionModule.class)
    public interface ConversionComponent {

        void inject(Object o);

        public interface Factory {
            ConversionComponent create();
        }

    }

    @Test
    public void testConversion() {

        ConversionComponent graph =
                Component.builder()
                        .withConverterProvider(to -> {
                            if (to.raw().equals(Long.class) || to.raw().equals(long.class)) {
                                return Collections.singletonList(new Converter() {
                                    @Override public List<Class<?>> supportedFromTypes() {
                                        return Arrays.asList(String.class);
                                    }
                                    @Override public Object convert(Object from) {
                                        return Long.valueOf(from.toString());
                                    }
                                });
                            }
                            return Collections.emptyList();
                        }).build(ConversionComponent.Factory.class).create();

        ConversionModule c = new ConversionModule();
        graph.inject(c);

        assertEquals(3425L, (long) c.numberLong);
    }

    @Module
    public static class MyModule {
        String name;
        @Provides static MyModule myModule(
                @Param String name,
                @MultiBind Provider<List<ProvisionStrategyDecorator>> provider,
                @MultiBind Ref<List<ProvisionStrategyDecorator>> ref) {
            MyModule m = new MyModule();
            m.name = name;
            return m;
        }
    }

    @Module(dependsOn = MyModule.class)
    public interface MyComponent {

        MyModule getMyModule(@Param String name);

        public interface Factory {
            MyComponent create();
        }
    }

    @Test
    public void testComponent() {
        MyComponent component = Component
                .builder()
                .build(MyComponent.Factory.class)
                .create();
        assertEquals("sweet", component.getMyModule("sweet").name);
    }



}
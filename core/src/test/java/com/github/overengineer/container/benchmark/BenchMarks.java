package com.github.overengineer.container.benchmark;

import com.github.overengineer.container.*;
import com.github.overengineer.container.key.Key;
import com.github.overengineer.container.key.Locksmith;
import com.github.overengineer.container.key.Qualifier;
import com.github.overengineer.container.testutil.ConcurrentExecutionAssistant;
import com.google.inject.Guice;
import com.google.inject.Injector;
import dagger.ObjectGraph;
import se.jbee.inject.Dependency;
import se.jbee.inject.bootstrap.Bootstrap;

import org.junit.Test;


/**
 */
public class BenchMarks {

    int threads = 4;
    long duration = 5000;
    long primingRuns = 100000;

    @Test
    public void testContainerCreationSpeed() throws Exception {

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                Gunmetal.jsr330().load(new BenchMarkModule()).get(AA.class);
            }
        }, threads).run(duration, primingRuns, "my container creation");

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                ObjectGraph.create(new DaggerSlimBenchMarkModule()).get(AA.class);
            }
        }, threads).run(duration, primingRuns, "dagger slim container creation");

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                ObjectGraph.create(new DaggerBenchMarkModule()).get(AA.class);
            }
        }, threads).run(duration, primingRuns, "dagger container creation");


        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                Bootstrap.injector(SilkBenchMarkModule.class).resolve(Dependency.dependency(AA.class));
            }
        }, threads).run(duration, primingRuns, "silk container creation");

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                new PicoBenchMarkModule().get().getComponent(AA.class);
            }
        }, threads).run(duration, primingRuns, "pico container creation");

        /*
        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                Guice.createInjector(new GuiceBenchMarkModule()).getInstance(AA.class);
            }
        }, threads).run(duration, primingRuns, "guice container creation");
        */

    }

    @Test
    public void testPrototypeSpeed() throws Exception {

        final Key<R> key = Locksmith.makeKey(R.class, Qualifier.NONE);

        final Container container = Gunmetal.jsr330().load(new BenchMarkModule());

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                container.get(key);
            }
        }, threads).run(duration, primingRuns, "my prototype creation");

        final ObjectGraph objectGraph = ObjectGraph.create(new DaggerBenchMarkModule());

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                objectGraph.get(R.class);
            }
        }, threads).run(duration, primingRuns, "dagger prototype creation");

        final Injector injector = Guice.createInjector(new GuiceBenchMarkModule());

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                injector.getInstance(R.class);
            }
        }, threads).run(duration, primingRuns, "guice prototype creation");

    }

    @Test
    public void testSingletonSpeed() throws Exception {

        final Key<E> key = Locksmith.makeKey(E.class, Qualifier.NONE);

        final Container container = Gunmetal.jsr330().load(new BenchMarkModule());

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                container.get(key);
            }
        }, threads).run(duration, primingRuns, "my singleton");

        final ObjectGraph objectGraph = ObjectGraph.create(new DaggerBenchMarkModule());

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                objectGraph.get(E.class);
            }
        }, threads).run(duration, primingRuns, "dagger singleton");

        final Injector injector = Guice.createInjector(new GuiceBenchMarkModule());

        new ConcurrentExecutionAssistant.TestThreadGroup(new ConcurrentExecutionAssistant.Execution() {
            @Override
            public void execute() {
                injector.getInstance(E.class);
            }
        }, threads).run(duration, primingRuns, "guice singleton");

    }

}
package com.github.overengineer.container.benchmark;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author rees.byars
 */
@Singleton
public class E {
    @Inject
    public E(F f) { }
}
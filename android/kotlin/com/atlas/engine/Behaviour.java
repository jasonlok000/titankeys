package com.atlas.engine;

/** Lets the benchmark run the same touches through the original Atlas behaviour. Only the benchmark build sets it. */
public final class Behaviour {
    public static volatile boolean atlas;

    private Behaviour() {}
}

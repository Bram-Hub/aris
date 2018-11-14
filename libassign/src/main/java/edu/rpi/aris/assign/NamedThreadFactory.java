package edu.rpi.aris.assign;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory implements ThreadFactory {

    private final String name;
    private final boolean isDaemon;
    private int threadNum = 0;

    public NamedThreadFactory(String name, boolean isDaemon) {
        this.name = name;
        this.isDaemon = isDaemon;
    }

    public NamedThreadFactory(String name) {
        this(name, false);
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread t;
        synchronized (this) {
            t = new Thread(r, name + " " + threadNum++);
        }
        t.setDaemon(isDaemon);
        return t;
    }
}

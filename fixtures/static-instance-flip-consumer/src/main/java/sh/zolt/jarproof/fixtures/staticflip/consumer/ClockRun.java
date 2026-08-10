package sh.zolt.jarproof.fixtures.staticflip.consumer;

import sh.zolt.jarproof.fixtures.staticflip.ClockSource;

/** Calls the accessor statically, which is only correct against version 1. */
public final class ClockRun {
    public static void main(String[] arguments) {
        System.out.println(ClockSource.label());
    }
}

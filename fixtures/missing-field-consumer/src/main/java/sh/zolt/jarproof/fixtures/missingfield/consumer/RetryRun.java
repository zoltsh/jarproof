package sh.zolt.jarproof.fixtures.missingfield.consumer;

import sh.zolt.jarproof.fixtures.missingfield.RetryBudget;

/** Reads the static field version 2 removes. */
public final class RetryRun {
    public static void main(String[] arguments) {
        System.out.println(RetryBudget.maximumAttempts + new RetryBudget().floor());
    }
}

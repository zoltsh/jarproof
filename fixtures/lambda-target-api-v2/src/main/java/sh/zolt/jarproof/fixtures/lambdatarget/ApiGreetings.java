package sh.zolt.jarproof.fixtures.lambdatarget;

/**
 * Version 2 removes the formal greeting, so the consumer's invokedynamic can only fail when its
 * bootstrap method handle argument is resolved.
 */
public final class ApiGreetings {
    public static String casual() {
        return "Hi";
    }
}

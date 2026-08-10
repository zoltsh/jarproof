package sh.zolt.jarproof.fixtures.lambdatarget.consumer;

import java.util.function.Supplier;
import sh.zolt.jarproof.fixtures.lambdatarget.ApiGreetings;

/**
 * The method reference compiles to an invokedynamic whose bootstrap arguments name the target
 * method, so the reference only exists inside the LambdaMetafactory call site.
 */
public final class GreetingRun {
    public static void main(String[] arguments) {
        Supplier<String> greeting = ApiGreetings::formal;
        System.out.println(greeting.get());
    }
}

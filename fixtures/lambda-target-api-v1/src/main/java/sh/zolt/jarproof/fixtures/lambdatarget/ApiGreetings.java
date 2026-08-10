package sh.zolt.jarproof.fixtures.lambdatarget;

/** Version 1 declares both greetings; the formal one is a method-reference target. */
public final class ApiGreetings {
    public static String formal() {
        return "Good day";
    }

    public static String casual() {
        return "Hi";
    }
}

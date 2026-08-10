package sh.zolt.jarproof.fixtures.missingmethod.consumer;

import sh.zolt.jarproof.fixtures.missingmethod.OrderPolicy;

/** Compiles against v1 and calls the removed method, so v2 fails at the invokevirtual site. */
public final class OrderReport {
    public static void main(String[] arguments) {
        OrderPolicy policy = new OrderPolicy();
        System.out.println(policy.describe("orders", policy.limit()));
    }
}

package sh.zolt.jarproof.fixtures.inaccessible.consumer;

import sh.zolt.jarproof.fixtures.inaccessible.SecretVault;

/** Lives in a different runtime package, which is what makes the v2 narrowing fatal. */
public final class VaultRun {
    public static void main(String[] arguments) {
        System.out.println(new SecretVault().read());
    }
}

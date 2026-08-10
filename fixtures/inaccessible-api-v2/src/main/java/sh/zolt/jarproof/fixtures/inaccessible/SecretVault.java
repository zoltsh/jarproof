package sh.zolt.jarproof.fixtures.inaccessible;

/**
 * Version 2 narrows the same class to package-private, so class resolution from another runtime
 * package fails with IllegalAccessError.
 */
final class SecretVault {
    public String read() {
        return "closed vault";
    }
}

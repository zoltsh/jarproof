package sh.zolt.jarproof.fixtures.inaccessible;

/** Version 1 is public, so a consumer in another package may reference it. */
public final class SecretVault {
    public String read() {
        return "open vault";
    }
}

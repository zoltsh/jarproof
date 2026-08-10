package sh.zolt.jarproof.fixtures.badservice;

/** Listed as a provider but implements nothing, so the service type does not match (JP4002). */
public final class UnrelatedCodec {
    public String encode(String value) {
        return value;
    }
}

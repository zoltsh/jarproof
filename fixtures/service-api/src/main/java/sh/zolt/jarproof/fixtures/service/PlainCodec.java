package sh.zolt.jarproof.fixtures.service;

/** A correct provider: public, concrete, and with an implicit public no-argument constructor. */
public final class PlainCodec implements Codec {
    @Override
    public String encode(String value) {
        return value;
    }
}

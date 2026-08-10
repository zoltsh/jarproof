package sh.zolt.jarproof.fixtures.badservice;

import sh.zolt.jarproof.fixtures.service.Codec;

/** Implements the service type but hides its only constructor, so it is not instantiable (JP4005). */
public final class PrivateCodec implements Codec {
    private PrivateCodec() {
    }

    @Override
    public String encode(String value) {
        return value;
    }
}

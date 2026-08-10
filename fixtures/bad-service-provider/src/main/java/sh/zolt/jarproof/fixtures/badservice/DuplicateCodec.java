package sh.zolt.jarproof.fixtures.badservice;

import sh.zolt.jarproof.fixtures.service.Codec;

/** A perfectly valid provider; the fault is that the services file lists it twice (JP4004). */
public final class DuplicateCodec implements Codec {
    @Override
    public String encode(String value) {
        return value;
    }
}

package sh.zolt.jarproof.fixtures.badservice;

import sh.zolt.jarproof.fixtures.service.Codec;

/** Implements the service type but is abstract, so it cannot be instantiated (JP4005). */
public abstract class AbstractCodec implements Codec {
}

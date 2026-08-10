package sh.zolt.jarproof.fixtures.methodhandle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A signature-polymorphic call site: {@code invokeExact} is compiled against a descriptor no
 * declared method matches, so descriptor-literal member resolution would report a missing method.
 * This member must produce no findings.
 */
public final class PolymorphicCall {
    public String shout(String value) throws Throwable {
        MethodHandle handle = MethodHandles.lookup()
                .findVirtual(String.class, "toUpperCase", MethodType.methodType(String.class));
        return (String) handle.invokeExact(value);
    }
}

package sh.zolt.jarproof.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Whether naming one type makes a method a linkage probe: code whose author has declared, in bytecode,
 * that a reference below it may fail to resolve.
 *
 * <p>This is the one rule that lets reachability be quieter than the truth, and it is the rule DESIGN
 * section 4's whole problem needs. A mature library reaches an absent optional integration through a
 * method that catches {@code LinkageError} and moves on: Netty tries its SLF4J, Log4J2, and Log4J
 * back ends exactly this way, and the JVM really does resolve, really does fail, and really is caught.
 * Reachability alone therefore cannot call that path healthy — the path executes. What settles it is the
 * handler, which is evidence in the artifact rather than a guess about intent, so the analysis follows
 * no edge out of a probe while still judging the probe's own references.
 *
 * <p>The set of guarding types is derived rather than listed: a type guards when {@code LinkageError} is
 * assignable to it, which is to say when the type is {@code LinkageError} or something below it —
 * {@code NoClassDefFoundError}, {@code NoSuchMethodError}, {@code ExceptionInInitializerError}, and
 * anything a library declares beneath them. {@code Throwable} and {@code Error} are deliberately
 * excluded even though both would catch a linkage failure at run time: catching either is ordinary
 * defensive code around ordinary logic, and treating it as a probe would silence far more than the
 * optional-dependency pattern this rule exists for. Naming {@code LinkageError} itself is not ordinary.
 *
 * <p>The reference index records a catch type the same way it records any other named class, so a method
 * that constructs or tests a {@code LinkageError} rather than catching one is treated as a probe too.
 * That over-approximates in the quiet direction, which is the direction this mode is defined to err in.
 */
final class ReachableGuard {
    private static final String LINKAGE_ERROR = "java/lang/LinkageError";

    private final ResolutionTable table;
    private final Map<String, Boolean> decided = new HashMap<>();

    ReachableGuard(ResolutionTable table) {
        this.table = table;
    }

    /**
     * Returns whether a method naming this type is a linkage probe.
     *
     * <p>Answers are remembered per name, because one hierarchy walk per distinct catch type is cheap
     * and one per reference is not.
     *
     * @param internalName a class the bytecode of some method named
     * @return whether a linkage failure could be caught by it
     */
    boolean absorbsLinkageFailure(String internalName) {
        return decided.computeIfAbsent(internalName, this::describesLinkageFailure);
    }

    private boolean describesLinkageFailure(String internalName) {
        return table.find(internalName).stream()
                .flatMap(type -> table.classChain(type).stream())
                .anyMatch(above -> LINKAGE_ERROR.equals(above.internalName()));
    }
}

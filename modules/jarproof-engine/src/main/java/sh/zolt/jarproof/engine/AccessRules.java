package sh.zolt.jarproof.engine;

/**
 * Whether a referencing class is allowed to see what it resolved, per JVMS §5.4.4.
 *
 * <p>Access is part of resolution, not a separate courtesy check: a class or member that exists and
 * cannot be seen fails the reference just as surely as one that is absent, and it fails with a
 * different throwable, which is why the two are reported apart.
 *
 * <p><strong>Run-time packages.</strong> Two classes share a run-time package when they share both a
 * package name and a defining source. Jarproof's 0.1 model is one flat classpath, so for classpath
 * classes the package name settles it. A class the target runtime supplies is never in a classpath
 * class's run-time package, and the bundled runtime profile describes exported API only, so a runtime
 * class is reachable exactly when it is public. That is the strong-encapsulation model stated once
 * here: everything the profile omits is unreachable rather than package-private.
 *
 * <p><strong>Nestmates.</strong> A class's nest host is its {@code NestHost} target when that host
 * lists the class in {@code NestMembers}, and otherwise the class itself. Two classes are nestmates
 * when they agree on a host. Both attributes have to be honoured: judging JDK 11 and later bytecode by
 * pre-nestmate rules turns every ordinary outer-class-to-nested-class private access into a reported
 * failure, which is the single largest source of false diagnostics in this area.
 *
 * <p><strong>Deliberately not modelled.</strong> For a protected instance member, JVMS §5.4.4 adds that
 * the receiver type must itself be the referencing class or a subclass of it. That refinement is the
 * verifier's, needs the operand stack this analysis does not track, and can only narrow what is
 * allowed — so a reference the declared rule accepts is reported as fine.
 */
final class AccessRules {
    private AccessRules() {
    }

    /**
     * Returns whether the referencing class may see a resolved class.
     *
     * @param resolved the class the reference resolved to
     * @param referencing the class whose bytecode holds the reference
     * @return whether resolution succeeds rather than failing on access
     */
    static boolean classAccessible(ResolvedClass resolved, ResolvedClass referencing) {
        if (resolved.isPublic()) {
            return true;
        }
        if (resolved.fromPlatform()) {
            return false;
        }
        return samePackage(resolved, referencing);
    }

    /**
     * Returns whether the referencing class may see a resolved member.
     *
     * @param table the resolution table, used to walk nests and superclasses
     * @param resolved the member and the class that declares it
     * @param referencing the class whose bytecode holds the reference
     * @return whether resolution succeeds rather than failing on access
     */
    static boolean memberAccessible(ResolutionTable table, ResolvedMember resolved, ResolvedClass referencing) {
        ResolvedClass declaring = resolved.owner();
        if (resolved.isPublic()) {
            return true;
        }
        if (resolved.isPrivate()) {
            return nestmates(table, declaring, referencing);
        }
        if (samePackage(declaring, referencing)) {
            return true;
        }
        return resolved.isProtected() && subclasses(table, referencing, declaring);
    }

    private static boolean samePackage(ResolvedClass one, ResolvedClass other) {
        return one.packageName().equals(other.packageName());
    }

    private static boolean nestmates(ResolutionTable table, ResolvedClass declaring, ResolvedClass referencing) {
        return nestHost(table, declaring).equals(nestHost(table, referencing));
    }

    private static String nestHost(ResolutionTable table, ResolvedClass member) {
        String claimed = member.shape().nestHostInternalName().orElse(member.internalName());
        if (claimed.equals(member.internalName())) {
            return member.internalName();
        }
        return table.find(claimed)
                .filter(host -> host.shape().nestMemberInternalNames().contains(member.internalName()))
                .map(ResolvedClass::internalName)
                .orElse(member.internalName());
    }

    private static boolean subclasses(ResolutionTable table, ResolvedClass candidate, ResolvedClass ancestor) {
        return table.classChain(candidate).stream()
                .anyMatch(above -> above.internalName().equals(ancestor.internalName()));
    }
}

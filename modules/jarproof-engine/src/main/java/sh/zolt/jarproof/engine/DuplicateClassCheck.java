package sh.zolt.jarproof.engine;

import java.util.ArrayList;
import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Reports class internal names that more than one artifact claims.
 *
 * <p>Three outcomes, in order of how much they cost the reader. Copies that hash the same are only
 * waste. Copies that differ mean the classpath order decides behaviour, which is worth a warning.
 * Copies that differ and arrived through the same classpath wildcard mean nothing decides behaviour
 * at all, because the launcher never promised an expansion order — that is the one that has to be an
 * error, and it replaces the plain differing-duplicate report rather than joining it.
 */
final class DuplicateClassCheck {
    private static final FindingCode IDENTICAL = FindingCode.of("JP2001");
    private static final FindingCode DIFFERING = FindingCode.of("JP2002");
    private static final FindingCode UNPREDICTABLE = FindingCode.of("JP2006");

    private DuplicateClassCheck() {
    }

    /**
     * Runs the duplicate-class checks.
     *
     * @param catalog the read classpath
     * @return one finding per duplicated class internal name
     */
    static List<Finding> run(ArtifactCatalog catalog) {
        List<Finding> findings = new ArrayList<>();
        for (List<ClassDeclaration> claims : catalog.declarations().values()) {
            if (claims.size() > 1) {
                findings.add(finding(claims));
            }
        }
        return List.copyOf(findings);
    }

    private static Finding finding(List<ClassDeclaration> claims) {
        ClassDeclaration winner = claims.get(0);
        if (hasIdenticalBytecode(claims)) {
            return identicalBytecode(winner, claims);
        }
        if (sharesWildcardWithLoser(winner, claims)) {
            return unpredictableWinner(winner, claims);
        }
        return differingBytecode(winner, claims);
    }

    private static boolean hasIdenticalBytecode(List<ClassDeclaration> claims) {
        return claims.stream().map(ClassDeclaration::digest).distinct().count() == 1;
    }

    private static boolean sharesWildcardWithLoser(ClassDeclaration winner, List<ClassDeclaration> claims) {
        return winner.wildcardSource().isPresent()
                && claims.stream().skip(1).anyMatch(loser -> loser.wildcardSource().equals(winner.wildcardSource())
                        && !loser.digest().equals(winner.digest()));
    }

    private static Finding identicalBytecode(ClassDeclaration winner, List<ClassDeclaration> claims) {
        return new Finding(
                IDENTICAL,
                Severity.INFO,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(winner.artifactPath(), winner.entryName()),
                winner.internalName(),
                "duplicate class with identical bytecode",
                "More than one artifact on the effective classpath declares this class, and every copy"
                        + " hashes the same, so whichever copy the runtime reaches behaves exactly like the"
                        + " others. This is waste rather than a hazard.",
                declarations(claims),
                List.of(new Remediation("Drop the redundant copies so each class has exactly one home.")));
    }

    private static Finding differingBytecode(ClassDeclaration winner, List<ClassDeclaration> claims) {
        return new Finding(
                DIFFERING,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(winner.artifactPath(), winner.entryName()),
                winner.internalName(),
                "duplicate class with differing bytecode",
                "Several artifacts declare this class with different bytecode. The first artifact on the"
                        + " effective classpath wins outright and every other definition is invisible, so code"
                        + " compiled against a losing definition can fail against the winning one.",
                declarations(claims),
                List.of(new Remediation(
                        "Exclude or align the artifacts so exactly one definition of this class survives.")));
    }

    private static Finding unpredictableWinner(ClassDeclaration winner, List<ClassDeclaration> claims) {
        return new Finding(
                UNPREDICTABLE,
                Severity.ERROR,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(winner.artifactPath(), winner.entryName()),
                winner.internalName(),
                "duplicate winner depends on wildcard expansion order",
                "The winning definition and a differing one both arrived from the same classpath wildcard."
                        + " The java launcher leaves wildcard expansion order unspecified, so the JVM may pick"
                        + " either copy on any given run and no analysis can promise which one it will be.",
                wildcardDeclarations(winner, claims),
                List.of(new Remediation("Name the conflicting artifacts explicitly instead of leaning on a"
                        + " wildcard, so the classpath itself settles which definition wins.")));
    }

    private static List<Evidence> declarations(List<ClassDeclaration> claims) {
        return claims.stream()
                .map(claim -> new Evidence(claim.artifactPath() + " declares " + claim.entryName()
                        + " with digest " + claim.declared().shortDigest()))
                .toList();
    }

    private static List<Evidence> wildcardDeclarations(ClassDeclaration winner, List<ClassDeclaration> claims) {
        List<Evidence> evidence = new ArrayList<>(declarations(claims));
        evidence.add(new Evidence("the winner and at least one differing definition were expanded from the"
                + " classpath wildcard " + winner.wildcardSource().orElseThrow()));
        return List.copyOf(evidence);
    }
}

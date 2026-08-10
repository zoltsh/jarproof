package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;
import sh.zolt.jarproof.api.Evidence;
import sh.zolt.jarproof.api.Finding;
import sh.zolt.jarproof.api.FindingCode;
import sh.zolt.jarproof.api.PredictedError;
import sh.zolt.jarproof.api.Remediation;
import sh.zolt.jarproof.api.Severity;

/**
 * Builds the diagnostics for an application archive whose nested layout does not hold together.
 *
 * <p>Every one of them is located on the outer artifact the caller supplied, because that is the file
 * somebody has to rebuild, and the offending entry is the subject. Severity splits on whether the
 * launcher can still assemble the classpath the archive describes: an index naming a library that is
 * not there and a library that nests archives of its own are both breaks in the layout itself, while a
 * compressed library still reads — jarproof reads it — and is evidence that the archive was assembled
 * by something other than a launcher-aware packager.
 */
final class NestedArchiveFinding {
    private static final FindingCode CODE = FindingCode.of("JP3006");
    private static final String SUMMARY = "invalid nested application archive layout";

    private NestedArchiveFinding() {
    }

    /**
     * Reports an index entry naming a nested library the archive does not hold.
     *
     * @param artifact artifact path text as the caller supplied it
     * @param indexedPath the path the index names
     * @return the finding
     */
    static Finding absentIndexEntry(String artifact, String indexedPath) {
        return new Finding(
                CODE,
                Severity.ERROR,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(artifact),
                indexedPath,
                SUMMARY,
                "The classpath index of an application archive names the libraries its launcher puts on"
                        + " the classpath, in order. This one names a library the archive does not hold, so"
                        + " the classpath the archive describes is not the classpath it can build, and"
                        + " whichever classes that library was carrying are simply absent at run time.",
                List.of(new Evidence("the classpath index names it, and no nested library entry matches")),
                List.of(new Remediation("Repackage the application archive so its index and its nested"
                        + " libraries agree, rather than editing either one by hand.")));
    }

    /**
     * Reports a nested library that carries archives of its own.
     *
     * @param position where the library sits inside the application archive
     * @param nestedPath the archive entry found inside that library
     * @return the finding
     */
    static Finding deeplyNested(NestedPosition position, String nestedPath) {
        return new Finding(
                CODE,
                Severity.ERROR,
                PredictedError.NONE,
                ArtifactLocation.ofClassEntry(position.outerDisplay(), position.path()),
                nestedPath,
                SUMMARY,
                "A launcher reads the libraries nested one level inside an application archive. This"
                        + " library nests an archive of its own, which no launcher unpacks and jarproof"
                        + " deliberately does not descend into: whatever classes that archive holds are"
                        + " invisible to the classpath, so verifying past this point would be a guess.",
                List.of(new Evidence("nesting is supported one level deep, and this entry sits two deep")),
                List.of(new Remediation("Flatten the dependency into the library directory of the"
                        + " application archive instead of nesting it inside another library.")));
    }

    /**
     * Reports a nested library the archive compressed rather than stored.
     *
     * @param artifact artifact path text as the caller supplied it
     * @param libraryPath the compressed library's entry name
     * @return the finding
     */
    static Finding compressedLibrary(String artifact, String libraryPath) {
        return new Finding(
                CODE,
                Severity.WARNING,
                PredictedError.NONE,
                ArtifactLocation.ofArtifact(artifact),
                libraryPath,
                SUMMARY,
                "A launcher reads a nested library in place, which it can only do while the entry is"
                        + " stored rather than compressed. This entry is compressed, so a launcher that"
                        + " expects to address it directly cannot, even though jarproof still reads it and"
                        + " every other finding about it holds.",
                List.of(new Evidence("this entry is compressed, and nested libraries are read in place")),
                List.of(new Remediation("Store nested library entries uncompressed, which is what an"
                        + " application-archive packager does by default.")));
    }
}

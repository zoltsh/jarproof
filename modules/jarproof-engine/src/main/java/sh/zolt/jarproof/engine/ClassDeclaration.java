package sh.zolt.jarproof.engine;

import java.util.Optional;

/** One artifact's claim on one class internal name, which is what makes a duplicate a duplicate. */
record ClassDeclaration(ClasspathEntry artifact, IndexedClass declared) {
    /** Returns the artifact path text a report shows. */
    String artifactPath() {
        return artifact.display();
    }

    /** Returns the entry the bytes came from inside that artifact. */
    String entryName() {
        return declared.entryName();
    }

    /** Returns the class internal name this declaration claims. */
    String internalName() {
        return declared.internalName();
    }

    /** Returns the full digest of the declared bytes. */
    String digest() {
        return declared.digest();
    }

    /** Returns the wildcard this declaration's artifact was expanded from, when it was. */
    Optional<String> wildcardSource() {
        return artifact.wildcardSource();
    }
}

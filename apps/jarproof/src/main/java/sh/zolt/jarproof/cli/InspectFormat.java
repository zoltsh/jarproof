package sh.zolt.jarproof.cli;

import sh.zolt.jarproof.api.ArtifactSummary;

/**
 * The {@code --format} vocabulary of {@code inspect}, and the one place a summary becomes text.
 *
 * <p>Inspect has its own vocabulary rather than borrowing the report one because it genuinely offers
 * less: SARIF describes findings, and an inspection produces none. Widening the report vocabulary to
 * cover a format one command cannot honour would push that gap into a runtime failure, where a
 * caller finds it, instead of into the flag, where the parser does.
 */
enum InspectFormat {
    /** An aligned table of facts for a person. */
    HUMAN,

    /** The same facts as JSON, whose member set and order are a versioned contract. */
    JSON;

    /**
     * Returns whether this format reports the artifact path relative to {@code --path-root}.
     *
     * <p>The same split the report formats make, for the same reason: a person reads the path they
     * typed, and a machine consumer reads one that does not change with the checkout directory.
     *
     * @return whether the artifact path is measured from the path root
     */
    boolean rootsArtifactPaths() {
        return this != HUMAN;
    }

    /**
     * Renders one artifact's facts in this format.
     *
     * @param summary the facts the inspection read
     * @return the complete document, terminated by a single LF
     */
    String render(ArtifactSummary summary) {
        return switch (this) {
            case HUMAN -> ArtifactFacts.render(summary);
            case JSON -> ArtifactJson.render(summary);
        };
    }
}

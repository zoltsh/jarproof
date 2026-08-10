package sh.zolt.jarproof.engine;

/**
 * The publication identity an artifact claims for itself.
 *
 * <p>Maven metadata carries all three parts. A manifest carries only a title and a version, so the
 * group is empty and {@link #name()} then opens with the separator — that visible gap is deliberate,
 * because a manifest-derived identity is a weaker claim than a published coordinate.
 */
record ArtifactCoordinate(String group, String artifact, String version) {
    /** Returns the group and artifact joined the way a coordinate is normally written. */
    String name() {
        return group + ":" + artifact;
    }
}

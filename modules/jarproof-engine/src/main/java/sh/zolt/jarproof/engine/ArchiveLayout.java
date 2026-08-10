package sh.zolt.jarproof.engine;

import java.util.Optional;

/**
 * The naming vocabulary of a Java artifact: the entry names the runtime recognises inside an
 * archive or class directory, and the derivations that turn one into a class internal name.
 *
 * <p>Every string the engine matches against an archive entry lives here so the rules stay in one
 * reviewable place instead of being restated at each use.
 */
final class ArchiveLayout {
    /** Suffix of an entry that holds one compiled class. */
    static final String CLASS_SUFFIX = ".class";

    /** Prefix of the multi-release area, whose next path segment names a Java release. */
    static final String VERSIONS_PREFIX = "META-INF/versions/";

    /** Prefix of the Maven metadata area that carries publication coordinates. */
    static final String MAVEN_PREFIX = "META-INF/maven/";

    /** File name Maven writes its coordinate properties into. */
    static final String POM_PROPERTIES_NAME = "pom.properties";

    /** Lower-case archive suffix a classpath wildcard expands to. */
    static final String JAR_SUFFIX = ".jar";

    /** Upper-case archive suffix a classpath wildcard expands to. */
    static final String UPPERCASE_JAR_SUFFIX = ".JAR";

    /** Final path segment that marks a classpath entry as a wildcard. */
    static final String WILDCARD_NAME = "*";

    private ArchiveLayout() {
    }

    /** Returns whether a wildcard expansion accepts this file name. */
    static boolean isExpandableArchive(String fileName) {
        return fileName.endsWith(JAR_SUFFIX) || fileName.endsWith(UPPERCASE_JAR_SUFFIX);
    }

    /**
     * Converts a base class entry name into the class internal name the runtime looks it up by.
     *
     * @param baseEntryName entry name with the multi-release prefix already removed
     * @return the internal name, such as {@code com/acme/orders/OrderValidator}
     */
    static String internalName(String baseEntryName) {
        return baseEntryName.substring(0, baseEntryName.length() - CLASS_SUFFIX.length());
    }

    /**
     * Returns the package prefix of a class internal name.
     *
     * @param internalName class internal name
     * @return the package internal prefix, or empty for the unnamed package
     */
    static Optional<String> packageOf(String internalName) {
        int separator = internalName.lastIndexOf('/');
        return separator <= 0 ? Optional.empty() : Optional.of(internalName.substring(0, separator));
    }
}

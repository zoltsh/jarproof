package sh.zolt.jarproof.engine;

import java.util.List;
import sh.zolt.jarproof.api.ArtifactLocation;

/**
 * One service configuration file as one artifact presents it: the artifact that carries it, the
 * service its file name registers providers for, and the provider lines it holds in file order.
 *
 * <p>{@code artifact} is the classpath entry's display text, so a diagnostic points at the artifact
 * whose resource has to be edited rather than at the artifact that happens to declare the provider
 * class. Two artifacts registering providers for one service are two declarations, because they are
 * two files with two independent contents.
 */
record ServiceDeclaration(String artifact, String serviceName, List<ServiceProviderEntry> providers) {
    /** Directory every service configuration file the runtime consults lives directly inside. */
    static final String RESOURCE_PREFIX = "META-INF/services/";

    ServiceDeclaration {
        providers = List.copyOf(providers);
    }

    /** Returns the archive-internal entry name this file occupies. */
    String resourceEntryName() {
        return RESOURCE_PREFIX + serviceName;
    }

    /** Returns the internal name of the service type this file registers providers for. */
    String serviceInternalName() {
        return ServiceBinaryName.internalName(serviceName);
    }

    /** Returns the location every finding about this file reports. */
    ArtifactLocation location() {
        return ArtifactLocation.ofClassEntry(artifact, resourceEntryName());
    }
}

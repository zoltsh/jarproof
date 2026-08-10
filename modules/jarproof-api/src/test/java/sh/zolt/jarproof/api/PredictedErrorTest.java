package sh.zolt.jarproof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PredictedErrorTest {
    private static final Map<PredictedError, String> DOCUMENTED_NAMES = Map.of(
            PredictedError.NONE, "None",
            PredictedError.NO_CLASS_DEF_FOUND_ERROR, "NoClassDefFoundError",
            PredictedError.NO_SUCH_METHOD_ERROR, "NoSuchMethodError",
            PredictedError.NO_SUCH_FIELD_ERROR, "NoSuchFieldError",
            PredictedError.INCOMPATIBLE_CLASS_CHANGE_ERROR, "IncompatibleClassChangeError",
            PredictedError.ILLEGAL_ACCESS_ERROR, "IllegalAccessError",
            PredictedError.ABSTRACT_METHOD_ERROR, "AbstractMethodError",
            PredictedError.UNSUPPORTED_CLASS_VERSION_ERROR, "UnsupportedClassVersionError",
            PredictedError.SERVICE_CONFIGURATION_ERROR, "ServiceConfigurationError");

    @Test
    void everyDocumentedNameRoundTripsToItsCanonicalInstance() {
        for (Map.Entry<PredictedError, String> documented : DOCUMENTED_NAMES.entrySet()) {
            assertEquals(documented.getValue(), documented.getKey().value());
            assertEquals(documented.getValue(), documented.getKey().toString());
            assertSame(documented.getKey(), PredictedError.of(documented.getValue()));
        }
    }

    @Test
    void rejectsNamesOutsideTheDocumentedSet() {
        assertThrows(IllegalArgumentException.class, () -> PredictedError.of("OutOfMemoryError"));
        assertThrows(IllegalArgumentException.class, () -> PredictedError.of(""));
        assertThrows(NullPointerException.class, () -> PredictedError.of(null));
    }

    @Test
    void comparesAndOrdersByCarriedName() {
        assertEquals(0, PredictedError.NONE.compareTo(PredictedError.of("None")));
        assertTrue(PredictedError.ABSTRACT_METHOD_ERROR.compareTo(PredictedError.NO_SUCH_METHOD_ERROR) < 0);
        assertThrows(NullPointerException.class, () -> PredictedError.NONE.compareTo(null));
    }

    @Test
    void equalityFollowsTheCarriedNameAlone() {
        PredictedError missing = PredictedError.of("NoSuchMethodError");

        assertEquals(PredictedError.NO_SUCH_METHOD_ERROR, missing);
        assertEquals(PredictedError.NO_SUCH_METHOD_ERROR.hashCode(), missing.hashCode());
        assertNotEquals(PredictedError.NO_SUCH_METHOD_ERROR, PredictedError.NO_SUCH_FIELD_ERROR);
        assertNotEquals(PredictedError.NONE, new Object());
    }

    @Test
    void sortsDeterministically() {
        List<PredictedError> sorted = List.of(
                        PredictedError.SERVICE_CONFIGURATION_ERROR,
                        PredictedError.ABSTRACT_METHOD_ERROR,
                        PredictedError.NONE)
                .stream()
                .sorted()
                .toList();

        assertEquals(
                List.of(
                        PredictedError.ABSTRACT_METHOD_ERROR,
                        PredictedError.NONE,
                        PredictedError.SERVICE_CONFIGURATION_ERROR),
                sorted);
    }
}

package sh.zolt.jarproof.fixtures.nestmates;

/**
 * Legitimate nest-based access: the outer class reads a private field, calls a private method, and
 * invokes the private constructor of its nested class. On 11+ bytecode javac emits those references
 * directly and relies on the NestHost/NestMembers attributes instead of synthetic bridges, so a
 * verifier applying pre-11 access rules would manufacture JP1007 findings here. This member must
 * produce none.
 */
public final class NestedLedger {
    public String read() {
        Entry entry = new Entry();
        return entry.secret + " " + entry.reveal();
    }

    private static final class Entry {
        // Deliberately not final: a final String initialized with a literal is a constant variable,
        // which javac folds into the caller instead of emitting the getfield this fixture needs.
        private String secret = "nest";

        private Entry() {
        }

        private String reveal() {
            return "mate";
        }
    }
}

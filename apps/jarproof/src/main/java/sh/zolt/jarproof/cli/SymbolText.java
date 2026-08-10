package sh.zolt.jarproof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a finding's subject the way a compiler would print it.
 *
 * <p>Subjects are canonical bytecode symbols so that baselines stay stable: a class arrives as an
 * internal name, and a member arrives as {@code owner#name(descriptor)returnType}. Reading that in
 * a terminal is work, so the human report turns slashes into dots, joins the owner and the member
 * with a dot, and renders parameter descriptors as simple type names. Machine output keeps the
 * canonical form untouched.
 *
 * <p>Nothing here validates the symbol. Anything that does not parse is passed through with its
 * slashes replaced, because an unreadable diagnostic is still better than a crashed report.
 */
final class SymbolText {
    private static final String PRIMITIVE_CODES = "ZBCSIJFDV";
    private static final List<String> PRIMITIVE_NAMES =
            List.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");
    private static final String PARAMETER_SEPARATOR = ", ";
    private static final String ARRAY_BRACKETS = "[]";
    private static final String OBJECT_PREFIX = "L";

    private SymbolText() {
    }

    /**
     * Renders a canonical subject symbol for human reading.
     *
     * @param symbol a class internal name, or a member reference of the form
     *     {@code owner#name(descriptor)returnType}
     * @return the readable form
     */
    static String readable(String symbol) {
        int separator = symbol.indexOf('#');
        if (separator < 0) {
            return typeName(symbol);
        }
        return typeName(symbol.substring(0, separator)) + '.' + memberName(symbol.substring(separator + 1));
    }

    private static String typeName(String internalName) {
        return internalName.replace('/', '.');
    }

    private static String memberName(String member) {
        int open = member.indexOf('(');
        int close = member.lastIndexOf(')');
        if (open < 0 || close < open) {
            return typeName(member);
        }
        return member.substring(0, open) + '(' + parameterNames(member.substring(open + 1, close)) + ')';
    }

    private static String parameterNames(String descriptors) {
        List<String> names = new ArrayList<>();
        int index = 0;
        while (index < descriptors.length()) {
            int end = descriptorEnd(descriptors, index);
            names.add(typeText(descriptors.substring(index, end)));
            index = end;
        }
        return String.join(PARAMETER_SEPARATOR, names);
    }

    private static int descriptorEnd(String descriptors, int start) {
        int index = start;
        while (index < descriptors.length() && descriptors.charAt(index) == '[') {
            index++;
        }
        if (index < descriptors.length() && descriptors.charAt(index) == 'L') {
            int terminator = descriptors.indexOf(';', index);
            return terminator < 0 ? descriptors.length() : terminator + 1;
        }
        return Math.min(index + 1, descriptors.length());
    }

    private static String typeText(String descriptor) {
        int dimensions = 0;
        while (dimensions < descriptor.length() && descriptor.charAt(dimensions) == '[') {
            dimensions++;
        }
        return elementText(descriptor.substring(dimensions)) + ARRAY_BRACKETS.repeat(dimensions);
    }

    private static String elementText(String descriptor) {
        if (descriptor.length() == 1) {
            return primitiveText(descriptor.charAt(0));
        }
        if (descriptor.startsWith(OBJECT_PREFIX)) {
            int end = descriptor.charAt(descriptor.length() - 1) == ';' ? descriptor.length() - 1 : descriptor.length();
            return simpleName(descriptor.substring(1, end));
        }
        return descriptor;
    }

    private static String primitiveText(char code) {
        int index = PRIMITIVE_CODES.indexOf(code);
        return index < 0 ? String.valueOf(code) : PRIMITIVE_NAMES.get(index);
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }
}

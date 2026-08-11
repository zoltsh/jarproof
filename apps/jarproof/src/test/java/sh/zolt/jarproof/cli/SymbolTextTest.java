package sh.zolt.jarproof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SymbolTextTest {
    @Test
    void rendersAClassInternalNameAsADottedName() {
        assertEquals("com.acme.orders.OrderValidator", SymbolText.readable("com/acme/orders/OrderValidator"));
    }

    @Test
    void rendersAMethodReferenceTheWayACompilerPrintsIt() {
        assertEquals(
                "com.google.common.base.Preconditions.checkArgument(boolean, String, Object)",
                SymbolText.readable(
                        "com/google/common/base/Preconditions#checkArgument(ZLjava/lang/String;Ljava/lang/Object;)V"));
    }

    @Test
    void rendersEveryPrimitiveDescriptor() {
        assertEquals(
                "A.m(boolean, byte, char, short, int, long, float, double)",
                SymbolText.readable("A#m(ZBCSIJFD)V"));
    }

    @Test
    void rendersArraysWithBrackets() {
        assertEquals(
                "A.m(String[], int[][], Object[])",
                SymbolText.readable("A#m([Ljava/lang/String;[[I[Ljava/lang/Object;)Z"));
    }

    @Test
    void rendersAMethodWithoutParameters() {
        assertEquals("com.acme.Api.close()", SymbolText.readable("com/acme/Api#close()V"));
    }

    @Test
    void rendersAFieldReferenceAsAMemberName() {
        assertEquals("com.acme.Api.LIMIT", SymbolText.readable("com/acme/Api#LIMIT"));
    }

    @Test
    void keepsNestedTypeNamesRecognisable() {
        assertEquals("java.util.Map$Entry", SymbolText.readable("java/util/Map$Entry"));
    }

    @Test
    void passesUnparseableSymbolsThroughInsteadOfFailing() {
        assertEquals("com.acme.Api.m(", SymbolText.readable("com/acme/Api#m("));
        assertEquals("A.m([])", SymbolText.readable("A#m([)V"));
        assertEquals("A.m(Foo)", SymbolText.readable("A#m(Lcom/acme/Foo)V"));
        assertEquals("A.m(Q)", SymbolText.readable("A#m(Q)V"));
    }

    /**
     * A separator in the opening position is a symbol whose owner or whose member name is missing, not
     * a symbol carrying no separator at all. Reading it as the latter hands the reader back the raw
     * bytecode text they were being spared -- the descriptors unrendered and the slashes still in -- so
     * the part that is there is still rendered and only the missing part comes out empty.
     */
    @Test
    void rendersTheHalfOfASymbolThatIsThereWhenTheOtherHalfIsMissing() {
        assertEquals(".check()", SymbolText.readable("#check()V"));
        assertEquals("com.acme.Api.(String)", SymbolText.readable("com/acme/Api#(Ljava/lang/String;)V"));
    }
}

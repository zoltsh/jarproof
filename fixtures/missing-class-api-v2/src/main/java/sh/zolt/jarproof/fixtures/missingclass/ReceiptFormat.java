package sh.zolt.jarproof.fixtures.missingclass;

/** Version 2 keeps only this class, so a v1 caller of LegacyReceipt hits NoClassDefFoundError. */
public final class ReceiptFormat {
    public String name() {
        return "plain";
    }
}

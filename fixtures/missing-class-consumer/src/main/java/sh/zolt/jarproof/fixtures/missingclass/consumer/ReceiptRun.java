package sh.zolt.jarproof.fixtures.missingclass.consumer;

import sh.zolt.jarproof.fixtures.missingclass.LegacyReceipt;
import sh.zolt.jarproof.fixtures.missingclass.ReceiptFormat;

/** Instantiates the class version 2 deletes. */
public final class ReceiptRun {
    public static void main(String[] arguments) {
        System.out.println(new ReceiptFormat().name());
        System.out.println(new LegacyReceipt().render());
    }
}

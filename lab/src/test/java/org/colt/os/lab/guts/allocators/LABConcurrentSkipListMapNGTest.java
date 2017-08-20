package org.colt.os.lab.guts.allocators;

import org.colt.os.lab.LABStats;
import org.colt.os.lab.api.FormatTransformer;
import org.colt.os.lab.api.rawhide.FixedWidthRawhide;
import org.colt.os.lab.guts.StripingBolBufferLocks;
import org.colt.os.lab.guts.api.Next;
import org.colt.os.lab.guts.api.Scanner;
import org.colt.os.lab.io.BolBuffer;
import org.colt.os.lab.io.api.UIO;
import org.testng.annotations.Test;

/**
 *
 * @author jonathan.colt
 */
public class LABConcurrentSkipListMapNGTest {

    @Test
    public void batTest() throws Exception {

        LABAppendOnlyAllocator allocator = new LABAppendOnlyAllocator("test",2);
        LABIndexableMemory labIndexableMemory = new LABIndexableMemory(allocator);
        FixedWidthRawhide rawhide = new FixedWidthRawhide(8, 8);

        LABConcurrentSkipListMap map = new LABConcurrentSkipListMap(new LABStats(), new LABConcurrentSkipListMemory(rawhide, labIndexableMemory),
            new StripingBolBufferLocks(1024));

        for (int i = 0; i < 100; i++) {

            BolBuffer key = new BolBuffer(UIO.longBytes(i));
            BolBuffer value = new BolBuffer(UIO.longBytes(i));
            map.compute(FormatTransformer.NO_OP, FormatTransformer.NO_OP, new BolBuffer(), key, value,
                (t1, t2, b, existing) -> value,
                (added, reused) -> {
                });
        }
        System.out.println("Count:" + map.size());
        System.out.println("first:" + UIO.bytesLong(map.firstKey()));
        System.out.println("last:" + UIO.bytesLong(map.lastKey()));

        Scanner scanner = map.scanner(null, null, new BolBuffer(), new BolBuffer());
        while (scanner.next((FormatTransformer readKeyFormatTransformer, FormatTransformer readValueFormatTransformer, BolBuffer rawEntry) -> {
            System.out.println("Keys:" + UIO.bytesLong(rawEntry.copy()));
            return true;
        }) == Next.more) {
        }

    }

}
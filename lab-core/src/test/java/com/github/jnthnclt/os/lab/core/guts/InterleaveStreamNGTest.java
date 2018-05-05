package com.github.jnthnclt.os.lab.core.guts;

import com.github.jnthnclt.os.lab.core.LABEnvironment;
import com.github.jnthnclt.os.lab.core.LABHeapPressure;
import com.github.jnthnclt.os.lab.core.LABStats;
import com.github.jnthnclt.os.lab.core.TestUtils;
import com.github.jnthnclt.os.lab.core.api.FormatTransformer;
import com.github.jnthnclt.os.lab.core.api.rawhide.LABRawhide;
import com.github.jnthnclt.os.lab.core.api.rawhide.Rawhide;
import com.github.jnthnclt.os.lab.core.guts.allocators.LABAppendOnlyAllocator;
import com.github.jnthnclt.os.lab.core.guts.allocators.LABConcurrentSkipListMap;
import com.github.jnthnclt.os.lab.core.guts.allocators.LABConcurrentSkipListMemory;
import com.github.jnthnclt.os.lab.core.guts.api.Next;
import com.github.jnthnclt.os.lab.core.guts.api.RawEntryStream;
import com.github.jnthnclt.os.lab.core.guts.api.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import com.github.jnthnclt.os.lab.core.guts.allocators.LABIndexableMemory;
import com.github.jnthnclt.os.lab.core.guts.api.ReadIndex;
import com.github.jnthnclt.os.lab.core.io.BolBuffer;
import com.github.jnthnclt.os.lab.core.io.api.UIO;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author jonathan.colt
 */
public class InterleaveStreamNGTest {

    @Test
    public void testNext() throws Exception {

        InterleaveStream ips = new InterleaveStream(LABRawhide.SINGLETON,
            ActiveScan.indexToFeeds(new ReadIndex[] {
                sequenceIndex(new long[] { 1, 2, 3, 4, 5 }, new long[] { 3, 3, 3, 3, 3 })
            }, null, null, LABRawhide.SINGLETON));

        List<Expected> expected = new ArrayList<>();
        expected.add(new Expected(1, 3));
        expected.add(new Expected(2, 3));
        expected.add(new Expected(3, 3));
        expected.add(new Expected(4, 3));
        expected.add(new Expected(5, 3));

        assertExpected(ips, expected);
        ips.close();
    }

    @Test
    public void testNext1() throws Exception {

        InterleaveStream ips = new InterleaveStream(LABRawhide.SINGLETON,
            ActiveScan.indexToFeeds(new ReadIndex[] {
                sequenceIndex(new long[] { 1, 2, 3, 4, 5 }, new long[] { 3, 3, 3, 3, 3 }),
                sequenceIndex(new long[] { 1, 2, 3, 4, 5 }, new long[] { 2, 2, 2, 2, 2 }),
                sequenceIndex(new long[] { 1, 2, 3, 4, 5 }, new long[] { 1, 1, 1, 1, 1 })
            }, null, null, LABRawhide.SINGLETON));

        List<Expected> expected = new ArrayList<>();
        expected.add(new Expected(1, 3));
        expected.add(new Expected(2, 3));
        expected.add(new Expected(3, 3));
        expected.add(new Expected(4, 3));
        expected.add(new Expected(5, 3));

        assertExpected(ips, expected);
        ips.close();
    }

    @Test
    public void testNext2() throws Exception {

        InterleaveStream ips = new InterleaveStream(LABRawhide.SINGLETON,
            ActiveScan.indexToFeeds(new ReadIndex[] {
                sequenceIndex(new long[] { 10, 21, 29, 41, 50 }, new long[] { 1, 0, 0, 0, 1 }),
                sequenceIndex(new long[] { 10, 21, 29, 40, 50 }, new long[] { 0, 0, 0, 1, 0 }),
                sequenceIndex(new long[] { 10, 20, 30, 39, 50 }, new long[] { 0, 1, 1, 0, 0 })
            }, null, null, LABRawhide.SINGLETON));

        List<Expected> expected = new ArrayList<>();
        expected.add(new Expected(10, 1));
        expected.add(new Expected(20, 1));
        expected.add(new Expected(21, 0));
        expected.add(new Expected(29, 0));
        expected.add(new Expected(30, 1));
        expected.add(new Expected(39, 0));
        expected.add(new Expected(40, 1));
        expected.add(new Expected(41, 0));
        expected.add(new Expected(50, 1));

        assertExpected(ips, expected);
        ips.close();

    }

    private ReadIndex sequenceIndex(long[] keys, long[] values) {
        return new ReadIndex() {
            @Override
            public void release() {
                throw new UnsupportedOperationException("Not supported.");
            }

            @Override
            public Scanner rangeScan(byte[] from, byte[] to, BolBuffer entryBuffer, BolBuffer entryKeyBuffer) throws Exception {
                throw new UnsupportedOperationException("Not supported.");
            }

            @Override
            public Scanner rowScan(BolBuffer entryBuffer, BolBuffer entryKeyBuffer) throws Exception {
                return nextEntrySequence(keys, values);
            }

            @Override
            public Scanner pointScan(boolean hashIndexEnabled, byte[] key, BolBuffer entryBuffer, BolBuffer entryKeyBuffer) throws Exception {
                throw new UnsupportedOperationException("Not supported.");
            }

            @Override
            public long count() throws Exception {
                throw new UnsupportedOperationException("Not supported.");
            }
        };
    }

    @Test
    public void testNext3() throws Exception {

        int count = 10;
        int step = 100;
        int indexes = 4;

        Random rand = new Random(123);
        ExecutorService destroy = Executors.newSingleThreadExecutor();
        Rawhide rawhide = LABRawhide.SINGLETON;

        ConcurrentSkipListMap<byte[], byte[]> desired = new ConcurrentSkipListMap<>(rawhide.getKeyComparator());
        LABStats labStats = new LABStats();

        LABHeapPressure labHeapPressure = new LABHeapPressure(labStats,
            LABEnvironment.buildLABHeapSchedulerThreadPool(1),
            "default",
            -1,
            -1,
            new AtomicLong(),
            LABHeapPressure.FreeHeapStrategy.mostBytesFirst
        );
        LABMemoryIndex[] memoryIndexes = new LABMemoryIndex[indexes];
        ReadIndex[] reorderIndexReaders = new ReadIndex[indexes];
        ReadIndex[] readerIndexs = new ReadIndex[indexes];
        BolBuffer keyBuffer = new BolBuffer();
        try {
            for (int wi = 0; wi < indexes; wi++) {

                int i = (indexes - 1) - wi;

                memoryIndexes[i] = new LABMemoryIndex(destroy, labHeapPressure, labStats,
                    rawhide,
                    new LABConcurrentSkipListMap(labStats,
                        new LABConcurrentSkipListMemory(rawhide,
                            new LABIndexableMemory(new LABAppendOnlyAllocator("test", 2))
                        ),
                        new StripingBolBufferLocks(1024)
                    ));
                TestUtils.append(rand, memoryIndexes[i], 0, step, count, desired, keyBuffer);
                //System.out.println("Index " + i);

                readerIndexs[wi] = memoryIndexes[i].acquireReader();
                Scanner nextRawEntry = readerIndexs[wi].rowScan(new BolBuffer(), new BolBuffer());
                while (nextRawEntry.next((readKeyFormatTransformer, readValueFormatTransformer, rawEntry) -> {
                    //System.out.println(TestUtils.toString(rawEntry));
                    return true;
                }, null) == Next.more) {
                }
                //System.out.println("\n");

                reorderIndexReaders[i] = readerIndexs[wi];
            }

            InterleaveStream ips = new InterleaveStream(rawhide,
                ActiveScan.indexToFeeds(reorderIndexReaders, null, null, rawhide));

            List<Expected> expected = new ArrayList<>();
            //System.out.println("Expected:");
            for (Map.Entry<byte[], byte[]> entry : desired.entrySet()) {
                long key = UIO.bytesLong(entry.getKey());
                long value = TestUtils.value(entry.getValue());
                expected.add(new Expected(key, value));
                //System.out.println(key + " timestamp:" + value);
            }
            //System.out.println("\n");

            assertExpected(ips, expected);
        } finally {
            for (ReadIndex readerIndex : readerIndexs) {
                if (readerIndex != null) {
                    readerIndex.release();
                }
            }
        }
    }

    private void assertExpected(InterleaveStream ips, List<Expected> expected) throws Exception {
        boolean[] passed = { true };
        while (ips.next((readKeyFormatTransformer, readValueFormatTransformer, rawEntry) -> {
            Expected expect = expected.remove(0);
            long key = TestUtils.key(rawEntry);
            long value = TestUtils.value(rawEntry);

            //System.out.println("key:" + key + " vs " + expect.key + " value:" + value + " vs " + expect.value);
            if (key != expect.key) {
                passed[0] = false;
            }
            if (value != expect.value) {
                passed[0] = false;
            }
            return true;
        }, null) == Next.more) {
        }
        Assert.assertTrue(passed[0], "key or value miss match");
        Assert.assertTrue(expected.isEmpty(), "failed to remove all");
    }


    /*

     */
    static private class Expected {

        long key;
        long value;

        private Expected(long key, long value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Expected{" + "key=" + key + ", value=" + value + '}';
        }

    }

    public Scanner nextEntrySequence(long[] keys, long[] values) {
        int[] index = { 0 };

        return new Scanner() {
            @Override
            public Next next(RawEntryStream stream, BolBuffer nextHint) throws Exception {
                if (index[0] < keys.length) {
                    byte[] rawEntry = TestUtils.rawEntry(keys[index[0]], values[index[0]]);
                    if (!stream.stream(FormatTransformer.NO_OP, FormatTransformer.NO_OP, new BolBuffer(rawEntry))) {
                        return Next.stopped;
                    }
                }
                index[0]++;
                return index[0] <= keys.length ? Next.more : Next.eos;
            }

            @Override
            public void close() {
            }
        };
    }

}

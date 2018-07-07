package com.github.jnthnclt.os.lab.core;

import com.github.jnthnclt.os.lab.collections.bah.LRUConcurrentBAHLinkedHash;
import com.github.jnthnclt.os.lab.core.LABHeapPressure.FreeHeapStrategy;
import com.github.jnthnclt.os.lab.core.api.MemoryRawEntryFormat;
import com.github.jnthnclt.os.lab.core.api.ValueIndex;
import com.github.jnthnclt.os.lab.core.api.ValueIndexConfig;
import com.github.jnthnclt.os.lab.core.api.rawhide.LABRawhide;
import com.github.jnthnclt.os.lab.core.guts.LABFiles;
import com.github.jnthnclt.os.lab.core.guts.LABHashIndexType;
import com.github.jnthnclt.os.lab.core.guts.Leaps;
import com.github.jnthnclt.os.lab.core.guts.StripingBolBufferLocks;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.FileUtils;

public class LABIndexProvider<T> {

    private final int cores = Runtime.getRuntime().availableProcessors();
    private final LABStats stats;
    private final LABFiles labFiles;
    private final LABHeapPressure labHeapPressure;
    private final ExecutorService scheduler = LABEnvironment.buildLABSchedulerThreadPool(cores);
    private final ExecutorService compact = LABEnvironment.buildLABCompactorThreadPool(cores);
    private final ExecutorService destroy = LABEnvironment.buildLABDestroyThreadPool(cores);
    private final ExecutorService heapScheduler = LABEnvironment.buildLABHeapSchedulerThreadPool(cores);
    private final LRUConcurrentBAHLinkedHash<Leaps> leapsCache = LABEnvironment.buildLeapsCache(100_000, 4);

    public LABIndexProvider(AtomicLong globalHeapCostInBytes, LABStats stats, LABFiles labFiles) {
        this.stats = stats;
        this.labFiles = labFiles;

        labHeapPressure = new LABHeapPressure(stats,
            heapScheduler,
            "default",
            1024 * 1024 * 512,
            1024 * 1024 * 768,
            globalHeapCostInBytes,
            FreeHeapStrategy.mostBytesFirst
        );
    }

    public void destroyIndex(File root, String name) throws Exception {
        FileUtils.forceDelete(new File(root, name));
    }

    public ValueIndex<byte[]> buildIndex(File root, String name) throws Exception {


        LABEnvironment environment = new LABEnvironment(stats,
            labFiles,
            scheduler,
            compact,
            destroy,
            null,
            root,
            labHeapPressure,
            4,
            16,
            leapsCache,
            new StripingBolBufferLocks(1024),
            true,
            true);

        return environment.open(new ValueIndexConfig(name,
            1024 * 4,
            1024 * 1024 * 512,
            -1,
            -1,
            128 * 1024 * 1024,
            "deprecated",
            LABRawhide.NAME,
            MemoryRawEntryFormat.NAME,
            28,
            LABHashIndexType.cuckoo,
            2d,
            true,
            Long.MAX_VALUE));
    }
}

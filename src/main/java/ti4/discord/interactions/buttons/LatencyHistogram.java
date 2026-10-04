package ti4.discord.interactions.buttons;

class LatencyHistogram {

    private static final long[] BUCKET_RANGE_STARTS = {0, 100, 1000, 10_000, 120_000};
    private static final long[] BUCKET_WIDTHS = {1, 10, 100, 1000};
    private static final int BUCKET_COUNT = bucketIndex(BUCKET_RANGE_STARTS[BUCKET_RANGE_STARTS.length - 1]) + 1;

    private final long[] bucketCounts = new long[BUCKET_COUNT];
    private long count;
    private long totalMillis;
    private long maxMillis;

    void record(long millis) {
        long clampedMillis = Math.max(0, millis);
        bucketCounts[bucketIndex(clampedMillis)]++;
        count++;
        totalMillis += clampedMillis;
        maxMillis = Math.max(maxMillis, clampedMillis);
    }

    long count() {
        return count;
    }

    long totalMillis() {
        return totalMillis;
    }

    double meanMillis() {
        return count == 0 ? 0 : totalMillis / (double) count;
    }

    long percentileMillis(double percentile) {
        if (count == 0) return 0;
        long rank = Math.max(1, (long) Math.ceil(percentile * count));
        long seen = 0;
        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
            seen += bucketCounts[bucket];
            if (seen >= rank) {
                return Math.min(bucketLowerBound(bucket), maxMillis);
            }
        }
        return maxMillis;
    }

    private static int bucketIndex(long millis) {
        int index = 0;
        for (int range = 0; range < BUCKET_WIDTHS.length; range++) {
            long rangeStart = BUCKET_RANGE_STARTS[range];
            long rangeEnd = BUCKET_RANGE_STARTS[range + 1];
            int bucketsInRange = (int) ((rangeEnd - rangeStart) / BUCKET_WIDTHS[range]);
            if (millis < rangeEnd) {
                return index + (int) ((millis - rangeStart) / BUCKET_WIDTHS[range]);
            }
            index += bucketsInRange;
        }
        return index;
    }

    private static long bucketLowerBound(int bucket) {
        int remaining = bucket;
        for (int range = 0; range < BUCKET_WIDTHS.length; range++) {
            long rangeStart = BUCKET_RANGE_STARTS[range];
            int bucketsInRange = (int) ((BUCKET_RANGE_STARTS[range + 1] - rangeStart) / BUCKET_WIDTHS[range]);
            if (remaining < bucketsInRange) {
                return rangeStart + remaining * BUCKET_WIDTHS[range];
            }
            remaining -= bucketsInRange;
        }
        return BUCKET_RANGE_STARTS[BUCKET_RANGE_STARTS.length - 1];
    }
}

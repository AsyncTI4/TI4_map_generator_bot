package ti4.discord.interactions.buttons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LatencyHistogramTest {

    @Test
    void emptyHistogramReportsZeros() {
        LatencyHistogram histogram = new LatencyHistogram();

        assertThat(histogram.count()).isZero();
        assertThat(histogram.meanMillis()).isZero();
        assertThat(histogram.percentileMillis(0.5)).isZero();
    }

    @Test
    void subHundredMillisecondValuesAreExact() {
        LatencyHistogram histogram = new LatencyHistogram();
        for (int millis = 1; millis <= 99; millis++) {
            histogram.record(millis);
        }

        assertThat(histogram.percentileMillis(0.5)).isEqualTo(50);
        assertThat(histogram.percentileMillis(0.95)).isEqualTo(95);
        assertThat(histogram.meanMillis()).isEqualTo(50.0);
    }

    @Test
    void percentilesShowTheTypicalPressDespiteOutliers() {
        // The case the old mean-only report hid: mostly fast presses with a few very slow ones.
        LatencyHistogram histogram = new LatencyHistogram();
        for (int i = 0; i < 97; i++) {
            histogram.record(20);
        }
        histogram.record(9000);
        histogram.record(9000);
        histogram.record(9000);

        assertThat(histogram.percentileMillis(0.5)).isEqualTo(20);
        assertThat(histogram.percentileMillis(0.95)).isEqualTo(20);
        assertThat(histogram.percentileMillis(0.99)).isEqualTo(9000);
        assertThat(histogram.meanMillis()).isGreaterThan(280);
    }

    @Test
    void maxIsTheExactLargestValueNotItsBucket() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(20);
        histogram.record(38_765);
        histogram.record(150);

        assertThat(histogram.maxMillis()).isEqualTo(38_765);
        assertThat(histogram.percentileMillis(1.0)).isEqualTo(38_000);
    }

    @Test
    void largerValuesRoundDownToTheirBucket() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(257);
        histogram.record(4321);
        histogram.record(65_432);

        assertThat(histogram.percentileMillis(0.33)).isEqualTo(250);
        assertThat(histogram.percentileMillis(0.66)).isEqualTo(4300);
        assertThat(histogram.percentileMillis(1.0)).isEqualTo(65_000);
    }

    @Test
    void valuesBeyondTheLastBucketReportTheObservedMax() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(500_000);

        assertThat(histogram.percentileMillis(0.5)).isEqualTo(120_000);
        assertThat(histogram.meanMillis()).isEqualTo(500_000.0);
    }

    @Test
    void negativeValuesFromClockSkewAreRecordedAsZero() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(-40);

        assertThat(histogram.count()).isEqualTo(1);
        assertThat(histogram.meanMillis()).isZero();
        assertThat(histogram.percentileMillis(0.5)).isZero();
    }
}

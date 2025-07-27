package ti4.website;

import lombok.Data;

@Data
public class WebPdsCoverage {
    private int count;
    private float expected;

    public WebPdsCoverage(int count, float expected) {
        this.count = count;
        this.expected = expected;
    }
}
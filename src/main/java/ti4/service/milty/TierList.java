package ti4.service.milty;

import lombok.Getter;

@Getter
public enum TierList {
    high("high"), mid("mid"), low("low"), red("red"), anomaly("anomaly");

    public final String value;

    TierList(String value) {
        this.value = value;
    }

    public boolean isBlue() {
        return switch (this) {
            case high, mid, low -> true;
            default -> false;
        };
    }
}

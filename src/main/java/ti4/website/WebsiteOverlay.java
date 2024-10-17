package ti4.website;

import java.util.List;

public class WebsiteOverlay {
    private String cardType;
    private String cardID;
    private List<Integer> boxXYWH;

    public WebsiteOverlay(String cardType, String cardID, List<Integer> boxXYWH) {
        this.cardType = cardType;
        this.cardID = cardID;
        this.boxXYWH = boxXYWH;
    }
}

package ti4.website;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.Setter;
import ti4.model.ModelInterface;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class WebsiteOverlay {
    private String cardType;
    private String cardID;
    private List<Integer> boxXYWH;
    private String text;

    public WebsiteOverlay(String cardType, String cardID, List<Integer> boxXYWH) {
        this.cardType = cardType;
        this.cardID = cardID;
        this.boxXYWH = boxXYWH;
    }

    public WebsiteOverlay(String text, List<Integer> boxXYWH) {
        this.text = text;
        this.boxXYWH = boxXYWH;
    }

    public WebsiteOverlay(ModelInterface model, List<Integer> boxXYWH) {
        this.cardType = model.getClass().getSimpleName();
        this.cardID = model.getAlias();
        this.boxXYWH = boxXYWH;
    }
}

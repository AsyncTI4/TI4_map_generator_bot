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
    private String dataModel;
    private String dataModelID;
    private List<Integer> boxXYWH;
    private String title;
    private String text;

    public WebsiteOverlay(String title, String text, List<Integer> boxXYWH) {
        this.title = title;
        this.text = text;
        this.boxXYWH = boxXYWH;
    }

    public WebsiteOverlay(ModelInterface model, List<Integer> boxXYWH) {
        this.dataModel = model.getClass().getSimpleName();
        this.dataModelID = model.getAlias();
        this.boxXYWH = boxXYWH;
    }
}

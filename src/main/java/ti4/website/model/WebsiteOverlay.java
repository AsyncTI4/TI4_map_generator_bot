package ti4.website.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import ti4.model.ModelInterface;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class WebsiteOverlay {
    private String dataModel;
    private String dataModelID;
    private String title;
    private String text;
    private List<Integer> boxXYWH;

    public WebsiteOverlay(String title, String text, List<Integer> boxXYWH) {
        this.title = title;
        this.text = text;
        this.boxXYWH = boxXYWH;
    }

    public WebsiteOverlay(ModelInterface model, List<Integer> boxXYWH) {
        dataModel = model.getClass().getSimpleName();
        dataModelID = model.getAlias();
        this.boxXYWH = boxXYWH;
    }
}

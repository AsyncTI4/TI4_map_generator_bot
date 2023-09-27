package ti4.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class AttachmentModel implements ModelInterface {
    private String id;
    private List<String> techSpeciality = new ArrayList<>();
    private List<String> planetTypes = new ArrayList<>();
    private int resourcesModifier = 0;
    private int influenceModifier = 0;
    private String token;
    private Boolean isLegendary;
    private String imagePath;

    private int spaceCannonHitsOn = 0;
    private int spaceCannonDieCount = 0;

    @Override
    public boolean isValid() {
        return id != null;
    }

    @Override
    public String getAlias() {
        return getImagePath(); // looks like were using the attachment_<name>.png for identification for now.
    }
}

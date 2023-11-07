package ti4.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private Boolean isFakeAttachment; // is an attachment on backend, but should not be displayed as one

    private int spaceCannonHitsOn = 0;
    private int spaceCannonDieCount = 0;

    @Override
    public boolean isValid() {
        return id != null
            && imagePath != null;
    }

    @Override
    public String getAlias() {
        return getImagePath(); // looks like were using the attachment_<name>.png for identification for now.
    }

    public boolean isFakeAttachment() {
        return Optional.ofNullable(isFakeAttachment).orElse(false);
    }

    public boolean isLegendary() {
        return Optional.ofNullable(isLegendary).orElse(false);
    }

    public Optional<String> getToken() {
        return Optional.ofNullable(token);
    }
}

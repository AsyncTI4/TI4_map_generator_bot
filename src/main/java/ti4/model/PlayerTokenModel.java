package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import ti4.image.Mapper;

/**
 * Command, Control, Fleet, + Homebrew stuff
 * <p>
 * Example Control Token
 * <ul>
 * <li>alias: control_bld
 * <li>name: Control Token (BloodRed)
 * <li>imagePath: control_bld.png
 * <li>color: bld
 */
@Data
public class PlayerTokenModel implements TokenModelInterface, ColorableModelInterface<PlayerTokenModel> {
    private String alias;
    private String name;
    private String imagePath;
    private UnitHolderType unitHolderType;
    private String color;

    @Override
    public boolean isValid() {
        return imagePath != null;
    }

    @Override
    public boolean isDupe() {
        return false;
    }

    @Override
    public boolean isColorable() {
        return color.equals("<color>");
    }

    @Override
    public PlayerTokenModel duplicateAndSetColor(ColorModel newColor) {
        PlayerTokenModel cc = new PlayerTokenModel();
        cc.setAlias(alias.replace(color, newColor.getAlias()));
        cc.setName(name.replace(color, newColor.getDisplayName()));
        cc.setImagePath(imagePath.replace(color, newColor.getAlias()));
        cc.setColor(newColor.getAlias());
        return cc;
    }

    @JsonIgnore
    public String getFileName() {
        return imagePath;
    }

    @JsonIgnore
    public String getFilePath() {
        return Mapper.getAttachmentImagePath(imagePath);
    }
}

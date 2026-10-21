package ti4.image;

import java.util.regex.Pattern;
import javax.annotation.Nullable;
import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.helpers.RenderRandom;
import ti4.helpers.Units.UnitKey;

public final class ImageResources {

    private static final Pattern DESTROYER = Pattern.compile(Constants.UNIT_DD);

    private ImageResources() {}

    @Nullable
    public static String tile(String name) {
        return find("tiles/", name);
    }

    @Nullable
    public static String faction(String name) {
        return find("factions/", name);
    }

    @Nullable
    public static String general(String name) {
        return find("general/", name);
    }

    @Nullable
    public static String unit(String name) {
        if (name.endsWith(Constants.UNIT_DD) && RenderRandom.oneIn("unit.destroyerEye", Constants.EYE_CHANCE)) {
            name = DESTROYER.matcher(name).replaceFirst(Constants.UNIT_DD_EYE);
        }
        return find("units/", name);
    }

    @Nullable
    public static String unit(UnitKey unit) {
        return find("units/", unit.getFileName());
    }

    @Nullable
    public static String unit(UnitKey unit, boolean eyes) {
        return find("units/", unit.getFileName(eyes));
    }

    @Nullable
    public static String decal(String name) {
        return find("decals/", name);
    }

    @Nullable
    public static String spoopyFace() {
        int face = RenderRandom.nextInt("unit.spoopyFace", 1, 4);
        return find("decals/", "overlay_jackolantern_" + face + ".png");
    }

    @Nullable
    public static String commandToken(String name) {
        return find("command_token/", name);
    }

    @Nullable
    public static String peekMarker(String name) {
        return find("peek_marker/", name);
    }

    @Nullable
    public static String attachment(String name) {
        String attachment = find("attachment_token/", name);
        return attachment != null ? attachment : token(name);
    }

    @Nullable
    public static String planetCard(String name) {
        return find("planet_cards/", name);
    }

    @Nullable
    public static String playerArea(String name) {
        return find("player_area/", name);
    }

    @Nullable
    public static String token(String name) {
        return find("tokens/", name);
    }

    @Nullable
    public static String extra(String name) {
        return find("extra/", name);
    }

    @Nullable
    private static String find(String folder, String name) {
        return ResourceHelper.getResourceFromFolder(folder, name);
    }
}

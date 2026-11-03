package ti4.image.io;

import java.io.File;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.CalendarHelper;
import ti4.helpers.Constants;
import ti4.helpers.RenderRandom;
import ti4.helpers.Units.UnitKey;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.Mapper;
import ti4.model.TileModel;

public final class ImageResources {

    private static final Pattern DESTROYER = Pattern.compile(Constants.UNIT_DD);

    private ImageResources() {}

    @Nullable
    public static String tile(String name) {
        return find("tiles/", name);
    }

    @Nullable
    public static String tilePath(Tile tile) {
        String tileID = tile.getTileID();
        String tileName = Mapper.getTileID(tileID);
        if (("44".equals(tileID) || ("45".equals(tileID)))
                && (RenderRandom.oneIn("tile.eyeCucumber", Constants.EYE_CHANCE))) {
            tileName = "S15_Cucumber.png";
        }
        if (("43".equals(tileID) || "80".equals(tileID))) {
            int baubleChance = CalendarHelper.isNearChristmas() ? 5 : 10_000;
            if (RenderRandom.oneIn("tile.xmasBauble", baubleChance)) {
                tileName = tileName.replace(".png", "_xmas.png");
            }
        }
        String tilePath = tile(tileName);
        if (tilePath == null) {
            BotLogger.warning("Could not find tile: " + tileID);
        }
        return tilePath;
    }

    @Nullable
    public static String tilePath(TileModel tileModel) {
        return tile(Mapper.getTileID(tileModel.getId()));
    }

    @Nullable
    public static String fogTilePath(Tile tile, @Nullable Player player) {
        String tileID = tile.getTileID();
        String fogTileColor = player == null ? "default" : player.getFogFilter();
        String fogTileColorSuffix = "_" + fogTileColor;
        String fowTileID = "fow" + fogTileColorSuffix;

        if ("82b".equals(tileID) || "51".equals(tileID)) {
            fowTileID = "fowb" + fogTileColorSuffix;
        }
        if ("82a".equals(tileID)) {
            fowTileID = "fowc" + fogTileColorSuffix;
        }

        String tileName = Mapper.getTileID(fowTileID);
        String tilePath = tile(tileName);
        if (tilePath == null) {
            BotLogger.warning(new LogOrigin(player), "Could not find tile: " + fowTileID);
        }
        return tilePath;
    }

    @Nullable
    public static String border(String name) {
        return find("borders/", name);
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
    public static String unitPath(UnitKey unitKey, Game game) {
        String unitPath = unit(unitKey);
        if (unitPath == null) return null;

        unitPath = switch (unitKey.unitType()) {
            case Fighter -> unitPath.replace(Constants.COLOR_FF, Constants.BULK_FF);
            case Infantry -> unitPath.replace(Constants.COLOR_GF, Constants.BULK_GF);
            default -> unitPath;
        };

        Player player = game.getPlayerByColorID(unitKey.colorID()).orElse(null);
        if (player == null) return unitPath;

        return switch (unitKey.unitType()) {
            case Lady -> unitPath.replace("lady", "fs");
            case Celagrom -> unitPath.replace("celagrom", "fs");
            case Aurelion -> unitPath.replace("aurelion", "fs");
            case Cavalry -> {
                boolean hasM2Tech = game.getPNOwner("cavalry") != null
                        && game.getPNOwner("cavalry").hasTech("m2");
                String version = hasM2Tech ? "Memoria_2.png" : "Memoria_1.png";
                yield unit(version);
            }
            default -> unitPath;
        };
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
    public static String attachment(String name) {
        String attachment = find("attachment_token/", name);
        return attachment != null ? attachment : token(name);
    }

    @Nullable
    public static String tokenOrAttachment(String tokenID) {
        String tokenPath = attachment(tokenID);
        if (tokenPath == null || !(new File(tokenPath).exists())) {
            tokenPath = token(tokenID);
            if (tokenPath == null) {
                BotLogger.warning("Could not find token path: " + tokenID);
                return null;
            }
        }
        return tokenPath;
    }

    @Nullable
    public static String techSkip(String skip) {
        return switch (skip.toLowerCase()) {
            case "biotic" -> general("Biotic light.png");
            case "cybernetic" -> general("Cybernetic light.png");
            case "propulsion" -> general("Propulsion_light.png");
            case "warfare" -> general("Warfare_light.png");
            default -> general("Generic_Technology.png");
        };
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

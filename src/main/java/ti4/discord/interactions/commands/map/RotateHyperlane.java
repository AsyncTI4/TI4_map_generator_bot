package ti4.discord.interactions.commands.map;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.TileHelper;
import ti4.service.map.AddTileService;

class RotateHyperlane extends GameStateSubcommand {

    private static final Pattern DEGREE_ROTATION_ID = Pattern.compile("(\\d{2}[ab])(\\d*)");
    private static final Pattern STEP_ROTATION_ID = Pattern.compile("(hl_.+)_(\\d+)");

    public RotateHyperlane() {
        super(Constants.ROTATE_HYPERLANE, "Rotate hyperlane", true, false);
        addOptions(new OptionData(
                OptionType.STRING, Constants.POSITION, "Tile position on map. Accepts comma separated list", true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.ROTATION, "Rotation in degrees, default = 60", false)
                .setRequiredRange(-360, 360));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String positionOption = event.getOptions().getFirst().getAsString();
        Set<String> positions = Helper.getSetFromCSV(positionOption);

        for (String position : positions) {
            if (!MapPositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position `" + position + "` not allowed");
                return;
            }
        }

        Game game = getGame();

        int rotation = 60;
        if (event.getOptions().size() > 1 && event.getOptions().get(1).getType() == OptionType.INTEGER) {
            rotation = event.getOptions().get(1).getAsInt();
            if (rotation % 60 != 0) {
                MessageHelper.replyToMessage(event, "Rotation must be a multiple of 60");
                return;
            }
        }
        for (String position : positions) {
            Tile originalTile = game.getTileByPosition(position);
            if (originalTile == null) {
                MessageHelper.replyToMessage(event, "No tile found at position `" + position + "`");
                continue;
            }
            String id = rotatedTileId(originalTile.getTileID(), rotation);
            if (id == null) {
                MessageHelper.replyToMessage(
                        event,
                        "Tile `" + originalTile.getTileID() + "` at position `" + position + "` is not a hyperlane");
                continue;
            }
            if (Mapper.getTileID(id) == null) {
                MessageHelper.replyToMessage(event, "No tile found with ID `" + id + "` after rotation");
                continue;
            }
            Tile tile = new Tile(id, position);

            AddTileService.addTile(getGame(), tile);
        }

        game.rebuildTilePositionAutoCompleteList();
    }

    static String rotatedTileId(String id, int rotation) {
        Matcher degreeMatcher = DEGREE_ROTATION_ID.matcher(id);
        if (degreeMatcher.matches()) {
            String base = degreeMatcher.group(1);
            int current = degreeMatcher.group(2).isEmpty() ? 0 : Integer.parseInt(degreeMatcher.group(2));
            int newRotation = Math.floorMod(current + rotation, 360);
            return newRotation == 0 ? base : base + newRotation;
        }
        Matcher stepMatcher = STEP_ROTATION_ID.matcher(id);
        if (stepMatcher.matches()) {
            String base = stepMatcher.group(1);
            int variants = countStepVariants(base);
            if (variants == 0) return null;
            int current = Integer.parseInt(stepMatcher.group(2));
            return base + "_" + Math.floorMod(current + rotation / 60, variants);
        }
        return null;
    }

    private static int countStepVariants(String base) {
        int variants = 0;
        while (TileHelper.getTileById(base + "_" + variants) != null) {
            variants++;
        }
        return variants;
    }
}

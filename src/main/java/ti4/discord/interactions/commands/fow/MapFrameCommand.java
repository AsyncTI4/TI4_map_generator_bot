package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.image.MapFrame;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class MapFrameCommand extends GameStateSubcommand {

    private static final String RADIUS = "radius";
    private static final int MAX_RADIUS = 8;

    MapFrameCommand() {
        super(
                "map_frame",
                "GM: frame every map render of this game around a centre system (reset returns to auto-fit)",
                true,
                true);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Centre tile position, e.g. 000 or 312"));
        addOptions(new OptionData(OptionType.INTEGER, RADIUS, "Rings to show around the centre (0-8)")
                .setRequiredRange(0, MAX_RADIUS));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.RESET, "True to go back to automatic framing"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode() || !game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "Only the GM of a Fog of War game can set the map frame.");
            return;
        }
        if (event.getOption(Constants.RESET, false, OptionMapping::getAsBoolean)) {
            MapFrame.clearGmFrame(game);
            MessageHelper.replyToMessage(event, "Map frame cleared. Maps now frame themselves automatically.");
            return;
        }
        String centre = event.getOption(Constants.POSITION, null, OptionMapping::getAsString);
        Integer radius = event.getOption(RADIUS, null, OptionMapping::getAsInt);
        if (centre == null || radius == null) {
            MessageHelper.replyToMessage(
                    event, "Give both a centre `position` and a `radius`, or set `reset` to true.");
            return;
        }
        if (!PositionMapper.isTilePositionValid(centre)) {
            MessageHelper.replyToMessage(event, "Tile position `" + centre + "` is invalid.");
            return;
        }
        MapFrame.setGmFrame(game, centre, radius);
        MessageHelper.replyToMessage(
                event,
                "Every map of this game will now be framed around `" + centre + "` with a radius of " + radius
                        + " ring(s).");
    }
}

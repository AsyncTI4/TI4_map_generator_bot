package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.FoWHelper;
import ti4.image.CompactOverviewGenerator;
import ti4.image.MapOverviewGenerator;
import ti4.image.MapRenderPipeline;
import ti4.message.MessageHelper;

class MapOverviewCommand extends GameStateSubcommand {

    private static final String SECTOR_NAMES = "sector_names";
    private static final String COMPACT = "compact";

    MapOverviewCommand() {
        super("map_overview", "GM: the whole unfogged map, Fracture included, scaled to fit one image", false, true);
        addOptions(
                new OptionData(
                        OptionType.BOOLEAN, COMPACT, "False: true map positions instead of packed sector panels"),
                new OptionData(
                        OptionType.BOOLEAN,
                        SECTOR_NAMES,
                        "True: full layout with the map sectors and their names overlaid"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode() || !game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "Only the GM of a Fog of War game can load the map overview.");
            return;
        }
        if (!FoWHelper.canSeeWholeMap(game, event)) {
            MessageHelper.replyToMessage(event, "The overview shows the whole unfogged map. Use it in the GM room.");
            return;
        }
        boolean sectorNames = event.getOption(SECTOR_NAMES, false, OptionMapping::getAsBoolean);
        if (event.getOption(COMPACT, !sectorNames, OptionMapping::getAsBoolean)) {
            MessageChannel channel = event.getMessageChannel();
            MessageHelper.replyToMessage(event, "Rendering the compact overview…");
            MapRenderPipeline.queueImage(
                    game,
                    "Map overview",
                    () -> CompactOverviewGenerator.gmOverview(game),
                    fileUpload -> MessageHelper.sendFileUploadToChannel(channel, fileUpload));
            return;
        }
        MessageHelper.sendFileUploadToChannel(
                event.getMessageChannel(), MapOverviewGenerator.createFileUpload(game, sectorNames));
    }
}

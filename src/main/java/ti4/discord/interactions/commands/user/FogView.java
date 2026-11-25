package ti4.discord.interactions.commands.user;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.FogViewService;

class FogView extends GameStateSubcommand {

    private static final String GHOST_HEXES = "ghost_hexes";
    private static final String OVERVIEW_LAYOUT = "overview_layout";
    private static final String HEXES = "hexes";
    private static final String BOXES = "boxes";

    public FogView() {
        super(
                Constants.FOG_VIEW,
                "Your fog view settings for this game: ghost hexes, overview layout, tile colour.",
                true,
                true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.FOG_FILTER, "Colour of the fog tiles")
                        .setAutoComplete(true),
                new OptionData(OptionType.BOOLEAN, GHOST_HEXES, "True to show ghost hexes, false to hide them"),
                new OptionData(OptionType.STRING, OVERVIEW_LAYOUT, "How the Overview packs the map")
                        .addChoice("Hexes", HEXES)
                        .addChoice("Boxes", BOXES));
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode()) {
            MessageHelper.replyToMessage(event, "Fog view settings are only available in Fog of War games.");
            return;
        }
        Player player = getPlayer();
        List<String> changes = new ArrayList<>();
        String filterInput = event.getOption(Constants.FOG_FILTER, null, OptionMapping::getAsString);
        if (filterInput != null) {
            String suffix = FogViewService.filterSuffixFor(filterInput);
            if (suffix == null) {
                MessageHelper.replyToMessage(
                        event,
                        "Unknown fog colour `" + filterInput + "`. Pick one of: "
                                + String.join(", ", FogViewService.filterLabels()));
                return;
            }
            player.setFogFilter(suffix);
            changes.add("fog tile colour");
        }
        Boolean ghosts = event.getOption(GHOST_HEXES, null, OptionMapping::getAsBoolean);
        if (ghosts != null) {
            player.setFogGhostHexes(ghosts);
            changes.add("ghost hexes");
        }
        String layout = event.getOption(OVERVIEW_LAYOUT, null, OptionMapping::getAsString);
        if (layout != null) {
            player.setFogOverviewBoxes(BOXES.equals(layout));
            changes.add("overview layout");
        }
        String notice = changes.isEmpty() ? null : "Changed: " + String.join(", ", changes) + ".";
        MessageChannel channel = player.getCorrectChannel();
        FogViewService.post(player, channel, notice);
        MessageHelper.replyToMessage(
                event,
                channel.getId().equals(event.getChannel().getId())
                        ? "Your fog view settings are below."
                        : "Your fog view settings are in " + channel.getAsMention() + ".");
    }
}

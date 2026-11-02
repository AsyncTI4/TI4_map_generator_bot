package ti4.discord.interactions.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class SetFogGhostHexes extends GameStateSubcommand {

    private static final String SHOW = "show";

    public SetFogGhostHexes() {
        super(
                Constants.FOG_GHOST_HEXES,
                "Show or hide faint numbered rings on unexplored hexes next to known space (fog maps).",
                true,
                true);
        addOptions(new OptionData(OptionType.BOOLEAN, SHOW, "True to show the rings, false to hide them")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean show = event.getOption(SHOW, true, OptionMapping::getAsBoolean);
        Player player = getPlayer();
        player.setFogGhostHexes(show);
        MessageHelper.sendMessageToEventChannel(
                event, "Ghost hexes on your fog map are now " + (show ? "shown." : "hidden."));
    }
}

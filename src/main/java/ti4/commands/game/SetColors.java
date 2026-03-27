package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.SetColorsService;

class SetColors extends GameStateSubcommand {

    public SetColors() {
        super(Constants.SET_COLORS, "Set player colors from user and faction preferences", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int changedPlayers = SetColorsService.setPreferredColors(getGame());
        MessageHelper.sendMessageToEventChannel(event, "Adjusted colors for " + changedPlayers
                + " players. Players with manually-set colors were not changed.");
    }
}

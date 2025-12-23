package ti4.commands.spin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.map.SpinService;

class ListSpinSettings extends GameStateSubcommand {

    public ListSpinSettings() {
        super(Constants.SPIN_LIST, "List current spin settings.", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !getPlayer().isGM()) {
            MessageHelper.replyToMessage(event, "You are not authorized to use this command.");
            return;
        }
        SpinService.listSpinSettings(event, getGame());
    }
}

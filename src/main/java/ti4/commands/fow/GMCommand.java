package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.fow.GMService;

class GMCommand extends GameStateSubcommand {

    public GMCommand() {
        super("gm", "Show GM buttons", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "You are not GM in this game.");
            return;
        }
        GMService.showGMButtons(game);
    }
}

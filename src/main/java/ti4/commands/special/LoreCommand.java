package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.fow.LoreService;

class LoreCommand extends GameStateSubcommand {

    public LoreCommand() {
        super("lore", "Show buttons to manage Lore", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "You are not GM in this game.");
            return;
        }
        LoreService.showLoreButtons(event, null, game);
    }
}

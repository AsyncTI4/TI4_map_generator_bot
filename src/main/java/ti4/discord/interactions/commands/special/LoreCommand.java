package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
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
        if (!LoreService.isLoreEnabled(game)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Lore is only functional in non-FoW games if `lore_mode` is enabled via `/game weird-game-setup`."
                            + " You can still add/edit lore now, but it will not trigger until then.");
        }
        LoreService.showLoreButtons(event, null, game);
    }
}

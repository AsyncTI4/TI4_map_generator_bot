package ti4.commands.draft.manage;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerClearMissingPlayers extends GameStateSubcommand {
    public DraftManagerClearMissingPlayers() {
        super(
                Constants.DRAFT_MANAGE_CLEAR_MISSING_PLAYERS,
                "Remove all players that aren't in the game from the draft",
                true,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        List<String> removed = new ArrayList<>();
        for (String playerUserId : draftManager.getPlayerUserIds()) {
            if (game.getPlayer(playerUserId) == null) {
                removed.add(playerUserId);
                draftManager.removePlayer(playerUserId);
            }
        }
        if (removed.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "All drafting players are in the current game.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Cleared all missing players from the draft: " + String.join(", ", removed));
        }
    }
}

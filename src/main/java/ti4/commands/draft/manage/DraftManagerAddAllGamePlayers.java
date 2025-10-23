package ti4.commands.draft.manage;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerAddAllGamePlayers extends GameStateSubcommand {
    public DraftManagerAddAllGamePlayers() {
        super(
                Constants.DRAFT_MANAGE_ADD_ALL_GAME_PLAYERS,
                "Add all players in the game to the draft (if they aren't already)",
                true,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        List<String> added = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            if (player.isDummy() || player.isNpc()) {
                continue;
            }
            if (!draftManager.getPlayerStates().containsKey(player.getUserID())) {
                draftManager.addPlayer(player.getUserID());
                added.add(player.getPing());
            }
        }
        if (added.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "All players in the game are already in the draft.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Added all missing players from the game to the draft: " + String.join(", ", added));
        }
    }
}

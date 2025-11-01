package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerListPlayers extends GameStateSubcommand {
    public DraftManagerListPlayers() {
        super(Constants.DRAFT_MANAGE_LIST_PLAYERS, "List players in the draft", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        StringBuilder sb = new StringBuilder();
        sb.append("Players in draft:\n");
        for (String playerUserId : draftManager.getPlayerUserIds()) {
            sb.append("- ");
            if (game.getPlayer(playerUserId) != null) {
                sb.append(game.getPlayer(playerUserId).getPing());
            } else {
                sb.append(playerUserId + " (not in game)");
            }

            sb.append(System.lineSeparator());
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }
}

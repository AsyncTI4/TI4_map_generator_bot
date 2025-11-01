package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerSwapDraftingPlayers extends GameStateSubcommand {
    public DraftManagerSwapDraftingPlayers() {
        super(Constants.DRAFT_MANAGE_SWAP_PLAYERS, "Swap two players that are in the draft", true, false);
        addOption(OptionType.USER, Constants.PLAYER1, "First player to swap", true);
        addOption(OptionType.USER, Constants.PLAYER2, "Second player to swap", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String playerUserId1 = event.getOption(Constants.PLAYER1).getAsUser().getId();
        String playerUserId2 = event.getOption(Constants.PLAYER2).getAsUser().getId();
        try {
            draftManager.swapPlayers(playerUserId1, playerUserId2);
            if (draftManager.getOrchestrator() != null) {
                draftManager.getOrchestrator().sendDraftButtons(draftManager);
            }
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Swapped players in draft: "
                            + game.getPlayer(playerUserId1).getPing() + " and "
                            + game.getPlayer(playerUserId2).getPing());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not swap players in draft: " + e.getMessage());
        }
    }
}

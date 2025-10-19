package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerReplacePlayer extends GameStateSubcommand {
    public DraftManagerReplacePlayer() {
        super(
                Constants.DRAFT_MANAGE_REPLACE_PLAYER,
                "Replace one player in the draft with another player that's not currently drafting",
                true,
                false);
        addOption(OptionType.USER, Constants.PLAYER2, "Player to add to the draft", true);
        addOption(OptionType.USER, Constants.PLAYER1, "Player to remove from the draft", false);
        addOption(
                OptionType.STRING,
                Constants.UNKNOWN_DRAFT_USER_ID_OPTION,
                "The user ID of the player to remove, if they're not in the game",
                false,
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String oldPlayerUserId;
        if (event.getOption(Constants.PLAYER1) != null) {
            oldPlayerUserId = event.getOption(Constants.PLAYER1).getAsUser().getId();
        } else if (event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION) != null) {
            oldPlayerUserId =
                    event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION).getAsString();
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must provide a player to remove");
            return;
        }
        String newPlayerUserId = event.getOption(Constants.PLAYER2).getAsUser().getId();
        try {
            draftManager.replacePlayer(oldPlayerUserId, newPlayerUserId);
            if (draftManager.getOrchestrator() != null) {
                draftManager.getOrchestrator().sendDraftButtons(draftManager);
            }
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Replaced player in draft: " + oldPlayerUserId + " with "
                            + game.getPlayer(newPlayerUserId).getPing());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not replace player in draft: " + e.getMessage());
        }
    }
}

package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerRemovePlayer extends GameStateSubcommand {
    public DraftManagerRemovePlayer() {
        super(Constants.DRAFT_MANAGE_REMOVE_PLAYER, "Remove player from the draft", true, false);
        addOption(OptionType.USER, Constants.PLAYER, "Player to remove", false);
        addOption(
                OptionType.STRING,
                Constants.UNKNOWN_DRAFT_USER_ID_OPTION,
                "Player to remove (if not in game)",
                false,
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String playerUserId;
        if (event.getOption(Constants.PLAYER) != null) {
            playerUserId = event.getOption(Constants.PLAYER).getAsUser().getId();
        } else if (event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION) != null) {
            playerUserId =
                    event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION).getAsString();
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must provide a player to remove");
            return;
        }
        try {
            draftManager.removePlayer(playerUserId);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Removed player from draft: " + playerUserId);
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not remove player from draft: " + e.getMessage());
        }
    }
}

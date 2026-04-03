package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;

class DraftManagerAddPlayer extends GameStateSubcommand {
    public DraftManagerAddPlayer() {
        super(Constants.DRAFT_MANAGE_ADD_PLAYER, "Add player to the draft", true, false);
        addOption(OptionType.USER, Constants.PLAYER, "Player to add", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        var playerToAdd = event.getOption(Constants.PLAYER).getAsUser();
        String playerUserId = playerToAdd.getId();
        try {
            draftManager.addPlayer(playerUserId);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Added player to draft: " + playerToAdd.getAsMention());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not add player to draft: " + e.getMessage());
        }
    }
}

package ti4.commands.draft.publicsnake;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

class PublicSnakeDraftOrchestratorSetOrder extends GameStateSubcommand {

    protected PublicSnakeDraftOrchestratorSetOrder() {
        super(Constants.DRAFT_PUBLIC_SNAKE_SET_ORDER, "Set the draft order for the public snake draft", true, true);
        addOption(OptionType.USER, Constants.PLAYER1, "The user ID of the first player", true);
        addOption(OptionType.USER, Constants.PLAYER2, "The user ID of the second player", false);
        addOption(OptionType.USER, Constants.PLAYER3, "The user ID of the third player", false);
        addOption(OptionType.USER, Constants.PLAYER4, "The user ID of the fourth player", false);
        addOption(OptionType.USER, Constants.PLAYER5, "The user ID of the fifth player", false);
        addOption(OptionType.USER, Constants.PLAYER6, "The user ID of the sixth player", false);
        addOption(OptionType.USER, Constants.PLAYER7, "The user ID of the seventh player", false);
        addOption(OptionType.USER, Constants.PLAYER8, "The user ID of the eighth player", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PublicSnakeDraftOrchestrator orchestrator = PublicSnakeDraftGroup.getOrchestrator(getGame());
        if (orchestrator == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "The draft isn't using the Public Snake Draft Orchestrator; you may need `/draft manage set_orchestrator public_snake`.");
            return;
        }
        List<String> userIds = new ArrayList<>();
        if (event.getOption(Constants.PLAYER1) != null) {
            userIds.add(event.getOption(Constants.PLAYER1).getAsUser().getId());
        }
        if (event.getOption(Constants.PLAYER2) != null) {
            userIds.add(event.getOption(Constants.PLAYER2).getAsUser().getId());
        }
        if (event.getOption(Constants.PLAYER3) != null) {
            userIds.add(event.getOption(Constants.PLAYER3).getAsUser().getId());
        }
        if (event.getOption(Constants.PLAYER4) != null) {
            userIds.add(event.getOption(Constants.PLAYER4).getAsUser().getId());
        }
        if (event.getOption(Constants.PLAYER5) != null) {
            userIds.add(event.getOption(Constants.PLAYER5).getAsUser().getId());
        }
        if (event.getOption(Constants.PLAYER6) != null) {
            userIds.add(event.getOption(Constants.PLAYER6).getAsUser().getId());
        }
        if (event.getOption(Constants.PLAYER7) != null) {
            userIds.add(event.getOption(Constants.PLAYER7).getAsUser().getId());
        }
        if (event.getOption(Constants.PLAYER8) != null) {
            userIds.add(event.getOption(Constants.PLAYER8).getAsUser().getId());
        }
        try {
            orchestrator.setDraftOrder(getGame().getDraftManager(), userIds);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Draft order was set.");
            orchestrator.validateState(getGame().getDraftManager());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not set draft order: " + e.getMessage());
        }
    }
}

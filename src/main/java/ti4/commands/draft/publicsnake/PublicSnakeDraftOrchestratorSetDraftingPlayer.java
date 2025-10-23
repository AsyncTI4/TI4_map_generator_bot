package ti4.commands.draft.publicsnake;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

class PublicSnakeDraftOrchestratorSetDraftingPlayer extends GameStateSubcommand {
    protected PublicSnakeDraftOrchestratorSetDraftingPlayer() {
        super(Constants.DRAFT_PUBLIC_SNAKE_SET_CURRENT_PLAYER, "Set the current drafting player", true, false);
        addOption(OptionType.USER, Constants.PLAYER, "The user ID of the drafting player", true);
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

        String userId = event.getOption(Constants.PLAYER).getAsUser().getId();
        List<String> playerOrder = orchestrator.getDraftOrder(getGame().getDraftManager());
        if (!playerOrder.contains(userId)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player " + userId + " is not in the draft.");
            return;
        }

        orchestrator.setCurrentPlayerIndex(playerOrder.indexOf(userId));
        MessageHelper.sendMessageToChannel(event.getChannel(), "Drafting player was set.");
        orchestrator.validateState(getGame().getDraftManager());
    }
}

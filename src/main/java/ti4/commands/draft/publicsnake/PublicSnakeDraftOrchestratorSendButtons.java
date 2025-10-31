package ti4.commands.draft.publicsnake;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

class PublicSnakeDraftOrchestratorSendButtons extends GameStateSubcommand {
    protected PublicSnakeDraftOrchestratorSendButtons() {
        super(Constants.DRAFT_PUBLIC_SNAKE_SEND_BUTTONS, "Send draft buttons for the current draft", true, false);
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

        orchestrator.sendDraftButtons(getGame().getDraftManager());
    }
}

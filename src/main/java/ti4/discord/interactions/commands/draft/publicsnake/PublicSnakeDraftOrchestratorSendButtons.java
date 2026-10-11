package ti4.discord.interactions.commands.draft.publicsnake;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

class PublicSnakeDraftOrchestratorSendButtons extends GameStateSubcommand {
    protected PublicSnakeDraftOrchestratorSendButtons() {
        super(Constants.DRAFT_PUBLIC_SNAKE_SEND_BUTTONS, "Send draft buttons for the current draft", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PublicSnakeDraftOrchestrator orchestrator = CommandHelper.requireOrReport(
                event,
                PublicSnakeDraftGroup.getOrchestrator(getGame()),
                "The draft isn't using the Public Snake Draft Orchestrator; you may need `/draft manage set_orchestrator public_snake`.");
        if (orchestrator == null) return;

        orchestrator.sendDraftButtons(getGame().getDraftManager());
    }
}

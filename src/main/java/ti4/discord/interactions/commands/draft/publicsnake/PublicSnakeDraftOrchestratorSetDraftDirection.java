package ti4.discord.interactions.commands.draft.publicsnake;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

class PublicSnakeDraftOrchestratorSetDraftDirection extends GameStateSubcommand {
    protected PublicSnakeDraftOrchestratorSetDraftDirection() {
        super(Constants.DRAFT_PUBLIC_SNAKE_SET_DRAFT_DIRECTION, "Set the draft direction", true, false);
        addOption(
                OptionType.BOOLEAN,
                Constants.DRAFT_DIRECTION_FORWARD_OPTION,
                "Whether the draft direction is forward",
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PublicSnakeDraftOrchestrator orchestrator = CommandHelper.requireOrReport(
                event,
                PublicSnakeDraftGroup.getOrchestrator(getGame()),
                "The draft isn't using the Public Snake Draft Orchestrator; you may need `/draft manage set_orchestrator public_snake`.");
        if (orchestrator == null) return;

        boolean directionForward =
                event.getOption(Constants.DRAFT_DIRECTION_FORWARD_OPTION).getAsBoolean();
        orchestrator.setReversing(!directionForward);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Draft direction was set.");
        orchestrator.validateState(getGame().getDraftManager());
    }
}

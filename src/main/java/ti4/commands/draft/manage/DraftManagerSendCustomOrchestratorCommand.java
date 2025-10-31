package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftManager.CommandSource;
import ti4.service.draft.DraftOrchestrator;

class DraftManagerSendCustomOrchestratorCommand extends GameStateSubcommand {
    public DraftManagerSendCustomOrchestratorCommand() {
        super(
                Constants.DRAFT_MANAGE_CUSTOM_ORCHESTRATOR_COMMAND,
                "Send a custom command to the Orchestrator in the draft",
                true,
                false);
        addOption(OptionType.USER, Constants.PLAYER, "Player sending the command", true);
        addOption(OptionType.STRING, Constants.DRAFT_COMMAND_OPTION, "Key of the command", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String playerUserId = event.getOption(Constants.PLAYER).getAsUser().getId();
        String commandKey = event.getOption(Constants.DRAFT_COMMAND_OPTION).getAsString();
        DraftOrchestrator orchestrator = draftManager.getOrchestrator();
        if (orchestrator == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No orchestrator in the draft manager");
            return;
        }
        try {
            String outcome = draftManager.routeCommand(
                    event,
                    game.getPlayer(playerUserId),
                    orchestrator.getButtonPrefix() + "_" + commandKey,
                    CommandSource.SLASH_COMMAND);
            if (DraftButtonService.isError(outcome)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + outcome);
                return;
            }
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Sent custom command for player "
                            + game.getPlayer(playerUserId).getPing()
                            + ": "
                            + commandKey
                            + " from "
                            + orchestrator.getClass().getSimpleName());
            // TODO: Handle magic strings from routeCommand, such as "delete button"
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + e.getMessage());
        }
    }
}

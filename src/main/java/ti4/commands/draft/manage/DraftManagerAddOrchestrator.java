package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftComponentFactory;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftOrchestrator;

class DraftManagerAddOrchestrator extends GameStateSubcommand {

    public DraftManagerAddOrchestrator() {
        super(Constants.DRAFT_MANAGE_SET_ORCHESTRATOR, "Set the orchestrator for the draft manager", true, false);
        addOption(OptionType.STRING, Constants.SET_ORCHESTRATOR_OPTION, "Type of orchestrator to use", true, true);
        addOption(
                OptionType.STRING,
                Constants.SAVE_DATA_OPTION,
                "Optional line of game save data to load into this orchestrator",
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String orchestratorType = event.getOption(Constants.SET_ORCHESTRATOR_OPTION, OptionMapping::getAsString);
        DraftOrchestrator orchestrator = DraftComponentFactory.createOrchestrator(orchestratorType);
        if (orchestrator == null) {
            orchestrator = DraftComponentFactory.createOrchestrator(orchestratorType + "Orchestrator");
        }
        if (orchestrator == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown orchestrator type: " + orchestratorType);
            return;
        }
        draftManager.setOrchestrator(orchestrator);
        String saveData = event.getOption(Constants.SAVE_DATA_OPTION, null, OptionMapping::getAsString);
        if (saveData != null) {
            orchestrator.load(saveData);
            orchestrator.validateState(draftManager);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Set and loaded orchestrator of type: " + orchestratorType);
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Set orchestrator of type: " + orchestratorType + "; be sure to configure it!");
        }
    }
}

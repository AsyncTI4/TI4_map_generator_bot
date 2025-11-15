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
import ti4.service.draft.Draftable;

class DraftManagerAddDraftable extends GameStateSubcommand {

    public DraftManagerAddDraftable() {
        super(Constants.DRAFT_MANAGE_ADD_DRAFTABLE, "Add a draftable to the draft manager", true, false);
        addOption(OptionType.STRING, Constants.ADD_DRAFTABLE_OPTION, "Type of draftable to add", true, true);
        addOption(
                OptionType.STRING,
                Constants.SAVE_DATA_OPTION,
                "Optional line of game save data to load into this draftable",
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String draftableType = event.getOption(Constants.ADD_DRAFTABLE_OPTION, OptionMapping::getAsString);
        Draftable draftable = DraftComponentFactory.createDraftable(draftableType);
        if (draftable == null) {
            draftable = DraftComponentFactory.createDraftable(draftableType + "Draftable");
        }

        if (draftable == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown draftable type: " + draftableType);
            return;
        }
        draftManager.addDraftable(draftable);
        String saveData = event.getOption(Constants.SAVE_DATA_OPTION, null, OptionMapping::getAsString);
        if (saveData != null) {
            draftable.load(saveData);
            draftable.validateState(draftManager);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Added and loaded draftable of type: " + draftableType);
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Added draftable of type: " + draftableType + "; be sure to configure it!");
        }
    }
}

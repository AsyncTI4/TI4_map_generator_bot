package ti4.commands.draft.speakerorder;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.SpeakerOrderDraftable;

class SpeakerOrderDraftableSetPickCount extends GameStateSubcommand {

    protected SpeakerOrderDraftableSetPickCount() {
        super(Constants.DRAFT_SPEAKER_ORDER_SET_PICK_COUNT, "Set the number of pick orders in the draft", true, false);
        addOption(
                OptionType.INTEGER,
                Constants.PICK_COUNT_OPTION,
                "The number of speaker positions to pick (defaults to player count)",
                false,
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SpeakerOrderDraftable draftable = SpeakerOrderDraftableGroup.getDraftable(getGame());
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Speaker order isn't draftable; you may need `/draft manage add_draftable SpeakerOrder`.");
            return;
        }
        OptionMapping optionData = event.getOption(Constants.PICK_COUNT_OPTION);
        int numPicks;
        if (optionData != null) {
            numPicks = optionData.getAsInt();
        } else {
            numPicks = getGame().getDraftManager().getPlayerStates().size();
        }
        draftable.setNumPicks(numPicks);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Set the number of picks in the draft to " + draftable.getNumPicks() + ".");
        draftable.validateState(getGame().getDraftManager());
    }
}

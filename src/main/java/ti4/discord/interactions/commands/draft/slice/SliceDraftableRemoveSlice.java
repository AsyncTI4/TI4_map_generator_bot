package ti4.discord.interactions.commands.draft.slice;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.milty.MiltyDraftSlice;

class SliceDraftableRemoveSlice extends GameStateSubcommand {

    SliceDraftableRemoveSlice() {
        super(Constants.DRAFT_SLICE_REMOVE_SLICE, "Remove an existing slice by name", true, false);
        addOption(OptionType.STRING, Constants.DRAFT_SLICE_OPTION, "The name of the slice to remove", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SliceDraftable draftable = SliceDraftableGroup.getDraftableOrReply(event, getGame());
        if (draftable == null) return;

        String sliceName = event.getOption(Constants.DRAFT_SLICE_OPTION).getAsString();
        MiltyDraftSlice slice = CommandHelper.requireOrReport(
                event, draftable.getSliceByName(sliceName), "Slice with name '" + sliceName + "' does not exist.");
        if (slice == null) return;

        draftable.getSlices().remove(slice);
        draftable.validateState(getGame().getDraftManager());
    }
}

package ti4.commands.draft;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.SpeakerOrderDraftable;

public class SpeakerOrderDraftableSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(new SpeakerOrderDraftableSetPickCount())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    protected SpeakerOrderDraftableSubcommands() {
        super(Constants.DRAFT_SPEAKER_ORDER, "Commands for managing speaker order drafting");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static SpeakerOrderDraftable getDraftable(Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            return null;
        }
        return (SpeakerOrderDraftable) draftManager.getDraftableByType(SpeakerOrderDraftable.TYPE);
    }

    public static class SpeakerOrderDraftableSetPickCount extends GameStateSubcommand {

        protected SpeakerOrderDraftableSetPickCount() {
            super(
                    Constants.DRAFT_SPEAKER_ORDER_SET_PICK_COUNT,
                    "Set the number of pick orders in the draft",
                    true,
                    false);
            addOption(
                    OptionType.INTEGER,
                    Constants.PICK_COUNT_OPTION,
                    "The number of speaker positions to pick (defaults to player count)",
                    false,
                    true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            SpeakerOrderDraftable draftable = getDraftable(getGame());
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
}

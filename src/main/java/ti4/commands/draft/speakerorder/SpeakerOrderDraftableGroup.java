package ti4.commands.draft.speakerorder;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.SpeakerOrderDraftable;

public class SpeakerOrderDraftableGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(new SpeakerOrderDraftableSetPickCount())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public SpeakerOrderDraftableGroup() {
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
        return (SpeakerOrderDraftable) draftManager.getDraftable(SpeakerOrderDraftable.TYPE);
    }
}

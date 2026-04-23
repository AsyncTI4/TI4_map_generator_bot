package ti4.discord.interactions.slashcommands.draft.speakerorder;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.discord.interactions.slashcommands.SubcommandGroup;
import ti4.game.Game;
import ti4.helpers.Constants;
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

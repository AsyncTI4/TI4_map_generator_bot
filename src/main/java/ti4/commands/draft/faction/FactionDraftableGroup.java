package ti4.commands.draft.faction;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.FactionDraftable;

public class FactionDraftableGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new FactionDraftableAddFaction(),
                    new FactionDraftableRemoveFaction(),
                    new FactionDraftableSetKeleresFlavor())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public FactionDraftableGroup() {
        super(Constants.DRAFT_FACTION, "Commands for managing faction drafting");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static FactionDraftable getDraftable(Game game) {
        DraftManager draftManager = game.getDraftManager();
        return (FactionDraftable) draftManager.getDraftable(FactionDraftable.TYPE);
    }
}

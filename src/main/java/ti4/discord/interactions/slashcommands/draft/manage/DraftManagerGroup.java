package ti4.discord.interactions.slashcommands.draft.manage;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.discord.interactions.slashcommands.SubcommandGroup;
import ti4.helpers.Constants;

public class DraftManagerGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new DraftManagerDebug(),
                    new DraftManagerValidateState(),
                    new DraftManagerStartDraft(),
                    new DraftManagerCanEndDraft(),
                    new DraftManagerTryEndDraft(),
                    new DraftManagerPostDraftWork(),
                    new DraftManagerCanSetupPlayers(),
                    new DraftManagerSetupPlayers(),
                    new DraftManagerAddDraftable(),
                    new DraftManagerAddOrchestrator(),
                    new DraftManagerAddPlayer(),
                    new DraftManagerRemoveDraftable(),
                    new DraftManagerRemovePlayer(),
                    new DraftManagerListPlayers(),
                    new DraftManagerClearMissingPlayers(),
                    new DraftManagerAddAllGamePlayers(),
                    new DraftManagerSwapDraftingPlayers(),
                    new DraftManagerReplacePlayer(),
                    new DraftManagerMakePick(),
                    new DraftManagerUnpick(),
                    new DraftManagerSendCustomDraftableCommand(),
                    new DraftManagerSendCustomOrchestratorCommand(),
                    new DraftManagerSetupDraft(),
                    new DraftManagerSetupNucleusCommand())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public DraftManagerGroup() {
        super(Constants.DRAFT_MANAGE, "Commands for managing an active draft");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }
}

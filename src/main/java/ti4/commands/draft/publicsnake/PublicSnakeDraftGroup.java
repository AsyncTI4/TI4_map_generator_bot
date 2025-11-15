package ti4.commands.draft.publicsnake;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

public class PublicSnakeDraftGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new PublicSnakeDraftOrchestratorSetOrder(),
                    new PublicSnakeDraftOrchestratorSetDraftingPlayer(),
                    new PublicSnakeDraftOrchestratorSetDraftDirection(),
                    new PublicSnakeDraftOrchestratorSendButtons())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public PublicSnakeDraftGroup() {
        super(Constants.DRAFT_PUBLIC_SNAKE, "Commands for managing public snake drafting");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static PublicSnakeDraftOrchestrator getOrchestrator(Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            return null;
        }
        DraftOrchestrator orchestrator = draftManager.getOrchestrator();
        if (!(orchestrator instanceof PublicSnakeDraftOrchestrator)) {
            return null;
        }
        return (PublicSnakeDraftOrchestrator) orchestrator;
    }
}

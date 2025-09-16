package ti4.service.draft;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;

public abstract class DraftOrchestrator extends DraftLifecycleHooks {
    public abstract void startDraft(DraftManager draftManager);

    // Interactions

    // Get the prefix this handler uses with all its buttons.
    public abstract String getButtonPrefix();

    public abstract String handleCustomButtonPress(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId);

    public abstract String applyDraftChoice(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice);

    // Persistence

    public abstract String save();

    public abstract void load(String data);

    public abstract String[] savePlayerStates(DraftManager draftManager);

    public abstract OrchestratorState loadPlayerState(String data);

    public abstract String validateState(DraftManager draftManager);
}

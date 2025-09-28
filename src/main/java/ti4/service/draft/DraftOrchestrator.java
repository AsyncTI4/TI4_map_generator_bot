package ti4.service.draft;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.service.draft.DraftManager.CommandSource;

public abstract class DraftOrchestrator extends DraftLifecycleHooks {
    public abstract void sendDraftButtons(DraftManager draftManager);

    // Interactions

    // Get the prefix this handler uses with all its buttons.
    public abstract String getButtonPrefix();

    public abstract String handleCustomButtonPress(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId);

    public abstract String applyDraftChoice(
            GenericInteractionCreateEvent event,
            DraftManager draftManager,
            String playerUserId,
            DraftChoice choice,
            CommandSource source);

    // Persistence

    public static final String SAVE_SEPARATOR = ",";

    public abstract String save();

    public abstract void load(String data);

    public abstract String savePlayerState(OrchestratorState state);

    public abstract OrchestratorState loadPlayerState(String data);

    public abstract String validateState(DraftManager draftManager);

    // Setup

    public abstract void initializePlayerStates(DraftPlayerManager draftManager);

    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        // Do nothing by default
        return null;
    }
}

package ti4.service.draft.draftables;

import java.util.List;
import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.map.Player;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;

public class MahactKingDraftable extends SinglePickDraftable {

    @Override
    public DraftableType getType() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getType'");
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAllDraftChoices'");
    }

    @Override
    public String handleCustomCommand(GenericInteractionCreateEvent event, DraftManager draftManager,
            String playerUserId, String commandKey) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleCustomCommand'");
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNothingPickedChoice'");
    }

    @Override
    public String save() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'save'");
    }

    @Override
    public void load(String data) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'load'");
    }

    @Override
    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'applySetupMenuChoices'");
    }

    @Override
    public Consumer<Player> setupPlayer(DraftManager draftManager, String playerUserId,
            PlayerSetupState playerSetupState) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setupPlayer'");
    }
    
}

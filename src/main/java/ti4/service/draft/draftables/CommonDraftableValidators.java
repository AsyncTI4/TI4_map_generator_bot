package ti4.service.draft.draftables;

import java.util.List;

import lombok.experimental.UtilityClass;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerDraftState;

/**
 * Some helper methods for common things to validate.
 */
@UtilityClass
public class CommonDraftableValidators {
    public boolean isChoiceKeyInList(DraftChoice choice, List<String> validChoiceKeys) {
        for (String validChoice : validChoiceKeys) {
            if (validChoice.equals(choice.getChoiceKey())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRemainingChoices(DraftManager draftManager, String playerUserId, DraftableType type, int maxChoices) {
                PlayerDraftState pState = draftManager.getPlayerStates().get(playerUserId);
        if(pState.getPicks().containsKey(type) && 
           pState.getPicks().get(type).size() >= maxChoices) {
            return false;
        }
        return true;
    }
}

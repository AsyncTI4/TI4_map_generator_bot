package ti4.service.draft.draftables;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ti4.map.Player;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.Draftable;

/**
 * Provide several implementations for common draftable methods which require
 * each player to pick exactly N of its choices.
 */
public abstract class FixedNumberDraftable extends Draftable {

    /**
     * Get the number of total picks each player is allowed and required to make for this
     * draftable.
     *
     * @return the number of picks per player
     */
    public abstract int getNumPicksPerPlayer();

    @Override
    public String isValidDraftChoice(DraftManager draftManager, String playerUserId, DraftChoice choice) {
        if (!CommonDraftableValidators.hasRemainingChoices(
                draftManager, playerUserId, getType(), getNumPicksPerPlayer())) {
            return "You already have picked " + getNumPicksPerPlayer() + " " + getDisplayName() + "!";
        }

        return super.isValidDraftChoice(draftManager, playerUserId, choice);
    }

    @Override
    public List<DraftChoice> getDeterministicPick(
            DraftManager draftManager, String playerUserId, int numberOfSimultaneousPicks) {
        if (draftManager.getPlayerPicks(playerUserId, getType()).size() >= getNumPicksPerPlayer()) {
            return List.of();
        }
        // If you can only choose one thing from the draftable,
        // numberofSimultaneousPicks is irrelevant.
        List<DraftChoice> allChoices = getAllDraftChoices();
        List<DraftChoice> allPicks = draftManager.getAllPicksOfType(getType());
        List<DraftChoice> remainingChoices =
                allChoices.stream().filter(c -> !allPicks.contains(c)).toList();
        if (remainingChoices.size() <= getNumPicksPerPlayer()) {
            return remainingChoices;
        }
        return null;
    }

    @Override
    public String validateState(DraftManager draftManager) {
        // Ensure no two players have picked the same thing.
        List<DraftChoice> picks = draftManager.getAllPicksOfType(getType());
        Set<String> pickSet = new HashSet<>();
        for (DraftChoice choice : picks) {
            if (pickSet.contains(choice.getChoiceKey())) {
                return "Multiple players have chosen " + choice.getChoiceKey() + " from draftable " + getType();
            }
            pickSet.add(choice.getChoiceKey());
        }

        return null;
    }

    @Override
    public String whatsStoppingDraftEnd(DraftManager draftManager) {
        for (String playerUserId : draftManager.getPlayerStates().keySet()) {
            int pickCount = draftManager.getPlayerPicks(playerUserId, getType()).size();
            if (pickCount < getNumPicksPerPlayer()) {
                Player player = draftManager.getGame().getPlayer(playerUserId);
                return "Player " + (player != null ? player.getRepresentation() : playerUserId) + " needs to make " + (getNumPicksPerPlayer() - pickCount)
                        + " more pick(s) for " + getDisplayName() + "!";
            }
        }
        return null;
    }
}

package ti4.service.actioncard;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.StringHelper;

@UtilityClass
public class KnownActionCardsService {

    private static final String KNOWN_ACTION_CARDS_PREFIX = "knownActionCards_";

    public static void rememberViewedHand(Player viewer, Player target) {
        if (viewer == null || target == null || viewer == target) {
            return;
        }

        String storageKey = getStorageKey(target);
        viewer.removeStoredValue(storageKey);
        List<String> knownCards = new ArrayList<>(target.getActionCards().keySet());
        if (!knownCards.isEmpty()) {
            viewer.addToStoredList(storageKey, knownCards.toArray(String[]::new));
        }
    }

    public static void rememberShownActionCard(Player viewer, Player target, String actionCardId) {
        if (viewer == null || target == null || viewer == target || isUnset(actionCardId)) {
            return;
        }

        String storageKey = getStorageKey(target);
        if (!viewer.getStoredList(storageKey).contains(actionCardId)) {
            viewer.addToStoredList(storageKey, actionCardId);
        }
    }

    public static void rememberShownActionCardToAll(Game game, Player target, String actionCardId) {
        if (game == null || target == null || isUnset(actionCardId)) {
            return;
        }

        for (Player player : game.getRealPlayers()) {
            rememberShownActionCard(player, target, actionCardId);
        }
    }

    public static void forgetCardFromKnownHands(Game game, Player target, String actionCardId) {
        if (game == null || target == null || actionCardId == null || actionCardId.isBlank()) {
            return;
        }

        String storageKey = getStorageKey(target);
        for (Player player : game.getPlayers().values()) {
            if (player == target || !player.hasStoredValue(storageKey)) {
                continue;
            }
            player.removeFromStoredList(storageKey, actionCardId);
        }
    }

    public static boolean shouldShowKnownActionCardsButton(Player player) {
        return player != null
                && (player.hasTech("mi")
                        || player.getExhaustedTechs().contains("mi")
                        || player.getStoredValueMap().keySet().stream()
                                .map(StringHelper::unescape)
                                .anyMatch(key -> key.startsWith(KNOWN_ACTION_CARDS_PREFIX)));
    }

    public static String getKnownActionCardsText(Game game, Player viewer) {
        StringBuilder sb = new StringBuilder("__Known action cards in other players' hands__:");
        boolean hasAnyKnownCards = false;

        for (Player target : game.getRealPlayers()) {
            if (target == viewer) {
                continue;
            }

            List<String> knownCards = viewer.getStoredList(getStorageKey(target));
            if (knownCards.isEmpty()) {
                continue;
            }

            hasAnyKnownCards = true;
            sb.append("\n### ").append(target.getRepresentationNoPing()).append('\n');
            sb.append(ActionCardHelper.actionCardListCondensedNoIds(knownCards, null))
                    .append('\n');
        }

        if (!hasAnyKnownCards) {
            sb.append("\n> None");
        }

        return sb.toString().trim();
    }

    private static String getStorageKey(Player target) {
        String targetId = target.getFaction();
        if (isUnset(targetId)) {
            targetId = target.getColor();
        }
        if (isUnset(targetId)) {
            targetId = target.getUserID();
        }
        if (isUnset(targetId)) {
            targetId = target.getUserName();
        }
        if (isUnset(targetId)) {
            targetId = "unknownPlayer";
        }
        return KNOWN_ACTION_CARDS_PREFIX + targetId;
    }

    private static boolean isUnset(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value);
    }
}

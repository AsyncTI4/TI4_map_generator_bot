package ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

@UtilityClass
public class XyrixLeadersHandler {
    private static final String HERO = "xyrixhero";
    private static final String TAKE_ACTION_CARD = "xyrixHeroTakeActionCard_";
    private static final String ACTION_CARD_SEPARATOR = "|";
    private static final String CARD_TAKEN = "xyrixHeroCardTaken_";

    public static void startHero(Game game, Player player) {
        if (!hasActiveHero(player) || cardWasTaken(game, player)) {
            return;
        }
        List<Button> buttons = actionCardButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " has no eligible action card to add with _Intillectual Immunity_.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", you may add 1 action card from the discard pile or purged cards to your hand with _Intillectual Immunity_.",
                buttons,
                List.of(Buttons.DONE_DELETE_BUTTONS),
                actionCardPrefix(player));
    }

    @ButtonHandler(TAKE_ACTION_CARD)
    public static void takeActionCard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!hasActiveHero(player) || cardWasTaken(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = actionCardButtons(game, player);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCardsInfoThread(),
                buttons,
                List.of(Buttons.DONE_DELETE_BUTTONS),
                player.getRepresentationNoPing()
                        + ", you may add 1 action card from the discard pile or purged cards to your hand with _Intillectual Immunity_.",
                actionCardPrefix(player),
                buttonID)) {
            return;
        }
        String[] payload = buttonID.substring(TAKE_ACTION_CARD.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Integer actionCardIndex = parseActionCardIndex(payload[1]);
        boolean purged = "purged".equals(payload[0]);
        if (actionCardIndex == null || !takeActionCard(game, player, actionCardIndex, purged)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(CARD_TAKEN + player.getFaction(), "true");
        ActionCardHelper.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " added an action card from "
                        + (purged ? "the purged pile" : "the discard pile") + " with _Intillectual Immunity_.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean preventsOpponentActionCards(Game game, Player player) {
        Player activePlayer = game.getPlayer(game.getActivePlayerID());
        return "action".equalsIgnoreCase(game.getPhaseOfGame())
                && activePlayer != null
                && activePlayer != player
                && hasActiveHero(activePlayer);
    }

    public static void clearHeroState(Game game, Player player) {
        game.removeStoredValue(CARD_TAKEN + player.getFaction());
    }

    private static boolean hasActiveHero(Player player) {
        return player.getLeader(HERO).map(Leader::isActive).orElse(false);
    }

    private static boolean cardWasTaken(Game game, Player player) {
        return "true".equals(game.getStoredValue(CARD_TAKEN + player.getFaction()));
    }

    private static List<Button> actionCardButtons(Game game, Player player) {
        return game.getDiscardActionCards().entrySet().stream()
                .filter(entry -> isEligibleActionCard(game, player, entry))
                .sorted(Comparator.comparing(
                        entry -> Mapper.getActionCard(entry.getKey()).getName()))
                .map(entry -> Buttons.green(
                        actionCardPrefix(player)
                                + (game.getDiscardACStatus().get(entry.getKey()) == ACStatus.purged
                                        ? "purged"
                                        : "discard")
                                + ACTION_CARD_SEPARATOR
                                + entry.getValue(),
                        Mapper.getActionCard(entry.getKey()).getName()))
                .toList();
    }

    private static boolean isEligibleActionCard(Game game, Player player, Map.Entry<String, Integer> entry) {
        ACStatus status = game.getDiscardACStatus().get(entry.getKey());
        return status == ACStatus.purged
                || (status == null && ActionCardHelper.isDiscardVisible(game, player, entry.getKey()));
    }

    private static boolean takeActionCard(Game game, Player player, int actionCardIndex, boolean purged) {
        return purged
                ? game.pickActionCardFromPurged(player.getUserID(), actionCardIndex)
                : game.pickActionCard(player.getUserID(), actionCardIndex);
    }

    private static String actionCardPrefix(Player player) {
        return player.factionButtonChecker() + TAKE_ACTION_CARD;
    }

    private static Integer parseActionCardIndex(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

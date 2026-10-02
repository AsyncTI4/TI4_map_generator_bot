package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class SarcosaHeroHandler {
    private static final String LEVIATHAN_HITS = "sarcosaLeviathanHits_";
    private static final String NEUTRAL_CONTROL = "sarcosaHeroNeutralControl";
    private static final String REMAINING_ACTIONS = "sarcosaHeroRemainingActions_";
    private static final String CONTINUE_ACTIONS = "sarcosaHeroContinueActions_";
    private static final String USE_HERO = "useSarcosaHero";
    private static final String SELECT_SYSTEM = "selectSarcosaHeroSystem_";
    private static final String BEGIN_NEUTRAL_TACTICAL = "beginSarcosaHeroNeutralTactical";
    private static final String DECLINE = "declineSarcosaHero";

    public static void offerPassAbility(Game game, Player player) {
        if (!player.hasLeaderUnlocked("sarcosahero")) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " may use **Todlys Unbound** to place a neutral war sun, then resolve tactical actions using neutral units."
                        + " **NOTE** the next player's start turn will appear, once all tactical actions have concluded or the sarcosa hero player declines to use it, their start turn button will appear again.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + USE_HERO, "Use Todlys Unbound"),
                        Buttons.red(player.factionButtonChecker() + DECLINE, "Decline")));
    }

    @ButtonHandler(USE_HERO)
    public static void useHero(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasLeaderUnlocked("sarcosahero")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (neutral == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The neutral player is not available.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getTileModel().isHyperlane()
                    || !FoWHelper.knowsTile(game, player, tile.getPosition())
                    || !tile.containsPlayersUnits(neutral)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_SYSTEM + tile.getPosition(),
                    "Place neutral war sun in " + tile.getRepresentationForButtons(game, player),
                    UnitType.Warsun.getUnitTypeEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no systems containing neutral units.");
            return;
        }
        MessageHelper.editMessageWithButtons(event, "Choose a system containing neutral units.", buttons);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void selectSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_SYSTEM.length()));
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (tile == null
                || neutral == null
                || tile.getTileModel().isHyperlane()
                || !FoWHelper.knowsTile(game, player, tile.getPosition())
                || !tile.containsPlayersUnits(neutral)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, neutral.getColor(), "ws");
        game.setStoredValue(REMAINING_ACTIONS + player.getFaction(), Integer.toString(player.getSoScored()));
        ButtonHelper.deleteMessage(event);
        offerNeutralTacticalAction(game, player);
    }

    public static void offerNeutralTacticalAction(Game game, Player player) {
        int remainingActions = getRemainingActions(game, player);
        if (remainingActions < 1) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " may resolve " + remainingActions + " neutral tactical action"
                        + (remainingActions == 1 ? "" : "s") + " with **Todlys Unbound**.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + BEGIN_NEUTRAL_TACTICAL,
                                "Resolve Neutral Tactical Action"),
                        Buttons.red(player.factionButtonChecker() + DECLINE, "Decline Remaining Actions")));
    }

    @ButtonHandler(BEGIN_NEUTRAL_TACTICAL)
    public static void beginNeutralTacticalAction(ButtonInteractionEvent event, Game game, Player player) {
        if (getRemainingActions(game, player) < 1 || player.getTacticalCC() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(NEUTRAL_CONTROL, player.getFaction());
        ButtonHelper.deleteMessage(event);
        ti4.helpers.ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        ti4.helpers.ButtonHelperTacticalAction.beginTacticalAction(game, player);
    }

    @ButtonHandler(DECLINE)
    public static void decline(ButtonInteractionEvent event, Game game, Player player) {
        game.removeStoredValue(REMAINING_ACTIONS + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    public static boolean isControllingNeutralUnits(Game game, Player player) {
        return game != null && player != null && player.getFaction().equals(game.getStoredValue(NEUTRAL_CONTROL));
    }

    public static void completeNeutralTacticalAction(Game game, Player player) {
        if (!isControllingNeutralUnits(game, player)) {
            return;
        }
        game.removeStoredValue(NEUTRAL_CONTROL);
        int remainingActions = Math.max(0, getRemainingActions(game, player) - 1);
        if (remainingActions == 0) {
            game.removeStoredValue(REMAINING_ACTIONS + player.getFaction());
        } else {
            game.setStoredValue(REMAINING_ACTIONS + player.getFaction(), Integer.toString(remainingActions));
            game.setStoredValue(CONTINUE_ACTIONS + player.getFaction(), "yes");
            offerNeutralTacticalAction(game, player);
        }
    }

    public static boolean consumeNeutralTacticalActionContinuation(Game game, Player player) {
        String key = CONTINUE_ACTIONS + player.getFaction();
        if (game.getStoredValue(key).isBlank()) {
            return false;
        }
        game.removeStoredValue(key);
        return true;
    }

    private static int getRemainingActions(Game game, Player player) {
        String value = game.getStoredValue(REMAINING_ACTIONS + player.getFaction());
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    public static boolean isLeviathan(Game game, Player player, UnitModel unitModel, CombatRollType rollType) {
        return isLeviathanActive(game)
                && rollType == CombatRollType.combatround
                && (player.isNeutral() || isControllingNeutralUnits(game, player))
                && unitModel.getUnitType() == UnitType.Warsun;
    }

    public static boolean isLeviathanCombatant(Game game, Player player) {
        return isLeviathanActive(game) && (player.isNeutral() || isControllingNeutralUnits(game, player));
    }

    private static boolean isLeviathanActive(Game game) {
        return game.getRealPlayers().stream().anyMatch(player -> player.hasLeaderUnlocked("sarcosahero"));
    }

    public static boolean isFirstCombatRound(Game game, Player player, Tile tile, UnitHolder combatOnHolder) {
        String key = "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
        return game.getStoredValue(key).isBlank();
    }

    public static int doubleFirstRoundLeviathanHits(
            Game game,
            Player player,
            Tile tile,
            UnitHolder combatOnHolder,
            UnitModel unitModel,
            CombatRollType rollType,
            int hits) {
        if (!isLeviathan(game, player, unitModel, rollType)
                || !isFirstCombatRound(game, player, tile, combatOnHolder)) {
            return hits;
        }
        return hits * 2;
    }

    public static void recordLeviathanCancellation(
            Game game,
            Player player,
            Tile tile,
            UnitHolder combatOnHolder,
            UnitModel unitModel,
            CombatRollType rollType,
            int rolledHits) {
        if (!isLeviathan(game, player, unitModel, rollType) || rolledHits < 1) {
            return;
        }

        String key = getCancellationKey(game, player, tile, combatOnHolder, true);
        int availableCancellations = getAvailableCancellations(game, player, tile, combatOnHolder, true);
        game.setStoredValue(key, Integer.toString(availableCancellations + 2));
    }

    public static int consumeLeviathanCancellations(
            Game game, Player neutralPlayer, Tile tile, UnitHolder combatOnHolder, int incomingHits) {
        int availableCancellations = getAvailableCancellations(game, neutralPlayer, tile, combatOnHolder, false);
        int cancelledHits = Math.min(incomingHits, availableCancellations);

        if (cancelledHits < 1) {
            return 0;
        }

        game.setStoredValue(
                getCancellationKey(game, neutralPlayer, tile, combatOnHolder, false),
                Integer.toString(availableCancellations - cancelledHits));
        return cancelledHits;
    }

    private static int getAvailableCancellations(
            Game game, Player neutralPlayer, Tile tile, UnitHolder combatOnHolder, boolean beforeCombatSummary) {
        String value = game.getStoredValue(getCancellationKey(game, neutralPlayer, tile, combatOnHolder, beforeCombatSummary));
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static String getCancellationKey(
            Game game, Player neutralPlayer, Tile tile, UnitHolder combatOnHolder, boolean beforeCombatSummary) {
        String roundKey =
                "combatRoundTracker" + neutralPlayer.getFaction() + tile.getPosition() + combatOnHolder.getName();
        String round = game.getStoredValue(roundKey);
        int roundNumber = round.isBlank() ? 0 : Integer.parseInt(round);
        if (beforeCombatSummary) {
            roundNumber++;
        }

        return LEVIATHAN_HITS + tile.getPosition() + "_" + combatOnHolder.getName() + "_" + roundNumber;
    }
}

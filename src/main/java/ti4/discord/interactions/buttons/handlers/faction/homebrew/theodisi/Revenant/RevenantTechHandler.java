package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class RevenantTechHandler {
    private static final String ETERNAL_AEGIS = "threvenantr";
    private static final String USE_ETERNAL_AEGIS = "useEternalAegis_";
    private static final String LAZARUS_PODS = "threvenanty";
    private static final String SELECT_LAZARUS_SYSTEM = "selectLazarusPodsSystem_";
    private static final String LAZARUS_PRODUCTION = "lazarusPodsProduction_";

    // Yellow
    public static void getProduceShipsInSystemsWithShipsButtons(Game game, Player player) {
        if (game == null || player == null || !player.hasTech(LAZARUS_PODS)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();

        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasActualShipsInSystem(player, tile)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_LAZARUS_SYSTEM + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        if (buttons.isEmpty()) {
            return;
        }

        String message = player.getRepresentation()
                + ", you may produce up to 2 units in a system containing 1 of your ships due to _Lazarus Pods_. Please choose the system in which you wish to produce.";
        String prefix = player.factionButtonChecker() + SELECT_LAZARUS_SYSTEM;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(extraButtons);
        if (displayedButtons.size() > 25) {
            displayedButtons = NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, displayedButtons);
    }

    public static void doLazarusPodsLeaderCheck(Game game) {
        if (game == null) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            getProduceShipsInSystemsWithShipsButtons(game, player);
        }
    }

    @ButtonHandler(SELECT_LAZARUS_SYSTEM)
    public static void resolveLazarusPods(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(LAZARUS_PODS)) {
            return;
        }

        List<Button> systemButtons = game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_LAZARUS_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
        String message = player.getRepresentation()
                + ", you may produce up to 2 units in a system containing 1 of your ships due to _Lazarus Pods_. Please choose the system in which you wish to produce.";
        String prefix = player.factionButtonChecker() + SELECT_LAZARUS_SYSTEM;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), systemButtons, extraButtons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.replace(SELECT_LAZARUS_SYSTEM, "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Unable to resolve that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(LAZARUS_PRODUCTION + player.getFaction(), position);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose up to 2 units to produce in "
                        + tile.getRepresentationForButtons(game, player) + " due to _Lazarus Pods_.",
                Helper.getPlaceUnitButtons(event, player, game, tile, "lazarusPods", "place"));
    }

    public static boolean canProduceLazarusUnit(
            ButtonInteractionEvent event, Game game, Player player, String unitAlias, String location) {
        String activePosition = game.getStoredValue(LAZARUS_PRODUCTION + player.getFaction());
        if (activePosition.isEmpty()) {
            return true;
        }

        Tile selectedTile = game.getTileByPosition(location.replace("space", ""));
        if (selectedTile == null) {
            selectedTile = game.getTileFromPlanet(location);
        }
        if (selectedTile == null || !activePosition.equals(selectedTile.getPosition())) {
            return true;
        }

        int alreadyProduced = player.getCurrentProducedUnits().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int requestedUnits = 1;
        int digitCount = 0;
        while (digitCount < unitAlias.length() && Character.isDigit(unitAlias.charAt(digitCount))) {
            digitCount++;
        }
        if (digitCount > 0) {
            requestedUnits = Integer.parseInt(unitAlias.substring(0, digitCount));
        }
        if (alreadyProduced + requestedUnits <= 2) {
            return true;
        }

        MessageHelper.sendEphemeralMessageToEventChannel(event, "_Lazarus Pods_ can produce no more than 2 units.");
        return false;
    }

    public static void clearLazarusProduction(Game game, Player player) {
        if (game != null && player != null) {
            game.removeStoredValue(LAZARUS_PRODUCTION + player.getFaction());
        }
    }

    public static void clearLazarusProduction(Game game) {
        if (game == null) {
            return;
        }
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(LAZARUS_PRODUCTION))
                .toList()
                .forEach(game::removeStoredValue);
    }

    // Red
    public static void addEternalAegisButton(
            List<Button> buttons,
            Game game,
            Player defender,
            Player attacker,
            Tile tile,
            UnitHolder combatOnHolder,
            int hits) {
        int canceledHits = Math.min(hits, getEternalAegisCancellationCount(defender));
        if (!defender.hasTech(ETERNAL_AEGIS) || canceledHits < 1) {
            return;
        }

        buttons.add(Buttons.green(
                defender.factionButtonChecker() + USE_ETERNAL_AEGIS + attacker.getFaction() + "|" + tile.getPosition()
                        + "|" + combatOnHolder.getName() + "|" + hits,
                "Eternal Aegis: Cancel " + canceledHits + " Hit" + (canceledHits == 1 ? "" : "s")));
    }

    @ButtonHandler(USE_ETERNAL_AEGIS)
    public static void useEternalAegis(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(USE_ETERNAL_AEGIS.length()).split("\\|", 4);
        if (parts.length != 4 || !player.hasTech(ETERNAL_AEGIS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[1]);
        UnitHolder combatOnHolder = tile == null ? null : tile.getUnitHolders().get(parts[2]);
        int hits;
        try {
            hits = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String attackerRound = game.getStoredValue("combatRoundTracker" + parts[0] + parts[1] + parts[2]);
        int canceledHits = Math.min(hits, getEternalAegisCancellationCount(player));
        if (tile == null
                || combatOnHolder == null
                || hits < 1
                || canceledHits < 1
                || (!attackerRound.isBlank() && !"1".equals(attackerRound))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remainingHits = hits - canceledHits;
        String cancellationMessage = player.getRepresentationUnfogged() + " canceled " + canceledHits + " hit"
                + (canceledHits == 1 ? "" : "s") + " with _Eternal Aegis_.";
        if (remainingHits == 0) {
            event.getMessage()
                    .editMessage(cancellationMessage)
                    .setComponents(List.of())
                    .queue();
            return;
        }

        List<Button> buttons = new ArrayList<>();
        String factionChecker = player.factionButtonChecker();
        String assignmentMessage;
        if (combatOnHolder instanceof Planet) {
            buttons.add(Buttons.green(
                    factionChecker + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + remainingHits,
                    "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                    "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + "cancelGroundHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
            assignmentMessage = cancellationMessage + "\n" + player.getRepresentation() + " may autoassign "
                    + remainingHits + " hit" + (remainingHits == 1 ? "" : "s") + ".";
        } else {
            buttons.add(Buttons.green(
                    factionChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + remainingHits,
                    "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                    "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    factionChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
            assignmentMessage = cancellationMessage + "\n" + player.getRepresentationNoPing()
                    + ", you may automatically assign " + (remainingHits == 1 ? "the hit" : "the hits") + ". "
                    + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(player, game, tile, remainingHits, event, true);
        }
        event.getMessage()
                .editMessage(assignmentMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue();
    }

    private static int getEternalAegisCancellationCount(Player player) {
        return (int) player.getLeaders().stream()
                .filter(leader -> !leader.isLocked() && !leader.isExhausted())
                .map(Leader::getType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .count();
    }
}

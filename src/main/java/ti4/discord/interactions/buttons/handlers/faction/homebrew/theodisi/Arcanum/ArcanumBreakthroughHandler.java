package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ArcanumBreakthroughHandler {
    private static final String FLIP_ON_GAIN = "flipPowerWordOnGain";
    private static final String POWER_WORD_WISH_FRONT = "arcanumbt";
    private static final String POWER_WORD_WISH_BACK = "arcanumbtback";
    private static final String USE_POWER_WORD_WISH = "usePowerWordWish";
    private static final String WISH_SELECT = "powerWordWishSelect_";
    private static final String WISH_UNIT = "powerWordWishUnit_";
    private static final String MOVE = "move";
    private static final String COMBAT = "combat";
    private static final String CAPACITY = "capacity";
    private static final String SELECTION_PREFIX = "powerWordWish";

    // Gain Choice
    public static void offerArcanumBTFlipOnGain(Game game, Player player) {
        if (game == null || player == null || !player.hasBreakthrough(POWER_WORD_WISH_FRONT)) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + FLIP_ON_GAIN, "Flip Power Word: Wish", FactionEmojis.arcanum),
                Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentation()
                        + ", you choose the side this breakthrough starts on. As such, you may press the button below to flip it to its Yellow-Green side:",
                buttons);
    }

    @ButtonHandler(FLIP_ON_GAIN)
    public static void resolvePowerWordFlipOnGain(ButtonInteractionEvent event, Player player, Game game) {
        if (game == null || player == null || !player.hasBreakthrough(POWER_WORD_WISH_FRONT)) {
            return;
        }

        player.changeBreakthrough(POWER_WORD_WISH_FRONT, POWER_WORD_WISH_BACK);
        MessageHelper.sendMessageToChannelWithEmbed(
                event.getMessageChannel(),
                player.getRepresentation() + " flipped _Power Word: Wish_ to its biotic/cybernetic side.",
                Mapper.getBreakthrough(POWER_WORD_WISH_BACK).getRepresentationEmbed());

        ButtonHelper.deleteMessage(event);
    }

    // Flip Mechanic
    public static void handlePowerWordWishTechGain(Player player, String techId) {
        if (player == null || techId == null) return;

        TechnologyModel techModel = Mapper.getTech(techId);
        if (techModel == null) return;

        if (player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)
                && (techModel.isPropulsionTech() || techModel.isWarfareTech())
                && player.changeBreakthrough(POWER_WORD_WISH_FRONT, POWER_WORD_WISH_BACK)) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " flipped _Power Word: Wish_ to its biotic/cybernetic side.",
                    Mapper.getBreakthrough(POWER_WORD_WISH_BACK).getRepresentationEmbed());
            return;
        }

        if (player.hasUnlockedBreakthrough(POWER_WORD_WISH_BACK)
                && (techModel.isCyberneticTech() || techModel.isBioticTech())
                && player.changeBreakthrough(POWER_WORD_WISH_BACK, POWER_WORD_WISH_FRONT)) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " flipped _Power Word: Wish_ to its propulsion/warfare side.",
                    Mapper.getBreakthrough(POWER_WORD_WISH_FRONT).getRepresentationEmbed());
        }
    }

    // Effects
    public static void offerPowerWordWish(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) return;

        if (player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may use _Power Word: Wish_ during this tactical action.",
                    List.of(
                            Buttons.green(
                                    player.factionButtonChecker() + USE_POWER_WORD_WISH,
                                    "Use Power Word: Wish",
                                    FactionEmojis.arcanum),
                            Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
        } else if (player.hasUnlockedBreakthrough(POWER_WORD_WISH_BACK)) {
            game.removeStoredValue(selectionKey(player, CAPACITY));
            sendUnitSelectionButtons(player.getCorrectChannel(), game, player, CAPACITY);
        }
    }

    @ButtonHandler(USE_POWER_WORD_WISH)
    public static void usePowerWordWish(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", select the individual units receiving _Power Word: Wish_.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + WISH_SELECT + MOVE, "Choose 1 Unit: +1 Move"),
                        Buttons.green(
                                player.factionButtonChecker() + WISH_SELECT + COMBAT, "Choose 2 Units: +2 Combat")));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(WISH_SELECT)
    public static void choosePowerWordWishUnits(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String mode = buttonID.replace(WISH_SELECT, "");
        if (!isModeAvailable(player, mode)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(selectionKey(player, mode));
        sendUnitSelectionButtons(event.getMessageChannel(), game, player, mode);
    }

    @ButtonHandler(WISH_UNIT)
    public static void selectPowerWordWishUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace(WISH_UNIT, "").split(";", 4);
        if (parts.length != 4 || !isModeAvailable(player, parts[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String mode = parts[0];
        Tile tile = game.getTileByPosition(parts[1]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[2]);
        UnitModel unit = player.getUnitFromAsyncID(parts[3]);
        if (holder == null || unit == null || !isEligible(mode, unit)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit is no longer eligible.");
            return;
        }

        String selection = String.join(";", parts[1], parts[2], parts[3]);
        List<String> selections = getSelections(game, player, mode);
        int required = requiredSelections(mode);
        if (selections.size() >= required) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have already selected all required units.");
            return;
        }

        int available = holder.getUnitKeys().stream()
                .filter(player::unitBelongsToPlayer)
                .filter(unitKey -> parts[3].equals(unitKey.asyncID()))
                .mapToInt(holder::getUnitCount)
                .sum();
        if (available <= countSelections(selections, selection)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "All copies of that unit are already selected.");
            return;
        }

        selections.add(selection);
        game.setStoredValue(selectionKey(player, mode), String.join(",", selections));

        if (selections.size() >= required) {
            ButtonHelper.deleteAllButtons(event);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " selected " + unit.getName() + " (" + selections.size() + "/" + required
                        + ") for _Power Word: Wish_"
                        + switch (mode) {
                            case MOVE -> " (MOVE " + (unit.getMoveValue() + 1) + ")";
                            case COMBAT -> " (+2 combat)";
                            case CAPACITY -> " (+1 capacity)";
                            default -> "";
                        }
                        + switch (mode) {
                            case MOVE -> ". It will be moved first whenever you move units.";
                            default -> ".";
                        });
    }

    public static void movePowerWordWishUnitsToActiveSystem(Game game, Player player, Tile activeSystem) {
        List<String> modes = player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)
                ? List.of(MOVE, COMBAT)
                : player.hasUnlockedBreakthrough(POWER_WORD_WISH_BACK) ? List.of(CAPACITY) : List.of();
        for (String mode : modes) {
            List<String> selections = getSelections(game, player, mode);
            if (selections.isEmpty()) continue;

            Map<String, Integer> movedBySource = new HashMap<>();
            for (Map.Entry<String, Map<UnitKey, List<Integer>>> entry :
                    game.getTacticalActionDisplacement().entrySet()) {
                for (Map.Entry<UnitKey, List<Integer>> movedUnit :
                        entry.getValue().entrySet()) {
                    movedBySource.put(
                            entry.getKey() + ";" + movedUnit.getKey().asyncID(),
                            movedUnit.getValue().stream()
                                    .mapToInt(Integer::intValue)
                                    .sum());
                }
            }

            Map<String, Integer> movedSoFar = new HashMap<>();
            List<String> updatedSelections = new ArrayList<>();
            for (String selection : selections) {
                String[] parts = selection.split(";", 3);
                if (parts.length != 3) continue;
                String source = parts[0] + "-" + parts[1] + ";" + parts[2];
                int moved = movedBySource.getOrDefault(source, 0);
                int used = movedSoFar.getOrDefault(source, 0);
                if (used < moved) {
                    updatedSelections.add(activeSystem.getPosition() + ";space;" + parts[2]);
                    movedSoFar.put(source, used + 1);
                } else {
                    updatedSelections.add(selection);
                }
            }
            game.setStoredValue(selectionKey(player, mode), String.join(",", updatedSelections));
        }
    }

    public static int getPowerWordWishCapacityBonus(Game game, Player player, Tile tile) {
        if (!player.hasUnlockedBreakthrough(POWER_WORD_WISH_BACK) || tile == null) return 0;
        UnitHolder space = tile.getUnitHolders().get("space");
        return getSelectionCountInHolder(game, player, CAPACITY, tile, space, null, Integer.MAX_VALUE);
    }

    public static int getPowerWordWishCombatBonus(
            Game game, Player player, Tile tile, UnitHolder holder, UnitModel unit, int unitCount) {
        if (!player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)) return 0;
        return getSelectionCountInHolder(game, player, COMBAT, tile, holder, unit.getAsyncId(), unitCount);
    }

    public static void clearPowerWordWish(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (String mode : List.of(MOVE, COMBAT, CAPACITY)) {
                game.removeStoredValue(selectionKey(player, mode));
            }
        }
    }

    public static String getPowerWordWishMoveNote(Game game, Player player, Tile tile) {
        if (!player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)) return "";
        for (String selection : getSelections(game, player, MOVE)) {
            String[] parts = selection.split(";", 3);
            if (parts.length != 3 || !tile.getPosition().equals(parts[0])) continue;
            UnitModel unit = player.getUnitFromAsyncID(parts[2]);
            if (unit != null) {
                return "> _Power Word: Wish_: 1 " + unit.getName() + " from " + parts[1] + " has MOVE "
                        + (unit.getMoveValue() + 1)
                        + ". It will be moved first whenever you use the normal move buttons.";
            }
        }
        return "";
    }

    private static void sendUnitSelectionButtons(
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel,
            Game game,
            Player player,
            String mode) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeys()) {
                    if (!player.unitBelongsToPlayer(unitKey) || holder.getUnitCount(unitKey) < 1) continue;
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    if (unit == null || !isEligible(mode, unit)) continue;
                    String id = player.factionButtonChecker() + WISH_UNIT + mode + ";" + tile.getPosition() + ";"
                            + holder.getName() + ";" + unitKey.asyncID();
                    buttons.add(Buttons.green(
                            id,
                            "Select 1 " + unit.getName() + " in " + tile.getRepresentationForButtons(game, player),
                            unit.getUnitEmoji()));
                }
            }
        }

        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentation() + ", choose " + requiredSelections(mode) + " individual "
                        + (CAPACITY.equals(mode) ? "ship" : "unit") + (requiredSelections(mode) == 1 ? "" : "s")
                        + " for _Power Word: Wish_.",
                buttons);
    }

    private static boolean isModeAvailable(Player player, String mode) {
        return (MOVE.equals(mode) || COMBAT.equals(mode)) && player.hasUnlockedBreakthrough(POWER_WORD_WISH_FRONT)
                || CAPACITY.equals(mode) && player.hasUnlockedBreakthrough(POWER_WORD_WISH_BACK);
    }

    private static boolean isEligible(String mode, UnitModel unit) {
        return switch (mode) {
            case MOVE -> unit.getMoveValue() > 0;
            case COMBAT -> unit.getCombatDieCount() != 0;
            case CAPACITY -> unit.getIsShip();
            default -> false;
        };
    }

    private static int requiredSelections(String mode) {
        return MOVE.equals(mode) ? 1 : 2;
    }

    private static String selectionKey(Player player, String mode) {
        return SELECTION_PREFIX + mode + "_" + player.getFaction();
    }

    private static List<String> getSelections(Game game, Player player, String mode) {
        String stored = game.getStoredValue(selectionKey(player, mode));
        return stored.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(stored.split(",")));
    }

    private static int countSelections(List<String> selections, String selection) {
        return (int) selections.stream().filter(selection::equals).count();
    }

    private static int getSelectionCountInHolder(
            Game game, Player player, String mode, Tile tile, UnitHolder holder, String asyncId, int maximum) {
        if (tile == null || holder == null) return 0;
        Map<String, Integer> selectedByAsyncId = new HashMap<>();
        for (String selection : getSelections(game, player, mode)) {
            String[] parts = selection.split(";", 3);
            if (parts.length == 3
                    && tile.getPosition().equals(parts[0])
                    && holder.getName().equals(parts[1])
                    && (asyncId == null || asyncId.equals(parts[2]))) {
                selectedByAsyncId.merge(parts[2], 1, Integer::sum);
            }
        }

        int total = 0;
        for (Map.Entry<String, Integer> entry : selectedByAsyncId.entrySet()) {
            int available = holder.getUnitKeys().stream()
                    .filter(player::unitBelongsToPlayer)
                    .filter(unitKey -> entry.getKey().equals(unitKey.asyncID()))
                    .mapToInt(holder::getUnitCount)
                    .sum();
            total += Math.min(entry.getValue(), available);
        }
        return Math.min(total, maximum);
    }
}

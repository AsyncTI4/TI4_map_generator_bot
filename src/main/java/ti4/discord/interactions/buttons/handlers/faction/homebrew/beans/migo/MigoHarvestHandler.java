package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.UnitModelValueInjectionService;

@UtilityClass
public class MigoHarvestHandler {
    private static final String USE_YELLOW_HARVEST = "useYellowHarvest_";
    private static final String YELLOW_HARVEST_PRODUCTION = "migoYellowHarvestProduction_";
    private static final String SELECT_RED_HARVEST = "selectRedHarvest_";
    private static final String RESOLVE_RED_HARVEST = "resolveRedHarvest";
    private static final String RED_HARVEST_PAGE = "redHarvestPage_";
    private static final String RED_HARVEST_CONTEXT = "migoRedHarvestContext_";
    private static final String RED_HARVEST_CANDIDATES = "migoRedHarvestCandidates_";
    private static final String RED_HARVEST_SELECTIONS = "migoRedHarvestSelections_";

    public static void addYellowHarvestButton(List<Button> buttons, Game game, Player player, Tile tile) {
        if (!player.hasAbility("harvest_yellow")
                || player.getExhaustedAbilities().contains("harvest_yellow")
                || tile == null
                || Helper.getProductionValue(player, game, tile, false) < 1) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_YELLOW_HARVEST + tile.getPosition(),
                "Use Biomechanical Splicing",
                FactionEmojis.migo));
    }

    @ButtonHandler(USE_YELLOW_HARVEST)
    public static void useYellowHarvest(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_YELLOW_HARVEST.length()));
        if (!player.hasAbility("harvest_yellow")
                || player.getExhaustedAbilities().contains("harvest_yellow")
                || tile == null) {
            return;
        }

        player.addExhaustedAbility("harvest_yellow");
        game.setStoredValue(YELLOW_HARVEST_PRODUCTION + player.getFaction(), tile.getPosition());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted _Biomechanical Splicing_. Their total PRODUCTION is increased by 1 and their combined production cost is reduced by 1.");
        ButtonHelper.deleteTheOneButton(event);
    }

    public static int getYellowHarvestProductionBonus(Game game, Player player, Tile tile) {
        return tile != null
                        && tile.getPosition()
                                .equals(game.getStoredValue(YELLOW_HARVEST_PRODUCTION + player.getFaction()))
                        && player.getCurrentProducedUnits().keySet().stream()
                                .anyMatch(unit -> unit.split("_", 3).length > 1
                                        && tile.getPosition().equals(unit.split("_", 3)[1]))
                ? 1
                : 0;
    }

    public static int applyYellowHarvestProductionDiscount(Game game, Player player, int cost) {
        if (getYellowHarvestProductionDiscount(game, player) > 0) {
            game.removeStoredValue(YELLOW_HARVEST_PRODUCTION + player.getFaction());
        }
        return cost;
    }

    public static int getYellowHarvestProductionDiscount(Game game, Player player) {
        String productionPosition = game.getStoredValue(YELLOW_HARVEST_PRODUCTION + player.getFaction());
        return !productionPosition.isBlank()
                        && player.getCurrentProducedUnits().keySet().stream()
                                .anyMatch(unit -> unit.split("_", 3).length > 1
                                        && productionPosition.equals(unit.split("_", 3)[1]))
                ? 1
                : 0;
    }

    public static void clearYellowHarvestProduction(Game game, Player player) {
        game.removeStoredValue(YELLOW_HARVEST_PRODUCTION + player.getFaction());
    }

    public static void offerRedHarvest(Game game, Player player, CombatRollPayload payload, MessageChannel channel) {
        if (!player.hasAbility("harvest_red")
                || player.getExhaustedAbilities().contains("harvest_red")
                || payload.header() == null
                || !"space".equalsIgnoreCase(payload.header().unitHolderName())
                || payload.total() == null
                || payload.total().diceRolled() < 1) {
            return;
        }

        List<String> candidateAsyncIds = payload.unitRolls().stream()
                .flatMap(unitRoll -> unitRoll.dice().stream()
                        .filter(die -> die.source() == CombatRollPayload.DieRollSource.PRIMARY && !die.success())
                        .map(die -> unitRoll.asyncId()))
                .toList();
        if (candidateAsyncIds.isEmpty()) {
            return;
        }

        game.setStoredValue(
                RED_HARVEST_CONTEXT + player.getFaction(),
                payload.header().tilePosition() + "|" + payload.header().unitHolderName());
        game.setStoredValue(RED_HARVEST_CANDIDATES + player.getFaction(), String.join(",", candidateAsyncIds));
        game.removeStoredValue(RED_HARVEST_SELECTIONS + player.getFaction());
        sendRedHarvestPrompt(channel, game, player);
    }

    @ButtonHandler(SELECT_RED_HARVEST)
    public static void selectRedHarvest(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int candidateIndex;
        try {
            candidateIndex = Integer.parseInt(buttonID.substring(SELECT_RED_HARVEST.length()));
        } catch (NumberFormatException _) {
            return;
        }

        List<String> candidates = getRedHarvestCandidates(game, player);
        Set<Integer> selectedCandidates = getSelectedRedHarvestCandidates(game, player);
        if (!player.hasAbility("harvest_red")
                || player.getExhaustedAbilities().contains("harvest_red")
                || candidateIndex < 0
                || candidateIndex >= candidates.size()) {
            return;
        }

        if (selectedCandidates.contains(candidateIndex)) {
            selectedCandidates.remove(candidateIndex);
        } else if (selectedCandidates.size() < 2) {
            selectedCandidates.add(candidateIndex);
        } else {
            return;
        }
        game.setStoredValue(RED_HARVEST_SELECTIONS + player.getFaction(), serializeSelections(selectedCandidates));
        updateRedHarvestPrompt(event, game, player);
    }

    @ButtonHandler(RESOLVE_RED_HARVEST)
    public static void resolveRedHarvest(ButtonInteractionEvent event, Game game, Player player) {
        List<String> candidates = getRedHarvestCandidates(game, player);
        Set<Integer> selectedCandidates = getSelectedRedHarvestCandidates(game, player);
        RedHarvestContext context = getRedHarvestContext(game, player);
        if (!player.hasAbility("harvest_red")
                || player.getExhaustedAbilities().contains("harvest_red")
                || context == null
                || selectedCandidates.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(context.tilePosition());
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(context.unitHolderName());
        if (tile == null || holder == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Map<Pair<UnitModel, UnitHolder>, Integer> selectedUnits = new java.util.HashMap<>();
        for (int index : selectedCandidates) {
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            UnitModel unit = player.getPriorityUnitByAsyncID(candidates.get(index), holder);
            if (unit == null || !unit.getIsShip()) {
                continue;
            }
            UnitModel rerollUnit = UnitModelValueInjectionService.injectValues(
                    unit,
                    UnitModelValueInjectionService.IntegerValueInjection.create()
                            .combatDieCount(1, true));
            selectedUnits.merge(new ImmutablePair<>(rerollUnit, holder), 1, Integer::sum);
        }
        if (selectedUnits.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addExhaustedAbility("harvest_red");
        clearRedHarvestContext(game, player);
        ButtonHelper.deleteMessage(event);
        CombatRollService.resolveRedHarvestRoll(player, game, event, tile, context.unitHolderName(), selectedUnits);
    }

    @ButtonHandler(RED_HARVEST_PAGE)
    public static void changeRedHarvestPage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        NewStuffHelper.turnPage(
                event,
                buttonID,
                redHarvestMessage(game, player),
                () -> getRedHarvestCandidateButtons(game, player),
                redHarvestPromptButtons(player),
                player.factionButtonChecker() + RED_HARVEST_PAGE);
    }

    private static void updateRedHarvestPrompt(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.editMessageWithButtons(
                event,
                redHarvestMessage(game, player),
                NewStuffHelper.paginateWithPinnedButtons(
                        getRedHarvestCandidateButtons(game, player),
                        redHarvestPromptButtons(player),
                        player.factionButtonChecker() + RED_HARVEST_PAGE,
                        25,
                        0));
    }

    private static void sendRedHarvestPrompt(MessageChannel channel, Game game, Player player) {
        List<Button> buttons = getRedHarvestCandidateButtons(game, player);
        if (buttons.isEmpty()) {
            clearRedHarvestContext(game, player);
            return;
        }
        NewStuffHelper.sendPaginated(
                channel,
                redHarvestMessage(game, player),
                buttons,
                redHarvestPromptButtons(player),
                player.factionButtonChecker() + RED_HARVEST_PAGE);
    }

    private static String redHarvestMessage(Game game, Player player) {
        int selected = getSelectedRedHarvestCandidates(game, player).size();
        return player.getRepresentationNoPing()
                + ", select up to 2 ships that rolled a miss, then press Done to reroll 1 die for each selected ship with _Tachyonic Reversion_."
                + (selected == 0 ? "" : " Selected: " + selected + ".");
    }

    private static List<Button> redHarvestPromptButtons(Player player) {
        return List.of(
                Buttons.green(player.factionButtonChecker() + RESOLVE_RED_HARVEST, "Done"),
                Buttons.DECLINE_DELETE_BUTTONS);
    }

    private static List<Button> getRedHarvestCandidateButtons(Game game, Player player) {
        RedHarvestContext context = getRedHarvestContext(game, player);
        Tile tile = context == null ? null : game.getTileByPosition(context.tilePosition());
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(context.unitHolderName());
        if (holder == null) {
            return List.of();
        }
        Set<Integer> selectedCandidates = getSelectedRedHarvestCandidates(game, player);
        List<String> candidates = getRedHarvestCandidates(game, player);
        List<Button> buttons = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            UnitModel unit = player.getPriorityUnitByAsyncID(candidates.get(index), holder);
            if (unit == null || !unit.getIsShip()) {
                continue;
            }
            boolean selected = selectedCandidates.contains(index);
            String label = unit.getName();
            Button button = selected
                    ? Buttons.green(
                            player.factionButtonChecker() + SELECT_RED_HARVEST + index,
                            label + " (selected)",
                            unit.getUnitEmoji())
                    : Buttons.gray(
                            player.factionButtonChecker() + SELECT_RED_HARVEST + index, label, unit.getUnitEmoji());
            buttons.add(button);
        }
        return buttons;
    }

    private static List<String> getRedHarvestCandidates(Game game, Player player) {
        String serializedCandidates = game.getStoredValue(RED_HARVEST_CANDIDATES + player.getFaction());
        return serializedCandidates.isBlank() ? List.of() : List.of(serializedCandidates.split(","));
    }

    private static RedHarvestContext getRedHarvestContext(Game game, Player player) {
        String[] context =
                game.getStoredValue(RED_HARVEST_CONTEXT + player.getFaction()).split("\\|", 2);
        return context.length == 2 ? new RedHarvestContext(context[0], context[1]) : null;
    }

    private static Set<Integer> getSelectedRedHarvestCandidates(Game game, Player player) {
        String serializedSelections = game.getStoredValue(RED_HARVEST_SELECTIONS + player.getFaction());
        Set<Integer> selections = new HashSet<>();
        if (serializedSelections.isBlank()) {
            return selections;
        }
        for (String selection : serializedSelections.split(",")) {
            try {
                selections.add(Integer.parseInt(selection));
            } catch (NumberFormatException _) {
                return new HashSet<>();
            }
        }
        return selections;
    }

    private static String serializeSelections(Set<Integer> selections) {
        return selections.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static void clearRedHarvestContext(Game game, Player player) {
        game.removeStoredValue(RED_HARVEST_CONTEXT + player.getFaction());
        game.removeStoredValue(RED_HARVEST_CANDIDATES + player.getFaction());
        game.removeStoredValue(RED_HARVEST_SELECTIONS + player.getFaction());
    }

    private record RedHarvestContext(String tilePosition, String unitHolderName) {}

    public static void offerBlueHarvest(Game game, Player player) {
        if (!player.hasAbility("harvest_blue") || player.getExhaustedAbilities().contains("harvest_blue")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "exhaustBlueHarvest", "Use Kinetic Morphology", FactionEmojis.migo));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may use _Kinetic Morphology_ to apply +1 to the MOVE value of 1 ship during this tactical action.",
                buttons);
    }

    @ButtonHandler("exhaustBlueHarvest")
    public static void exhaustBlueHarvest(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasAbility("harvest_blue") || player.getExhaustedAbilities().contains("harvest_blue")) {
            return;
        }

        player.addExhaustedAbility("harvest_blue");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " used _Kinetic Morphology_ to give 1 of their ships +1 until the end of this tactical action.");

        ButtonHelper.deleteMessage(event);
    }

    public static void offerGreenHarvest(Player player) {
        if (!player.hasAbility("harvest_green")
                || player.getExhaustedAbilities().contains("harvest_green")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "exhaustGreenHarvest", "Use Synaptic Grafting"));
        buttons.add(Buttons.DELETE_BUTTONS);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you exhausted a planet that has a technology specialty and as such, you may use _Synaptic Grafting_ to draw 1 action card.",
                buttons);
    }

    @ButtonHandler("exhaustGreenHarvest")
    public static void exhaustGreenHarvest(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasAbility("harvest_green")
                || player.getExhaustedAbilities().contains("harvest_green")) {
            return;
        }

        player.addExhaustedAbility("harvest_green");
        ActionCardHelper.drawActionCards(player, 1);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted _Synaptic Grafting_ to draw 1 action card.");

        ButtonHelper.deleteMessage(event);
    }
}

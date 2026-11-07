package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.choir;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.UnitModel;

@UtilityClass
public class ChoirAbilityHandler {
    private static final String DEATHS_REFRAIN = "deaths_refrain";
    private static final String SNAPSHOT = "deathsRefrainSnapshot_";
    private static final String SELECTED = "deathsRefrainSelected_";
    private static final String PICK = "deathsRefrainPick_";
    private static final String PAGE = "deathsRefrainPage_";
    private static final String DONE = "deathsRefrainDone";
    private static final String DECLINE = "deathsRefrainDecline";

    public static boolean hasMoreDamagedSustainUnitsThanUndamaged(Player player, Game game) {
        int damaged = 0;
        int undamaged = 0;

        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                    if (!player.unitBelongsToPlayer(unitKey)) {
                        continue;
                    }

                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    if (unit == null || !unit.getSustainDamage()) {
                        continue;
                    }

                    int damagedCount = unitHolder.getDamagedUnitCount(unitKey);
                    damaged += damagedCount;
                    undamaged += unitHolder.getUnitCount(unitKey) - damagedCount;
                }
            }
        }

        return damaged > undamaged;
    }

    public static boolean hasMoreDamagedShipsThanUndamagedShipsInSystem(Game game, Tile tile) {
        if (tile == null) {
            return false;
        }

        int damaged = 0;
        int undamaged = 0;

        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                UnitModel unitModel = game.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.isNonFighterShip()) {
                    continue;
                }

                int damagedCount = unitHolder.getDamagedUnitCount(unitKey);
                damaged += damagedCount;
                undamaged += unitHolder.getUnitCount(unitKey) - damagedCount;
            }
        }

        return damaged > undamaged;
    }

    public static boolean hasMoreUndamagedShipsThanDamagedShipsInSystem(Game game, Tile tile) {
        if (tile == null) {
            return false;
        }

        int damaged = 0;
        int undamaged = 0;

        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                UnitModel unitModel = game.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.isNonFighterShip()) {
                    continue;
                }

                int damagedCount = unitHolder.getDamagedUnitCount(unitKey);
                damaged += damagedCount;
                undamaged += unitHolder.getUnitCount(unitKey) - damagedCount;
            }
        }

        return undamaged > damaged;
    }

    public static void syncMorbidHarmony(Player player) {
        if (!player.hasAbility("morbid_harmony")) {
            return;
        }

        boolean requiem = hasMoreDamagedSustainUnitsThanUndamaged(player, player.getGame());
        List<String> flippedComponents = new ArrayList<>();
        List<MessageEmbed> flippedComponentEmbeds = new ArrayList<>();

        if (syncMorbidHarmonyTech(player, "bechoiry", "bechoirylament", "bechoiryrequiem", requiem)) {
            flippedComponents.add("_Tomb Resonance_");
            addTechEmbed(flippedComponentEmbeds, requiem ? "bechoiryrequiem" : "bechoirylament");
        }
        if (syncMorbidHarmonyTech(player, "bechoirr", "bechoirrlament", "bechoirrrequiem", requiem)) {
            flippedComponents.add("_Oblivion's Echo_");
            addTechEmbed(flippedComponentEmbeds, requiem ? "bechoirrrequiem" : "bechoirrlament");
        }
        if (syncMorbidHarmonyMech(player, requiem)) {
            flippedComponents.add("Elegy");
            addUnitEmbed(flippedComponentEmbeds, requiem ? "choir_mechrequiem" : "choir_mechlament");
        }

        if (!flippedComponents.isEmpty()) {
            MessageHelper.sendMessageToChannelWithEmbeds(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + "'s **Morbid Harmony** changed "
                            + String.join(", ", flippedComponents)
                            + " to their **"
                            + (requiem ? "Requiem" : "Lament")
                            + "** sides.",
                    flippedComponentEmbeds);
        }
    }

    private static void addTechEmbed(List<MessageEmbed> embeds, String techId) {
        var tech = Mapper.getTech(techId);
        if (tech != null) {
            embeds.add(tech.getRepresentationEmbed());
        }
    }

    private static void addUnitEmbed(List<MessageEmbed> embeds, String unitId) {
        UnitModel unit = Mapper.getUnit(unitId);
        if (unit != null) {
            embeds.add(unit.getRepresentationEmbed());
        }
    }

    private static boolean syncMorbidHarmonyTech(
            Player player, String combinedTech, String lamentTech, String requiemTech, boolean requiem) {
        String targetTech = requiem ? requiemTech : lamentTech;
        String otherSide = requiem ? lamentTech : requiemTech;
        boolean changed = syncMorbidHarmonyResearchedTech(player, combinedTech, targetTech);
        changed |= syncMorbidHarmonyResearchedTech(player, otherSide, targetTech);
        changed |= player.removeFactionTech(combinedTech);
        changed |= player.removeFactionTech(otherSide);
        if (changed) {
            player.addFactionTech(targetTech);
        }
        return changed;
    }

    private static boolean syncMorbidHarmonyResearchedTech(Player player, String currentTech, String targetTech) {
        if (!player.hasTech(currentTech)) {
            return false;
        }

        boolean exhausted = player.getExhaustedTechs().contains(currentTech);
        player.removeTech(currentTech);
        player.addTech(targetTech);
        if (exhausted) {
            player.exhaustTech(targetTech);
        }
        return true;
    }

    private static boolean syncMorbidHarmonyMech(Player player, boolean requiem) {
        String targetMech = requiem ? "choir_mechrequiem" : "choir_mechlament";
        String otherSide = requiem ? "choir_mechlament" : "choir_mechrequiem";
        boolean changed = syncMorbidHarmonyMech(player, "choir_mech", targetMech);
        changed |= syncMorbidHarmonyMech(player, otherSide, targetMech);
        return changed;
    }

    private static boolean syncMorbidHarmonyMech(Player player, String currentMech, String targetMech) {
        if (!player.hasUnit(currentMech)) {
            return false;
        }

        player.removeOwnedUnitByID(currentMech);
        player.addOwnedUnitByID(targetMech);
        return true;
    }

    public static void snapshotDeathsRefrainDamagedUnits(Game game) {
        for (Player player : game.getRealPlayers()) {
            clearDeathsRefrainState(game, player);
            if (!player.hasAbility(DEATHS_REFRAIN)) {
                continue;
            }

            List<DamageSelection> selections = new ArrayList<>();
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    for (UnitKey unitKey : unitHolder.getUnitKeys()) {
                        if (!player.unitBelongsToPlayer(unitKey)) {
                            continue;
                        }
                        for (int count = unitHolder.getDamagedUnitCount(unitKey); count > 0; count--) {
                            selections.add(
                                    new DamageSelection(tile.getPosition(), unitHolder.getName(), unitKey.asyncID()));
                        }
                    }
                }
            }
            if (!selections.isEmpty()) {
                game.setStoredValue(getSnapshotKey(player), serializeSelections(selections));
            }
        }
    }

    public static void offerDeathsRefrain(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility(DEATHS_REFRAIN) || getSnapshot(game, player).isEmpty()) {
                continue;
            }
            sendDeathsRefrainButtons(null, game, player, 0);
        }
    }

    @ButtonHandler(PICK)
    public static void chooseDeathsRefrainUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Integer> selected = getSelectedIndexes(game, player);
        if (selected.size() >= 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] values = buttonID.substring(PICK.length()).split("\\|", 2);
        if (values.length != 2 || !values[0].equals(Integer.toString(game.getRound()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int index;
        try {
            index = Integer.parseInt(values[1]);
        } catch (NumberFormatException e) {
            return;
        }
        List<DamageSelection> selections = getSnapshot(game, player);
        if (index < 0 || index >= selections.size() || selected.contains(index)) {
            return;
        }
        DamageSelection selection = selections.get(index);
        Tile tile = game.getTileByPosition(selection.tilePosition());
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(selection.unitHolderName());
        UnitKey unitKey = Mapper.getUnitKey(selection.unitAsyncId(), player.getColorID());
        if (unitHolder == null
                || unitKey == null
                || unitHolder.getUnitCount(unitKey) <= unitHolder.getDamagedUnitCount(unitKey)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit can no longer be left damaged.");
            return;
        }

        unitHolder.addDamagedUnit(unitKey, 1);
        selected.add(index);
        game.setStoredValue(
                getSelectedKey(player),
                selected.stream()
                        .map(String::valueOf)
                        .reduce((a, b) -> a + "," + b)
                        .orElse(""));
        UnitModel unit = player.getUnitFromUnitKey(unitKey);
        String unitName = unit == null ? unitKey.humanReadableName() : unit.getName();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " left 1 " + unitName + " damaged in "
                        + tile.getRepresentationForButtons(game, player) + " due to **Death's Refrain**.");
        if (selected.size() >= 2) {
            clearDeathsRefrainState(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendDeathsRefrainButtons(event, game, player, 0);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PAGE)
    public static void pageDeathsRefrain(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getDeathsRefrainButtons(game, player);
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                buttons,
                getDeathsRefrainExtraButtons(game, player),
                getDeathsRefrainMessage(player),
                player.factionButtonChecker() + PAGE,
                buttonID);
    }

    @ButtonHandler(DONE)
    public static void finishDeathsRefrain(ButtonInteractionEvent event, Game game, Player player) {
        if (getSelectedIndexes(game, player).isEmpty()) {
            return;
        }
        clearDeathsRefrainState(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE)
    public static void declineDeathsRefrain(ButtonInteractionEvent event, Game game, Player player) {
        if (!getSelectedIndexes(game, player).isEmpty()) {
            return;
        }
        clearDeathsRefrainState(game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static void sendDeathsRefrainButtons(ButtonInteractionEvent event, Game game, Player player, int page) {
        List<Button> buttons = getDeathsRefrainButtons(game, player);
        if (buttons.isEmpty()) {
            clearDeathsRefrainState(game, player);
            return;
        }
        List<Button> extraButtons = getDeathsRefrainExtraButtons(game, player);
        List<Button> displayedButtons = buttons.size() + extraButtons.size() <= 25
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons, extraButtons, player.factionButtonChecker() + PAGE, 25, page, false);
        if (displayedButtons.size() == buttons.size()) {
            displayedButtons.addAll(extraButtons);
        }
        if (event == null) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), getDeathsRefrainMessage(player), displayedButtons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), getDeathsRefrainMessage(player), displayedButtons);
        }
    }

    private static List<Button> getDeathsRefrainButtons(Game game, Player player) {
        List<DamageSelection> selections = getSnapshot(game, player);
        List<Integer> selected = getSelectedIndexes(game, player);
        List<Button> buttons = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            if (selected.contains(index)) {
                continue;
            }
            DamageSelection selection = selections.get(index);
            Tile tile = game.getTileByPosition(selection.tilePosition());
            UnitHolder unitHolder = tile == null ? null : tile.getUnitHolders().get(selection.unitHolderName());
            UnitKey unitKey = Mapper.getUnitKey(selection.unitAsyncId(), player.getColorID());
            if (tile == null
                    || unitHolder == null
                    || unitKey == null
                    || unitHolder.getUnitCount(unitKey) <= unitHolder.getDamagedUnitCount(unitKey)) {
                continue;
            }
            UnitModel unit = player.getUnitFromUnitKey(unitKey);
            String unitName = unit == null ? unitKey.humanReadableName() : unit.getName();
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PICK + game.getRound() + "|" + index,
                    "Leave " + unitName + " Damaged in " + tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static List<Button> getDeathsRefrainExtraButtons(Game game, Player player) {
        return getSelectedIndexes(game, player).isEmpty()
                ? List.of(Buttons.red(player.factionButtonChecker() + DECLINE, "Decline"))
                : List.of(Buttons.green(player.factionButtonChecker() + DONE, "Done"));
    }

    private static String getDeathsRefrainMessage(Player player) {
        return player.getRepresentationNoPing()
                + ", during the Repair Units step, you may choose up to 2 units to leave damaged with **Death's Refrain**.";
    }

    private static List<DamageSelection> getSnapshot(Game game, Player player) {
        String snapshot = game.getStoredValue(getSnapshotKey(player));
        if (snapshot.isEmpty()) {
            return List.of();
        }
        List<DamageSelection> selections = new ArrayList<>();
        for (String entry : snapshot.split(";")) {
            String[] values = entry.split("\\|", 3);
            if (values.length == 3) {
                selections.add(new DamageSelection(values[0], values[1], values[2]));
            }
        }
        return selections;
    }

    private static String serializeSelections(List<DamageSelection> selections) {
        return selections.stream()
                .map(selection ->
                        selection.tilePosition() + "|" + selection.unitHolderName() + "|" + selection.unitAsyncId())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static List<Integer> getSelectedIndexes(Game game, Player player) {
        String storedIndexes = game.getStoredValue(getSelectedKey(player));
        if (storedIndexes.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> indexes = new ArrayList<>();
        for (String index : storedIndexes.split(",")) {
            try {
                indexes.add(Integer.parseInt(index));
            } catch (NumberFormatException e) {
                return new ArrayList<>();
            }
        }
        return indexes;
    }

    private static void clearDeathsRefrainState(Game game, Player player) {
        game.removeStoredValue(getSnapshotKey(player));
        game.removeStoredValue(getSelectedKey(player));
    }

    private static String getSnapshotKey(Player player) {
        return SNAPSHOT + player.getFaction();
    }

    private static String getSelectedKey(Player player) {
        return SELECTED + player.getFaction();
    }

    private record DamageSelection(String tilePosition, String unitHolderName, String unitAsyncId) {}
}

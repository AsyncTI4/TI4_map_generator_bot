package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Scrapyard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class ScrapyardAbilitiesHandler {
    public static final List<String> CUSTOM_RIGS =
            List.of("destroyer_customrig", "cruiser_customrig", "carrier_customrig", "dreadnought_customrig");
    private static final String CHOOSE_RIG = "chooseCustomRig_";
    private static final String USE_RIG = "useCustomRig_";
    private static final String ACTIVE_SUFFIX = "_active";
    private static final String DRIVE_DECOHERENCE = "drive_decoherence";
    private static final String REPURPOSED_PARTS = "repurposed_parts";
    private static final String USE_REPURPOSED_PARTS = "useRepurposedParts_";
    private static final String SELECT_REPURPOSED_PARTS_DOCK = "selectRepurposedPartsDock_";

    public static void getScrapyardRigsButtons(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility("custom_rigs")) {
            return;
        }

        List<Button> rigs = CUSTOM_RIGS.stream()
                .filter(rig -> !player.hasAbility(rig))
                .map(rig -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_RIG + rig,
                        Mapper.getAbility(rig).getName().replace("Custom Rig: ", ""),
                        switch (rig) {
                            case "destroyer_customrig" -> UnitEmojis.destroyer;
                            case "cruiser_customrig" -> UnitEmojis.cruiser;
                            case "carrier_customrig" -> UnitEmojis.carrier;
                            default -> UnitEmojis.dreadnought;
                        }))
                .toList();
        List<MessageEmbed> embeds = CUSTOM_RIGS.stream()
                .map(Mapper::getAbility)
                .filter(Objects::nonNull)
                .map(AbilityModel::getRepresentationEmbed)
                .toList();
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(), "Choose 2 custom rigs:", embeds, rigs);
    }

    @ButtonHandler(CHOOSE_RIG)
    public static void selectCustomRig(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !player.hasAbility("custom_rigs")) {
            return;
        }

        String chosenRig = buttonID.substring(CHOOSE_RIG.length());
        long selectedRigs = CUSTOM_RIGS.stream().filter(player::hasAbility).count();
        if (!CUSTOM_RIGS.contains(chosenRig) || player.hasAbility(chosenRig) || selectedRigs >= 2) {
            return;
        }

        player.addAbility(chosenRig);
        if (selectedRigs + 1 >= 2) {
            event.getMessage().delete().queue();
        } else {
            ButtonHelper.deleteTheOneButton(event);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " gained "
                        + Mapper.getAbility(chosenRig).getRepresentation() + ".");
    }

    public static void offerActivationRigButtons(Game game, Player player) {
        if (game == null
                || player == null
                || game.getActiveSystem().isEmpty()
                || !player.hasAbility("drive_decoherence")) {
            return;
        }

        List<Button> buttons = CUSTOM_RIGS.stream()
                .filter(player::hasAbility)
                .filter(rig -> !player.getExhaustedAbilities().contains(rig))
                .map(rig -> Buttons.green(
                        player.factionButtonChecker() + USE_RIG + rig,
                        "Use " + Mapper.getAbility(rig).getName()))
                .toList();
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may exhaust one or more Custom Rigs for this tactical action:",
                    buttons);
        }
    }

    @ButtonHandler(USE_RIG)
    public static void useCustomRig(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rig = buttonID.substring(USE_RIG.length());
        if (event == null
                || game == null
                || player == null
                || !CUSTOM_RIGS.contains(rig)
                || !player.hasAbility(rig)
                || player.getExhaustedAbilities().contains(rig)
                || game.getActiveSystem().isEmpty()) {
            return;
        }

        player.addExhaustedAbility(rig);
        player.addExhaustedAbility(rig + ACTIVE_SUFFIX);
        ButtonHelper.deleteTheOneButton(event);
        scheduleDriveDecoherence(game, player, rig);

        String message = player.getRepresentation() + " exhausted "
                + Mapper.getAbility(rig).getRepresentation() + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }

    public static boolean isRigActive(Player player, String rig) {
        return player != null && player.getExhaustedAbilities().contains(rig + ACTIVE_SUFFIX);
    }

    public static boolean isDestroyerTransportRigActive(Game game, Player player, Tile tile) {
        return game != null
                && tile != null
                && tile.getPosition().equals(game.getActiveSystem())
                && isRigActive(player, "destroyer_customrig");
    }

    public static void offerRepurposedParts(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits, boolean combat) {
        if (event == null || game == null || destroyedUnits == null || destroyedUnits.isEmpty() || !combat) {
            return;
        }

        var currentCombat = StartCombatService.getCurrentCombat(game);
        if (currentCombat == null || currentCombat.tilePosition() == null || currentCombat.unitHolderName() == null) {
            return;
        }
        Tile tile = game.getTileByPosition(currentCombat.tilePosition());
        UnitHolder combatHolder = tile == null ? null : tile.getUnitHolders().get(currentCombat.unitHolderName());
        if (combatHolder == null) {
            return;
        }

        for (String faction : currentCombat.factions()) {
            Player player = game.getPlayerFromColorOrFaction(faction);
            if (player == null || !player.hasAbility(REPURPOSED_PARTS)) {
                continue;
            }

            String usageKey = repurposedPartsKey(player, tile, combatHolder, currentCombat.round());
            if (!game.getStoredValue(usageKey).isBlank()) {
                continue;
            }

            Map<Float, UnitModel> destroyedByCost = new LinkedHashMap<>();
            for (RemovedUnit destroyedUnit : destroyedUnits) {
                if (destroyedUnit.tile() != tile
                        || destroyedUnit.uh() != combatHolder
                        || !player.unitBelongsToPlayer(destroyedUnit.unitKey())
                        || destroyedUnit.getTotalRemoved() < 1) {
                    continue;
                }
                UnitModel unit =
                        player.getPriorityUnitByAsyncID(destroyedUnit.unitKey().asyncID(), combatHolder);
                if (unit != null && unit.getCost() > 1) {
                    destroyedByCost.putIfAbsent(unit.getCost(), unit);
                }
            }
            if (destroyedByCost.isEmpty()) {
                continue;
            }

            List<Button> buttons = new ArrayList<>();
            for (Map.Entry<Float, UnitModel> entry : destroyedByCost.entrySet()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + USE_REPURPOSED_PARTS + tile.getPosition() + "|"
                                + combatHolder.getName() + "|" + entry.getKey() + "|" + currentCombat.round(),
                        "Use Repurposed Parts (" + entry.getValue().getName() + ")",
                        entry.getValue().getUnitEmoji()));
            }
            buttons.add(Buttons.gray("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may use _Repurposed Parts_ to produce 1 unit with a lower "
                            + "cost at a space dock you control.",
                    buttons);
        }
    }

    @ButtonHandler(USE_REPURPOSED_PARTS)
    public static void selectRepurposedPartsDock(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_REPURPOSED_PARTS.length()).split("\\|", 4);
        if (payload.length != 4 || !player.hasAbility(REPURPOSED_PARTS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile combatTile = game.getTileByPosition(payload[0]);
        UnitHolder combatHolder =
                combatTile == null ? null : combatTile.getUnitHolders().get(payload[1]);
        float destroyedCost;
        int combatRound;
        try {
            destroyedCost = Float.parseFloat(payload[2]);
            combatRound = Integer.parseInt(payload[3]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        var currentCombat = StartCombatService.getCurrentCombat(game);
        if (combatHolder == null
                || currentCombat == null
                || currentCombat.round() != combatRound
                || !combatTile.getPosition().equals(currentCombat.tilePosition())
                || !combatHolder.getName().equals(currentCombat.unitHolderName())
                || !currentCombat.factions().contains(player.getFaction())
                || !game.getStoredValue(repurposedPartsKey(player, combatTile, combatHolder, combatRound))
                        .isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SELECT_REPURPOSED_PARTS_DOCK + tile.getPosition() + "|"
                                + destroyedCost + "|" + combatRound,
                        "Produce in " + tile.getRepresentationForButtons(game, player)))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(repurposedPartsKey(player, combatTile, combatHolder, combatRound), "used");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a space dock for _Repurposed Parts_.",
                buttons);
    }

    @ButtonHandler(SELECT_REPURPOSED_PARTS_DOCK)
    public static void produceWithRepurposedParts(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_REPURPOSED_PARTS_DOCK.length()).split("\\|", 3);
        if (payload.length != 3 || !player.hasAbility(REPURPOSED_PARTS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        float destroyedCost;
        try {
            destroyedCost = Float.parseFloat(payload[1]);
            Integer.parseInt(payload[2]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tile == null
                || ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock).stream()
                        .noneMatch(tile::equals)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>(
                Helper.getPlaceUnitButtons(event, player, game, tile, "repurposedParts", "placeOneNDone_dontskip"));
        String placementPrefix = player.factionButtonChecker() + "placeOneNDone_dontskip_";
        buttons.removeIf(button -> {
            String id = button.getCustomId();
            if (id == null || !id.startsWith(placementPrefix)) {
                return true;
            }
            String placement = id.substring(placementPrefix.length());
            int separator = placement.indexOf('_');
            if (separator < 1 || Character.isDigit(placement.charAt(0))) return true;
            UnitModel unit =
                    player.getPriorityUnitByAsyncID(AliasHandler.resolveUnit(placement.substring(0, separator)), null);
            return unit == null || unit.getCost() >= destroyedCost;
        });
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose 1 unit with a cost lower than " + destroyedCost
                        + " to produce with _Repurposed Parts_.",
                buttons);
    }

    public static void resolveEndOfTacticalAction(Game game, Player player, GenericInteractionCreateEvent event) {
        if (game == null || player == null) {
            return;
        }

        if (isRigActive(player, "cruiser_customrig")) {
            Tile tile = game.getTileByPosition(game.getActiveSystem());
            if (tile != null) {
                for (var planet : tile.getPlanetUnitHolders()) {
                    int cruisers = planet.getUnitCount(UnitType.Cruiser, player);
                    if (cruisers > 0) {
                        MoveUnitService.moveUnits(
                                event,
                                tile,
                                game,
                                player.getColor(),
                                cruisers + " cruiser " + planet.getName(),
                                tile,
                                Constants.SPACE);
                    }
                }
            }
        }

        List<String> rigsToDestroy = List.of(
                        game.getStoredValue(driveDecoherenceKey(player)).split(","))
                .stream()
                .filter(CUSTOM_RIGS::contains)
                .toList();
        game.removeStoredValue(driveDecoherenceKey(player));
        for (String rig : rigsToDestroy) {
            String unit = rig.replace("_customrig", "");
            List<Button> buttons =
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, unit, true, true);
            if (buttons.size() > 1) {
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentation()
                                + " must destroy 1 "
                                + unit
                                + " because _Drive Decoherence_ triggered when they exhausted "
                                + Mapper.getAbility(rig).getRepresentation()
                                + ".",
                        buttons);
            }
        }

        CUSTOM_RIGS.forEach(rig -> player.removeExhaustedAbility(rig + ACTIVE_SUFFIX));
        ScrapyardUnitHandler.clearFuelCell(game, player);
    }

    private static void scheduleDriveDecoherence(Game game, Player player, String rig) {
        if (game.getRealPlayers().stream().noneMatch(owner -> owner.hasAbility(DRIVE_DECOHERENCE))) {
            return;
        }
        String key = driveDecoherenceKey(player);
        String scheduledRigs = game.getStoredValue(key);
        game.setStoredValue(key, scheduledRigs.isEmpty() ? rig : scheduledRigs + "," + rig);
    }

    private static String driveDecoherenceKey(Player player) {
        return "scrapyardDriveDecoherence" + player.getFaction();
    }

    private static String repurposedPartsKey(Player player, Tile tile, UnitHolder holder, int round) {
        return "scrapyardRepurposedParts" + player.getFaction() + tile.getPosition() + holder.getName() + round;
    }
}

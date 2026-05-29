package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class NetrunnersLeadersHandler {

    private static final String AGENT_ID = "netrunnersagent";
    private static final String AGENT_SPENT_PREFIX = "netrunnersagent";
    private static final String AGENT_BUTTON_PREFIX = "netrunnersAgent_";
    private static final String CHOOSE_TARGET_BUTTON_ID = "netrunnersAgentChooseTarget";
    private static final String TARGET_PICKER_MESSAGE = "choose a player to use **Overclock** on.";
    private static final String COMMANDER_ID = "netrunnerscommander";
    private static final String COMMANDER_FAKE_UNIT_ID = "netrunnerscommanderpds";
    private static final String HERO_CHOOSE_STRUCTURE_PREFIX = "netrunnersHeroChooseStructure_";
    private static final String HERO_DESTROY_STRUCTURE_PREFIX = "netrunnersHeroDestroyStructure_";

    public static List<Button> getOverclockButtons(Game game, Player producingPlayer, Tile productionTile) {
        if (producingPlayer == null
                || productionTile == null
                || getHighestResourceOverclockPlanet(game, producingPlayer, productionTile) == null) {
            return List.of();
        }
        return game.getRealPlayers().stream()
                .filter(netrunner -> netrunner.hasUnexhaustedLeader(AGENT_ID))
                .map(netrunner -> Buttons.gray(
                        getOverclockButtonID(netrunner, producingPlayer, productionTile),
                        "Use Overclock",
                        FactionEmojis.netrunners))
                .toList();
    }

    public static Button getOverclockCardsInfoButton(Player netrunner) {
        return Buttons.gray(
                netrunner.factionButtonChecker() + CHOOSE_TARGET_BUTTON_ID,
                "Use Overclock on someone else",
                FactionEmojis.netrunners);
    }

    @ButtonHandler(CHOOSE_TARGET_BUTTON_ID)
    public static void offerOverclockTargetButtons(Game game, Player netrunner, ButtonInteractionEvent event) {
        List<Button> buttons = getOverclockTargetButtons(game, netrunner);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No eligible players were found for **Overclock**.");
            return;
        }

        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event, netrunner.getRepresentationUnfogged() + ", choose a player to use **Overclock** on.", buttons);
    }

    @ButtonHandler(AGENT_BUTTON_PREFIX)
    public static void resolveOverclock(Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace(AGENT_BUTTON_PREFIX, "").split("_", 2);
        if (parts.length < 2) {
            return;
        }
        if (!netrunner.hasUnexhaustedLeader(AGENT_ID)) {
            rejectOverclock(event, "**Overclock** is no longer available.");
            return;
        }

        OverclockTarget target = getOverclockTarget(game, parts[0], parts[1]);
        if (target == null) {
            rejectOverclock(event, "No eligible adjacent planets were found for **Overclock**.");
            return;
        }

        if (isTargetPickerMessage(event.getMessage())) {
            resolveTargetPickerOverclock(game, netrunner, target, event);
            return;
        }

        String exhaustedMessage = applyOverclock(game, netrunner, target.producingPlayer(), target.planet());
        if (exhaustedMessage == null) {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static String applyOverclock(Game game, Player netrunner, Player producingPlayer, Planet planet) {
        Leader agent = netrunner.getLeader(AGENT_ID).orElse(null);
        if (agent == null || !netrunner.hasUnexhaustedLeader(AGENT_ID)) {
            return null;
        }
        ExhaustLeaderService.exhaustLeader(game, netrunner, agent);
        producingPlayer.addSpentThing(AGENT_SPENT_PREFIX + "_" + planet.getResources() + "_" + netrunner.getFaction());
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                netrunner.getRepresentation() + " exhausted **Overclock** to reduce "
                        + producingPlayer.getRepresentation()
                        + "'s production cost by " + planet.getResources()
                        + ". The highest-resource eligible planet was "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".");
        return Helper.buildSpentThingsMessage(producingPlayer, game, "res");
    }

    private static List<Button> getOverclockTargetButtons(Game game, Player netrunner) {
        if (!netrunner.hasUnexhaustedLeader(AGENT_ID)) {
            return List.of();
        }

        return game.getRealPlayers().stream()
                .filter(producingPlayer -> producingPlayer != netrunner)
                .map(producingPlayer -> getBestOverclockTarget(game, producingPlayer))
                .filter(Objects::nonNull)
                .map(target -> Buttons.gray(
                        getOverclockButtonID(netrunner, target.producingPlayer(), target.productionTile()),
                        target.producingPlayer().getColorDisplayName() + ": "
                                + Helper.getPlanetRepresentation(target.planet().getName(), game),
                        target.producingPlayer().fogSafeEmoji()))
                .toList();
    }

    private static OverclockTarget getOverclockTarget(Game game, String faction, String position) {
        return getOverclockTarget(game, game.getPlayerFromColorOrFaction(faction), game.getTileByPosition(position));
    }

    private static OverclockTarget getBestOverclockTarget(Game game, Player producingPlayer) {
        return producingPlayer.getCurrentProducedUnits().keySet().stream()
                .map(producedUnit -> getProductionTile(game, producedUnit))
                .filter(Objects::nonNull)
                .map(productionTile -> getOverclockTarget(game, producingPlayer, productionTile))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(target -> target.planet().getResources()))
                .orElse(null);
    }

    private static OverclockTarget getOverclockTarget(Game game, Player producingPlayer, Tile productionTile) {
        Planet planet = getHighestResourceOverclockPlanet(game, producingPlayer, productionTile);
        return planet == null ? null : new OverclockTarget(producingPlayer, productionTile, planet);
    }

    private static Tile getProductionTile(Game game, String producedUnit) {
        String[] parts = producedUnit.split("_");
        return parts.length < 2 ? null : game.getTileByPosition(parts[1]);
    }

    public static boolean isOverclockSpentThing(String thing) {
        return thing.startsWith(AGENT_SPENT_PREFIX + "_");
    }

    public static int getOverclockSpentResources(String thing) {
        return Integer.parseInt(thing.split("_")[1]);
    }

    public static String getOverclockSpentMessage(Game game, String thing) {
        String[] parts = thing.split("_");
        int discount = Integer.parseInt(parts[1]);
        String faction = game.isFowMode() ? "someone" : parts[2];
        Player netrunner = game.getPlayerFromColorOrFaction(faction);
        String source = "someone".equals(faction) || netrunner == null
                ? ""
                : " (from " + netrunner.getRepresentationNoPing() + ")";
        return "> Used **Overclock**" + source + " for " + discount + " resource" + (discount == 1 ? "" : "s") + "\n";
    }

    private static boolean isTargetPickerMessage(Message message) {
        return message.getContentRaw().contains(TARGET_PICKER_MESSAGE);
    }

    private static void resolveTargetPickerOverclock(
            Game game, Player netrunner, OverclockTarget target, ButtonInteractionEvent event) {
        String overclockButtonID = getOverclockButtonID(netrunner, target.producingPlayer(), target.productionTile());
        target.producingPlayer()
                .getCorrectChannel()
                .getHistory()
                .retrievePast(25)
                .queue(
                        messages -> {
                            Message message = messages.stream()
                                    .filter(message_ -> hasButton(message_, overclockButtonID))
                                    .findFirst()
                                    .orElse(null);
                            if (message != null) {
                                applyOverclockToPaymentMessage(
                                        game, netrunner, target, event, message, overclockButtonID);
                                return;
                            }
                            deleteWithEphemeral(
                                    event, "Could not find the active production payment message for **Overclock**.");
                            BotLogger.warning("Could not find Overclock production payment message for "
                                    + target.producingPlayer().getFaction() + ".");
                        },
                        BotLogger::catchRestError);
    }

    private static void applyOverclockToPaymentMessage(
            Game game,
            Player netrunner,
            OverclockTarget target,
            ButtonInteractionEvent event,
            Message message,
            String overclockButtonID) {
        String exhaustedMessage = applyOverclock(game, netrunner, target.producingPlayer(), target.planet());
        if (exhaustedMessage == null) {
            deleteWithEphemeral(event, "**Overclock** is no longer available.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        editSpentSummary(message, overclockButtonID, exhaustedMessage);
    }

    private static void rejectOverclock(ButtonInteractionEvent event, String message) {
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
        removeOverclockButton(event);
    }

    private static void deleteWithEphemeral(ButtonInteractionEvent event, String message) {
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
        ButtonHelper.deleteMessage(event);
    }

    private static void removeOverclockButton(ButtonInteractionEvent event) {
        if (isTargetPickerMessage(event.getMessage())) {
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        }
    }

    private static String getOverclockButtonID(Player netrunner, Player producingPlayer, Tile productionTile) {
        return netrunner.factionButtonChecker() + AGENT_BUTTON_PREFIX + producingPlayer.getFaction() + "_"
                + productionTile.getPosition();
    }

    private static boolean hasButton(Message message, String buttonID) {
        return message.getComponentTree().findAll(Button.class).stream()
                .map(Button::getCustomId)
                .anyMatch(buttonID::equals);
    }

    private static void editSpentSummary(Message message, String buttonID, String exhaustedMessage) {
        List<Button> buttons = message.getComponentTree().findAll(Button.class).stream()
                .filter(button -> !buttonID.equals(button.getCustomId()))
                .toList();
        message.editMessage(exhaustedMessage)
                .setComponents(ActionRow.partitionOf(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static Planet getHighestResourceOverclockPlanet(Game game, Player producingPlayer, Tile productionTile) {
        if (producingPlayer == null || productionTile == null) {
            return null;
        }

        Set<String> adjacentTilePositions =
                FoWHelper.getAdjacentTiles(game, productionTile.getPosition(), producingPlayer, false, true);
        return adjacentTilePositions.stream()
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .filter(Planet.class::isInstance)
                .map(Planet.class::cast)
                .filter(planet -> isEligibleOverclockPlanet(game, producingPlayer, adjacentTilePositions, planet))
                .max(Comparator.comparingInt(Planet::getResources))
                .orElse(null);
    }

    private static boolean isEligibleOverclockPlanet(
            Game game, Player producingPlayer, Set<String> adjacentTilePositions, Planet planet) {
        Tile planetTile = game.getTileFromPlanet(planet.getName());
        Player planetOwner = game.getPlanetOwner(planet.getName());
        return planet.getResources() >= 1
                && planetTile != null
                && planetOwner != null
                && planetOwner != producingPlayer
                && adjacentTilePositions.contains(planetTile.getPosition());
    }

    public static void startRevolution(Game game, Player netrunner) {
        if (!isNetrunnersPlayer(netrunner)) {
            return;
        }

        List<Player> targets = getRevolutionTargets(game, netrunner);
        if (targets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    netrunner.getCorrectChannel(),
                    netrunner.getRepresentation()
                            + " used **Digital Uprising**, but no players with control tokens in the **"
                            + NetrunnersAbilitiesHandler.SYSTEM_BREACH_POOL
                            + "** pool have structures on non-home planets to destroy.");
            return;
        }

        String targetPings =
                String.join(" ", targets.stream().map(Player::getRepresentation).toList());
        List<Button> buttons = targets.stream()
                .map(target -> Buttons.red(
                        target.factionButtonChecker() + HERO_CHOOSE_STRUCTURE_PREFIX + netrunner.getFaction(),
                        "Destroy a structure",
                        FactionEmojis.netrunners))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                netrunner.getCorrectChannel(),
                netrunner.getRepresentation() + " used **Digital Uprising**. "
                        + targetPings
                        + ", use your button to choose and destroy 1 structure you own on a non-home planet.",
                buttons);
    }

    @ButtonHandler(HERO_CHOOSE_STRUCTURE_PREFIX)
    public static void offerRevolutionStructureButtons(
            Game game, Player target, ButtonInteractionEvent event, String buttonID) {
        Player netrunner = game.getPlayerFromColorOrFaction(buttonID.replace(HERO_CHOOSE_STRUCTURE_PREFIX, ""));
        if (!isRevolutionTarget(game, netrunner, target)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You do not have an eligible structure to destroy for **Digital Uprising**.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
            return;
        }

        List<Button> buttons = getRevolutionStructureButtons(game, netrunner, target);
        MessageChannel channel =
                target.getCardsInfoThread() == null ? target.getCorrectChannel() : target.getCardsInfoThread();
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                target.getRepresentationUnfogged()
                        + ", choose 1 structure you own on a non-home planet to destroy for **Digital Uprising**.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }

    @ButtonHandler(HERO_DESTROY_STRUCTURE_PREFIX)
    public static void resolveRevolutionStructure(
            Game game, Player target, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace(HERO_DESTROY_STRUCTURE_PREFIX, "").split("_", 4);
        if (parts.length < 4) {
            return;
        }

        Player netrunner = game.getPlayerFromColorOrFaction(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        UnitType unitType = getUnitType(parts[2]);
        String unitHolderName = parts[3];
        RevolutionStructure structure = getRevolutionStructure(target, tile, unitHolderName, unitType);
        if (!isRevolutionTarget(game, netrunner, target) || structure == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That structure is no longer eligible for **Digital Uprising**.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnit(event, tile, game, structure.unitKey(), 1, structure.unitHolder(), false);
        AddUnitService.addUnits(event, tile, game, netrunner.getColor(), "2 inf " + unitHolderName);
        StartCombatService.groundCombatCheck(game, structure.unitHolder(), tile, event);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                target.getRepresentation() + " destroyed 1 " + getStructureName(target, structure)
                        + " on " + Helper.getPlanetRepresentation(unitHolderName, game)
                        + " for " + netrunner.getRepresentation(false, true)
                        + "'s **Digital Uprising**. Added 2 "
                        + netrunner.getFactionEmojiOrColor()
                        + " infantry to that planet. Resolve ground combat if able.");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(
                    game, event, "Digital Uprising on " + Helper.getPlanetRepresentation(unitHolderName, game));
        }
    }

    public static void checkCommanderUnlock(Game game, UnitKey unitKey) {
        if (game == null || unitKey == null || unitKey.unitType() != UnitType.Pds) {
            return;
        }

        Player player = game.getPlayerByColorID(unitKey.colorID()).orElse(null);
        if (player != null && player.hasLeader(COMMANDER_ID)) {
            CommanderUnlockCheckService.checkPlayer(player, "netrunners");
        }
    }

    public static List<Player> getCommanderSpaceCannonPlayers(
            Player activePlayer, Game game, String tilePos, List<Player> playersWithPds) {
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null || tile.isScar(game)) {
            return List.of();
        }
        return game.getRealPlayers().stream()
                .filter(netrunner -> !playersWithPds.contains(netrunner))
                .filter(netrunner -> game.playerHasLeaderUnlockedOrAlliance(netrunner, COMMANDER_ID))
                .filter(netrunner -> canUseSpaceCannonAgainstActivePlayer(activePlayer, game, tile, netrunner))
                .filter(netrunner ->
                        !getCommanderSpaceCannonUnits(game, netrunner, tile).isEmpty())
                .toList();
    }

    public static Map<UnitModel, Integer> getCommanderSpaceCannonUnits(Game game, Player netrunner, Tile targetTile) {
        if (targetTile == null || targetTile.isScar(game)) {
            return Map.of();
        }

        Map<UnitModel, Integer> units = new LinkedHashMap<>();
        for (Player owner : game.getRealPlayersExcludingThis(netrunner)) {
            if (netrunner.getDebtTokenCount(owner.getColor(), NetrunnersAbilitiesHandler.SYSTEM_BREACH_POOL) < 1) {
                continue;
            }
            BorrowedPds pds = getBestBorrowedPds(game, netrunner, owner, targetTile);
            if (pds != null) {
                units.put(getCommanderPdsModel(netrunner, owner, pds), 1);
            }
        }
        return units;
    }

    private static boolean canUseSpaceCannonAgainstActivePlayer(
            Player activePlayer, Game game, Tile tile, Player rollingPlayer) {
        if (rollingPlayer == activePlayer || activePlayer.getAllianceMembers().contains(rollingPlayer.getFaction())) {
            return FoWHelper.otherPlayersHaveShipsInSystem(activePlayer, tile, game);
        }
        return true;
    }

    private static BorrowedPds getBestBorrowedPds(Game game, Player netrunner, Player owner, Tile targetTile) {
        return CheckUnitContainmentService.getTilesContainingPlayersUnits(game, owner, UnitType.Pds).stream()
                .filter(pdsTile -> isCommanderPdsTileUsable(game, netrunner, targetTile, pdsTile))
                .flatMap(pdsTile -> pdsTile.getUnitHolders().values().stream()
                        .flatMap(unitHolder -> getBorrowedPds(owner, pdsTile, unitHolder).stream()))
                .filter(pds -> isCommanderPdsInRange(game, netrunner, owner, targetTile, pds))
                .max(Comparator.comparingInt(pds -> getPdsScore(owner, pds.model())))
                .orElse(null);
    }

    private static boolean isCommanderPdsTileUsable(Game game, Player netrunner, Tile targetTile, Tile pdsTile) {
        return pdsTile != null
                && !pdsTile.isScar(game)
                && (targetTile.getPosition().equals(pdsTile.getPosition())
                        || !TeHelperUnits.affectedByQuietus(game, netrunner, pdsTile));
    }

    private static List<BorrowedPds> getBorrowedPds(Player owner, Tile tile, UnitHolder unitHolder) {
        return unitHolder.getUnitKeysForPlayer(owner).stream()
                .filter(unitKey -> unitKey.unitType() == UnitType.Pds)
                .map(owner::getUnitFromUnitKey)
                .filter(Objects::nonNull)
                .filter(model -> model.getSpaceCannonDieCount(owner) > 0)
                .map(model -> new BorrowedPds(tile, model))
                .toList();
    }

    private static boolean isCommanderPdsInRange(
            Game game, Player netrunner, Player owner, Tile targetTile, BorrowedPds pds) {
        return targetTile.getPosition().equals(pds.tile().getPosition())
                || (FoWHelper.getAdjacentTiles(game, targetTile.getPosition(), netrunner, false, true)
                                .contains(pds.tile().getPosition())
                        && (pds.model().getDeepSpaceCannon(owner)
                                || game.playerHasLeaderUnlockedOrAlliance(netrunner, "mirvedacommander")));
    }

    private static int getPdsScore(Player owner, UnitModel model) {
        return model.getSpaceCannonDieCount(owner) * (11 - model.getSpaceCannonHitsOn(owner));
    }

    private static List<Player> getRevolutionTargets(Game game, Player netrunner) {
        return game.getRealPlayersExcludingThis(netrunner).stream()
                .filter(target -> isRevolutionTarget(game, netrunner, target))
                .toList();
    }

    private static boolean isRevolutionTarget(Game game, Player netrunner, Player target) {
        return isNetrunnersPlayer(netrunner)
                && target != null
                && netrunner.getDebtTokenCount(target.getColor(), NetrunnersAbilitiesHandler.SYSTEM_BREACH_POOL) > 0
                && !getRevolutionStructures(game, target).isEmpty();
    }

    private static boolean isNetrunnersPlayer(Player player) {
        return player != null && "netrunners".equals(player.getFaction());
    }

    private static List<Button> getRevolutionStructureButtons(Game game, Player netrunner, Player target) {
        return getRevolutionStructures(game, target).stream()
                .map(structure -> Buttons.red(
                        target.factionButtonChecker() + HERO_DESTROY_STRUCTURE_PREFIX
                                + netrunner.getFaction() + "_"
                                + structure.tile().getPosition() + "_"
                                + structure.unitKey().unitTypeVal() + "_"
                                + structure.unitHolder().getName(),
                        getStructureName(target, structure) + " on "
                                + Helper.getPlanetRepresentation(
                                        structure.unitHolder().getName(), game),
                        structure.unitKey().unitEmoji()))
                .toList();
    }

    private static List<RevolutionStructure> getRevolutionStructures(Game game, Player target) {
        if (game == null) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .flatMap(tile -> tile.getPlanetUnitHolders().stream()
                        .flatMap(unitHolder -> getRevolutionStructures(target, tile, unitHolder).stream()))
                .toList();
    }

    private static List<RevolutionStructure> getRevolutionStructures(Player target, Tile tile, UnitHolder unitHolder) {
        if (target == null || unitHolder == null) {
            return List.of();
        }
        return unitHolder.getUnitKeysForPlayer(target).stream()
                .filter(unitKey -> isStructure(target, unitKey))
                .map(unitKey -> new RevolutionStructure(tile, unitHolder, unitKey))
                .toList();
    }

    private static UnitType getUnitType(String unitType) {
        if (unitType == null) {
            return null;
        }
        UnitType type = Units.findUnitType(unitType);
        if (type != null) {
            return type;
        }
        try {
            return UnitType.valueOf(unitType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static RevolutionStructure getRevolutionStructure(
            Player target, Tile tile, String unitHolderName, UnitType unitType) {
        if (target == null || tile == null || unitType == null) {
            return null;
        }
        UnitHolder unitHolder = tile.getUnitHolders().get(unitHolderName);
        if (!(unitHolder instanceof Planet)) {
            return null;
        }
        return getRevolutionStructures(target, tile, unitHolder).stream()
                .filter(structure -> structure.unitKey().unitType() == unitType)
                .findFirst()
                .orElse(null);
    }

    private static boolean isStructure(Player target, UnitKey unitKey) {
        UnitModel model = target.getUnitFromUnitKey(unitKey);
        return model == null
                ? unitKey.unitType() == UnitType.Pds || unitKey.unitType() == UnitType.Spacedock
                : model.getIsStructure();
    }

    private static String getStructureName(Player target, RevolutionStructure structure) {
        UnitModel model = target.getUnitFromUnitKey(structure.unitKey());
        return model == null ? structure.unitKey().humanReadableName() : model.getName();
    }

    private static UnitModel getCommanderPdsModel(Player netrunner, Player owner, BorrowedPds pds) {
        UnitModel model = new UnitModel() {
            @Override
            public UnitType getUnitType() {
                return UnitType.Pds;
            }
        };
        model.setSpaceCannonHitsOn(pds.model().getSpaceCannonHitsOn(owner));
        model.setSpaceCannonDieCount(pds.model().getSpaceCannonDieCount(owner));
        model.setName(
                "Seize: " + owner.getColorDisplayName() + " " + pds.model().getName());
        model.setAsyncId(COMMANDER_FAKE_UNIT_ID + owner.getFaction());
        model.setId(COMMANDER_FAKE_UNIT_ID + owner.getFaction());
        model.setBaseType("pds");
        model.setFaction(netrunner.getFaction());
        model.setDeepSpaceCannon(pds.model().getDeepSpaceCannon(owner));
        return model;
    }

    private record OverclockTarget(Player producingPlayer, Tile productionTile, Planet planet) {}

    private record BorrowedPds(Tile tile, UnitModel model) {}

    private record RevolutionStructure(Tile tile, UnitHolder unitHolder, UnitKey unitKey) {}
}

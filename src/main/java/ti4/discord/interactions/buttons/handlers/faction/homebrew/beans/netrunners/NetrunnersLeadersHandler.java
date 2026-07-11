package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.UnitQueryService;

@UtilityClass
public class NetrunnersLeadersHandler {

    private static final String AGENT_ID = "netrunnersagent";
    private static final String AGENT_CHOOSE_TARGET = "netrunnersAgentChooseTarget";
    private static final String AGENT_TARGET = "netrunnersAgentTarget_";
    private static final String OVERCLOCK_TILE_PREFIX = "netrunnersAgentTile_";
    private static final String OVERCLOCK_UNIT_PREFIX = "netrunnersAgentUnit_";
    private static final String OVERCLOCK_DONE_PREFIX = "netrunnersAgentDone_";
    private static final String OVERCLOCK_REMAINING_PREFIX = "netrunnersAgentRemaining_";
    private static final String COMMANDER_ID = "netrunnerscommander";
    private static final String COMMANDER_FAKE_UNIT_ID = "netrunnerscommanderpds";
    private static final String HERO_CHOOSE_STRUCTURE_PREFIX = "netrunnersHeroChooseStructure_";
    private static final String HERO_DESTROY_STRUCTURE_PREFIX = "netrunnersHeroDestroyStructure_";

    public static Button getOverclockCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + AGENT_CHOOSE_TARGET, "Use Overclock", FactionEmojis.netrunners);
    }

    @ButtonHandler(AGENT_CHOOSE_TARGET)
    public static void chooseOverclockTarget(ButtonInteractionEvent event, Player player, Game game) {
        if (!player.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Overclock is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + AGENT_TARGET + target.getFaction(), target.getColorDisplayName()));
        }

        MessageChannel channel =
                player.getCardsInfoThread() == null ? player.getCorrectChannel() : player.getCardsInfoThread();
        MessageHelper.sendMessageToChannelWithButtons(
                channel, player.toString() + ", please choose a player to use _Overclock_ on:", buttons);
    }

    public static void startOverclockSession(Game game, Player netrunner, Player affectedPlayer) {
        game.setStoredValue(OVERCLOCK_REMAINING_PREFIX + affectedPlayer.getFaction(), "2");
    }

    public static int getOverclockRemaining(Game game, Player affectedPlayer) {
        String stored = game.getStoredValue(OVERCLOCK_REMAINING_PREFIX + affectedPlayer.getFaction());
        return stored.isEmpty() ? 0 : Integer.parseInt(stored);
    }

    private static void setOverclockRemaining(Game game, Player affectedPlayer, int remaining) {
        if (remaining <= 0) {
            clearOverclockSession(game, affectedPlayer);
            return;
        }
        game.setStoredValue(OVERCLOCK_REMAINING_PREFIX + affectedPlayer.getFaction(), Integer.toString(remaining));
    }

    private static void clearOverclockSession(Game game, Player affectedPlayer) {
        game.removeStoredValue(OVERCLOCK_REMAINING_PREFIX + affectedPlayer.getFaction());
    }

    private static List<Tile> getOverclockStructureTiles(Game game, Player affectedPlayer) {
        if (game == null || affectedPlayer == null) {
            return List.of();
        }

        return game.getTiles().stream()
                .filter(Objects::nonNull)
                .filter(tile -> tile.containsPlayersUnitsWithModelCondition(affectedPlayer, UnitModel::getIsStructure))
                .toList();
    }

    private static List<Button> getOverclockStructureTileButtons(Game game, Player affectedPlayer) {
        List<Button> buttons = getOverclockStructureTiles(game, affectedPlayer).stream()
                .limit(24)
                .map(tile -> Buttons.green(
                        affectedPlayer.factionButtonChecker()
                                + OVERCLOCK_TILE_PREFIX
                                + affectedPlayer.getFaction()
                                + "_"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, affectedPlayer)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        buttons.add(Buttons.red(
                affectedPlayer.factionButtonChecker() + OVERCLOCK_DONE_PREFIX + affectedPlayer.getFaction(),
                "Done With Overclock"));
        return buttons;
    }

    @ButtonHandler(AGENT_TARGET)
    public static void resolveOverclockTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Overclock_ is no longer available.");
            return;
        }

        String faction = getOverclockPayload(buttonID, AGENT_TARGET);
        Player affectedPlayer = game.getPlayerFromColorOrFaction(faction);
        if (affectedPlayer == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }

        startOverclockSession(game, player, affectedPlayer);

        Leader agent = player.getLeader(AGENT_ID).orElse(null);
        if (agent != null) {
            ExhaustLeaderService.exhaustLeader(game, player, agent);
        }

        List<Button> buttons = getOverclockStructureTileButtons(game, affectedPlayer);
        if (buttons.isEmpty()) {
            clearOverclockSession(game, affectedPlayer);
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Player has no eligibl tiles with structures.");
            return;
        }

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.toString() + " exhausted **Overclock** to allow "
                        + affectedPlayer.toString()
                        + " to produce up to 2 units with tiles containing their structures.");

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                affectedPlayer.getRepresentationUnfogged()
                        + ", choose a tile containing one of your structures for _Overclock_. "
                        + "You have " + getOverclockRemaining(game, affectedPlayer) + " use(s) remaining.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    private static String getOverclockPayload(String buttonID, String prefix) {
        int prefixIndex = buttonID.indexOf(prefix);
        if (prefixIndex < 0) {
            return "";
        }
        return buttonID.substring(prefixIndex + prefix.length());
    }

    @ButtonHandler(OVERCLOCK_TILE_PREFIX)
    public static void resolveOverclockTile(
            Game game, Player affectedPlayer, ButtonInteractionEvent event, String buttonID) {
        String payload = getOverclockPayload(buttonID, OVERCLOCK_TILE_PREFIX);
        String[] parts = payload.split("_", 2);
        if (parts.length < 2) {
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);

        if (target == null || tile == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Overclock option is no longer valid.");
            return;
        }

        if (!affectedPlayer.equals(target)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That Overclock option is not for you.");
            return;
        }

        if (getOverclockRemaining(game, affectedPlayer) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Overclock** is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!getOverclockStructureTiles(game, affectedPlayer).contains(tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That tile no longer contains one of your structures.");
            return;
        }

        List<Button> buttons = getOverclockUnitButtons(event, game, affectedPlayer, tile);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No valid Overclock production options were found for that tile.");
            return;
        }

        String message = affectedPlayer.getRepresentationUnfogged()
                + ", use these buttons to produce in "
                + tile.getRepresentationForButtons(game, affectedPlayer)
                + " for **Overclock**. "
                + getOverclockRemaining(game, affectedPlayer)
                + " Overclock use(s) remaining.";

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
    }

    @ButtonHandler(OVERCLOCK_UNIT_PREFIX)
    public static void resolveOverclockUnit(
            Game game, Player affectedPlayer, ButtonInteractionEvent event, String buttonID) {
        String payload = getOverclockPayload(buttonID, OVERCLOCK_UNIT_PREFIX);
        String[] parts = payload.split("\\|", 2);
        if (parts.length < 2) {
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        String placeButtonId = parts[1];
        if (target == null || !affectedPlayer.equals(target) || getOverclockRemaining(game, affectedPlayer) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Overclock** is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelperModifyUnits.placeUnitAndDeleteButton(placeButtonId, event, game, affectedPlayer);
        sendOverclockPaymentPrompt(game, affectedPlayer, event);
        int remaining = getOverclockRemaining(game, affectedPlayer) - 1;
        setOverclockRemaining(game, affectedPlayer, remaining);
        if (remaining < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    affectedPlayer.getRepresentationUnfogged() + " has finished resolving **Overclock**.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                affectedPlayer.getRepresentationUnfogged()
                        + ", choose another tile containing one of your structures for _Overclock_. "
                        + "You have " + remaining + " use(s) remaining.",
                getOverclockStructureTileButtons(game, affectedPlayer));
    }

    @ButtonHandler(OVERCLOCK_DONE_PREFIX)
    public static void resolveOverclockDone(
            Game game, Player affectedPlayer, ButtonInteractionEvent event, String buttonID) {
        String faction = getOverclockPayload(buttonID, OVERCLOCK_DONE_PREFIX);
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target != null && affectedPlayer.equals(target)) {
            clearOverclockSession(game, affectedPlayer);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getOverclockUnitButtons(
            ButtonInteractionEvent event, Game game, Player affectedPlayer, Tile tile) {
        Map<String, Integer> producedUnitsSnapshot = new LinkedHashMap<>(affectedPlayer.getCurrentProducedUnits());
        List<Button> generatedButtons = Helper.getPlaceUnitButtons(
                event, affectedPlayer, game, tile, "netrunnersagent", "placeOneNDone_skipbuild");
        restoreProducedUnits(affectedPlayer, producedUnitsSnapshot);

        List<Button> wrappedButtons = generatedButtons.stream()
                .filter(button -> button.getCustomId() != null)
                .filter(button -> button.getCustomId().contains("placeOneNDone_"))
                .map(button -> wrapOverclockUnitButton(affectedPlayer, button))
                .limit(24)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        wrappedButtons.add(Buttons.red(
                affectedPlayer.factionButtonChecker() + OVERCLOCK_DONE_PREFIX + affectedPlayer.getFaction(),
                "Done With Overclock"));
        return wrappedButtons;
    }

    private static void sendOverclockPaymentPrompt(Game game, Player affectedPlayer, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, affectedPlayer, "res"));
        buttons.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                affectedPlayer.getRepresentationUnfogged() + ", Pay for the additional unit.",
                buttons);
    }

    private static void restoreProducedUnits(Player affectedPlayer, Map<String, Integer> producedUnitsSnapshot) {
        affectedPlayer.resetProducedUnits();
        producedUnitsSnapshot.forEach(affectedPlayer::setProducedUnit);
    }

    private static Button wrapOverclockUnitButton(Player affectedPlayer, Button button) {
        String innerId = button.getCustomId();
        String checker = affectedPlayer.factionButtonChecker();
        if (innerId.startsWith(checker)) {
            innerId = innerId.substring(checker.length());
        }
        String wrappedId = checker + OVERCLOCK_UNIT_PREFIX + affectedPlayer.getFaction() + "|" + innerId;
        String emoji = button.getEmoji() == null ? null : button.getEmoji().toString();

        return switch (button.getStyle()) {
            case PRIMARY -> Buttons.blue(wrappedId, button.getLabel(), emoji);
            case SECONDARY -> Buttons.gray(wrappedId, button.getLabel(), emoji);
            case DANGER -> Buttons.red(wrappedId, button.getLabel(), emoji);
            default -> Buttons.green(wrappedId, button.getLabel(), emoji);
        };
    }

    public static void startRevolution(Game game, Player netrunner) {
        List<Player> targets = getRevolutionTargets(game, netrunner);
        if (targets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    netrunner.getCorrectChannel(),
                    netrunner.toString()
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
                netrunner.toString() + " used **Digital Uprising**. "
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
                target.toString() + " destroyed 1 " + getStructureName(target, structure)
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
        if (rollingPlayer == activePlayer || activePlayer.hasAllianceMember(rollingPlayer.getFaction())) {
            return FoWHelper.otherPlayersHaveShipsInSystem(activePlayer, tile, game);
        }
        return true;
    }

    private static BorrowedPds getBestBorrowedPds(Game game, Player netrunner, Player owner, Tile targetTile) {
        return UnitQueryService.getTilesContainingPlayersUnits(game, owner, UnitType.Pds).stream()
                .filter(pdsTile -> isCommanderPdsTileUsable(game, netrunner, targetTile, pdsTile))
                .flatMap(pdsTile -> pdsTile.getUnitHolderValues().stream()
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
        return target != null
                && netrunner.getDebtTokenCount(target.getColor(), NetrunnersAbilitiesHandler.SYSTEM_BREACH_POOL) > 0
                && !getRevolutionStructures(game, target).isEmpty();
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
        if (game == null || target == null) {
            return List.of();
        }
        return UnitQueryService.getTilesContainingPlayersUnits(game, target, UnitType.Pds, UnitType.Spacedock).stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream()
                        .filter(planet -> !planet.isHomePlanet(game))
                        .flatMap(planet -> getRevolutionStructures(target, tile, planet).stream()))
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
        UnitHolder unitHolder = tile.getUnitHolder(unitHolderName);
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

    private record BorrowedPds(Tile tile, UnitModel model) {}

    private record RevolutionStructure(Tile tile, UnitHolder unitHolder, UnitKey unitKey) {}
}

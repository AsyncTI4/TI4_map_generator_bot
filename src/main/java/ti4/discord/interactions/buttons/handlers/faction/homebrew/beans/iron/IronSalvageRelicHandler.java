package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.iron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.unit.monuments.TwilightsFallMonumentsButtonHandler;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.info.RelicInfoService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.CheckUnitContainmentService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class IronSalvageRelicHandler {
    private static final String SALVAGE_COLLECTORS = "salvage_collectors";
    private static final String COLLECT = "ironCollectSalvage_";
    private static final String DISCARD = "ironDiscardSalvage_";
    private static final String USE_FLAGSHIP = "ironUseSalvageFlagship_";
    private static final String USE_DREAD_MOVE = "ironUseSalvageDreadMove";
    private static final String USE_DREAD_COMBAT = "ironUseSalvageDreadCombat_";
    private static final String USE_CARRIER_TRANSPORT = "ironUseSalvageCarrierTransport";
    private static final String USE_CRUISER = "ironUseSalvageCruiser_";
    private static final String SELECT_CRUISER = "ironSelectSalvageCruiser_";
    private static final String USE_WAR_SUN = "ironUseSalvageWarSun_";
    private static final String USE_DESTROYER = "ironUseSalvageDestroyer_";
    private static final String COLLECTED = "ironSalvageCollected_";
    private static final String COLLECTIBLE = "ironSalvageCollectible_";
    private static final String DREAD_MOVE = "ironSalvageDreadMove_";
    private static final String CARRIER_TRANSPORT = "ironSalvageCarrierTransport_";
    private static final String DESTROYER_AFB_REROLL = "ironSalvageDestroyerAfbReroll_";
    private static final Map<UnitType, String> SALVAGE_BY_UNIT = Map.of(
            UnitType.Warsun, "salvage_war_sun",
            UnitType.Flagship, "salvage_flagship",
            UnitType.Dreadnought, "salvage_dreadnought",
            UnitType.Carrier, "salvage_carrier",
            UnitType.Cruiser, "salvage_cruiser",
            UnitType.Destroyer, "salvage_destroyer");

    public static Set<String> getSalvageRelics() {
        return Set.copyOf(SALVAGE_BY_UNIT.values());
    }

    public static boolean isSalvageRelic(String relic) {
        return getSalvageRelics().contains(relic);
    }

    public static boolean isAvailable(Game game, String relic) {
        return isSalvageRelic(relic) && game.getPlayers().values().stream().noneMatch(player -> player.hasRelic(relic));
    }

    public static void onSalvageGained(Player player) {
        CommanderUnlockCheckService.checkPlayer(player, "iron");
    }

    public static String getUnitRulesText(Player player, UnitType unitType) {
        String relic = SALVAGE_BY_UNIT.get(unitType);
        if (relic == null || !player.hasRelic(relic)) return "";
        return Mapper.getRelic(relic).getText();
    }

    public static void offerSalvageCollectors(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits, boolean combat) {
        if (!combat) return;
        for (Player player : game.getRealPlayersNNeutral()) {
            if (!player.hasAbility(SALVAGE_COLLECTORS)
                    || hasCollected(game, player)
                    || !isParticipatingInCombat(game, player)) continue;
            List<String> relics = destroyedUnits.stream()
                    .map(RemovedUnit::unitKey)
                    .map(key -> SALVAGE_BY_UNIT.get(key.unitType()))
                    .filter(relic -> relic != null && isAvailable(game, relic))
                    .distinct()
                    .toList();
            if (relics.isEmpty()) continue;
            setCollectibleSalvage(game, player, relics);
            List<Button> buttons = relics.stream()
                    .map(relic -> Buttons.gray(
                            player.factionButtonChecker() + COLLECT + relic,
                            Mapper.getRelic(relic).getName(),
                            FactionEmojis.iron))
                    .toList();
            MessageHelper.sendMessageToChannelWithButtons(
                    game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                    player.getRepresentation() + ", choose a Salvage card to collect with **Salvage Collectors**.",
                    buttons);
        }
    }

    @ButtonHandler(COLLECT)
    public static void collectSalvage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring(COLLECT.length());
        if (!player.hasAbility(SALVAGE_COLLECTORS)
                || hasCollected(game, player)
                || !isCollectibleSalvage(game, player, relic)
                || !isAvailable(game, relic)) return;
        player.addRelic(relic);
        onSalvageGained(player);
        game.setStoredValue(COLLECTED + player.getFaction(), "true");
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + " collected "
                        + Mapper.getRelic(relic).getNameRepresentation() + " with **Salvage Collectors**.");
        RelicInfoService.sendRelicInfo(player);
        ButtonHelper.deleteMessage(event);
    }

    public static void offerTacticalActionSalvage(Game game, Player player) {
        if (game.getCurrentActiveSystem().isBlank()) return;
        List<Button> buttons = new java.util.ArrayList<>();
        if (player.hasRelic("salvage_dreadnought")) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + USE_DREAD_MOVE, "Use Dyadic Jump Drive", FactionEmojis.iron));
        }
        if (player.hasRelic("salvage_carrier")) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + USE_CARRIER_TRANSPORT,
                    "Use Quantum Translocation",
                    FactionEmojis.iron));
        }
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you may use a Salvage card after activating this system.",
                    buttons);
        }
    }

    public static void offerCruiserSalvageAfterMovement(
            GenericInteractionCreateEvent event, Game game, Tile destination) {
        for (Player player : game.getRealPlayers()) {
            if (!canUseCruiserSalvage(game, player, destination)) continue;
            MessageHelper.sendMessageToChannelWithButtons(
                    game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", you may use _Slip-Link_ to move up to 2 Cruisers from adjacent systems into "
                            + destination.getRepresentationForButtons(game, player) + ".",
                    List.of(
                            Buttons.blue(
                                    player.factionButtonChecker() + USE_CRUISER + destination.getPosition(),
                                    "Use Slip-Link",
                                    FactionEmojis.iron),
                            Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline")));
        }
    }

    public static void addFlagshipCombatButton(List<Button> buttons, Game game, Player player, Tile tile) {
        if (!player.hasRelic("salvage_flagship")) return;
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + USE_FLAGSHIP + tile.getPosition(),
                "Use Slipspace Transfer",
                FactionEmojis.iron));
    }

    public static void addDreadnoughtCombatButton(List<Button> buttons, Player player, Tile tile) {
        if (!player.hasRelic("salvage_dreadnought")
                || tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, player) < 1) return;
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + USE_DREAD_COMBAT + tile.getPosition(),
                "Use Dreadnought",
                FactionEmojis.iron));
    }

    public static void offerDreadnoughtCombatButton(Game game, Player player, Tile tile) {
        if (!game.isFowMode()
                || !player.hasRelic("salvage_dreadnought")
                || tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, player) < 1) return;
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may use _Dreadnought_.",
                Buttons.blue(
                        player.factionButtonChecker() + USE_DREAD_COMBAT + tile.getPosition(),
                        "Use Dreadnought",
                        FactionEmojis.iron));
    }

    public static void offerFlagshipCombatButton(Game game, Player player, Tile tile) {
        if (!player.hasRelic("salvage_flagship")) return;
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may use _Slipspace Transfer_ this combat round.",
                Buttons.blue(
                        player.factionButtonChecker() + USE_FLAGSHIP + tile.getPosition(),
                        "Use Slipspace Transfer",
                        FactionEmojis.iron));
    }

    public static void offerWarSunBombardment(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planet, int hits) {
        if (!player.hasRelic("salvage_war_sun")
                || tile.getSpaceUnitHolder().getUnitCount(UnitType.Warsun, player) < 1
                || planet.isBlank()
                || hits < 1) return;
        MessageHelper.sendMessageToChannelWithButton(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + ", you may use _Hellfire_ to double these " + hits
                        + " BOMBARDMENT hit" + (hits == 1 ? "." : "s."),
                Buttons.blue(
                        player.factionButtonChecker() + USE_WAR_SUN + tile.getPosition() + "|" + planet + "|" + hits,
                        "Use Hellfire",
                        FactionEmojis.iron));
    }

    public static void offerDestroyerAfbReroll(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            List<Integer> missThresholds) {
        if (!player.hasRelic("salvage_destroyer")
                || opponent == null
                || !hasDestroyerAfb(game, player, tile)
                || missThresholds.isEmpty()) return;
        game.setStoredValue(
                DESTROYER_AFB_REROLL + player.getFaction(),
                tile.getPosition() + "|" + opponent.getFaction() + "|"
                        + missThresholds.stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining(",")));
        int misses = missThresholds.size();
        MessageHelper.sendMessageToChannelWithButton(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + ", you may use _Heat Seekers_ to reroll these " + misses
                        + " ANTI-FIGHTER BARRAGE miss" + (misses == 1 ? "." : "es."),
                Buttons.blue(
                        player.factionButtonChecker() + USE_DESTROYER + misses,
                        "Use Heat Seekers",
                        FactionEmojis.iron));
    }

    private static boolean hasDestroyerAfb(Game game, Player player, Tile combatTile) {
        return combatTile.getSpaceUnitHolder().getUnitCount(UnitType.Destroyer, player) > 0
                || FoWHelper.getAdjacentTiles(game, combatTile.getPosition(), player, false, false).stream()
                        .map(game::getTileByPosition)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(tile -> tile.getSpaceUnitHolder().getUnitCount(UnitType.Destroyer, player) > 0);
    }

    @ButtonHandler(DISCARD)
    public static void discardSalvage(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring(DISCARD.length());
        if (!isSalvageRelic(relic) || !player.hasRelic(relic)) return;
        discard(game, player, relic, event.getMessageChannel());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_CRUISER)
    public static void useCruiser(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile destination = game.getTileByPosition(buttonID.substring(USE_CRUISER.length()));
        if (destination == null || !canUseCruiserSalvage(game, player, destination)) return;
        discard(game, player, "salvage_cruiser", event.getMessageChannel());
        editCruiserMovePrompt(event, game, player, destination);
    }

    @ButtonHandler(SELECT_CRUISER)
    public static void selectCruiser(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] positions = buttonID.substring(SELECT_CRUISER.length()).split("\\|", 2);
        if (positions.length != 2) return;
        Tile destination = game.getTileByPosition(positions[0]);
        String sourcePosition = positions[1];
        Tile source = game.getTileByPosition(sourcePosition);
        if (destination == null || source == null) return;
        if (!FoWHelper.getAdjacentTilesAndNotThisTile(game, destination.getPosition(), player, false)
                .contains(sourcePosition)) return;
        if (source.getSpaceUnitHolder().getUnitCount(UnitType.Cruiser, player) < 1) return;
        List<RemovedUnit> removed = RemoveUnitService.removeUnit(
                event, source, game, player, source.getSpaceUnitHolder(), UnitType.Cruiser, 1);
        if (removed.isEmpty()) return;
        AddUnitService.addUnits(event, destination, game, player.getColor(), "1 ca", removed);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 Cruiser from "
                        + source.getRepresentationForButtons(game, player) + " into "
                        + destination.getRepresentationForButtons(game, player)
                        + " with _Slip-Link_.");
        editCruiserMovePrompt(event, game, player, destination);
    }

    @ButtonHandler(USE_FLAGSHIP)
    public static void useFlagship(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(USE_FLAGSHIP.length());
        if (!player.hasRelic("salvage_flagship")) return;
        Tile destination = game.getTileByPosition(position);
        if (destination == null) return;
        Tile source =
                CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Flagship).stream()
                        .filter(tile -> !tile.getPosition().equals(destination.getPosition()))
                        .findFirst()
                        .orElse(null);
        if (source == null) return;
        List<RemovedUnit> removed = RemoveUnitService.removeUnit(
                event, source, game, player, source.getSpaceUnitHolder(), UnitType.Flagship, 1);
        if (removed.isEmpty()) return;
        AddUnitService.addUnits(event, destination, game, player.getColor(), "1 fs", removed);
        discard(game, player, "salvage_flagship", event.getMessageChannel());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " moved their flagship from "
                        + source.getRepresentation()
                        + " into "
                        + destination.getRepresentation()
                        + " using _Slipspace Transfer_.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_WAR_SUN)
    public static void useWarSun(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_WAR_SUN.length()).split("\\|", 3);
        if (payload.length != 3) return;
        Tile tile = game.getTileByPosition(payload[0]);
        int hits;
        try {
            hits = Integer.parseInt(payload[2]);
        } catch (NumberFormatException e) {
            return;
        }
        if (!player.hasRelic("salvage_war_sun") || tile == null || payload[1].isBlank() || hits < 1) return;
        discard(game, player, "salvage_war_sun", event.getMessageChannel());
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Hellfire_ to double " + hits + " BOMBARDMENT hit"
                        + (hits == 1
                                ? " against the selected planet. Assign 1 additional hit."
                                : "s against the selected planet. Assign " + hits + " additional hits."));
        sendWarSunBombardmentHitAssignment(event, game, player, tile, payload[1], hits);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_DESTROYER)
    public static void useDestroyer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DestroyerAfbRerollContext rerollContext = getDestroyerAfbRerollContext(game, player);
        if (!player.hasRelic("salvage_destroyer")
                || rerollContext.missThresholds().isEmpty()) return;
        List<DiceHelper.Die> rerolls = rerollContext.missThresholds().stream()
                .flatMap(threshold -> DiceHelper.rollDice(threshold, 1).stream())
                .toList();
        int hits = DiceHelper.countSuccesses(rerolls);
        discard(game, player, "salvage_destroyer", event.getMessageChannel());
        game.removeStoredValue(DESTROYER_AFB_REROLL + player.getFaction());
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                getDestroyerAfbRerollSummary(game, player, rerollContext.tilePosition(), rerolls, hits));
        if (hits > 0) {
            sendDestroyerAfbHitSummary(event, game, player, rerollContext, hits);
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_DREAD_MOVE)
    public static void useDreadnoughtMove(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic("salvage_dreadnought")
                || game.getCurrentActiveSystem().isBlank()) return;
        game.setStoredValue(DREAD_MOVE + player.getFaction(), game.getCurrentActiveSystem());
        discard(game, player, "salvage_dreadnought", event.getMessageChannel());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " used _Dyadic Jump Drive_ to add +2 to the MOVE value of each of their Dreadnoughts during this tactical action.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_DREAD_COMBAT)
    public static void useDreadnoughtCombat(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_DREAD_COMBAT.length()));
        if (tile == null
                || !player.hasRelic("salvage_dreadnought")
                || tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, player) < 1) return;
        UnitModel dreadnought = player.getUnitByType(UnitType.Dreadnought);
        if (dreadnought == null) return;
        int threshold = dreadnought.getCombatDieHitsOnForAbility(CombatRollType.bombardment, player);
        List<DiceHelper.Die> rolls = DiceHelper.rollDice(threshold, 1);
        int hits = DiceHelper.countSuccesses(rolls);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                CombatMessageHelper.displayCombatSummary(
                                player, tile, tile.getSpaceUnitHolder(), CombatRollType.combatround)
                        + "_Dreadnought_:\n"
                        + CombatMessageHelper.displayUnitRoll(dreadnought, threshold, 0, 1, 1, 0, rolls, hits)
                        + CombatMessageHelper.displayHitResults(hits));
        if (hits > 0) {
            Player opponent = getCombatOpponent(game, player);
            if (opponent != null) {
                CombatRollService.sendSpaceAssignHitsButtons(
                        event,
                        game,
                        opponent,
                        tile,
                        hits,
                        game.isFowMode() ? opponent.getCorrectChannel() : event.getMessageChannel());
            }
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(USE_CARRIER_TRANSPORT)
    public static void useCarrierTransport(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasRelic("salvage_carrier") || game.getCurrentActiveSystem().isBlank()) return;
        game.setStoredValue(CARRIER_TRANSPORT + player.getFaction(), game.getCurrentActiveSystem());
        discard(game, player, "salvage_carrier", event.getMessageChannel());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " used _Quantum Translocation_ to let their Carriers transport ground forces out of systems that contain their command tokens during this tactical action."
                        + "\n-# You may need to press \"Reset all unit movement\" for the system to appear in the move menu.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void clearCombatState(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(COLLECTED + player.getFaction());
            game.removeStoredValue(COLLECTIBLE + player.getFaction());
            game.removeStoredValue(DESTROYER_AFB_REROLL + player.getFaction());
        }
    }

    public static void clearTacticalActionState(Game game, Player player) {
        game.removeStoredValue(DREAD_MOVE + player.getFaction());
        game.removeStoredValue(CARRIER_TRANSPORT + player.getFaction());
    }

    public static boolean hasDreadnoughtMoveBonus(Game game, Player player) {
        return !game.getStoredValue(DREAD_MOVE + player.getFaction()).isBlank();
    }

    public static boolean carrierIgnoresAnomalies(Game game, Player player, UnitType unitType) {
        return player.isActivePlayer() && unitType == UnitType.Carrier && player.hasRelic("salvage_carrier");
    }

    public static boolean carrierCanTransportOutOfLockedSystem(Game game, Player player) {
        return !game.getStoredValue(CARRIER_TRANSPORT + player.getFaction()).isBlank();
    }

    private static void discard(
            Game game,
            Player player,
            String relic,
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        player.removeRelic(relic);
        player.removeExhaustedRelic(relic);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : channel,
                player.getRepresentationNoPing() + " discarded "
                        + Mapper.getRelic(relic).getNameRepresentation() + ".");
        RelicInfoService.sendRelicInfo(player);
    }

    private static void sendWarSunBombardmentHitAssignment(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String planet, int hits) {
        var planetHolder = game.getUnitHolderFromPlanet(planet);
        if (planetHolder == null) return;
        for (Player target : game.getRealPlayersNNeutral()) {
            if (target == player || !FoWHelper.playerHasUnitsOnPlanet(target, planetHolder)) continue;
            List<Button> buttons = new ArrayList<>();
            if (target.isDummy() || target.isNpc()) {
                buttons.add(Buttons.green(
                        target.dummyPlayerSpoof() + AutoAssignGroundHitsButtonIds.format(planet, hits),
                        "Auto-assign Hit" + (hits == 1 ? "" : "s") + " For Dummy"));
            } else {
                buttons.add(Buttons.green(
                        target.factionButtonChecker() + AutoAssignGroundHitsButtonIds.format(planet, hits),
                        "Auto-assign Hit" + (hits == 1 ? "" : "s")));
                buttons.add(Buttons.red(
                        "getDamageButtons_" + tile.getPosition() + "_bombardment",
                        "Assign Hit" + (hits == 1 ? "" : "s")));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    game.isFowMode() ? target.getCorrectChannel() : event.getMessageChannel(),
                    target.getRepresentationNoPing() + ", assign the additional BOMBARDMENT hit"
                            + (hits == 1 ? "." : "s."),
                    buttons);
        }
    }

    private static boolean canUseCruiserSalvage(Game game, Player player, Tile destination) {
        return player.hasRelic("salvage_cruiser")
                && destination.getSpaceUnitHolder().getUnitCount(UnitType.Cruiser, player) > 0
                && FoWHelper.getAdjacentTilesAndNotThisTile(game, destination.getPosition(), player, false).stream()
                        .map(game::getTileByPosition)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(tile -> tile.getSpaceUnitHolder().getUnitCount(UnitType.Cruiser, player) > 0);
    }

    private static Player getCombatOpponent(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null) return null;
        return combat.factions().stream()
                .filter(faction -> !player.getFaction().equals(faction))
                .map(game::getPlayerFromColorOrFaction)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static void editCruiserMovePrompt(
            ButtonInteractionEvent event, Game game, Player player, Tile destination) {
        List<Button> buttons = new ArrayList<>();
        for (String position :
                FoWHelper.getAdjacentTilesAndNotThisTile(game, destination.getPosition(), player, false)) {
            Tile source = game.getTileByPosition(position);
            if (source == null || source.getSpaceUnitHolder().getUnitCount(UnitType.Cruiser, player) < 1) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_CRUISER + destination.getPosition() + "|" + position,
                    "Move Cruiser from " + source.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose up to 2 Cruisers to move into "
                        + destination.getRepresentationForButtons(game, player) + ".",
                buttons);
    }

    private static boolean hasCollected(Game game, Player player) {
        return "true".equals(game.getStoredValue(COLLECTED + player.getFaction()));
    }

    private static void setCollectibleSalvage(Game game, Player player, List<String> relics) {
        Set<String> collectible = new java.util.HashSet<>(getCollectibleSalvage(game, player));
        collectible.addAll(relics);
        game.setStoredValue(COLLECTIBLE + player.getFaction(), String.join("|", collectible));
    }

    private static boolean isCollectibleSalvage(Game game, Player player, String relic) {
        return getCollectibleSalvage(game, player).contains(relic);
    }

    private static Set<String> getCollectibleSalvage(Game game, Player player) {
        String stored = game.getStoredValue(COLLECTIBLE + player.getFaction());
        return stored.isBlank() ? Set.of() : Set.of(stored.split("\\|"));
    }

    private static boolean isParticipatingInCombat(Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        return combat != null && combat.factions().contains(player.getFaction());
    }

    private static DestroyerAfbRerollContext getDestroyerAfbRerollContext(Game game, Player player) {
        String context = game.getStoredValue(DESTROYER_AFB_REROLL + player.getFaction());
        String[] parts = context.split("\\|", 3);
        if (parts.length == 1
                && !parts[0].isBlank()
                && !game.getCurrentActiveSystem().isBlank()) {
            String tilePosition = game.getCurrentActiveSystem();
            return new DestroyerAfbRerollContext(
                    tilePosition,
                    getAfbOpponentFaction(game, player, tilePosition),
                    parseDestroyerAfbMissThresholds(parts[0]));
        }
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return new DestroyerAfbRerollContext(
                    parts[0], getAfbOpponentFaction(game, player, parts[0]), parseDestroyerAfbMissThresholds(parts[1]));
        }
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return new DestroyerAfbRerollContext("", "", List.of());
        }
        return new DestroyerAfbRerollContext(parts[0], parts[1], parseDestroyerAfbMissThresholds(parts[2]));
    }

    private static List<Integer> parseDestroyerAfbMissThresholds(String thresholds) {
        try {
            return Arrays.stream(thresholds.split(",")).map(Integer::parseInt).toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    private static String getAfbOpponentFaction(Game game, Player player, String tilePosition) {
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) return "";
        return tile.getSpaceUnitHolder().getUnitColorsOnHolder().stream()
                .map(game::getPlayerByColorID)
                .flatMap(java.util.Optional::stream)
                .filter(other -> !other.is(player))
                .map(Player::getFaction)
                .findFirst()
                .orElse("");
    }

    private static void sendDestroyerAfbHitSummary(
            ButtonInteractionEvent event, Game game, Player player, DestroyerAfbRerollContext rerollContext, int hits) {
        Tile tile = game.getTileByPosition(rerollContext.tilePosition());
        Player opponent = game.getPlayerFromColorOrFaction(rerollContext.opponentFaction());
        if (tile == null || opponent == null) return;
        List<Button> buttons = new ArrayList<>();
        if (opponent.isNpc() || opponent.isDummy()) {
            buttons.add(Buttons.green(
                    opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + tile.getPosition() + "_" + hits,
                    "Auto-assign Hit" + (hits == 1 ? "" : "s For Dummy")));
        } else {
            buttons.add(Buttons.green(
                    opponent.factionButtonChecker() + "autoAssignAFBHits_" + tile.getPosition() + "_" + hits,
                    "Auto-assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.red(
                    opponent.factionButtonChecker() + "getDamageButtons_" + tile.getPosition() + "_afb",
                    "Manually Assign Hit" + (hits == 1 ? "" : "s")));
            buttons.add(Buttons.gray(
                    opponent.factionButtonChecker() + "cancelAFBHits_" + tile.getPosition() + "_" + hits,
                    "Cancel a Hit"));
            TwilightsFallMonumentsButtonHandler.addYellowTfMonumentCancelHitButton(
                    buttons, game, opponent, tile, "afb", hits);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? opponent.getCorrectChannel() : event.getMessageChannel(),
                opponent.getRepresentation() + ", you may automatically assign " + (hits == 1 ? "the hit" : "hits")
                        + " from AFB.",
                buttons);
    }

    private static String getDestroyerAfbRerollSummary(
            Game game, Player player, String tilePosition, List<DiceHelper.Die> rerolls, int hits) {
        Tile tile = game.getTileByPosition(tilePosition);
        UnitModel destroyer = player.getUnitByType(UnitType.Destroyer);
        if (tile == null) tile = game.getTileByPosition(game.getCurrentActiveSystem());
        if (destroyer == null) destroyer = Mapper.getUnit("destroyer");
        if (tile == null || destroyer == null) return DiceHelper.formatDiceOutput(rerolls);
        int threshold = rerolls.getFirst().getThreshold();
        return CombatMessageHelper.displayCombatSummary(player, tile, tile.getSpaceUnitHolder(), CombatRollType.AFB)
                + "Rerolling " + rerolls.size() + " miss" + (rerolls.size() == 1 ? "" : "es")
                + " due to _Heat Seekers_:\n"
                + CombatMessageHelper.displayUnitRoll(destroyer, threshold, 0, 1, rerolls.size(), 0, rerolls, hits)
                + CombatMessageHelper.displayHitResults(hits);
    }

    private record DestroyerAfbRerollContext(
            String tilePosition, String opponentFaction, List<Integer> missThresholds) {}
}

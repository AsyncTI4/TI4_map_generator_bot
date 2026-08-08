package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RiftUnitsHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.commodities.CommodityConversionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class ThronesUnitHandler {
    private static final String PLACE_AURELION = "placeAurelion_";
    private static final String CONVERT_COMMODITIES_WITH_AURELION = "convertCommoditiesWithAurelion";
    private static final String AURELION = "aurelion";
    private static final String AURELION_STATION = "aurelionstation";
    private static final String THRONES_AURELION = "thrones_aurelion";
    private static final String THRONES_MECH = "thrones_mech";
    private static final String USE_GHOLA = "useGhola_";
    private static final String DESTROY_GHOLA = "destroyGhola_";
    private static final String DECLINE_GHOLA = "declineGhola_";
    private static final String PENDING_GHOLA_ROLL = "pendingGholaRoll_";
    private static final String USE_GHOLA_RIFT = "useGholaRift_";
    private static final String DESTROY_GHOLA_RIFT = "destroyGholaRift_";
    private static final String DECLINE_GHOLA_RIFT = "declineGholaRift_";
    private static final String PENDING_GHOLA_RIFT = "pendingGholaRift_";

    public static Button getAurelionCommodityConversionButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + CONVERT_COMMODITIES_WITH_AURELION,
                "Convert Commodities With Aurelion",
                UnitEmojis.flagship);
    }

    @ButtonHandler(CONVERT_COMMODITIES_WITH_AURELION)
    public static void convertCommoditiesWithAurelion(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet(AURELION_STATION) || player.getExhaustedPlanets().contains(AURELION_STATION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int commodities = player.getCommodities();
        player.exhaustPlanet(AURELION_STATION);
        CommodityConversionService.convertAllComm(event, player, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted " + Helper.getPlanetRepresentation(AURELION_STATION, game)
                        + ", washing their commodit"
                        + (commodities == 1 ? "y" : "ies")
                        + ".");
    }

    public static void syncAurelionStation(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        if (isAurelionOnBoard(game, player)) {
            player.addPlanet(AURELION_STATION);
        } else {
            player.removePlanet(AURELION_STATION);
        }
    }

    public static void offerAurelionPlacement(Game game, Player player) {
        List<Button> buttons = getAurelionPlacementButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no system containing a planet they control to place _Aurelion_.");
            return;
        }

        String message = player.getRepresentation()
                + ", please choose a system containing a planet you control on which to place an Aurelion.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_AURELION;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    @ButtonHandler(PLACE_AURELION)
    public static void placeAurelion(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.ownsUnit(THRONES_AURELION) || isAurelionOnBoard(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getAurelionPlacementButtons(game, player);
        String message = player.getRepresentation()
                + ", please choose a system containing a planet you control on which to place an Aurelion.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_AURELION;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(PLACE_AURELION.length());
        Tile tile = game.getTileByPosition(position);
        boolean controlsPlanetInTile =
                player.getPlanets().stream().anyMatch(planet -> tile == game.getTileFromPlanet(planet));
        if (tile == null || !controlsPlanetInTile) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), AURELION);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed a " + UnitEmojis.flagship + " in " + tile.getRepresentation()
                        + ".");

        ButtonHelper.deleteMessage(event);
    }

    private static boolean isAurelionOnBoard(Game game, Player player) {
        UnitKey aurelion = Units.getUnitKey(UnitType.Aurelion, player.getColorID());
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .anyMatch(unitHolder -> unitHolder.getUnitCount(aurelion) > 0);
    }

    private static List<Button> getAurelionPlacementButtons(Game game, Player player) {
        List<Tile> eligibleTiles = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile != null && !eligibleTiles.contains(tile)) {
                eligibleTiles.add(tile);
            }
        }

        return eligibleTiles.stream()
                .map(tile -> Buttons.red(
                        player.factionButtonChecker() + PLACE_AURELION + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        UnitEmojis.flagship))
                .toList();
    }

    public static void offerGholaAfterRoll(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            UnitHolder combatHolder,
            CombatRollType rollType,
            CombatRollPayload payload) {
        if (event == null
                || game == null
                || player == null
                || opponent == null
                || tile == null
                || combatHolder == null
                || rollType == null
                || payload == null
                || !player.ownsUnit(THRONES_MECH)
                || !hasGholaOnBoard(game, player)) {
            return;
        }
        boolean producesHit = payload.unitRolls().stream()
                .flatMap(unitRoll ->
                        unitRoll.dice().stream().filter(die -> dieRemainsAfterRerolls(payload, unitRoll, die)))
                .anyMatch(die -> !die.success() && die.result() + 3 >= die.threshold());
        String context = tile.getPosition() + "|" + opponent.getFaction() + "|"
                + (rollType == CombatRollType.combatround && Constants.SPACE.equals(combatHolder.getName())) + "|"
                + producesHit;
        String pendingId = createPendingId(game, PENDING_GHOLA_ROLL, context);
        game.setStoredValue(PENDING_GHOLA_ROLL + pendingId, player.getFaction() + "|" + context);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may destroy a Ghola (Thrones mech) to increase 1 die from this roll by +3.",
                List.of(
                        Buttons.red(
                                player.factionButtonChecker() + USE_GHOLA + pendingId,
                                "Use Ghola",
                                FactionEmojis.thrones),
                        Buttons.gray(player.factionButtonChecker() + DECLINE_GHOLA + pendingId, "Decline")));
    }

    private static boolean dieRemainsAfterRerolls(
            CombatRollPayload payload, CombatRollPayload.UnitRoll unitRoll, CombatRollPayload.DieRoll die) {
        CombatRollPayload.RollSegmentType segment = unitRoll.segmentType();
        if (segment == CombatRollPayload.RollSegmentType.JOL_NAR_COMMANDER_REROLL_MISSES
                || segment == CombatRollPayload.RollSegmentType.JOL_NAR_COMMANDER_REROLL_HITS
                || segment == CombatRollPayload.RollSegmentType.IRON_COMMANDER_REROLL_MISSES
                || segment == CombatRollPayload.RollSegmentType.KALTRIM_COMMANDER_REROLL_ONES
                || segment == CombatRollPayload.RollSegmentType.MUNITIONS_RESERVES_REROLL) {
            return true;
        }

        List<CombatRollPayload.RollSegmentType> rerolls = payload.unitRolls().stream()
                .filter(other -> java.util.Objects.equals(unitRoll.asyncId(), other.asyncId()))
                .map(CombatRollPayload.UnitRoll::segmentType)
                .filter(other -> other != CombatRollPayload.RollSegmentType.PRIMARY
                        && other != CombatRollPayload.RollSegmentType.SUPERCHARGE_SELECTED_UNIT
                        && other != CombatRollPayload.RollSegmentType.SUPERCHARGE_REST
                        && other != CombatRollPayload.RollSegmentType.GRAVLEASH_SELECTED_UNIT
                        && other != CombatRollPayload.RollSegmentType.GRAVLEASH_REST)
                .toList();
        boolean rerollsAllDice = rerolls.contains(CombatRollPayload.RollSegmentType.MUNITIONS_RESERVES_REROLL);
        boolean rerollsThisMiss = !die.success()
                && (rerolls.contains(CombatRollPayload.RollSegmentType.JOL_NAR_COMMANDER_REROLL_MISSES)
                        || rerolls.contains(CombatRollPayload.RollSegmentType.IRON_COMMANDER_REROLL_MISSES));
        boolean rerollsThisHit =
                die.success() && rerolls.contains(CombatRollPayload.RollSegmentType.JOL_NAR_COMMANDER_REROLL_HITS);
        boolean rerollsThisOne =
                die.result() == 1 && rerolls.contains(CombatRollPayload.RollSegmentType.KALTRIM_COMMANDER_REROLL_ONES);
        return !rerollsAllDice && !rerollsThisMiss && !rerollsThisHit && !rerollsThisOne;
    }

    @ButtonHandler(USE_GHOLA)
    public static void offerGholaDestructionButtons(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }
        String pendingId = buttonID.substring(USE_GHOLA.length());
        String[] payload = game.getStoredValue(PENDING_GHOLA_ROLL + pendingId).split("\\|", 5);
        Tile combatTile = payload.length == 5 ? game.getTileByPosition(payload[1]) : null;
        if (game == null
                || player == null
                || !player.ownsUnit(THRONES_MECH)
                || combatTile == null
                || !hasGholaOnBoard(game, player)
                || payload.length != 5
                || !player.getFaction().equals(payload[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile mechTile : game.getTileMap().values()) {
            for (UnitHolder mechHolder : mechTile.getUnitHolders().values()) {
                for (UnitKey unitKey : mechHolder.getUnitKeysForPlayer(player)) {
                    if (!isGhola(player, unitKey)) {
                        continue;
                    }
                    for (UnitState state : mechHolder.getNonZeroUnitStates(unitKey)) {
                        int holderIndex =
                                new ArrayList<>(mechTile.getUnitHolders().values()).indexOf(mechHolder);
                        int count = mechHolder.getUnitCountForState(unitKey, state);
                        String stateText =
                                switch (state) {
                                    case dmg -> "Damaged ";
                                    case glv -> "Galvanized ";
                                    case dmg_glv -> "Damaged Galvanized ";
                                    default -> "";
                                };
                        buttons.add(Buttons.red(
                                player.factionButtonChecker() + DESTROY_GHOLA + pendingId + "|"
                                        + mechTile.getPosition() + "|"
                                        + holderIndex + "|" + unitKey.asyncID() + "|" + state,
                                "Destroy 1 " + stateText + "Ghola On "
                                        + Helper.getUnitHolderRepresentation(
                                                mechTile, mechHolder.getName(), game, player)
                                        + " (" + count + ")",
                                unitKey.unitEmoji()));
                    }
                }
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", please choose a Ghola (Thrones mech) to destroy. The highest eligible die from the previous roll gains +3.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(DESTROY_GHOLA)
    public static void destroyGholaForRollBonus(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }
        String[] payload = buttonID.substring(DESTROY_GHOLA.length()).split("\\|", 5);
        String pendingId = payload.length == 5 ? payload[0] : "";
        String[] pending = game.getStoredValue(PENDING_GHOLA_ROLL + pendingId).split("\\|", 5);
        Tile combatTile = pending.length == 5 ? game.getTileByPosition(pending[1]) : null;
        Player opponent = pending.length == 5 ? game.getPlayerFromColorOrFaction(pending[2]) : null;
        Tile mechTile = payload.length == 5 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder mechHolder = payload.length == 5 ? getHolderByIndex(mechTile, payload[2]) : null;
        UnitKey mechKey = mechHolder == null
                ? null
                : mechHolder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> isGhola(player, key))
                        .filter(key -> key.asyncID().equals(payload[3]))
                        .findFirst()
                        .orElse(null);
        UnitState state = payload.length == 5 ? Units.findUnitState(payload[4]) : null;
        if (game == null
                || player == null
                || pending.length != 5
                || !player.getFaction().equals(pending[0])
                || !player.ownsUnit(THRONES_MECH)
                || combatTile == null
                || opponent == null
                || mechHolder == null
                || mechKey == null
                || state == null
                || mechHolder.getUnitCountForState(mechKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnit(
                event, mechTile, game, new ParsedUnit(mechKey, 1, mechHolder.getName()), true, state);
        game.removeStoredValue(PENDING_GHOLA_ROLL + pendingId);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " destroyed a Ghola (Thrones mech), increasing 1 die from their previous roll by +3."
                        + (Boolean.parseBoolean(pending[4]) ? " This produced 1 additional hit." : ""));
        if (Boolean.parseBoolean(pending[4]) && Boolean.parseBoolean(pending[3]) && opponent != player) {
            CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, combatTile, 1);
        } else if (Boolean.parseBoolean(pending[4])) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Resolve the 1 additional hit from _Ghola_ using the appropriate hit-assignment buttons.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_GHOLA)
    public static void declineGholaForRollBonus(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String pendingId = buttonID.substring(DECLINE_GHOLA.length());
        String[] pending = game.getStoredValue(PENDING_GHOLA_ROLL + pendingId).split("\\|", 5);
        if (pending.length == 5 && player.getFaction().equals(pending[0])) {
            game.removeStoredValue(PENDING_GHOLA_ROLL + pendingId);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static boolean offerGholaAfterRiftRoll(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitKey unitKey,
            boolean damaged,
            Player cabal,
            Die die) {
        if (event == null
                || game == null
                || player == null
                || tile == null
                || unitKey == null
                || die == null
                || die.isSuccess()
                || die.getResult() + 3 < die.getThreshold()
                || !player.ownsUnit(THRONES_MECH)
                || !hasGholaOnBoard(game, player)) {
            return false;
        }
        String context = tile.getPosition() + "|" + unitKey.asyncID() + "|" + damaged + "|"
                + (cabal == null ? "-" : cabal.getFaction());
        String pendingId = createPendingId(game, PENDING_GHOLA_RIFT, context);
        game.setStoredValue(PENDING_GHOLA_RIFT + pendingId, player.getFaction() + "|" + context);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may destroy a Ghola (Thrones mech) to add +3 to this gravity-rift roll and save the unit.",
                List.of(
                        Buttons.red(
                                player.factionButtonChecker() + USE_GHOLA_RIFT + pendingId,
                                "Use Ghola",
                                FactionEmojis.thrones),
                        Buttons.gray(player.factionButtonChecker() + DECLINE_GHOLA_RIFT + pendingId, "Decline")));
        return true;
    }

    @ButtonHandler(USE_GHOLA_RIFT)
    public static void offerGholaRiftDestructionButtons(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String pendingId = buttonID.substring(USE_GHOLA_RIFT.length());
        String[] payload = game.getStoredValue(PENDING_GHOLA_RIFT + pendingId).split("\\|", 5);
        if (payload.length != 5
                || !player.getFaction().equals(payload[0])
                || !player.ownsUnit(THRONES_MECH)
                || !hasGholaOnBoard(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile mechTile : game.getTileMap().values()) {
            for (UnitHolder mechHolder : mechTile.getUnitHolders().values()) {
                for (UnitKey mechKey : mechHolder.getUnitKeysForPlayer(player)) {
                    if (!isGhola(player, mechKey)) continue;
                    for (UnitState state : mechHolder.getNonZeroUnitStates(mechKey)) {
                        int holderIndex =
                                new ArrayList<>(mechTile.getUnitHolders().values()).indexOf(mechHolder);
                        buttons.add(Buttons.red(
                                player.factionButtonChecker() + DESTROY_GHOLA_RIFT + pendingId + "|"
                                        + mechTile.getPosition() + "|" + holderIndex + "|"
                                        + mechKey.asyncID() + "|" + state,
                                "Destroy Ghola On "
                                        + Helper.getUnitHolderRepresentation(
                                                mechTile, mechHolder.getName(), game, player),
                                mechKey.unitEmoji()));
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose a Ghola (Thrones mech) to destroy.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DESTROY_GHOLA_RIFT)
    public static void destroyGholaForRiftBonus(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] payload = buttonID.substring(DESTROY_GHOLA_RIFT.length()).split("\\|", 5);
        String pendingId = payload.length == 5 ? payload[0] : "";
        String[] pending = game.getStoredValue(PENDING_GHOLA_RIFT + pendingId).split("\\|", 5);
        Tile mechTile = payload.length == 5 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder mechHolder = payload.length == 5 ? getHolderByIndex(mechTile, payload[2]) : null;
        UnitKey mechKey = mechHolder == null
                ? null
                : mechHolder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> isGhola(player, key) && key.asyncID().equals(payload[3]))
                        .findFirst()
                        .orElse(null);
        UnitState state = payload.length == 5 ? Units.findUnitState(payload[4]) : null;
        if (pending.length != 5
                || !player.getFaction().equals(pending[0])
                || mechHolder == null
                || mechKey == null
                || state == null
                || mechHolder.getUnitCountForState(mechKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        DestroyUnitService.destroyUnit(
                event, mechTile, game, new ParsedUnit(mechKey, 1, mechHolder.getName()), true, state);
        game.removeStoredValue(PENDING_GHOLA_RIFT + pendingId);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " destroyed a Ghola (Thrones mech) and added +3 to the gravity-rift roll; the unit survived.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_GHOLA_RIFT)
    public static void declineGholaForRiftBonus(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String pendingId = buttonID.substring(DECLINE_GHOLA_RIFT.length());
        String[] payload = game.getStoredValue(PENDING_GHOLA_RIFT + pendingId).split("\\|", 5);
        Tile tile = payload.length == 5 ? game.getTileByPosition(payload[1]) : null;
        UnitKey unitKey = payload.length == 5 ? ti4.image.Mapper.getUnitKey(payload[2], player.getColorID()) : null;
        Player cabal =
                payload.length == 5 && !"-".equals(payload[4]) ? game.getPlayerFromColorOrFaction(payload[4]) : null;
        if (payload.length != 5 || !player.getFaction().equals(payload[0]) || tile == null || unitKey == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String result = RiftUnitsHelper.resolveFailedRiftUnit(
                event, game, player, tile, unitKey, Boolean.parseBoolean(payload[3]), cabal);
        game.removeStoredValue(PENDING_GHOLA_RIFT + pendingId);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), unitKey.unitEmoji() + result);
        ButtonHelper.deleteMessage(event);
    }

    public static void clearPendingGholaWindows(Game game) {
        if (game == null) return;
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(PENDING_GHOLA_ROLL))
                .toList()
                .forEach(game::removeStoredValue);
        for (String key : game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(PENDING_GHOLA_RIFT))
                .toList()) {
            String[] payload = game.getStoredValue(key).split("\\|", 5);
            Player player = payload.length == 5 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
            Tile tile = payload.length == 5 ? game.getTileByPosition(payload[1]) : null;
            UnitKey unitKey = player == null || payload.length != 5
                    ? null
                    : ti4.image.Mapper.getUnitKey(payload[2], player.getColorID());
            Player cabal = payload.length == 5 && !"-".equals(payload[4])
                    ? game.getPlayerFromColorOrFaction(payload[4])
                    : null;
            if (player != null
                    && tile != null
                    && unitKey != null
                    && tile.getSpaceUnitHolder().getUnitCount(unitKey) > 0) {
                String result = RiftUnitsHelper.resolveFailedRiftUnit(
                        null, game, player, tile, unitKey, Boolean.parseBoolean(payload[3]), cabal);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        unitKey.unitEmoji() + result + " The unresolved Ghola decision was closed.");
            }
            game.removeStoredValue(key);
        }
    }

    private static boolean hasGholaOnBoard(Game game, Player player) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .flatMap(holder -> holder.getUnitKeysForPlayer(player).stream()
                        .filter(unitKey -> holder.getUnitCount(unitKey) > 0))
                .anyMatch(unitKey -> isGhola(player, unitKey));
    }

    private static UnitHolder getHolderByIndex(Tile tile, String indexText) {
        if (tile == null || indexText == null || !indexText.matches("\\d+")) {
            return null;
        }
        int index = Integer.parseInt(indexText);
        List<UnitHolder> holders = new ArrayList<>(tile.getUnitHolders().values());
        return index < holders.size() ? holders.get(index) : null;
    }

    private static String createPendingId(Game game, String prefix, String context) {
        String pendingId;
        do {
            pendingId = Integer.toUnsignedString((context + System.nanoTime()).hashCode(), 36);
        } while (!game.getStoredValue(prefix + pendingId).isEmpty());
        return pendingId;
    }

    private static boolean isGhola(Player player, UnitKey unitKey) {
        UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
        return unitKey.unitType() == UnitType.Mech && unitModel != null && THRONES_MECH.equals(unitModel.getId());
    }
}

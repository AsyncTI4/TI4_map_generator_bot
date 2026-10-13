package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thirtyk.ThirtyKDistance;
import ti4.helpers.thirtyk.ThirtyKExploreMath;
import ti4.helpers.thirtyk.ThirtyKRelics;
import ti4.helpers.thirtyk.ThirtyKTransactions;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.fow.BlindSelectionService;
import ti4.service.objectives.DrawSecretService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ThirtyKFrontierExploreHandler {

    private static final String FAULTY_SIGNAL_MOVE = "thirtyKFaultySignal_";
    private static final String REPLICATION_DISCARD = "thirtyKReplicationDiscard_";
    private static final String REPLICATION_COUNT_KEY = "thirtyKReplicationDiscards";
    private static final String TRAP_PLACE = "thirtyKTrapPlace_";
    private static final String PONDER_DISCARD = "thirtyKPonderDiscard_";
    private static final String WERE_LOST_MOVE = "thkWL_";
    private static final String WERE_LOST_PENDING_KEY = "thirtyKWereLostPending";
    private static final String FOREGONE_GIVE = "thkFgGive_";
    private static final String SYMPATHIES_OBJECTIVE = "Systematic Sympathies";

    public static void resolveDistractionEfforts(GenericInteractionCreateEvent event, Game game, Player player) {
        ButtonHelperStats.gainTGs(event, game, player, 3, false);
        Button extraAction = Buttons.green(player.factionButtonChecker() + "componentAction", "Extra Component Action");
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", _Distraction Efforts_ lets you make an extra component action.",
                List.of(extraAction, ThirtyKExploreSupport.decline(player)));
    }

    public static void resolveWastefulPirates(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        List<Die> dice = DiceHelper.rollDice(6, 5);
        int hits = DiceHelper.countSuccesses(dice);
        int tradeGoods = tile == null
                ? 0
                : ThirtyKExploreMath.wastefulPiratesTradeGoods(
                        hits, ThirtyKExploreSupport.hitCapacityOfShips(player, tile));
        StringBuilder message = new StringBuilder(player.getRepresentationNoPing())
                .append(" rolled for _Wasteful Pirates_.\n")
                .append(DiceHelper.formatDiceOutput(dice));
        if (hits == 0 || tile == null) {
            MessageHelper.sendMessageToEventChannel(event, message.toString());
            return;
        }
        message.append("\nAssign ")
                .append(hits)
                .append(" hit")
                .append(hits == 1 ? "" : "s")
                .append(" to your ships in this system; you gain 1 trade good for each hit you can assign.");
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                message.toString(),
                List.of(Buttons.red(
                        player.factionButtonChecker() + "getDamageButtons_" + tile.getPosition() + "_spacecombat",
                        "Assign Hit" + (hits == 1 ? "" : "s"))));
        ButtonHelperStats.gainTGs(event, game, player, tradeGoods, false);
    }

    public static void resolveFaultyEmergencySignal(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        Tile home = player.getHomeSystemTile();
        if (tile == null || home == null || home == tile) {
            MessageHelper.sendMessageToEventChannel(event, "_Faulty Emergency Signal_ has nothing to move.");
            return;
        }
        if (ThirtyKExploreSupport.otherPlayersHaveShips(game, player, home)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Another player's ships are in your home system, so no ships can be moved there.");
            return;
        }
        sendFaultySignalButtons(event, game, player, tile, home);
    }

    private static void sendFaultySignalButtons(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, Tile home) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey key : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
            if (!ThirtyKExploreSupport.isShip(player, key)) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FAULTY_SIGNAL_MOVE + tile.getPosition() + "_"
                            + key.unitType().getValue(),
                    "Move 1 " + key.unitType().humanReadableName() + " Home"));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "You have no ships left in this system to move.");
            return;
        }
        List<Button> choices = ThirtyKExploreSupport.capped(buttons);
        choices.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Done Moving Ships"));
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", choose ships to move to "
                        + home.getRepresentationForButtons(game, player) + " (_Faulty Emergency Signal_).",
                choices);
    }

    @ButtonHandler(FAULTY_SIGNAL_MOVE)
    public static void faultySignalMove(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(FAULTY_SIGNAL_MOVE.length()).split("_");
        Tile tile = game.getTileByPosition(parts[0]);
        Tile home = player.getHomeSystemTile();
        UnitType type = Units.findUnitType(parts[1]);
        if (tile == null || home == null || type == null) return;
        var removed =
                RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), type, 1, false);
        AddUnitService.addUnits(
                event,
                game,
                removed.stream()
                        .map(r -> r.onUnitHolder(home.getSpaceUnitHolder()))
                        .toList());
        if (removed.stream()
                        .mapToInt(RemoveUnitService.RemovedUnit::getTotalRemoved)
                        .sum()
                > 0) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " moved 1 " + type.humanReadableName()
                            + " to their home system.");
        }
        ThirtyKExploreSupport.deleteMessage(event);
        sendFaultySignalButtons(event, game, player, tile, home);
    }

    public static void resolveInsatiableFuelSource(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (tile == null) return;
        List<Player> affected = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            if (!tile.hasPlayerCC(other)) continue;
            tile.removeCC(Mapper.getCCID(other.getColor()));
            other.setTacticalCC(other.getTacticalCC() + 1);
            affected.add(other);
        }
        if (affected.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No command tokens were in this system.");
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event,
                "_Insatiable Fuel Source_ returned " + affected.size()
                        + " command token(s) from this system to their owners' tactic pools.");
        for (Player other : affected) {
            if (other == player || !game.isFowMode()) continue;
            MessageHelper.sendMessageToChannel(
                    other.getCorrectChannel(),
                    other.getRepresentationUnfogged() + ", your command token in "
                            + tile.getRepresentationForButtons(game, other)
                            + " returned to your tactic pool (_Insatiable Fuel Source_).");
        }
    }

    public static void resolveReplicationArrays(GenericInteractionCreateEvent event, Game game, Player player) {
        game.setStoredValue(REPLICATION_COUNT_KEY + player.getFaction(), "0");
        if (player.getActionCards().isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "You have no action cards to discard.");
            offerReplicationPick(game, player);
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event,
                "Discard 2 action cards (if able) in your cards info thread, then pick 1 from the discard pile.");
        sendReplicationDiscardButtons(player);
    }

    private static void sendReplicationDiscardButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> card : player.getActionCards().entrySet()) {
            var model = Mapper.getActionCard(card.getKey());
            if (model == null) continue;
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + REPLICATION_DISCARD + card.getValue(),
                    "Discard " + model.getName()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", discard an action card for _Replication Arrays_.",
                ThirtyKExploreSupport.capped(buttons));
    }

    @ButtonHandler(REPLICATION_DISCARD)
    public static void replicationDiscard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int number = Integer.parseInt(buttonID.substring(REPLICATION_DISCARD.length()));
        String cardID = player.getActionCards().entrySet().stream()
                .filter(entry -> entry.getValue() == number)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (cardID == null || !game.discardActionCard(player.getUserID(), number)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That action card is no longer in hand.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                player.getRepresentationNoPing() + " discarded _"
                        + Mapper.getActionCard(cardID).getName() + "_ for _Replication Arrays_.");
        ThirtyKExploreSupport.deleteMessage(event);
        String key = REPLICATION_COUNT_KEY + player.getFaction();
        int discarded = parseCount(game.getStoredValue(key)) + 1;
        game.setStoredValue(key, String.valueOf(discarded));
        if (discarded < 2 && !player.getActionCards().isEmpty()) {
            sendReplicationDiscardButtons(player);
            return;
        }
        game.removeStoredValue(key);
        offerReplicationPick(game, player);
    }

    private static int parseCount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void offerReplicationPick(Game game, Player player) {
        ActionCardHelper.pickACardFromDiscardStep1(game, player);
    }

    public static void resolveInterestingContagion(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (tile == null) return;
        String frontier = Mapper.getTokenID(Constants.FRONTIER);
        List<Tile> placed = new ArrayList<>();
        for (Tile adjacent : ThirtyKExploreSupport.tilesInOrAdjacent(game, player, tile)) {
            if (adjacent == tile || !adjacent.getPlanetUnitHolders().isEmpty()) continue;
            if (!Mapper.getFrontierTileIds().contains(adjacent.getTileID())) continue;
            if (adjacent.getSpaceUnitHolder().getTokenList().contains(frontier)) continue;
            adjacent.addToken(frontier, Constants.SPACE);
            placed.add(adjacent);
        }
        MessageHelper.sendMessageToEventChannel(
                event,
                "_Interesting Contagion_ placed " + placed.size() + " frontier token" + (placed.size() == 1 ? "" : "s")
                        + " in adjacent empty systems.");
    }

    public static void resolveItsATrap(GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (tile == null) return;
        Map<Player, Integer> candidates = new LinkedHashMap<>();
        for (Player other : game.getRealPlayers()) {
            if (ThirtyKDistance.isMoreThanFiveFromHome(game, other, tile.getPosition())) {
                candidates.put(other, other.getTotalVictoryPoints());
            }
        }
        Optional<Player> trapper = ThirtyKExploreMath.pickFewestPoints(candidates, ThreadLocalRandom.current());
        if (trapper.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "No player has a home system more than 5 hexes away, so _It's a Trap!_ does nothing.");
            return;
        }
        Player chosen = trapper.get();
        MessageHelper.sendMessageToEventChannel(
                event,
                FoWHelper.actorOrAnon(game, chosen, "A player in last place")
                        + " springs _It's a Trap!_ and may place up to 4 destroyers in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= 4; amount++) {
            buttons.add(Buttons.red(
                    chosen.factionButtonChecker() + TRAP_PLACE + tile.getPosition() + "_" + amount,
                    "Place " + amount + " Destroyer" + (amount == 1 ? "" : "s")));
        }
        buttons.add(ThirtyKExploreSupport.decline(chosen));
        MessageHelper.sendMessageToChannelWithButtons(
                ThirtyKExploreSupport.channelFor(chosen),
                chosen.getRepresentationUnfogged() + ", you were chosen by _It's a Trap!_ in "
                        + tile.getRepresentationForButtons(game, chosen)
                        + ". You may place up to 4 destroyers there; space combat starts right away.",
                buttons);
    }

    @ButtonHandler(TRAP_PLACE)
    public static void trapPlace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(TRAP_PLACE.length()).split("_");
        Tile tile = game.getTileByPosition(parts[0]);
        int amount = Integer.parseInt(parts[1]);
        if (tile == null) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " dd");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + amount + " destroyer" + (amount == 1 ? "" : "s")
                        + " with _It's a Trap!_.");
        ThirtyKExploreSupport.deleteMessage(event);
        StartCombatService.combatCheck(game, event, tile);
    }

    public static void resolveSystematicSympathies(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (tile == null) return;
        Map<Player, Double> shipCostByPlayer = new LinkedHashMap<>();
        for (Player other : game.getRealPlayers()) {
            if (!FoWHelper.playerHasUnitsInSystem(other, tile)) continue;
            shipCostByPlayer.put(other, destroyedShipCost(other, tile));
        }
        DestroyUnitService.destroyAllUnitsInSystem(event, tile, game, false);
        MessageHelper.sendMessageToEventChannel(event, "_Systematic Sympathies_ destroyed all units in this system.");
        shipCostByPlayer.forEach((other, cost) -> rewardSympathies(event, game, player, other, cost));
    }

    private static double destroyedShipCost(Player player, Tile tile) {
        double cost = 0;
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                UnitModel model = player.getUnitFromUnitKey(key);
                if (model == null || !model.getIsShip()) continue;
                cost += model.getCost() * holder.getUnitCount(key);
            }
        }
        return cost;
    }

    private static void rewardSympathies(
            GenericInteractionCreateEvent event, Game game, Player explorer, Player player, double cost) {
        if (ThirtyKExploreMath.sympathiesEarnsVictoryPoint(cost)) {
            Integer objective = game.addCustomPO(SYMPATHIES_OBJECTIVE, 1);
            game.scorePublicObjective(player.getUserID(), objective);
            FoWHelper.notifyPlayerAndAffectedInFog(
                    game,
                    explorer,
                    player,
                    FoWHelper.actorOrAnon(game, player, "A player") + " lost ships worth " + formatCost(cost)
                            + " resources and permanently gains 1 victory point (_" + SYMPATHIES_OBJECTIVE + "_).");
            Helper.checkEndGame(game, player);
            return;
        }
        ButtonHelperStats.gainTGs(event, game, player, 1, false);
    }

    private static String formatCost(double cost) {
        return cost == Math.floor(cost) ? String.valueOf((int) cost) : String.valueOf(cost);
    }

    public static void resolvePonderingTheNebulae(GenericInteractionCreateEvent event, Game game, Player player) {
        if (player.getSecretsUnscored().isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "You have no unscored secret objectives to discard.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> secret : player.getSecretsUnscored().entrySet()) {
            var model = Mapper.getSecretObjective(secret.getKey());
            if (model == null) continue;
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + PONDER_DISCARD + secret.getValue(), "Discard " + model.getName()));
        }
        List<Button> choices = ThirtyKExploreSupport.capped(buttons);
        choices.add(ThirtyKExploreSupport.decline(player));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", you may discard 1 unscored secret objective to draw 1 (_Pondering the Nebulae_).",
                choices);
        MessageHelper.sendMessageToEventChannel(
                event, "The secret objective choice for _Pondering the Nebulae_ is in your cards info thread.");
    }

    @ButtonHandler(PONDER_DISCARD)
    public static void ponderDiscard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int number = Integer.parseInt(buttonID.substring(PONDER_DISCARD.length()));
        if (!game.discardSecretObjective(player.getUserID(), number)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That secret objective is no longer in hand.");
            return;
        }
        ThirtyKExploreSupport.deleteMessage(event);
        DrawSecretService.drawSO(event, game, player, 1, false);
    }

    public static void resolveWereLostCaptain(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        Player speaker = game.getSpeaker();
        if (tile == null || speaker == null) {
            MessageHelper.sendMessageToEventChannel(event, "There is no speaker to resolve _We're Lost, Captain_.");
            return;
        }
        if (!hasUnitsInSpace(player, tile)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "You have no units in space in this system, so _We're Lost, Captain_ moves nothing.");
            return;
        }
        if (!game.isFowMode() && lostCaptainTargets(game, speaker, player, tile).isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No system qualifies for _We're Lost, Captain_.");
            return;
        }
        game.setStoredValue(WERE_LOST_PENDING_KEY, player.getFaction() + "+" + tile.getPosition());
        String movingShips = game.isFowMode()
                ? "the exploring player's ships"
                : "the ships in " + tile.getRepresentationForButtons(game, speaker);
        MessageHelper.sendMessageToEventChannel(
                event, "The speaker chooses where the ships go for _We're Lost, Captain_.");
        MessageHelper.sendMessageToChannelWithButtons(
                ThirtyKExploreSupport.channelFor(speaker),
                speaker.getRepresentationUnfogged() + ", choose a system without planets or other players' ships; "
                        + movingShips + " move there (_We're Lost, Captain_).",
                lostCaptainPage(game, speaker, player, tile, 0));
    }

    private static boolean hasUnitsInSpace(Player player, Tile tile) {
        UnitHolder space = tile.getSpaceUnitHolder();
        return space != null
                && space.getUnitKeysForPlayer(player).stream().anyMatch(key -> space.getUnitCount(key) > 0);
    }

    private static List<Button> lostCaptainTargets(Game game, Player speaker, Player explorer, Tile origin) {
        Set<String> visible = game.isFowMode() ? FoWHelper.getTilePositionsToShow(game, speaker) : null;
        List<Button> buttons = new ArrayList<>();
        for (Tile target : game.getTileMap().values()) {
            if (visible != null && !visible.contains(target.getPosition())) continue;
            if (!isLostCaptainTarget(game, explorer, origin, target)) continue;
            buttons.add(Buttons.gray(
                    wereLostPrefix(speaker, explorer, origin) + target.getPosition(),
                    target.getRepresentationForButtons(game, speaker)));
        }
        return buttons;
    }

    private static List<Button> lostCaptainPage(Game game, Player speaker, Player explorer, Tile origin, int page) {
        String prefix = wereLostPrefix(speaker, explorer, origin);
        List<Button> extras = game.isFowMode()
                ? List.of(BlindSelectionService.blindTargetButton(prefix.substring(0, prefix.length() - 1), false))
                : List.of();
        List<Button> targets = lostCaptainTargets(game, speaker, explorer, origin);
        List<Button> result =
                new ArrayList<>(NewStuffHelper.buttonPagination(targets, extras, prefix, 25, page, false));
        if (targets.size() + extras.size() <= 25) result.addAll(extras);
        return result;
    }

    private static String wereLostPrefix(Player speaker, Player explorer, Tile origin) {
        return speaker.factionButtonChecker() + WERE_LOST_MOVE + explorer.getColor() + "_" + origin.getPosition() + "_";
    }

    private static boolean isLostCaptainTarget(Game game, Player player, Tile origin, Tile target) {
        if (target == origin || !target.getPlanetUnitHolders().isEmpty()) return false;
        if (target.getTileModel() == null || target.getTilePath().toLowerCase().contains("hyperlane")) return false;
        if ("0".equals(target.getTileID()) || "-1".equals(target.getTileID())) return false;
        return !ThirtyKExploreSupport.otherPlayersHaveShips(game, player, target);
    }

    @ButtonHandler(WERE_LOST_MOVE)
    public static void wereLostMove(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(WERE_LOST_MOVE.length()).split("_", 3);
        String[] pending = game.getStoredValue(WERE_LOST_PENDING_KEY).split("\\+");
        Player explorer = pending.length == 2 ? game.getPlayerFromColorOrFaction(pending[0]) : null;
        Tile origin = pending.length == 2 ? game.getTileByPosition(pending[1]) : null;
        if (explorer == null
                || origin == null
                || parts.length != 3
                || !parts[0].equals(explorer.getColor())
                || !parts[1].equals(origin.getPosition())) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "_We're Lost, Captain_ has already been resolved.");
            ThirtyKExploreSupport.deleteMessage(event);
            return;
        }
        String choice = parts[2];
        if (choice.startsWith("page")) {
            int page = Integer.parseInt(choice.substring("page".length()));
            MessageHelper.editMessageButtons(event, lostCaptainPage(game, player, explorer, origin, page));
            return;
        }
        Tile target = game.getTileByPosition(choice);
        ThirtyKExploreSupport.deleteMessage(event);
        if (target == null || !isLostCaptainTarget(game, explorer, origin, target)) {
            MessageHelper.sendMessageToChannelWithButtons(
                    ThirtyKExploreSupport.channelFor(player),
                    player.getRepresentationUnfogged() + ", not a valid choice, pick again.",
                    lostCaptainPage(game, player, explorer, origin, 0));
            return;
        }
        game.removeStoredValue(WERE_LOST_PENDING_KEY);
        UnitHolder from = origin.getSpaceUnitHolder();
        UnitHolder to = target.getSpaceUnitHolder();
        for (UnitKey key : new ArrayList<>(from.getUnitKeysForPlayer(explorer))) {
            List<Integer> states = from.removeUnit(key, from.getUnitCount(key));
            to.addUnitsWithStates(key, states);
        }
        if (!target.hasPlayerCC(explorer)) {
            target.addCC(Mapper.getCCID(explorer.getColor()));
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                FoWHelper.actorOrAnon(game, explorer, "The exploring player") + "'s ships moved to "
                        + target.getRepresentationForButtons(game, player) + " (_We're Lost, Captain_).");
        if (game.isFowMode() && explorer != player) {
            MessageHelper.sendMessageToChannel(
                    explorer.getCorrectChannel(),
                    explorer.getRepresentationUnfogged() + ", your ships moved to "
                            + target.getRepresentationForButtons(game, explorer)
                            + " and a command token was placed there (_We're Lost, Captain_).");
        }
    }

    public static void clearWereLost(Game game) {
        game.removeStoredValue(WERE_LOST_PENDING_KEY);
    }

    public static void resolveUnseenDanger(GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (tile == null) return;
        UnitHolder space = tile.getSpaceUnitHolder();
        boolean destroyedAny = false;
        for (UnitKey key : new ArrayList<>(space.getUnitKeysForPlayer(player))) {
            if (!ThirtyKExploreSupport.isShip(player, key)) continue;
            int count = space.getUnitCount(key);
            if (count <= 0) continue;
            DestroyUnitService.destroyUnit(event, tile, game, key, count, space, false);
            destroyedAny = true;
        }
        for (UnitKey key : new ArrayList<>(space.getUnitKeysForPlayer(player))) {
            RemoveUnitService.removeUnit(event, tile, game, player, space, key.unitType(), space.getUnitCount(key));
        }
        tile.addToken(Mapper.getTokenID("asteroids"), Constants.SPACE);
        MessageHelper.sendMessageToEventChannel(
                event,
                "_Unseen Danger_ destroyed " + player.getRepresentationNoPing()
                        + "'s ships in this system and placed an asteroid field token.");
        if (!destroyedAny || player.hasTech("amd")) return;
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", you may gain _Antimass Deflectors_.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + "getTech_amd__noPay__comp", "Gain Antimass Deflectors"),
                        ThirtyKExploreSupport.decline(player)));
    }

    public static void resolveForegoneDiscoveries(GenericInteractionCreateEvent event, Game game, Player player) {
        List<String> relics = game.getAllRelics();
        if (relics.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "The relic deck is empty.");
            return;
        }
        String revealed = relics.getFirst();
        RelicModel relic = Mapper.getRelic(revealed);
        String relicName = relic == null ? revealed : relic.getName();
        List<Button> buttons = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + FOREGONE_GIVE + other.getColor() + "_" + revealed,
                    "green",
                    other,
                    player));
        }
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", the top relic is _" + relicName
                        + "_. Choose a player to gain it; they may transact with you this action phase.",
                ThirtyKExploreSupport.capped(buttons));
    }

    @ButtonHandler(FOREGONE_GIVE)
    public static void foregoneGive(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(FOREGONE_GIVE.length()).split("_", 2);
        Player chosen = game.getPlayerFromColorOrFaction(parts[0]);
        if (chosen == null || parts.length < 2) return;
        ThirtyKExploreSupport.deleteMessage(event);
        if (!game.getAllRelics().remove(parts[1])) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The revealed relic is no longer in the relic deck.");
            return;
        }
        ThirtyKRelics.gainRelic(
                event, game, chosen, parts[1].replace("extra1", "").replace("extra2", ""));
        if (chosen != player) {
            ThirtyKTransactions.allowForThisActionPhase(game, player, chosen);
            FoWHelper.notifyPlayerAndAffectedInFog(
                    game,
                    player,
                    "You may transact with "
                            + FoWHelper.identityOrColorIfCanSeeStats(
                                    game, chosen, player, chosen.getRepresentationNoPing())
                            + " during this action phase (_Foregone Discoveries_).",
                    chosen,
                    chosen.getRepresentationUnfogged() + ", you may transact with "
                            + FoWHelper.identityOrColorIfCanSeeStats(
                                    game, player, chosen, player.getRepresentationNoPing())
                            + " during this action phase (_Foregone Discoveries_).");
        }
    }
}

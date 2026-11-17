package ti4.service.objectives.fog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class FogSecretHooks {

    static final String STRIKE_FROM_FOG = "strikefromfog";
    static final String APPEAR_FROM_NOWHERE = "appearnowherefog";
    static final String INTO_THE_UNKNOWN = "intounknownfog";
    static final String BLIND_THEM = "blindthemfog";
    static final String SILENT_LANDING = "silentlandingfog";
    static final String LONG_RANGE_RECON = "longreconfog";
    static final String WITNESS_THE_FALL = "witnessfallfog";
    static final String JOIN_THE_CONSENSUS = "joinconsensusfog";
    static final String KINGMAKER = "kingmakerfog";
    static final String READ_THE_ROOM = "readroomfog";
    static final String DECIDING_WORD = "decidingwordfog";
    static final int LONG_RANGE_RECON_SYSTEMS = 3;
    static final int CONSENSUS_OTHER_VOTERS = 2;
    private static final String ONLY_IF_YOU_WIN =
            "\n**Only press Score if you win this combat, with your own units surviving it.**";

    static final String SIGHT_KEY = "fogSecretSight";
    static final String FOUND_KEY = "fogSecretFound";
    static final String ORIGINS_KEY = "fogSecretOrigins";
    static final String LANDED_KEY = "fogSecretLanded";
    static final String READ_ROOM_KEY = "fogSecretReadRoom";
    private static final String PROMPTED_KEY = "fogSecretPrompts";
    private static final List<String> TURN_KEYS = List.of(SIGHT_KEY, FOUND_KEY, ORIGINS_KEY, LANDED_KEY, PROMPTED_KEY);
    private static final String SEPARATOR = ";";

    public static boolean inPlay(Game game) {
        return game != null && game.isFowMode() && FogSecrets.usesFogDeck(game);
    }

    public static void onSystemActivated(Game game, Player player) {
        if (!inPlay(game)) return;
        if (!holdsAny(player, STRIKE_FROM_FOG, APPEAR_FROM_NOWHERE, INTO_THE_UNKNOWN, LONG_RANGE_RECON)) return;
        clearTurnState(game);
        storePositions(game, FOUND_KEY + player.getFaction(), FogSight.found(game, player));
        if (holdsAny(player, STRIKE_FROM_FOG, APPEAR_FROM_NOWHERE)) {
            for (Player other : FogSight.others(game, player)) {
                storePositions(game, SIGHT_KEY + other.getFaction(), FogSight.visible(game, other));
            }
        }
    }

    public static void onUnitsMovedIn(
            Game game, Player player, Tile activeTile, Map<String, Map<UnitKey, List<Integer>>> displacement) {
        if (!inPlay(game) || activeTile == null) return;
        if (!holdsAny(player, STRIKE_FROM_FOG, APPEAR_FROM_NOWHERE)) return;
        Set<String> movedFrom = originPositions(displacement);
        movedFrom.remove(activeTile.getPosition());
        if (movedFrom.isEmpty()) return;
        if (holdsAny(player, STRIKE_FROM_FOG)) {
            storePositions(game, ORIGINS_KEY + player.getFaction(), movedFrom);
        }
        if (holdsAny(player, APPEAR_FROM_NOWHERE)
                && appearedNextToUnwatchingHome(game, player, activeTile, movedFrom)) {
            offerScore(
                    game,
                    player,
                    APPEAR_FROM_NOWHERE,
                    "your ships arrived next to a home system from space its owner was not watching.");
        }
    }

    static Set<String> originPositions(Map<String, Map<UnitKey, List<Integer>>> displacement) {
        Set<String> origins = new HashSet<>();
        for (Map.Entry<String, Map<UnitKey, List<Integer>>> entry : displacement.entrySet()) {
            boolean anyUnits = entry.getValue().values().stream()
                    .anyMatch(states ->
                            states.stream().mapToInt(Integer::intValue).sum() > 0);
            int split = entry.getKey().indexOf('-');
            if (anyUnits)
                origins.add(split < 0 ? entry.getKey() : entry.getKey().substring(0, split));
        }
        return origins;
    }

    private static boolean appearedNextToUnwatchingHome(
            Game game, Player player, Tile activeTile, Set<String> movedFrom) {
        Adjacency adjacency = Adjacency.of(game, player).forEffects();
        String active = activeTile.getPosition();
        for (Player other : FogSight.others(game, player)) {
            String home = FogSight.homePosition(other);
            if (home == null || home.equals(active)) continue;
            if (!adjacency.isAdjacent(active, home) && !adjacency.isAdjacent(home, active)) continue;
            Set<String> sightAtStart = storedPositions(game, SIGHT_KEY + other.getFaction());
            if (sightAtStart == null) continue;
            if (movedFrom.stream().noneMatch(sightAtStart::contains)) return true;
        }
        return false;
    }

    public static void afterCombatHits(Game game, Player loser, Tile tile, UnitHolder holder, String assignHitsType) {
        if (!inPlay(game) || loser == null || tile == null || assignHitsType == null) return;
        if (!assignHitsType.contains("combat")) return;
        if (game.getRealPlayers().stream()
                .noneMatch(p -> holdsAny(p, STRIKE_FROM_FOG, INTO_THE_UNKNOWN, BLIND_THEM, WITNESS_THE_FALL))) return;
        if (assignHitsType.contains("ground") || holder instanceof Planet) {
            checkGroundCombatWin(game, tile);
            return;
        }
        if (FoWHelper.playerHasShipsInSystem(loser, tile)) return;
        List<Player> remaining = game.getRealPlayersNNeutral().stream()
                .filter(p -> p != loser && FoWHelper.playerHasShipsInSystem(p, tile))
                .toList();
        if (remaining.size() > 1) return;
        if (!markOnce(game, "space+" + tile.getPosition() + "+" + loser.getFaction())) return;
        Player winner = remaining.isEmpty() ? null : remaining.getFirst();
        if (winner != null && winner.isRealPlayer()) {
            checkSpaceCombatWin(game, winner, loser, tile);
        }
        offerWitnesses(game, loser, winner, tile);
    }

    private static void checkSpaceCombatWin(Game game, Player winner, Player loser, Tile tile) {
        String position = tile.getPosition();
        boolean winnerIsActive = winner == game.getActivePlayer();
        if (winnerIsActive && struckFromTheFog(game, winner, loser)) {
            offerScoreIfWon(
                    game,
                    winner,
                    STRIKE_FROM_FOG,
                    "the enemy fleet fell after you struck from systems your opponent could not see.");
        }
        if (winnerIsActive && wasUnknownAtStart(game, winner, position)) {
            offerScoreIfWon(
                    game, winner, INTO_THE_UNKNOWN, "the enemy fleet fell in a system you had not found before.");
        }
        if (loser.isRealPlayer()
                && holdsAny(winner, BLIND_THEM)
                && !FogSight.visible(game, loser).contains(position)) {
            offerScore(
                    game,
                    winner,
                    BLIND_THEM,
                    "you destroyed the last ships of a player, who can no longer see that system.");
        }
    }

    static boolean struckFromTheFog(Game game, Player winner, Player loser) {
        if (!holdsAny(winner, STRIKE_FROM_FOG) || !loser.isRealPlayer()) return false;
        Set<String> origins = storedPositions(game, ORIGINS_KEY + winner.getFaction());
        Set<String> loserSight = storedPositions(game, SIGHT_KEY + loser.getFaction());
        if (origins == null || origins.isEmpty() || loserSight == null) return false;
        return origins.stream().noneMatch(loserSight::contains);
    }

    private static void checkGroundCombatWin(Game game, Tile tile) {
        Player active = game.getActivePlayer();
        if (active == null || !holdsAny(active, INTO_THE_UNKNOWN)) return;
        if (!tile.getPosition().equals(game.getActiveSystem())) return;
        if (!wasUnknownAtStart(game, active, tile.getPosition())) return;
        boolean wonAPlanet = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> FoWHelper.playerHasUnitsOnPlanet(active, planet)
                        && game.getRealPlayersNNeutral().stream()
                                .filter(p -> p != active)
                                .noneMatch(p -> FoWHelper.playerHasUnitsOnPlanet(p, planet)));
        if (wonAPlanet) {
            offerScoreIfWon(game, active, INTO_THE_UNKNOWN, "the defenders fell in a system you had not found before.");
        }
    }

    private static void offerWitnesses(Game game, Player loser, Player winner, Tile tile) {
        for (Player watcher : game.getRealPlayers()) {
            if (watcher == loser || watcher == winner || !holdsAny(watcher, WITNESS_THE_FALL)) continue;
            if (FoWHelper.playerHasUnitsInSystem(watcher, tile)) continue;
            if (!FogSight.visible(game, watcher).contains(tile.getPosition())) continue;
            offerScore(game, watcher, WITNESS_THE_FALL, "you watched a space combat in which a fleet was wiped out.");
        }
    }

    public static void onPlanetGained(Game game, Player player, String planet) {
        if (!inPlay(game) || !holdsAny(player, INTO_THE_UNKNOWN, SILENT_LANDING)) return;
        if (player != game.getActivePlayer() || !"action".equalsIgnoreCase(game.getPhaseOfGame())) return;
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null || !tile.getPosition().equals(game.getActiveSystem())) return;
        if (wasUnknownAtStart(game, player, tile.getPosition())) {
            offerScore(
                    game,
                    player,
                    INTO_THE_UNKNOWN,
                    "you gained control of a planet in a system you had not found before.");
        }
        if (holdsAny(player, SILENT_LANDING)) {
            game.setStoredValue(LANDED_KEY + player.getFaction(), tile.getPosition());
        }
    }

    public static void onTurnEnd(Game game, Player player) {
        if (!inPlay(game)) return;
        if (player != null) {
            checkLongRangeRecon(game, player);
            checkSilentLanding(game, player);
        }
        clearTurnState(game);
    }

    private static void checkLongRangeRecon(Game game, Player player) {
        if (!holdsAny(player, LONG_RANGE_RECON)) return;
        Set<String> foundAtStart = storedPositions(game, FOUND_KEY + player.getFaction());
        if (foundAtStart == null) return;
        Set<String> newlyFound = new HashSet<>(FogSight.found(game, player));
        newlyFound.removeAll(foundAtStart);
        if (newlyFound.size() >= LONG_RANGE_RECON_SYSTEMS) {
            offerScore(
                    game,
                    player,
                    LONG_RANGE_RECON,
                    "you found " + newlyFound.size() + " new systems during that tactical action.");
        }
    }

    private static void checkSilentLanding(Game game, Player player) {
        String landed = game.getStoredValue(LANDED_KEY + player.getFaction());
        if (landed.isEmpty() || !holdsAny(player, SILENT_LANDING)) return;
        if (!FogSight.seenByAny(FogSight.visionOfOthers(game, player), landed)) {
            offerScore(
                    game,
                    player,
                    SILENT_LANDING,
                    "you took a planet and ended your tactical action unseen by every other player.");
        }
    }

    private static boolean wasUnknownAtStart(Game game, Player player, String position) {
        if (!holdsAny(player, INTO_THE_UNKNOWN)) return false;
        Set<String> foundAtStart = storedPositions(game, FOUND_KEY + player.getFaction());
        return foundAtStart != null && !foundAtStart.contains(position);
    }

    public static void onAgendaResolved(Game game, String winner) {
        if (!inPlay(game) || winner == null) return;
        if (game.getRealPlayers().stream().noneMatch(p -> holdsAny(p, JOIN_THE_CONSENSUS, KINGMAKER, READ_THE_ROOM)))
            return;
        String outcome = winner.toLowerCase();
        List<Player> winningVoters = new ArrayList<>(winningVotes(game, outcome).keySet());
        if (isConsensus(game, outcome)) {
            for (Player voter : winningVoters) {
                offerScoreEveryTime(
                        voter, JOIN_THE_CONSENSUS, "every vote cast on that agenda went to the outcome you backed.");
            }
        }
        for (Player kingmaker : kingmakers(game, outcome)) {
            offerScoreEveryTime(kingmaker, KINGMAKER, "your votes decided that agenda.");
        }
        if (opposingVotes(game, outcome) == 0) return;
        for (Player voter : winningVoters) {
            if (recordReadTheRoom(game, voter) >= 2) {
                offerScoreEveryTime(
                        voter,
                        READ_THE_ROOM,
                        "you voted for the contested elected outcome of both agendas this agenda phase.");
            }
        }
    }

    static boolean isConsensus(Game game, String outcome) {
        return opposingVotes(game, outcome) == 0 && winningVotes(game, outcome).size() > CONSENSUS_OTHER_VOTERS;
    }

    static List<Player> kingmakers(Game game, String outcome) {
        int winningTotal = totalsByOutcome(game).getOrDefault(outcome, 0);
        int opposing = opposingVotes(game, outcome);
        if (winningTotal <= opposing) return List.of();
        return winningVotes(game, outcome).entrySet().stream()
                .filter(vote -> winningTotal - vote.getValue() < opposing)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static LinkedHashMap<Player, Integer> winningVotes(Game game, String outcome) {
        return votesByOutcome(game).getOrDefault(outcome, new LinkedHashMap<>());
    }

    private static int opposingVotes(Game game, String outcome) {
        return totalsByOutcome(game).entrySet().stream()
                .filter(entry -> !entry.getKey().equals(outcome))
                .mapToInt(Map.Entry::getValue)
                .max()
                .orElse(0);
    }

    private static int recordReadTheRoom(Game game, Player voter) {
        if (!holdsAny(voter, READ_THE_ROOM)) return 0;
        String key = READ_ROOM_KEY + voter.getFaction();
        String[] stored = game.getStoredValue(key).split(SEPARATOR);
        int count = stored.length == 2 && String.valueOf(game.getRound()).equals(stored[0])
                ? Integer.parseInt(stored[1]) + 1
                : 1;
        game.setStoredValue(key, game.getRound() + SEPARATOR + count);
        return count;
    }

    public static void onSpeakerBrokeTie(Game game) {
        if (!inPlay(game)) return;
        Player speaker = game.getSpeaker();
        if (speaker != null && holdsAny(speaker, DECIDING_WORD)) {
            offerScoreEveryTime(speaker, DECIDING_WORD, "as the speaker, you broke the tie on that agenda.");
        }
    }

    static Map<String, LinkedHashMap<Player, Integer>> votesByOutcome(Game game) {
        Map<String, LinkedHashMap<Player, Integer>> result = new LinkedHashMap<>();
        forEachVote(game, (outcome, voter, amount) -> {
            if (voter != null) {
                result.computeIfAbsent(outcome, k -> new LinkedHashMap<>()).merge(voter, amount, Integer::sum);
            }
        });
        return result;
    }

    static Map<String, Integer> totalsByOutcome(Game game) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        forEachVote(game, (outcome, voter, amount) -> totals.merge(outcome, amount, Integer::sum));
        return totals;
    }

    private interface VoteConsumer {
        void accept(String outcome, Player voter, int amount);
    }

    private static void forEachVote(Game game, VoteConsumer consumer) {
        for (Map.Entry<String, String> entry : game.getCurrentAgendaVotes().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            StringTokenizer tokens = new StringTokenizer(entry.getValue(), ";");
            while (tokens.hasMoreTokens()) {
                String vote = tokens.nextToken();
                int split = vote.indexOf('_');
                if (split < 1) continue;
                Integer amount = parseVotes(vote.substring(split + 1));
                if (amount == null || amount < 1) continue;
                Player voter = game.getPlayerFromColorOrFaction(vote.substring(0, split));
                consumer.accept(entry.getKey().toLowerCase(), voter, amount);
            }
        }
    }

    private static Integer parseVotes(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean holdsAny(Player player, String... soIDs) {
        if (player == null) return false;
        Map<String, Integer> unscored = player.getSecretsUnscored();
        return Arrays.stream(soIDs).anyMatch(unscored::containsKey);
    }

    static void offerScore(Game game, Player holder, String soID, String reason) {
        if (!markOnce(game, soID + "+" + (holder == null ? "" : holder.getFaction()))) return;
        offerScoreEveryTime(holder, soID, reason);
    }

    private static void offerScoreIfWon(Game game, Player holder, String soID, String reason) {
        if (!markOnce(game, soID + "+" + holder.getFaction())) return;
        offerScoreEveryTime(holder, soID, reason, ONLY_IF_YOU_WIN);
    }

    static void offerScoreEveryTime(Player holder, String soID, String reason) {
        offerScoreEveryTime(holder, soID, reason, "");
    }

    private static void offerScoreEveryTime(Player holder, String soID, String reason, String condition) {
        if (!holdsAny(holder, soID)) return;
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        if (so == null) return;
        List<Button> buttons = List.of(
                Buttons.green(
                        holder.factionButtonChecker()
                                + Constants.SO_SCORE_FROM_HAND
                                + holder.getSecretsUnscored().get(soID),
                        "Score " + so.getName(),
                        CardEmojis.SecretObjective),
                Buttons.red(holder.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                holder.getRepresentationUnfogged() + ", " + reason + " You may score _" + so.getName() + "_."
                        + condition,
                buttons);
    }

    private static void storePositions(Game game, String key, Set<String> positions) {
        game.setStoredValue(key, positions.isEmpty() ? SEPARATOR : String.join(SEPARATOR, positions));
    }

    private static Set<String> storedPositions(Game game, String key) {
        String stored = game.getStoredValue(key);
        if (stored.isEmpty()) return null;
        Set<String> positions = new LinkedHashSet<>(Arrays.asList(stored.split(SEPARATOR)));
        positions.remove("");
        return positions;
    }

    static boolean markOnce(Game game, String event) {
        String stored = game.getStoredValue(PROMPTED_KEY);
        Set<String> seen =
                stored.isBlank() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(stored.split(SEPARATOR)));
        if (!seen.add(event)) return false;
        game.setStoredValue(PROMPTED_KEY, String.join(SEPARATOR, seen));
        return true;
    }

    static void clearTurnState(Game game) {
        game.getStoredValueMap().keySet().removeIf(key -> TURN_KEYS.stream().anyMatch(key::startsWith));
    }
}

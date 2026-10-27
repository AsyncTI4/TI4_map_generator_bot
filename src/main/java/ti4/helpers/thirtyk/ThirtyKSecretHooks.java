package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.model.Mapper;
import ti4.model.SecretObjectiveModel;
import ti4.model.UnitModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class ThirtyKSecretHooks {

    static final String RELINQUISH_TECHNOLOGY = "relinquishtechnology30k";
    static final String PLANNED_OBSOLESCENCE = "plannedobsolescence30k";
    static final String POLICY_INDIFFERENCE = "policyindifference30k";
    static final String POLICY_DEFIANCE = "policydefiance30k";
    static final String POLICY_MAKER = "policymakerjmsc";
    static final String UNDER_OUR_SUPERVISION = "underoursupervisionjmsc";
    static final String A_GRAND_ENTRANCE = "agrandentrancejmsc";
    static final String REPRIMANDED = "reprimandedjmsc";
    static final String EXPLOIT_EXHAUSTION = "exploitexhaustionjmsc";
    static final String QUICK_DELIBERATIONS = "quickdeliberationsjmsc";
    static final String AGENDA_VOTERS_KEY = "thirtyKAgendaVoters";
    static final String FLAGSHIP_START_KEY = "thirtyKFlagshipStart";
    static final String STRUCTURE_AT_COMBAT_KEY = "thirtyKStructureAtCombat";
    private static final String PROMPTED_KEY = "thirtyKSecretPrompts";

    public static void offerScore(Player holder, String soID, String reason) {
        if (holder == null || !holder.getSecretsUnscored().containsKey(soID)) return;
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
                holder.getRepresentationUnfogged() + ", " + reason + " Score _" + so.getName() + "_ if you qualify.",
                buttons);
    }

    public static void onPlanetLost(Game game, Player previousOwner, UnitHolder planet) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || previousOwner == null || planet == null || !previousOwner.isRealPlayer()) return;
        String planetName = Mapper.getPlanet(planet.getName()) == null
                ? planet.getName()
                : Mapper.getPlanet(planet.getName()).getName();
        if (hasAttachment(planet)) {
            offerScore(
                    previousOwner,
                    RELINQUISH_TECHNOLOGY,
                    "you have handed over " + planetName + ", attachment and all.");
        }
        if (hasStructure(game, planet) || hadStructureAtCombatStart(game, planet)) {
            offerScore(
                    previousOwner,
                    PLANNED_OBSOLESCENCE,
                    "you have abandoned " + planetName + " and the structure on it.");
        } else if (previousOwner.getSecretsUnscored().containsKey(PLANNED_OBSOLESCENCE)) {
            offerScore(
                    previousOwner,
                    PLANNED_OBSOLESCENCE,
                    "you have abandoned " + planetName
                            + ". No structure stands there now, but you qualify if one stood there when you lost control.");
        }
    }

    public static void onGroundCombatStart(Game game, UnitHolder planet) {
        if (!ThirtyKCards.inUse(game)) return;
        if (planet == null || !hasStructure(game, planet)) return;
        boolean anyHolder = game.getRealPlayers().stream()
                .anyMatch(player -> player.getSecretsUnscored().containsKey(PLANNED_OBSOLESCENCE));
        if (anyHolder) game.setStoredValue(STRUCTURE_AT_COMBAT_KEY + planet.getName(), "yes");
    }

    static boolean hadStructureAtCombatStart(Game game, UnitHolder planet) {
        return !game.getStoredValue(STRUCTURE_AT_COMBAT_KEY + planet.getName()).isBlank();
    }

    static boolean hasAttachment(UnitHolder planet) {
        return planet.getTokenList().stream().anyMatch(token -> {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            return attachment != null && attachment.isRealAttachment();
        });
    }

    private static boolean hasStructure(Game game, UnitHolder planet) {
        for (Player owner : game.getPlayers().values()) {
            for (UnitKey key : planet.getUnitKeysForPlayer(owner)) {
                UnitModel model = owner.getUnitFromUnitKey(key);
                if (model != null && model.getIsStructure() && planet.getUnitCount(key) > 0) return true;
            }
        }
        return false;
    }

    public static void afterCombatHits(Game game, Player player, Tile tile, UnitHolder holder, String assignHitsType) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null || tile == null || assignHitsType == null) return;
        if (!assignHitsType.contains("combat")) return;
        boolean ground = assignHitsType.contains("ground") || (holder != null && holder instanceof Planet);
        if (ground) {
            List<UnitHolder> planets =
                    holder instanceof Planet ? List.of(holder) : new ArrayList<>(tile.getPlanetUnitHolders());
            planets.forEach(planet -> checkGroundCombatEnd(game, player, tile, planet));
        } else {
            checkSpaceCombatEnd(game, player, tile);
        }
    }

    private static void checkSpaceCombatEnd(Game game, Player loser, Tile tile) {
        if (FoWHelper.playerHasShipsInSystem(loser, tile)) return;
        List<Player> remaining = game.getRealPlayersNNeutral().stream()
                .filter(p -> p != loser && FoWHelper.playerHasShipsInSystem(p, tile))
                .toList();
        if (remaining.size() != 1) return;
        Player winner = remaining.getFirst();
        boolean reprimanded =
                loser.getSecretsUnscored().containsKey(REPRIMANDED) && isAtOrNextToMecatol(game, loser, tile);
        boolean grandEntrance = winner.isRealPlayer() && flagshipEnteredFromAfar(game, winner, tile);
        if (!reprimanded && !grandEntrance) return;
        if (!markOnce(game, "space+" + tile.getPosition() + "+" + loser.getFaction())) return;
        if (reprimanded) {
            offerScore(loser, REPRIMANDED, "the Council watched your fleet fall at or next to Mecatol Rex.");
        }
        if (grandEntrance) {
            offerScore(
                    winner,
                    A_GRAND_ENTRANCE,
                    "your flagship swept in from more than 5 hexes away and won the space combat.");
        }
    }

    private static boolean isAtOrNextToMecatol(Game game, Player player, Tile tile) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null) return false;
        return FoWHelper.getAdjacentTiles(game, mecatol.getPosition(), player, false, true)
                .contains(tile.getPosition());
    }

    private static boolean flagshipEnteredFromAfar(Game game, Player winner, Tile tile) {
        if (!winner.getSecretsUnscored().containsKey(A_GRAND_ENTRANCE)) return false;
        if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, winner) < 1) return false;
        if (game.getActivePlayer() != winner) return false;
        String start = StringUtils.substringAfter(game.getStoredValue(FLAGSHIP_START_KEY + winner.getFaction()), ";");
        return start.isBlank() || ThirtyKDistance.isMoreThanFiveAway(game, start, tile.getPosition());
    }

    private static void checkGroundCombatEnd(Game game, Player loser, Tile tile, UnitHolder planet) {
        if (ButtonHelperModifyUnits.doesPlayerHaveGfOnPlanet(planet, loser)) return;
        List<Player> remaining = game.getRealPlayersNNeutral().stream()
                .filter(p -> p != loser && ButtonHelperModifyUnits.doesPlayerHaveGfOnPlanet(planet, p))
                .toList();
        if (remaining.size() != 1 || !loser.isPassed()) return;
        Player winner = remaining.getFirst();
        if (!winner.getSecretsUnscored().containsKey(EXPLOIT_EXHAUSTION)) return;
        if (!markOnce(game, "ground+" + tile.getPosition() + "+" + planet.getName() + "+" + loser.getFaction())) return;
        offerScore(
                winner,
                EXPLOIT_EXHAUSTION,
                "you caught a passed player off guard and won the ground combat on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".");
    }

    public static void onPassed(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null) return;
        boolean anyoneElsePassed =
                game.getRealPlayers().stream().filter(p -> p != player).anyMatch(Player::isPassed);
        if (anyoneElsePassed) return;
        offerScore(
                player,
                QUICK_DELIBERATIONS,
                "the debate was over before it began: you are the first player to pass this round.");
    }

    public static void onAgendaResolved(Game game) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null) return;
        Map<String, Map<Player, Integer>> votesByOutcome = votesByOutcome(game);
        if (anyoneHolds(game, POLICY_INDIFFERENCE)) {
            rememberVoters(game, votesByOutcome);
        }
        if (game.isFowMode()) return;
        offerPolicyDefiance(votesByOutcome);
        offerPolicyMaker(votesByOutcome);
    }

    private static boolean anyoneHolds(Game game, String soID) {
        return game.getRealPlayers().stream()
                .anyMatch(player -> player.getSecretsUnscored().containsKey(soID));
    }

    static Map<String, Map<Player, Integer>> votesByOutcome(Game game) {
        Map<String, Map<Player, Integer>> result = new HashMap<>();
        for (Map.Entry<String, String> entry : game.getCurrentAgendaVotes().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            Map<Player, Integer> votes = new HashMap<>();
            StringTokenizer tokens = new StringTokenizer(entry.getValue(), ";");
            while (tokens.hasMoreTokens()) {
                String vote = tokens.nextToken();
                int split = vote.indexOf('_');
                if (split < 1) continue;
                Player voter = game.getPlayerFromColorOrFaction(vote.substring(0, split));
                Integer amount = parseVotes(vote.substring(split + 1));
                if (voter == null || amount == null || amount < 1) continue;
                votes.merge(voter, amount, Integer::sum);
            }
            if (!votes.isEmpty()) result.put(entry.getKey().toLowerCase(), votes);
        }
        return result;
    }

    private static Integer parseVotes(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void rememberVoters(Game game, Map<String, Map<Player, Integer>> votesByOutcome) {
        Set<String> voters = new LinkedHashSet<>(votersThisRound(game));
        votesByOutcome.values().forEach(votes -> votes.keySet().forEach(p -> voters.add(p.getFaction())));
        game.setStoredValue(AGENDA_VOTERS_KEY, game.getRound() + ";" + String.join(";", voters));
    }

    static Set<String> votersThisRound(Game game) {
        String stored = game.getStoredValue(AGENDA_VOTERS_KEY);
        if (stored.isBlank()) return Set.of();
        List<String> parts = Arrays.asList(stored.split(";"));
        if (!String.valueOf(game.getRound()).equals(parts.getFirst())) return Set.of();
        return new HashSet<>(parts.subList(1, parts.size()));
    }

    private static void offerPolicyDefiance(Map<String, Map<Player, Integer>> votesByOutcome) {
        for (String side : List.of("for", "against")) {
            Map<Player, Integer> votes = votesByOutcome.get(side);
            if (votes == null || votes.size() != 1) continue;
            Player lone = votes.keySet().iterator().next();
            offerScore(lone, POLICY_DEFIANCE, "you stood alone as the only player to vote \"" + side + "\".");
        }
    }

    private static void offerPolicyMaker(Map<String, Map<Player, Integer>> votesByOutcome) {
        Map<Player, Integer> totals = new HashMap<>();
        votesByOutcome.values().forEach(votes -> votes.forEach((p, amount) -> totals.merge(p, amount, Integer::sum)));
        Player top = soleTopVoter(totals);
        if (top != null) {
            offerScore(
                    top,
                    POLICY_MAKER,
                    "your voice carried the chamber: you cast more votes on this agenda than any other player.");
        }
    }

    static <T> T soleTopVoter(Map<T, Integer> totals) {
        int best = totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (best < 1) return null;
        List<T> leaders = totals.entrySet().stream()
                .filter(e -> e.getValue() == best)
                .map(Map.Entry::getKey)
                .toList();
        return leaders.size() == 1 ? leaders.getFirst() : null;
    }

    public static void onAgendaPhaseEnd(Game game) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || !game.getPhaseOfGame().toLowerCase().contains("agenda")) return;
        Set<String> voters = votersThisRound(game);
        for (Player player : game.getRealPlayers()) {
            if (voters.contains(player.getFaction())) continue;
            boolean allReady = player.getExhaustedPlanets().stream().noneMatch(player.getPlanets()::contains);
            if (!allReady) continue;
            offerScore(
                    player,
                    POLICY_INDIFFERENCE,
                    "the agenda phase ends without a word from you: no votes cast and all your planets ready. "
                            + "Check that you also played no before/after agenda action cards.");
        }
    }

    // TODO: fog - the prompt tells the holder that someone scored via Imperial (minor; the scorer is not named).
    public static void onImperialPointScored(Game game, Player scorer) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || scorer == null) return;
        Tile mecatol = game.getMecatolTile();
        Tile scorerHome = scorer.getHomeSystemTile();
        for (Player holder : game.getRealPlayers()) {
            if (holder == scorer || !holder.getSecretsUnscored().containsKey(UNDER_OUR_SUPERVISION)) continue;
            if (hasShipsAdjacentTo(game, holder, mecatol) || hasShipsAdjacentTo(game, holder, scorerHome)) {
                offerScore(
                        holder,
                        UNDER_OUR_SUPERVISION,
                        "another player claimed a victory point via **Imperial** while your ships watched from next to Mecatol Rex or their home system.");
            }
        }
    }

    private static boolean hasShipsAdjacentTo(Game game, Player player, Tile center) {
        if (center == null) return false;
        return FoWHelper.getAdjacentTiles(game, center.getPosition(), player, false, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(tile -> tile != null && FoWHelper.playerHasShipsInSystem(player, tile));
    }

    public static void onTurnStart(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null) return;
        if (!game.getStoredValue(FLAGSHIP_START_KEY + player.getFaction()).isBlank()) return;
        recordFlagshipStart(game, player);
    }

    public static void onSystemActivated(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null) return;
        recordFlagshipStart(game, player);
    }

    private static void recordFlagshipStart(Game game, Player player) {
        if (!player.getSecretsUnscored().containsKey(A_GRAND_ENTRANCE)) return;
        String key = FLAGSHIP_START_KEY + player.getFaction();
        String position = game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder() != null
                        && tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player) > 0)
                .map(Tile::getPosition)
                .findFirst()
                .orElse("");
        game.setStoredValue(key, "start;" + position);
    }

    static void clearTurnState(Game game) {
        ThirtyKTurnState.removeKeysStartingWith(game, FLAGSHIP_START_KEY);
        ThirtyKTurnState.removeKeysStartingWith(game, STRUCTURE_AT_COMBAT_KEY);
        game.removeStoredValue(PROMPTED_KEY);
    }

    static boolean markOnce(Game game, String event) {
        String stored = game.getStoredValue(PROMPTED_KEY);
        Set<String> seen =
                stored.isBlank() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(stored.split(";")));
        if (!seen.add(event)) return false;
        game.setStoredValue(PROMPTED_KEY, String.join(";", seen));
        return true;
    }
}

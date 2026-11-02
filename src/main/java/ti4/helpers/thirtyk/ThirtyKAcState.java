package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ThirtyKAcState {

    static final String COMPOUND_INTEREST = "thkCompound";
    static final String WAR_PROFITEERING = "thkWarProfit";
    static final String CONFLAGRATION = "thkConflagration";
    static final String PURGED_FRAGMENTS = "thkPurgedFrags";
    static final String STRUCTURE_LOST = "thkStructureLost";
    static final String DUMMY_RIDER_PLAYER = "thkDummyRider";
    static final String DUMMY_RIDER_COPIES = "thkDummyRiderCopies";
    static final String SIPHON_TARGETS = "thkSiphon";
    static final String BLEW_UP = "thkBlewUp";
    static final String EXTENDED_CAMPAIGNING = "thkExtCampaign";
    static final String ONE_SHOT = "thkOneShot";
    static final String FINAL_STAND = "thkFinalStand";
    static final String RIDER_CARDS = "thkRiderCards";
    static final String DUMMY_RIDER_CARD = "dummyrider30k";
    private static final String SEPARATOR = "+";
    private static final String SPLIT = "\\+";

    public record WarProfiteering(String targetFaction, String position) {}

    public record PlanetLoss(String planet, String newOwnerFaction) {}

    public record PurgedFragments(String purgerFaction, List<String> fragments) {}

    public record StructureLoss(String loserFaction, String position) {}

    public enum ProfiteeringResult {
        WON,
        LOST,
        PENDING
    }

    public static int compoundInterest(Game game, Player player) {
        return NumberUtils.toInt(game.getStoredValue(COMPOUND_INTEREST + player.getFaction()), 0);
    }

    public static void addCompoundInterest(Game game, Player player, int tradeGoods) {
        game.setStoredValue(
                COMPOUND_INTEREST + player.getFaction(), String.valueOf(compoundInterest(game, player) + tradeGoods));
    }

    public static int collectCompoundInterest(Game game, Player player) {
        int payout = compoundInterest(game, player) * 2;
        game.removeStoredValue(COMPOUND_INTEREST + player.getFaction());
        return payout;
    }

    public static void setWarProfiteering(Game game, Player player, Player target, String position) {
        game.setStoredValue(WAR_PROFITEERING + player.getFaction(), target.getFaction() + SEPARATOR + position);
    }

    public static WarProfiteering warProfiteering(Game game, Player player) {
        String[] parts =
                game.getStoredValue(WAR_PROFITEERING + player.getFaction()).split(SPLIT);
        if (parts.length != 2) return null;
        return new WarProfiteering(parts[0], parts[1]);
    }

    public static void clearWarProfiteering(Game game, Player player) {
        game.removeStoredValue(WAR_PROFITEERING + player.getFaction());
    }

    public static ProfiteeringResult profiteeringResult(String targetFaction, List<String> factionsWithShips) {
        if (!factionsWithShips.contains(targetFaction)) return ProfiteeringResult.LOST;
        if (factionsWithShips.size() == 1) return ProfiteeringResult.WON;
        return ProfiteeringResult.PENDING;
    }

    public static void setPlanetLoss(Game game, Player previousOwner, String planet, Player newOwner) {
        game.setStoredValue(CONFLAGRATION + previousOwner.getFaction(), planet + SEPARATOR + newOwner.getFaction());
    }

    public static PlanetLoss planetLoss(Game game, Player player) {
        String[] parts =
                game.getStoredValue(CONFLAGRATION + player.getFaction()).split(SPLIT);
        if (parts.length != 2) return null;
        return new PlanetLoss(parts[0], parts[1]);
    }

    public static void clearPlanetLoss(Game game, Player player) {
        game.removeStoredValue(CONFLAGRATION + player.getFaction());
    }

    public static void setPurgedFragments(Game game, Player purger, List<String> fragments) {
        List<String> parts = new ArrayList<>();
        parts.add(purger.getFaction());
        parts.addAll(fragments);
        game.setStoredValue(PURGED_FRAGMENTS, String.join(SEPARATOR, parts));
    }

    public static PurgedFragments purgedFragments(Game game) {
        String stored = game.getStoredValue(PURGED_FRAGMENTS);
        if (stored.isBlank()) return null;
        List<String> parts = Arrays.asList(stored.split(SPLIT));
        return new PurgedFragments(parts.getFirst(), new ArrayList<>(parts.subList(1, parts.size())));
    }

    public static void removePurgedFragment(Game game, String fragment) {
        PurgedFragments purged = purgedFragments(game);
        if (purged == null) return;
        List<String> remaining = new ArrayList<>(purged.fragments());
        remaining.remove(fragment);
        List<String> parts = new ArrayList<>();
        parts.add(purged.purgerFaction());
        parts.addAll(remaining);
        game.setStoredValue(PURGED_FRAGMENTS, String.join(SEPARATOR, parts));
    }

    public static void setStructureLost(Game game, Player loser, String position) {
        game.setStoredValue(STRUCTURE_LOST, loser.getFaction() + SEPARATOR + position);
    }

    public static StructureLoss structureLoss(Game game) {
        String[] parts = game.getStoredValue(STRUCTURE_LOST).split(SPLIT);
        if (parts.length != 2) return null;
        return new StructureLoss(parts[0], parts[1]);
    }

    public static void clearStructureLost(Game game) {
        game.removeStoredValue(STRUCTURE_LOST);
    }

    public static void setDummyRider(Game game, Player player, String outcome) {
        game.setStoredValue(DUMMY_RIDER_PLAYER, player.getFaction() + SEPARATOR + outcome.toLowerCase());
    }

    public static boolean isDummyRider(Game game, Player player, String outcome) {
        return (player.getFaction() + SEPARATOR + outcome.toLowerCase())
                .equals(game.getStoredValue(DUMMY_RIDER_PLAYER));
    }

    public static void clearDummyRider(Game game) {
        game.removeStoredValue(DUMMY_RIDER_PLAYER);
        game.removeStoredValue(DUMMY_RIDER_COPIES);
    }

    public static void addDummyRiderCopy(Game game, Player player, String outcome, String copyingCard) {
        List<String> copies = new ArrayList<>(dummyRiderCopies(game));
        copies.add(copyEntry(player, outcome, copyingCard));
        game.setStoredValue(DUMMY_RIDER_COPIES, String.join(";", copies));
    }

    public static String takeWrongDummyRiderCard(Game game, Player player, String outcome) {
        if (isDummyRider(game, player, outcome)) {
            game.removeStoredValue(DUMMY_RIDER_PLAYER);
            return DUMMY_RIDER_CARD;
        }
        String prefix = player.getFaction() + SEPARATOR + outcome.toLowerCase() + SEPARATOR;
        List<String> remaining = new ArrayList<>(dummyRiderCopies(game));
        for (String copy : remaining) {
            if (!copy.startsWith(prefix)) continue;
            remaining.remove(copy);
            game.setStoredValue(DUMMY_RIDER_COPIES, String.join(";", remaining));
            return copy.substring(prefix.length());
        }
        return null;
    }

    private static String copyEntry(Player player, String outcome, String copyingCard) {
        return player.getFaction() + SEPARATOR + outcome.toLowerCase() + SEPARATOR + copyingCard;
    }

    private static List<String> dummyRiderCopies(Game game) {
        String stored = game.getStoredValue(DUMMY_RIDER_COPIES);
        if (stored.isBlank()) return List.of();
        return Arrays.asList(stored.split(";"));
    }

    public static List<String> siphonTargets(Game game, Player player) {
        String stored = game.getStoredValue(SIPHON_TARGETS + player.getFaction());
        if (stored.isBlank()) return List.of();
        return Arrays.asList(stored.split(SPLIT));
    }

    public static void addSiphonTarget(Game game, Player player, Player target) {
        List<String> targets = new ArrayList<>(siphonTargets(game, player));
        targets.add(target.getFaction());
        game.setStoredValue(SIPHON_TARGETS + player.getFaction(), String.join(SEPARATOR, targets));
    }

    public static void clearSiphonTargets(Game game, Player player) {
        game.removeStoredValue(SIPHON_TARGETS + player.getFaction());
    }

    public static int fightersBlownUp(Game game, Player player, String position) {
        String[] parts = game.getStoredValue(BLEW_UP + player.getFaction()).split(SPLIT);
        if (parts.length != 2 || !parts[0].equals(position)) return 0;
        return NumberUtils.toInt(parts[1], 0);
    }

    public static void setFightersBlownUp(Game game, Player player, String position, int count) {
        game.setStoredValue(BLEW_UP + player.getFaction(), position + SEPARATOR + count);
    }

    public static void clearFightersBlownUp(Game game, Player player) {
        game.removeStoredValue(BLEW_UP + player.getFaction());
    }

    public static void setExtendedCampaigning(Game game, Player player, Player target) {
        game.setStoredValue(EXTENDED_CAMPAIGNING + target.getFaction(), player.getFaction());
    }

    public static boolean takeExtendedCampaigning(Game game, Player player, Player target) {
        if (!player.getFaction().equals(game.getStoredValue(EXTENDED_CAMPAIGNING + target.getFaction()))) return false;
        game.removeStoredValue(EXTENDED_CAMPAIGNING + target.getFaction());
        return true;
    }

    public static void setFinalStand(Game game, Player player, String planet) {
        game.setStoredValue(FINAL_STAND + player.getFaction(), planet);
    }

    public static void clearFinalStandOn(Game game, String planet) {
        game.getStoredValueMap()
                .entrySet()
                .removeIf(entry -> entry.getKey().startsWith(FINAL_STAND) && planet.equals(entry.getValue()));
    }

    public static boolean hasFinalStand(Game game, Player player, String planet) {
        return planet.equals(game.getStoredValue(FINAL_STAND + player.getFaction()));
    }

    public static void markRiderCard(Game game, Player player, String rider) {
        List<String> riders = new ArrayList<>(riderCards(game, player));
        if (riders.contains(rider)) return;
        riders.add(rider);
        game.setStoredValue(RIDER_CARDS + player.getFaction(), String.join(SEPARATOR, riders));
    }

    public static boolean playedRiderCard(Game game, Player player, String rider) {
        return riderCards(game, player).contains(rider);
    }

    private static List<String> riderCards(Game game, Player player) {
        String stored = game.getStoredValue(RIDER_CARDS + player.getFaction());
        if (stored.isBlank()) return List.of();
        return Arrays.asList(stored.split(SPLIT));
    }

    public static void clearRiderCards(Game game) {
        ThirtyKTurnState.removeKeysStartingWith(game, RIDER_CARDS);
    }

    static void clearTurnState(Game game) {
        ThirtyKTurnState.removeKeysStartingWith(game, WAR_PROFITEERING);
        ThirtyKTurnState.removeKeysStartingWith(game, EXTENDED_CAMPAIGNING);
        ThirtyKTurnState.removeKeysStartingWith(game, ONE_SHOT);
        ThirtyKTurnState.removeKeysStartingWith(game, FINAL_STAND);
    }

    static void clearActionPhaseState(Game game) {
        ThirtyKTurnState.removeKeysStartingWith(game, CONFLAGRATION);
        game.removeStoredValue(STRUCTURE_LOST);
    }
}

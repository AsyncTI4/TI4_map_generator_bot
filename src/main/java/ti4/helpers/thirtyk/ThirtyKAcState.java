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
    private static final String SEPARATOR = "+";
    private static final String SPLIT = "\\+";

    public record WarProfiteering(String targetFaction, String position) {}

    public record PlanetLoss(String planet, String newOwnerFaction) {}

    public record PurgedFragments(String purgerFaction, List<String> fragments) {}

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

    public static void setStructureLost(Game game, Player loser) {
        game.setStoredValue(STRUCTURE_LOST, loser.getFaction());
    }

    public static Player structureLoser(Game game) {
        String faction = game.getStoredValue(STRUCTURE_LOST);
        return faction.isBlank() ? null : game.getPlayerFromColorOrFaction(faction);
    }

    public static void setDummyRiderPlayer(Game game, Player player) {
        game.setStoredValue(DUMMY_RIDER_PLAYER, player.getFaction());
    }

    public static boolean isDummyRiderPlayer(Game game, Player player) {
        return player.getFaction().equals(game.getStoredValue(DUMMY_RIDER_PLAYER));
    }

    public static void clearDummyRiderPlayer(Game game) {
        game.removeStoredValue(DUMMY_RIDER_PLAYER);
        game.removeStoredValue(DUMMY_RIDER_COPIES);
    }

    public static void addDummyRiderCopy(Game game, Player player, String copyingCard) {
        List<String> copies = new ArrayList<>(dummyRiderCopies(game));
        copies.add(player.getFaction() + SEPARATOR + copyingCard);
        game.setStoredValue(DUMMY_RIDER_COPIES, String.join(";", copies));
    }

    public static List<String> takeDummyRiderCopies(Game game, Player player) {
        List<String> remaining = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (String copy : dummyRiderCopies(game)) {
            String[] parts = copy.split(SPLIT, 2);
            if (parts.length == 2 && parts[0].equals(player.getFaction())) {
                taken.add(parts[1]);
            } else {
                remaining.add(copy);
            }
        }
        game.setStoredValue(DUMMY_RIDER_COPIES, String.join(";", remaining));
        return taken;
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

    public static int cumulativeSupportInfantry(int distanceFromHome) {
        return Math.max(0, Math.min(4, distanceFromHome));
    }
}

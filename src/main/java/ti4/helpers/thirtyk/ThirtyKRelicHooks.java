package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

@UtilityClass
public class ThirtyKRelicHooks {

    public static final String TECH_SUPPORT_KEY = "thirtyKTechSupport";
    public static final String CHRONO_STASIS_KEY = "thirtyKChronoStasis";
    public static final String SOCIAL_DISSONANCE_KEY = "thirtyKSocialDissonance";
    private static final int SOCIAL_DISSONANCE_CAPACITY = 3;

    public static void onLeadershipGain(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null || !player.hasRelicReady(ThirtyKRelics.RAZZLING_MESSAGE)) return;
        player.addExhaustedRelic(ThirtyKRelics.RAZZLING_MESSAGE);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", fresh recruits rally to the _Razzling Message_ (exhausted): gain 1 additional command token with the **Leadership** gain buttons.");
    }

    public static int applyProductionDiscount(Player player, int cost) {
        if (!ThirtyKCards.inUse(player == null ? null : player.getGame())) return cost;
        if (player == null || !player.hasRelic(ThirtyKRelics.SOVEREIGN_REFUGEES)) return cost;
        boolean producedFlagship = player.getCurrentProducedUnits().entrySet().stream()
                .anyMatch(e -> "fs".equals(e.getKey().split("_")[0]) && e.getValue() > 0);
        return producedFlagship ? Math.max(0, cost - 2) : cost;
    }

    public static void afterProduction(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null || !player.hasRelic(ThirtyKRelics.OVERPOPULATION_MANAGEMENT)) return;
        List<String> planets = infantryProductionPlanets(player.getCurrentProducedUnits());
        if (planets.isEmpty()) return;
        List<Button> buttons = new ArrayList<>();
        for (String planet : planets.subList(0, Math.min(12, planets.size()))) {
            String name = Helper.getPlanetRepresentation(planet, game);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "thirtyKOverpop_add_" + planet,
                    "Spend 2 Res: +4 Infantry on " + name));
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "thirtyKOverpop_remove_" + planet, "Remove 1 Infantry on " + name));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", the barracks are overflowing. For each planet below, choose with _Overpopulation Management_: spend 2 resources to add 4 infantry, or remove 1 infantry.",
                buttons);
    }

    static List<String> infantryProductionPlanets(Map<String, Integer> produced) {
        Set<String> planets = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : produced.entrySet()) {
            String[] parts = entry.getKey().split("_");
            if (parts.length < 3 || !"gf".equals(parts[0]) || entry.getValue() < 1) continue;
            if (!"space".equalsIgnoreCase(parts[2])) planets.add(parts[2]);
        }
        return new ArrayList<>(planets);
    }

    public static void onTransactionAccepted(Game game, Player p1, Player p2) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || p1 == null || p2 == null) return;
        resolveTechSupport(game, p1, p2);
        resolveTechSupport(game, p2, p1);
        offerSocialDissonanceToken(game, p1, p2);
        offerSocialDissonanceToken(game, p2, p1);
    }

    public static void rememberTechSupport(Game game, Player holder, Player target) {
        game.setStoredValue(TECH_SUPPORT_KEY, techSupportMarker(holder, target));
    }

    static String techSupportMarker(Player holder, Player target) {
        return holder.getFaction() + "+" + target.getFaction();
    }

    static void clearTechSupport(Game game) {
        game.removeStoredValue(TECH_SUPPORT_KEY);
    }

    private static void resolveTechSupport(Game game, Player holder, Player target) {
        if (!techSupportMarker(holder, target).equals(game.getStoredValue(TECH_SUPPORT_KEY))) return;
        game.removeStoredValue(TECH_SUPPORT_KEY);
        List<Button> buttons = ThirtyKTechSupport.noPrerequisiteTechButtons(game, target);
        if (buttons.isEmpty()) return;
        List<Button> withDecline = new ArrayList<>(buttons);
        withDecline.add(Buttons.red(target.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged()
                        + ", the deal is done and the technicians deliver: through the _Tech Support Hotline_ you may research 1 technology with no prerequisites.",
                withDecline);
    }

    private static void offerSocialDissonanceToken(Game game, Player holder, Player giver) {
        if (!holder.hasRelic(ThirtyKRelics.SOCIAL_DISSONANCE) || giver == holder) return;
        if (socialDissonanceTokens(game).contains(giver.getFaction())) return;
        Button give = Buttons.green(
                giver.factionButtonChecker() + "thirtyKSocialGive_" + holder.getFaction(),
                "Give an Ownership Token to Social Dissonance");
        MessageHelper.sendMessageToChannelWithButtons(
                giver.getCardsInfoThread(),
                giver.getRepresentationUnfogged()
                        + ", you may place an ownership token on _Social Dissonance_ for this transaction (+1 commodity value while it stays there).",
                List.of(give, Buttons.red(giver.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    public static List<String> socialDissonanceTokens(Game game) {
        String stored = game.getStoredValue(SOCIAL_DISSONANCE_KEY);
        if (stored.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(stored.split(";")));
    }

    public static void setSocialDissonanceTokens(Game game, List<String> tokens) {
        if (tokens.isEmpty()) {
            game.removeStoredValue(SOCIAL_DISSONANCE_KEY);
        } else {
            game.setStoredValue(SOCIAL_DISSONANCE_KEY, String.join(";", tokens));
        }
    }

    public static boolean socialDissonanceOverCapacity(List<String> tokens) {
        return tokens.size() > SOCIAL_DISSONANCE_CAPACITY;
    }

    public static int commodityBonus(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return 0;
        if (game == null || player == null) return 0;
        List<String> tokens = socialDissonanceTokens(game);
        if (tokens.isEmpty()) return 0;
        boolean anyHolder =
                game.getPlayers().values().stream().anyMatch(p -> p.hasRelic(ThirtyKRelics.SOCIAL_DISSONANCE));
        if (!anyHolder) return 0;
        if (player.hasRelic(ThirtyKRelics.SOCIAL_DISSONANCE)) return tokens.size();
        return tokens.contains(player.getFaction()) ? 1 : 0;
    }

    public static boolean deepNetworksAllows(Game game, Player player, Player other) {
        return neighbourOfNeighbourOnTurn(game, player, other) || neighbourOfNeighbourOnTurn(game, other, player);
    }

    private static boolean neighbourOfNeighbourOnTurn(Game game, Player holder, Player other) {
        if (!holder.hasRelic(ThirtyKRelics.DEEP_NETWORKS) || game.getActivePlayer() != holder) return false;
        return holder.getNeighbouringPlayers(false).stream()
                .anyMatch(neighbour -> neighbour.getNeighbouringPlayers(false).contains(other));
    }

    public static void rememberChronoStasis(Game game, Player owner, String planet) {
        List<String> entries = chronoEntries(game);
        entries.add(owner.getFaction() + "+" + planet);
        game.setStoredValue(CHRONO_STASIS_KEY, String.join(";", entries));
    }

    private static List<String> chronoEntries(Game game) {
        String stored = game.getStoredValue(CHRONO_STASIS_KEY);
        if (stored.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(stored.split(";")));
    }

    public static void onTurnStart(Game game, Player player) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || player == null) return;
        List<String> entries = chronoEntries(game);
        List<String> remaining = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.split("\\+", 2);
            if (parts.length != 2 || !parts[0].equals(player.getFaction())) {
                remaining.add(entry);
                continue;
            }
            offerChronoReturn(game, player, parts[1]);
        }
        if (remaining.size() == entries.size()) return;
        if (remaining.isEmpty()) {
            game.removeStoredValue(CHRONO_STASIS_KEY);
        } else {
            game.setStoredValue(CHRONO_STASIS_KEY, String.join(";", remaining));
        }
    }

    private static void offerChronoReturn(Game game, Player owner, String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null) return;
        String name = Helper.getPlanetRepresentation(planetName, game);
        if (blockedForChronoReturn(game, owner, planet)) {
            MessageHelper.sendMessageToChannel(
                    owner.getCardsInfoThread(),
                    owner.getRepresentationUnfogged() + ", your infantry steps out of _Chrono Stasis_ to find " + name
                            + " occupied: another player has ground forces or structures there, so it cannot return.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCardsInfoThread(),
                owner.getRepresentationUnfogged()
                        + ", the _Chrono Stasis_ field is fading: you may place your infantry back on " + name + ".",
                List.of(
                        Buttons.green(
                                owner.factionButtonChecker() + "thirtyKChronoReturn_" + planetName, "Place It Back"),
                        Buttons.red(owner.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    public static boolean blockedForChronoReturn(Game game, Player owner, Planet planet) {
        return game.getPlayers().values().stream()
                .filter(other -> other != owner)
                .anyMatch(other -> planet.hasGroundForces(other) || planet.hasStructures(other));
    }

    // TODO: fog - the prompt tells the holder that a nearby planet was exhausted, even one they cannot see.
    public static void onPlanetExhausted(Game game, Player owner, String planetName) {
        if (!ThirtyKCards.inUse(game)) return;
        if (game == null || planetName == null) return;
        if (game.getRealPlayers().stream().noneMatch(p -> p.hasRelicReady(ThirtyKRelics.DEFIANT_PROPAGANDA))) return;
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (tile == null || planet == null || infantryOwners(game, planet).isEmpty()) return;
        for (Player holder : game.getRealPlayers()) {
            if (!holder.hasRelicReady(ThirtyKRelics.DEFIANT_PROPAGANDA)) continue;
            if (!hasUnitsInOrAdjacent(game, holder, tile)) continue;
            MessageHelper.sendMessageToChannelWithButtons(
                    holder.getCardsInfoThread(),
                    holder.getRepresentationUnfogged() + ", " + Helper.getPlanetRepresentation(planetName, game)
                            + " was just exhausted. You may exhaust _Defiant Propaganda_ to destroy up to 2 infantry there.",
                    List.of(
                            Buttons.green(
                                    holder.factionButtonChecker() + "thirtyKDefiant_" + planetName,
                                    "Use Defiant Propaganda"),
                            Buttons.red(holder.factionButtonChecker() + "deleteButtons", "Decline")));
        }
    }

    public static List<Player> infantryOwners(Game game, Planet planet) {
        return game.getPlayers().values().stream()
                .filter(p -> p.getColor() != null && planet.getUnitCount(UnitType.Infantry, p) > 0)
                .toList();
    }

    private static boolean hasUnitsInOrAdjacent(Game game, Player player, Tile tile) {
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true).stream()
                .map(game::getTileByPosition)
                .anyMatch(t -> t != null && FoWHelper.playerHasUnitsInSystem(player, t));
    }
}

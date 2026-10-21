package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.model.RelicModel;

@UtilityClass
public class ThirtyKRelics {

    public static final String PLACEBOTRON = "placebotron30k";
    public static final String REDISCOVERY = "rediscovery30k";
    public static final String RANSOM_CANNON = "ransomcannon30k";
    public static final String RAZZLING_MESSAGE = "razzlingmessage30k";
    public static final String DEADLINE_DOCUMENTATION = "deadlinedocumentation30k";
    public static final String DEFIANT_PROPAGANDA = "defiantpropaganda30k";
    public static final String TECH_SUPPORT_HOTLINE = "techsupporthotline30k";
    public static final String CHRONO_STASIS = "chronostasis30k";
    public static final String SPY_REPORTS = "spyreportsjmsc";
    public static final String OVERPOPULATION_MANAGEMENT = "overpopulationmanagementjmsc";
    public static final String SOCIAL_DISSONANCE = "socialdissonancejmsc";
    public static final String DEEP_NETWORKS = "deepnetworksjmsc";
    public static final String SOVEREIGN_REFUGEES = "sovereignrefugeesjmsc";
    public static final String SOVEREIGN_REFUGEES_USED_KEY = "thirtyKSovereignRefugees";

    private static final Set<String> OWN_COMPONENT_ACTIONS = Set.of(
            PLACEBOTRON,
            REDISCOVERY,
            RANSOM_CANNON,
            DEADLINE_DOCUMENTATION,
            TECH_SUPPORT_HOTLINE,
            CHRONO_STASIS,
            SPY_REPORTS,
            DEEP_NETWORKS,
            SOVEREIGN_REFUGEES);

    public static boolean handlesComponentAction(String relicID) {
        return OWN_COMPONENT_ACTIONS.contains(relicID);
    }

    public static Optional<Button> componentActionButton(Game game, Player player, String relicID, String idPrefix) {
        RelicModel relic = Mapper.getRelic(relicID);
        if (relic == null) return Optional.empty();
        String id = idPrefix + "relic_" + relicID;
        boolean ready = player.hasRelicReady(relicID);
        if (PLACEBOTRON.equals(relicID)) {
            return ready ? Optional.of(Buttons.blue(id, "Use " + relic.getName())) : Optional.empty();
        }
        if (SOVEREIGN_REFUGEES.equals(relicID)) {
            if (sovereignRefugeesUsed(game, player)
                    || eliminatedPlayers(game, player).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Buttons.blue(id, "Use " + relic.getName()));
        }
        if (!ready || !hasAnyTarget(game, player, relicID)) return Optional.empty();
        return Optional.of(Buttons.blue(id, "Exhaust " + relic.getName()));
    }

    private static boolean hasAnyTarget(Game game, Player player, String relicID) {
        return switch (relicID) {
            case REDISCOVERY -> !rediscoveryPlanetButtons(game, player).isEmpty();
            case RANSOM_CANNON -> !ransomCannonTargets(game, player).isEmpty();
            case TECH_SUPPORT_HOTLINE -> !techSupportTargets(game, player).isEmpty();
            case CHRONO_STASIS -> !chronoStasisTargets(game, player).isEmpty();
            case SPY_REPORTS -> !spyReportTargets(game, player).isEmpty();
            case DEEP_NETWORKS -> !deepNetworkPlanets(game, player).isEmpty();
            default -> true;
        };
    }

    public static void resolveComponentAction(Game game, Player player, ButtonInteractionEvent event, String relicID) {
        if (!SOVEREIGN_REFUGEES.equals(relicID) && !player.hasRelicReady(relicID)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), notReadyMessage(relicID));
            return;
        }
        String prefix = player.factionButtonChecker();
        switch (relicID) {
            case PLACEBOTRON -> offerPlacebotron(game, player, event);
            case REDISCOVERY ->
                NewStuffHelper.sendPaginated(
                        event.getMessageChannel(),
                        rediscoveryMessage(player),
                        rediscoveryPlanetButtons(game, player),
                        cancel(),
                        prefix + "thirtyKRediscoverPlanet_");
            case RANSOM_CANNON -> {
                List<Button> buttons = ransomCannonTargets(game, player).stream()
                        .map(p -> FoWHelper.fogSafeTargetButton(
                                prefix + "thirtyKRansomTarget_" + p.getFaction(), "red", p, player))
                        .toList();
                send(
                        event,
                        player.getRepresentationUnfogged() + ", choose whose flagship _Ransom Cannon_ targets.",
                        buttons);
            }
            case DEADLINE_DOCUMENTATION -> {
                List<Button> buttons = game.getRealPlayers().stream()
                        .map(p -> FoWHelper.fogSafeTargetButton(
                                prefix + "thirtyKDeadlineTarget_" + p.getFaction(), "gray", p, player))
                        .toList();
                send(
                        event,
                        player.getRepresentationUnfogged() + ", choose the player for _Deadline Documentation_.",
                        buttons);
            }
            case TECH_SUPPORT_HOTLINE -> {
                List<Button> buttons = techSupportTargets(game, player).stream()
                        .map(p -> FoWHelper.fogSafeTargetButton(
                                prefix + "thirtyKTechSupportTarget_" + p.getFaction(), "green", p, player))
                        .toList();
                send(
                        event,
                        player.getRepresentationUnfogged() + ", choose the player for _Tech Support Hotline_.",
                        buttons);
            }
            case CHRONO_STASIS -> {
                List<Button> buttons = chronoStasisTargets(game, player).stream()
                        .map(p -> FoWHelper.fogSafeTargetButton(
                                prefix + "thirtyKChronoOwner_" + p.getFaction(), "gray", p, player))
                        .toList();
                send(
                        event,
                        player.getRepresentationUnfogged()
                                + ", choose whose infantry _Chrono Stasis_ removes from a planet.",
                        buttons);
            }
            case SPY_REPORTS -> {
                List<Button> buttons = spyReportTargets(game, player).stream()
                        .map(p -> FoWHelper.fogSafeTargetButton(
                                prefix + "thirtyKSpyTarget_" + p.getFaction(), "gray", p, player))
                        .toList();
                send(event, player.getRepresentationUnfogged() + ", choose the player for _Spy Reports_.", buttons);
            }
            case DEEP_NETWORKS ->
                NewStuffHelper.sendPaginated(
                        event.getMessageChannel(),
                        deepNetworksMessage(player),
                        deepNetworkButtons(game, player),
                        cancel(),
                        prefix + "thirtyKDeepNet_");
            case SOVEREIGN_REFUGEES -> {
                List<Button> buttons = eliminatedPlayers(game, player).stream()
                        .map(p -> FoWHelper.fogSafeTargetButton(
                                prefix + "thirtyKSovereignTarget_" + p.getFaction(), "gray", p, player))
                        .toList();
                send(
                        event,
                        player.getRepresentationUnfogged()
                                + ", choose the eliminated faction whose flagship _Sovereign Refugees_ gives you access to.",
                        buttons);
            }
            default -> {}
        }
    }

    public static void resolveRelicGained(
            GenericInteractionCreateEvent event, Game game, Player player, String relicID) {
        if (!ThirtyKCards.isRelic(relicID)) return;
        if (SOVEREIGN_REFUGEES.equals(relicID)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", refugee shipwrights have joined your docks: _Sovereign Refugees_ automatically reduces the cost of each flagship you produce by 2.");
        }
    }

    private static void offerPlacebotron(Game game, Player player, ButtonInteractionEvent event) {
        String prefix = player.factionButtonChecker();
        if (!player.hasRelicReady(PLACEBOTRON)) return;
        List<Button> buttons = List.of(
                Buttons.gray(prefix + "thirtyKPlacebotron_exhaust", "Exhaust (no effect)"),
                Buttons.green(prefix + "thirtyKPlacebotron_purge", "Purge and Draw a Relic"));
        send(event, player.getRepresentationUnfogged() + ", how do you use _Placebotron_?", buttons);
    }

    public static boolean exhaustIfReady(Game game, Player player, ButtonInteractionEvent event, String relicID) {
        if (!player.hasRelicReady(relicID)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), notReadyMessage(relicID));
            ButtonHelper.deleteMessage(event);
            return false;
        }
        exhaust(game, player, event, relicID);
        return true;
    }

    private static String notReadyMessage(String relicID) {
        RelicModel relic = Mapper.getRelic(relicID);
        String name = relic == null ? relicID : relic.getName();
        return "_" + name + "_ is not ready, so it cannot be used.";
    }

    public static void exhaust(Game game, Player player, GenericInteractionCreateEvent event, String relicID) {
        player.addExhaustedRelic(relicID);
        announce(game, player, event, relicID, "exhausted");
    }

    public static void purge(Game game, Player player, GenericInteractionCreateEvent event, String relicID) {
        player.removeRelic(relicID);
        player.removeExhaustedRelic(relicID);
        announce(game, player, event, relicID, "purged");
    }

    public static void addActionSummary(Game game, Player player, String relicID) {
        RelicModel relic = Mapper.getRelic(relicID);
        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Used the "
                        + (relic == null ? relicID : relic.getName()) + " relic.");
    }

    private static void announce(
            Game game, Player player, GenericInteractionCreateEvent event, String relicID, String verb) {
        RelicModel relic = Mapper.getRelic(relicID);
        addActionSummary(game, player, relicID);
        MessageHelper.sendMessageToChannelWithEmbed(
                event == null ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + " " + announcementLine(relicID, verb, relic.getName()),
                relic.getRepresentationEmbed(false, true));
    }

    private static String announcementLine(String relicID, String verb, String name) {
        if ("purged".equals(verb)) {
            return PLACEBOTRON.equals(relicID)
                    ? "cracked open the _Placebotron_ and found something real inside (purged to draw 1 relic)."
                    : "purged _" + name + "_.";
        }
        return switch (relicID) {
            case PLACEBOTRON -> "switched on the _Placebotron_. It hums and does nothing at all (exhausted).";
            case REDISCOVERY -> "dusted off forgotten survey logs with _Rediscovery_ (exhausted).";
            case RANSOM_CANNON -> "trained the _Ransom Cannon_ on an enemy flagship (exhausted).";
            case DEADLINE_DOCUMENTATION -> "stamped the _Deadline Documentation_ and sent it off (exhausted).";
            case DEFIANT_PROPAGANDA -> "dropped _Defiant Propaganda_ over an occupied world (exhausted).";
            case TECH_SUPPORT_HOTLINE -> "dialled the _Tech Support Hotline_ (exhausted).";
            case CHRONO_STASIS -> "powered up the _Chrono Stasis_ field (exhausted).";
            case SPY_REPORTS -> "sent agents into the field with _Spy Reports_ (exhausted).";
            case DEEP_NETWORKS -> "woke a sleeper cell through _Deep Networks_ (exhausted).";
            default -> verb + " _" + name + "_.";
        };
    }

    private static void send(ButtonInteractionEvent event, String message, List<Button> buttons) {
        List<Button> withCancel = new ArrayList<>(buttons);
        withCancel.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, withCancel);
    }

    public static List<Button> cancel() {
        return List.of(Buttons.red("deleteButtons", "Cancel"));
    }

    public static String rediscoveryMessage(Player player) {
        return player.getRepresentationUnfogged() + ", choose the planet for _Rediscovery_.";
    }

    public static String deepNetworksMessage(Player player) {
        return player.getRepresentationUnfogged() + ", choose the planet where _Deep Networks_ places 1 infantry.";
    }

    public static List<Button> rediscoveryPlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ThirtyKCards.visibleTiles(game, player)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (rediscoveryOptions(game, planet).isEmpty()) continue;
                String planetName = planet.getName();
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "thirtyKRediscoverPlanet_" + planetName,
                        Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        return buttons;
    }

    public static List<String> rediscoveryOptions(Game game, Planet planet) {
        Set<String> cards = new LinkedHashSet<>();
        for (String trait : planet.getPlanetTypes()) {
            for (String card : game.getExploreDiscard(trait)) {
                ExploreModel model = Mapper.getExplore(card);
                if (model != null) cards.add(card);
            }
        }
        return new ArrayList<>(cards);
    }

    public static List<Player> ransomCannonTargets(Game game, Player player) {
        Set<String> reach = new LinkedHashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!FoWHelper.playerHasShipsInSystem(player, tile)) continue;
            reach.addAll(FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false));
        }
        List<Player> targets = new ArrayList<>();
        for (Player other : game.getRealPlayers()) {
            if (other == player) continue;
            boolean flagshipInReach = reach.stream()
                    .map(game::getTileByPosition)
                    .anyMatch(tile ->
                            tile != null && tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, other) > 0);
            if (flagshipInReach) targets.add(other);
        }
        return targets;
    }

    public static List<Player> techSupportTargets(Game game, Player player) {
        return game.getRealPlayers().stream()
                .filter(p -> p != player && ThirtyKDistance.homesMoreThanFiveApart(game, player, p))
                .toList();
    }

    public static List<Player> chronoStasisTargets(Game game, Player player) {
        List<Tile> tiles = ThirtyKCards.visibleTiles(game, player);
        return game.getRealPlayers().stream()
                .filter(owner -> !infantryPlanets(tiles, owner).isEmpty())
                .toList();
    }

    public static List<Planet> infantryPlanets(List<Tile> tiles, Player owner) {
        List<Planet> planets = new ArrayList<>();
        for (Tile tile : tiles) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitCount(UnitType.Infantry, owner) > 0) planets.add(planet);
            }
        }
        return planets;
    }

    public static List<Player> spyReportTargets(Game game, Player player) {
        return game.getRealPlayers().stream()
                .filter(p -> p != player
                        && !player.isNeighboursWith(p)
                        && !p.getActionCards().isEmpty())
                .toList();
    }

    public static List<Planet> deepNetworkPlanets(Game game, Player player) {
        Predicate<String> farFromHome = ThirtyKSecrets.farFromHome(game, player);
        List<Planet> planets = new ArrayList<>();
        for (Tile tile : ThirtyKCards.visibleTiles(game, player)) {
            if (tile.isHomeSystem(game) || !farFromHome.test(tile.getPosition())) continue;
            if (containsOtherPlayersUnits(game, player, tile)) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!planet.isHomePlanet(game) && !planet.isSpaceStation(game)) planets.add(planet);
            }
        }
        return planets;
    }

    private static boolean containsOtherPlayersUnits(Game game, Player player, Tile tile) {
        return game.getPlayers().values().stream()
                .filter(other -> other != player)
                .anyMatch(other -> tile.getUnitHolders().values().stream()
                        .anyMatch(holder -> holder.getUnitKeysForPlayer(other).stream()
                                .anyMatch(key -> holder.getUnitCount(key) > 0)));
    }

    public static List<Button> deepNetworkButtons(Game game, Player player) {
        return deepNetworkPlanets(game, player).stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "thirtyKDeepNet_" + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
    }

    public static List<Player> eliminatedPlayers(Game game, Player player) {
        return game.getRealAndEliminatedPlayers().stream()
                .filter(p -> p != player && p.isEliminated())
                .toList();
    }

    public static boolean sovereignRefugeesUsed(Game game, Player player) {
        return !game.getStoredValue(SOVEREIGN_REFUGEES_USED_KEY + player.getFaction())
                .isBlank();
    }

    public static UnitHolder firstHomePlanet(Game game, Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return null;
        return home.getPlanetUnitHolders().stream()
                .filter(planet -> player.getPlanets().contains(planet.getName()))
                .findFirst()
                .orElse(null);
    }
}

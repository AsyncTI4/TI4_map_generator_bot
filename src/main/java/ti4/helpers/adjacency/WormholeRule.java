package ti4.helpers.adjacency;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.utils.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.model.WormholeModel.Wormhole;

public final class WormholeRule implements AdjacencyRule {

    public static Set<String> connectionsFrom(Game game, Player player, String position) {
        Set<String> adjacent = new HashSet<>();
        new WormholeRule().from(new AdjacencyContext(game, player, AdjacencyPurpose.EFFECTS), position, adjacent);
        return adjacent;
    }

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Tile tile = context.getGame().getTileByPosition(position);
        if (tile == null || tile.getTileID() == null) return;
        State state = state(context);
        state.addGhostAgentLinks(tile, adjacent);
        Set<Wormhole> departures = state.departures(tile);
        if (departures.isEmpty()) return;
        for (Tile target : context.getGame().getTileMap().values()) {
            if (state.connects(departures, target)) {
                adjacent.add(target.getPosition());
            }
        }
    }

    @Override
    public void into(AdjacencyContext context, String position, Set<String> sources) {
        Tile target = context.getGame().getTileByPosition(position);
        if (target == null) return;
        State state = state(context);
        if (target.getTileID() != null) state.addGhostAgentLinks(target, sources);
        for (Tile source : context.getGame().getTileMap().values()) {
            if (source.getTileID() == null) continue;
            Set<Wormhole> departures = state.departures(source);
            if (!departures.isEmpty() && state.connects(departures, target)) {
                sources.add(source.getPosition());
            }
        }
    }

    private static State state(AdjacencyContext context) {
        return context.memo("wormholes", () -> new State(context));
    }

    private static boolean hasUncontrolledLegendaryPlanet(Player player, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> planet.isLegendary() && !player.getPlanets().contains(planet.getName()));
    }

    private static boolean isSevered(Tile tile) {
        return tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_SEVERED);
    }

    private static final class State {
        private final Game game;
        private final boolean movement;
        private final List<String> creussFlagshipColors;
        private final boolean alphaBetaLinked;
        private final boolean sundered;
        private final boolean ghostWithoutEpsilon;
        private final Player gateFolder;
        private final boolean travelBanBlocksAlphaBeta;
        private final String ghostAgentSystem;
        private final Map<String, EnumSet<Wormhole>> wormholes = new HashMap<>();
        private final Map<String, Set<Wormhole>> departures = new HashMap<>();
        private final Map<String, Set<Wormhole>> arrivals = new HashMap<>();

        private State(AdjacencyContext context) {
            game = context.getGame();
            Player player = context.getPlayer();
            movement = context.isMovement();
            creussFlagshipColors = Tile.creussFlagshipColors(game);
            alphaBetaLinked = player != null && player.hasAbility("quantum_entanglement")
                    || ButtonHelper.isLawInPlay(game, "wormhole_recon")
                    || ButtonHelper.isLawInPlay(game, "absol_recon");
            sundered = context.isActivePlayer()
                    && context.hasActivatedSystem()
                    && movement
                    && player.hasAbility("sundered");
            ghostWithoutEpsilon = player != null
                    && "ghost".equals(player.getFaction())
                    && game.getPlayerFromColorOrFaction("crimson") != null;
            gateFolder = context.hasActivatedSystem() && context.isActivePlayer() ? player : null;
            travelBanBlocksAlphaBeta = !alphaBetaLinked && movement && ButtonHelper.isLawInPlay(game, "travel_ban");
            String ghostAgentActive = game.getStoredValue("ghostagent_active");
            ghostAgentSystem = !StringUtils.isEmpty(ghostAgentActive) && ghostAgentActive.equals(game.getActiveSystem())
                    ? ghostAgentActive
                    : null;
        }

        private Set<Wormhole> departures(Tile tile) {
            return departures.computeIfAbsent(tile.getPosition(), position -> computeDepartures(tile));
        }

        private Set<Wormhole> arrivals(Tile tile) {
            return arrivals.computeIfAbsent(tile.getPosition(), position -> computeArrivals(tile));
        }

        private EnumSet<Wormhole> wormholes(Tile tile) {
            return wormholes.computeIfAbsent(tile.getPosition(), position -> computeWormholes(tile));
        }

        private boolean connects(Set<Wormhole> departures, Tile target) {
            return !Collections.disjoint(departures, arrivals(target));
        }

        private void addGhostAgentLinks(Tile tile, Set<String> linked) {
            if (ghostAgentSystem == null) return;
            if (tile.getPosition().equals(ghostAgentSystem)) {
                for (Tile other : game.getTileMap().values()) {
                    if (other.getTileID() != null && !wormholes(other).isEmpty()) {
                        linked.add(other.getPosition());
                    }
                }
            } else if (!wormholes(tile).isEmpty()) {
                linked.add(ghostAgentSystem);
            }
        }

        private EnumSet<Wormhole> computeWormholes(Tile tile) {
            return withGateFolding(tile, tile.getWormholes(creussFlagshipColors));
        }

        private EnumSet<Wormhole> withGateFolding(Tile tile, Collection<Wormhole> printed) {
            EnumSet<Wormhole> wormholes = EnumSet.noneOf(Wormhole.class);
            wormholes.addAll(printed);
            if (isGateFolded(tile)) {
                wormholes.add(Wormhole.ALPHA);
                wormholes.add(Wormhole.BETA);
            }
            return wormholes;
        }

        private boolean isGateFolded(Tile tile) {
            if (gateFolder == null) return false;
            boolean mecatol = tile.getUnitHolders().containsKey("mrte")
                    || tile.getUnitHolders().containsKey("mr");
            boolean controlsMecatol = gateFolder.getPlanets().contains("mrte")
                    || gateFolder.getPlanets().contains("mr");
            return gateFolder.hasTech("lgf") && mecatol && !controlsMecatol
                    || (gateFolder.hasTech("lgf_br") || gateFolder.hasTech("tf-lazaxgatefolding"))
                            && hasUncontrolledLegendaryPlanet(gateFolder, tile);
        }

        private Set<Wormhole> computeArrivals(Tile tile) {
            Set<Wormhole> arriving = withGateFolding(tile, tile.getArrivalWormholes(creussFlagshipColors));
            if (movement && isSevered(tile)) {
                arriving.retainAll(linkedOr(EnumSet.noneOf(Wormhole.class)));
            }
            return arriving;
        }

        private Set<Wormhole> computeDepartures(Tile tile) {
            Set<Wormhole> departing = withGateFolding(tile, tile.getDepartureWormholes(creussFlagshipColors));
            if (sundered) {
                departing.retainAll(linkedOr(EnumSet.of(Wormhole.EPSILON)));
            }
            if (movement && isSevered(tile)) {
                departing.retainAll(linkedOr(EnumSet.noneOf(Wormhole.class)));
            }
            if (ghostWithoutEpsilon) {
                departing.remove(Wormhole.EPSILON);
            }
            if (alphaBetaLinked) {
                if (departing.contains(Wormhole.ALPHA)) {
                    departing.add(Wormhole.BETA);
                } else if (departing.contains(Wormhole.BETA)) {
                    departing.add(Wormhole.ALPHA);
                }
            }
            if (travelBanBlocksAlphaBeta) {
                departing.remove(Wormhole.ALPHA);
                departing.remove(Wormhole.BETA);
            }
            return departing;
        }

        private Set<Wormhole> linkedOr(Set<Wormhole> keepers) {
            if (alphaBetaLinked) {
                keepers.add(Wormhole.ALPHA);
                keepers.add(Wormhole.BETA);
            }
            return keepers;
        }
    }
}

package ti4.helpers.adjacency;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import ti4.discord.interactions.buttons.handlers.actioncards.bluereverie.FrameShiftBrActionCardHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;

final class IngressEgressRule implements AdjacencyRule {

    private enum Feature {
        INGRESS,
        EGRESS,
        BREACH,
        SCAR
    }

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Tile tile = context.getGame().getTileByPosition(position);
        if (tile == null) return;
        State state = state(context);
        Set<Feature> links = state.links(tile);
        if (links.isEmpty()) return;
        for (Tile target : context.getGame().getTileMap().values()) {
            if (!Collections.disjoint(links, state.accepts(target))) {
                adjacent.add(target.getPosition());
            }
        }
    }

    @Override
    public void into(AdjacencyContext context, String position, Set<String> sources) {
        Tile target = context.getGame().getTileByPosition(position);
        if (target == null) return;
        State state = state(context);
        Set<Feature> accepts = state.accepts(target);
        if (accepts.isEmpty()) return;
        for (Tile source : context.getGame().getTileMap().values()) {
            if (!Collections.disjoint(state.links(source), accepts)) {
                sources.add(source.getPosition());
            }
        }
    }

    private static State state(AdjacencyContext context) {
        return context.memo("ingressEgress", () -> new State(context));
    }

    private static boolean hasToken(Tile tile, String token) {
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder.getTokenList().contains(token)) return true;
        }
        return false;
    }

    private static final class State {
        private final Game game;
        private final Player player;
        private final boolean scarAsIngress;
        private final boolean wormholeAsIngress;
        private final boolean anomalyAsIngress;
        private final Map<String, Set<Feature>> links = new HashMap<>();
        private final Map<String, Set<Feature>> accepts = new HashMap<>();

        private State(AdjacencyContext context) {
            game = context.getGame();
            player = context.getPlayer();
            scarAsIngress = player != null && player.hasUnlockedBreakthrough("nivynbt");
            boolean frameShift = FrameShiftBrActionCardHandler.isActive(game, player);
            wormholeAsIngress =
                    player != null && (player.hasTech("tf-fraactalspikedrives") || player.hasTech("fsd")) || frameShift;
            anomalyAsIngress = player != null && game.playerHasLeaderUnlockedOrAlliance(player, "nataucommander");
        }

        private Set<Feature> links(Tile tile) {
            return links.computeIfAbsent(tile.getPosition(), position -> computeLinks(tile));
        }

        private Set<Feature> accepts(Tile tile) {
            return accepts.computeIfAbsent(tile.getPosition(), position -> computeAccepts(tile));
        }

        private Set<Feature> computeLinks(Tile tile) {
            Set<Feature> features = EnumSet.noneOf(Feature.class);
            if (actsAsEgress(tile)) features.add(Feature.INGRESS);
            if (actsAsIngress(tile)) features.add(Feature.EGRESS);
            if (game.isCosmicPhenomenaeMode() && tile.isScar(game)) features.add(Feature.SCAR);
            if (hasToken(tile, Constants.TOKEN_BREACH_ACTIVE)) features.add(Feature.BREACH);
            return features;
        }

        private Set<Feature> computeAccepts(Tile tile) {
            Set<Feature> features = EnumSet.noneOf(Feature.class);
            if (actsAsEgress(tile)) features.add(Feature.EGRESS);
            if (actsAsIngress(tile)) features.add(Feature.INGRESS);
            if (game.isCosmicPhenomenaeMode() && tile.isScar(game)) features.add(Feature.SCAR);
            if (hasToken(tile, Constants.TOKEN_BREACH_ACTIVE)) features.add(Feature.BREACH);
            return features;
        }

        private boolean actsAsEgress(Tile tile) {
            return tile.hasEgress() || hasToken(tile, Constants.TOKEN_EGRESS);
        }

        private boolean actsAsIngress(Tile tile) {
            return tile.hasIngress()
                    || hasToken(tile, Constants.TOKEN_INGRESS)
                    || scarAsIngress && tile.isScar(game)
                    || wormholeAsIngress && !tile.getWormholes(game).isEmpty()
                    || anomalyAsIngress && tile.isAnomaly(game, player);
        }
    }
}

package ti4.helpers.adjacency;

import java.util.Set;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.game.Tile;

final class ObsidianMirrorRule implements AdjacencyRule {

    @Override
    public void from(AdjacencyContext context, String position, Set<String> adjacent) {
        Set<String> flagshipPositions = flagshipPositions(context);
        Tile tile = context.getGame().getTileByPosition(position);
        if (flagshipPositions.isEmpty() || !isEmptyOfPlanets(tile)) return;
        if (flagshipPositions.contains(position)) {
            addPlanetlessOutsideFracture(context, adjacent, Set.of());
        } else if (!tile.isFracture()) {
            adjacent.addAll(flagshipPositions);
        }
    }

    @Override
    public void into(AdjacencyContext context, String position, Set<String> sources) {
        Set<String> flagshipPositions = flagshipPositions(context);
        Tile target = context.getGame().getTileByPosition(position);
        if (flagshipPositions.isEmpty() || target == null) return;
        if (isEmptyOfPlanets(target) && !target.isFracture()) {
            sources.addAll(flagshipPositions);
        }
        if (flagshipPositions.contains(position)) {
            addPlanetlessOutsideFracture(context, sources, flagshipPositions);
        }
    }

    private static Set<String> flagshipPositions(AdjacencyContext context) {
        return context.memo(
                "obsidianMirrorFlagships",
                () -> OblivionUnitHandler.obsidianMirrorFlagshipPositions(context.getGame(), context.getPlayer()));
    }

    private static boolean isEmptyOfPlanets(Tile tile) {
        return tile != null && tile.getPlanetUnitHolders().isEmpty();
    }

    private static void addPlanetlessOutsideFracture(
            AdjacencyContext context, Set<String> positions, Set<String> excluded) {
        context.getGame().getTileMap().values().stream()
                .filter(tile -> !tile.isFracture())
                .filter(ObsidianMirrorRule::isEmptyOfPlanets)
                .map(Tile::getPosition)
                .filter(position -> !excluded.contains(position))
                .forEach(positions::add);
    }
}

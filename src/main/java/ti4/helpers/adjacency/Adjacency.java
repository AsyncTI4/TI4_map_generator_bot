package ti4.helpers.adjacency;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.service.fow.FOWPlusService;

public final class Adjacency {
    private static final List<AdjacencyRule> RULES = List.of(
            new GridRule(),
            new CustomLinkRule(),
            new WormholeRule(),
            new IngressEgressRule(),
            new BifrostBridgeRule(),
            new ChaosTurbineRule(),
            new DreamNebulaRule(),
            new SpaceDockWebwayRule(),
            new SaarMonumentRule(),
            new GheminaMonumentRule(),
            new NivynMonumentRule(),
            new GhotiCommanderRule(),
            new ObsidianMirrorRule(),
            new UydaiHeroRule(),
            new TreatedAsAdjacentRule());

    private final AdjacencyContext context;
    private final boolean includeSelf;
    private final Map<String, Set<String>> fromCache;
    private final Map<String, Set<String>> intoCache;

    private Adjacency(
            AdjacencyContext context,
            boolean includeSelf,
            Map<String, Set<String>> fromCache,
            Map<String, Set<String>> intoCache) {
        this.context = context;
        this.includeSelf = includeSelf;
        this.fromCache = fromCache;
        this.intoCache = intoCache;
    }

    public static Adjacency of(Game game, Player player) {
        return new Adjacency(
                new AdjacencyContext(game, player, AdjacencyPurpose.EFFECTS), false, new HashMap<>(), new HashMap<>());
    }

    public static Adjacency of(Game game) {
        return of(game, null);
    }

    public Adjacency forVision() {
        return forPurpose(AdjacencyPurpose.VISION);
    }

    public Adjacency forMovement() {
        return forPurpose(AdjacencyPurpose.MOVEMENT);
    }

    public Adjacency forEffects() {
        return forPurpose(AdjacencyPurpose.EFFECTS);
    }

    public Adjacency forPurpose(AdjacencyPurpose purpose) {
        return new Adjacency(
                new AdjacencyContext(context.getGame(), context.getPlayer(), purpose),
                includeSelf,
                new HashMap<>(),
                new HashMap<>());
    }

    public Adjacency withSelf() {
        return new Adjacency(context, true, fromCache, intoCache);
    }

    public Set<String> from(String position) {
        return withSelfIfAsked(position, followsDirection() ? outbound(position) : fromBothWays(position));
    }

    public Set<String> into(String position) {
        return withSelfIfAsked(position, followsDirection() ? inbound(position) : intoBothWays(position));
    }

    public Set<String> either(String position) {
        Set<String> either = fromBothWays(position);
        either.addAll(intoBothWays(position));
        return withSelfIfAsked(position, either);
    }

    public boolean isAdjacent(String position, String otherPosition) {
        if (position.equals(otherPosition)) return !isVoid(position);
        return from(position).contains(otherPosition);
    }

    private boolean followsDirection() {
        return context.isMovement() || context.getGame().usesOneWayAdjacency();
    }

    private Set<String> outbound(String position) {
        return fromCache.computeIfAbsent(position, this::computeFrom);
    }

    private Set<String> inbound(String position) {
        return intoCache.computeIfAbsent(position, this::computeInto);
    }

    private Set<String> fromBothWays(String position) {
        Set<String> adjacent = new HashSet<>(outbound(position));
        if (!isHyperlaneTile(position)) {
            adjacent.addAll(inbound(position));
        }
        return adjacent;
    }

    private Set<String> intoBothWays(String position) {
        Set<String> sources = new HashSet<>(inbound(position));
        outbound(position).stream().filter(source -> !isHyperlaneTile(source)).forEach(sources::add);
        return sources;
    }

    private boolean isHyperlaneTile(String position) {
        Tile tile = context.getGame().getTileByPosition(position);
        return tile != null
                && tile.getTileModel() != null
                && tile.getTileModel().isHyperlane();
    }

    private Set<String> computeFrom(String position) {
        Set<String> adjacent = new HashSet<>();
        if (isVoid(position)) return adjacent;
        for (AdjacencyRule rule : RULES) {
            rule.from(context, position, adjacent);
        }
        adjacent.remove(position);
        return adjacent;
    }

    private Set<String> computeInto(String position) {
        Set<String> sources = new HashSet<>();
        for (AdjacencyRule rule : RULES) {
            rule.into(context, position, sources);
        }
        sources.remove(position);
        sources.removeIf(this::isVoid);
        return sources;
    }

    private Set<String> withSelfIfAsked(String position, Set<String> cached) {
        Set<String> result = new HashSet<>(cached);
        if (includeSelf && !isVoid(position)) {
            result.add(position);
        }
        return result;
    }

    private boolean isVoid(String position) {
        return FOWPlusService.isVoid(context.getGame(), position);
    }
}

package ti4.ai.trade;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FlagshipRating;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;
import ti4.model.LeaderModel;

@UtilityClass
public class AllianceValue {

    record Row(double early, double late, double locked) {}

    static final String HUBRIS = "hubris";
    static final int LATE_ROUND = 4;
    static final double LATER_SALE_SHARE = 0.6;
    private static final String COMMANDER = "commander";
    private static final String KELERES = "keleres";
    private static final String RIDER = "Rider";
    private static final String FLAGSHIP_OBJECTIVE = "engineer_marvel";
    private static final double OWN_BACK = 0.5;
    private static final double NEIGHBOUR_GOODWILL = 0.2;
    private static final double BAD_FLAGSHIP = 0.4;
    private static final int MANY_DREADNOUGHTS = 4;
    private static final Set<String> UNIT_ABILITY_SECRETS = Set.of("mew", "ttfd", "fwp");
    private static final String MAKE_AN_EXAMPLE = "mew";
    private static final Row DEFAULT_ROW = new Row(1, 1, -1);
    private static final Map<String, Row> ROWS = Map.ofEntries(
            Map.entry("muaat", new Row(4, 1, -1)),
            Map.entry("hacan", new Row(1, 0, -1)),
            Map.entry(KELERES, new Row(1, 1, -1)),
            Map.entry("xxcha", new Row(1, 0, -1)),
            Map.entry("winnu", new Row(2, 2, 0)),
            Map.entry("titans", new Row(2, 1, 0)),
            Map.entry("nomad", new Row(4, 4, 0)),
            Map.entry("crimson", new Row(4, 3, 0)),
            Map.entry("yin", new Row(3, 2, -1)),
            Map.entry("nekro", new Row(2, 1, 0)),
            Map.entry("sardakk", new Row(1, 1, -1)),
            Map.entry("saar", new Row(0.5, 0.5, 0.5)),
            Map.entry("cabal", new Row(2, 1, -1)),
            Map.entry("mentak", new Row(0.5, 0.5, 0.5)),
            Map.entry("argent", new Row(1, 1, -1)),
            Map.entry("sol", new Row(1, 1, -1)),
            Map.entry("letnev", new Row(1, 1, -1)),
            Map.entry("deepwrought", new Row(3, 2, 0)),
            Map.entry("empyrean", new Row(1, 1, -1)),
            Map.entry("ralnel", new Row(0.5, 0.5, 0.5)),
            Map.entry("l1z1x", new Row(0.5, 0.5, 0.5)),
            Map.entry("firmament", new Row(0.5, 0.5, 0.5)),
            Map.entry("obsidian", new Row(0.5, 0.5, 0.5)),
            Map.entry("jolnar", new Row(1, 1, 0)),
            Map.entry("naalu", new Row(0.5, 0.5, 0.5)),
            Map.entry("bastion", new Row(2, 2, 0)),
            Map.entry("naaz", new Row(1, 1, -1)),
            Map.entry("ghost", new Row(0.5, 0.5, 0.5)),
            Map.entry("yssaril", new Row(1, 1, -1)),
            Map.entry("arborec", new Row(0.5, 0.5, 0.5)));

    static double received(Game game, Player seat, Player owner) {
        return received(game, seat, owner, false);
    }

    static double received(Game game, Player seat, Player owner, boolean seatPrivate) {
        if (owner == null) return NotesForTrade.OTHER_NOTE;
        if (seat.hasAbility(HUBRIS)) return 0;
        return buyValue(game, seat, owner, seatPrivate);
    }

    public static double lost(Game game, Player holder, Player owner) {
        return buyValue(game, holder, owner, false);
    }

    static double buyValue(Game game, Player buyer, Player owner, boolean buyerPrivate) {
        Optional<String> commander = commanderId(owner);
        if (commander.isEmpty()) return NotesForTrade.MIN_NOTE_WORTH;
        Row row = row(owner);
        double value = tableValue(row, game.getRound() >= LATE_ROUND, !owner.hasLeaderUnlocked(commander.get()));
        value += publicBonus(game, buyer, owner);
        if (!buyerPrivate) value += privateBonus(buyer, owner);
        return Math.max(NotesForTrade.MIN_NOTE_WORTH, value);
    }

    static double tableValue(Row row, boolean late, boolean locked) {
        double value = late ? row.late() : row.early();
        return locked ? value - Math.abs(row.locked()) : value;
    }

    static List<Row> rows() {
        return Stream.concat(ROWS.values().stream(), Stream.of(DEFAULT_ROW)).toList();
    }

    static double givenCost(Game game, Player seat, Player receiver) {
        double goodwill = TradeLegality.neighbours(game, seat, receiver) ? NEIGHBOUR_GOODWILL : 0;
        return Math.max(NotesForTrade.MIN_NOTE_WORTH, bestOtherSale(game, seat, receiver) - goodwill);
    }

    static double bestOtherSale(Game game, Player seat, Player receiver) {
        return Standings.others(game, seat).stream()
                .filter(buyer -> !Standings.same(buyer, receiver))
                .filter(buyer -> !buyer.hasAbility(HUBRIS))
                .mapToDouble(buyer -> LATER_SALE_SHARE * buyValue(game, buyer, seat, true))
                .max()
                .orElse(0);
    }

    static double ownBack() {
        return OWN_BACK;
    }

    static Row row(Player owner) {
        String faction = owner.getFaction();
        if (faction.startsWith(KELERES)) return ROWS.get(KELERES);
        return ROWS.getOrDefault(faction, DEFAULT_ROW);
    }

    private static double publicBonus(Game game, Player buyer, Player owner) {
        String faction = buyer.getFaction();
        return switch (owner.getFaction()) {
            case "xxcha" -> "nekro".equals(faction) ? 2 : 0;
            case "titans" -> Set.of("arborec", "mentak").contains(faction) || faction.startsWith(KELERES) ? 2 : 0;
            case "nomad" -> nomadBonus(game, buyer);
            case "yin" -> Set.of("deepwrought", "jolnar").contains(faction) ? -3 : 0;
            case "sardakk" -> Set.of("yin", "sol").contains(faction) ? 1 : 0;
            case "argent" -> "jolnar".equals(faction) ? 2 : 0;
            case "jolnar" -> "argent".equals(faction) ? 2 : 0;
            case "letnev" -> "mentak".equals(faction) ? 1 : 0;
            case "l1z1x" -> shipsOnBoard(game, buyer, UnitType.Dreadnought) >= MANY_DREADNOUGHTS ? 1 : 0;
            case "yssaril", "arborec" -> leadsInPoints(game, buyer) ? 1 : 0;
            default -> 0;
        };
    }

    private static double privateBonus(Player buyer, Player owner) {
        return switch (owner.getFaction()) {
            case "xxcha" -> holdsRider(buyer) ? 1 : 0;
            case "argent", "jolnar" -> holdsUnitAbilitySecret(buyer) ? 1 : 0;
            case "l1z1x" -> buyer.getSecretsUnscored().containsKey(MAKE_AN_EXAMPLE) ? 1 : 0;
            default -> 0;
        };
    }

    private static double nomadBonus(Game game, Player buyer) {
        double bonus = "yin".equals(buyer.getFaction()) ? 1 : 0;
        if (shipsOnBoard(game, buyer, UnitType.Flagship) > 0) return bonus - 4;
        if (FlagshipRating.of(buyer) <= BAD_FLAGSHIP) bonus -= 1;
        if (game.getRevealedPublicObjectives().containsKey(FLAGSHIP_OBJECTIVE)
                && !ObjectivePolicy.hasScored(game, buyer, FLAGSHIP_OBJECTIVE)) {
            bonus += 1;
        }
        return bonus;
    }

    private static int shipsOnBoard(Game game, Player buyer, UnitType type) {
        return game.getTileMap().values().stream()
                .mapToInt(tile -> BoardView.countInTile(tile, buyer, type))
                .sum();
    }

    private static boolean leadsInPoints(Game game, Player buyer) {
        int points = buyer.getTotalVictoryPoints();
        return Standings.others(game, buyer).stream().allMatch(other -> other.getTotalVictoryPoints() < points);
    }

    private static boolean holdsRider(Player buyer) {
        return buyer.getActionCards().keySet().stream()
                .map(Mapper::getActionCard)
                .anyMatch(card -> card != null && cardName(card).endsWith(RIDER));
    }

    private static String cardName(ActionCardModel card) {
        return card.getName() == null ? "" : card.getName();
    }

    private static boolean holdsUnitAbilitySecret(Player buyer) {
        return buyer.getSecretsUnscored().keySet().stream().anyMatch(UNIT_ABILITY_SECRETS::contains);
    }

    private static Optional<String> commanderId(Player owner) {
        return owner.getLeaderIDs().stream()
                .filter(id -> {
                    LeaderModel model = Mapper.getLeader(id);
                    return model != null && COMMANDER.equals(model.getType());
                })
                .findFirst();
    }
}

package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.KeepFloor;
import ti4.ai.actioncards.CardChains.Source;
import ti4.ai.eval.BoardView;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatForecast.ShipLoss;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class DirectHitCards {

    static final List<String> ALIASES = List.of("dh1", "dh2", "dh3", "dh4");
    static final String DESTROY_THEIR_GREATEST_SHIP = "dtgs";
    static final String TARGET_PREFIX = "directHitOn_";
    static final double DREADNOUGHT_COST = 4.0;
    static final double DECIDED_WIN = 0.95;
    static final double DECIDING_WIN_GAIN = 0.1;
    private static final String RESOLVE_HANDLER = "resolveDirectHit";
    private static final String SEPARATOR = "_";
    private static final double KEEP = 2.0;
    private static final double DTGS_POINTS = 1.0;

    record Hurt(Player owner, UnitType type, UnitModel model, int count) {

        boolean heavy() {
            return type == UnitType.Flagship || type == UnitType.Warsun;
        }
    }

    record Choice(Hurt hurt, double value, Plan plan) {}

    static List<AiCard> all() {
        return ALIASES.stream()
                .map(alias -> AiActionCards.combatRound(
                        alias, keepFloor(), RESOLVE_HANDLER, List.of(TARGET_PREFIX), targetStep()))
                .toList();
    }

    static boolean holdsDestroyTheirGreatestShip(Player seat) {
        return seat.getSecretsUnscored().containsKey(DESTROY_THEIR_GREATEST_SHIP);
    }

    static Optional<String> heldAlias(Player seat) {
        return seat.getActionCards().keySet().stream()
                .filter(ALIASES::contains)
                .sorted()
                .findFirst();
    }

    static List<Hurt> hurtShips(Game game, Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        List<Hurt> hurt = new ArrayList<>();
        for (Player other : game.getRealPlayersNDummies()) {
            if (other == seat || seat.getAllianceMembers().contains(other.getFaction())) continue;
            for (UnitType type : BoardView.MOVING_SHIPS) {
                UnitModel model = BoardView.model(other, type).orElse(null);
                int damaged = BoardView.damaged(space, other, type);
                if (model != null && model.getCanBeDirectHit() && damaged > 0) {
                    hurt.add(new Hurt(other, type, model, damaged));
                }
            }
        }
        return hurt;
    }

    static boolean worthPlaying(Game game, Player seat, String position, Hurt hurt) {
        if (hurt.heavy()) return true;
        Optional<ShipLoss> loss = CombatForecast.enemyShipLoss(game, seat, position, hurt.owner(), hurt.type());
        double win = loss.map(ShipLoss::winBefore).orElse(0.0);
        boolean decides = loss.map(found -> found.winAfter() - found.winBefore() >= DECIDING_WIN_GAIN)
                .orElse(false);
        boolean expensive = hurt.model().getCost() >= DREADNOUGHT_COST && win < DECIDED_WIN;
        return decides || expensive;
    }

    static double value(Game game, Player seat, String position, Hurt hurt) {
        double value = hurt.model().getCost() * TableStanding.of(game, seat, hurt.owner());
        value += CombatForecast.enemyShipLoss(game, seat, position, hurt.owner(), hurt.type())
                .map(ShipLoss::swing)
                .orElse(0.0);
        if (hurt.heavy() && holdsDestroyTheirGreatestShip(seat)) {
            value += ScoringStakes.scoreValue(game, seat, DTGS_POINTS);
        }
        return value;
    }

    static Optional<Choice> best(Game game, Player seat, String position, List<Hurt> candidates) {
        return candidates.stream()
                .filter(hurt -> worthPlaying(game, seat, position, hurt))
                .map(hurt -> choice(game, seat, position, hurt))
                .max(Comparator.comparingDouble(Choice::value)
                        .thenComparing(choice -> choice.hurt().owner().getFaction(), Comparator.reverseOrder())
                        .thenComparing(choice -> choice.hurt().type(), Comparator.reverseOrder()));
    }

    private static Choice choice(Game game, Player seat, String position, Hurt hurt) {
        double value = value(game, seat, position, hurt);
        String announcement = "on the damaged " + hurt.model().getName() + " of "
                + hurt.owner().getRepresentationNoPing() + ".";
        Plan plan = Plan.aimedAt(value, CardChains.target(suffix(position, hurt)), announcement);
        return new Choice(hurt, value, plan);
    }

    private static String suffix(String position, Hurt hurt) {
        UnitKey key = Units.getUnitKey(hurt.type(), hurt.owner().getColor());
        return position + SEPARATOR + key.unitName() + SEPARATOR + key.getColor();
    }

    private static KeepFloor keepFloor() {
        return (game, seat, alias) -> Math.max(
                ActionCardPrior.keepPrior(alias),
                holdsDestroyTheirGreatestShip(seat) ? ActionCardValue.ECONOMY_CAP : KEEP);
    }

    private static TargetStep targetStep() {
        return CardChains.chain(CardChains.last(
                TARGET_PREFIX,
                Source.OWNED,
                (context, suffix) -> offeredValue(context.game(), context.seat(), suffix),
                "destroy"));
    }

    private static double offeredValue(Game game, Player seat, String suffix) {
        String[] fields = suffix.split(SEPARATOR);
        if (fields.length != 3) return Double.NEGATIVE_INFINITY;
        Player owner = game.getPlayerFromColorOrFaction(fields[2]);
        if (owner == null || owner == seat) return Double.NEGATIVE_INFINITY;
        return BoardView.MOVING_SHIPS.stream()
                .filter(type ->
                        Units.getUnitKey(type, owner.getColor()).unitName().equals(fields[1]))
                .findFirst()
                .flatMap(type -> BoardView.model(owner, type).map(model -> new Hurt(owner, type, model, 1)))
                .map(hurt -> value(game, seat, fields[0], hurt))
                .orElse(Double.NEGATIVE_INFINITY);
    }
}

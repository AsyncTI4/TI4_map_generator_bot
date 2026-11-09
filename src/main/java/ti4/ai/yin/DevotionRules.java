package ti4.ai.yin;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class DevotionRules {

    static final String ABILITY = "devotion";
    static final String START_PREFIX = "startDevotion_";
    static final String SACRIFICE_PREFIX = "resolveDevote_";
    static final String REFRESH_PREFIX = "refreshViewOfSystem_";
    static final double MIN_GAIN = 0.5;
    static final List<UnitType> SACRIFICES = List.of(UnitType.Destroyer, UnitType.Cruiser);
    private static final String SPACE_SUFFIX = "_space";
    private static final String SPACE_HITS_PREFIX = "autoAssignSpaceHits_";
    private static final String KEY = "devotion|";
    private static final String PLAN_KEY = "devotionPlan|";
    private static final String DECLINED = "declined";
    private static final String USE = "use";
    private static final String REFRESHED = "refreshed";
    private static final String HIT_PREFIX = "hitOpponent_";
    private static final String FIELD = "~";
    private static final String DAMAGED = "damaged";

    record Trade(UnitType sacrifice, UnitType target, double gain) {}

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!context.seat().hasAbility(ABILITY)) return Optional.empty();
        List<AiPrompt> combat = Prompts.newestFirst(context.prompts()).stream()
                .filter(prompt -> prompt.source() == PromptSource.COMBAT_THREAD)
                .toList();
        return target(context, combat).or(() -> sacrifice(context, combat)).or(() -> start(context, combat));
    }

    private static Optional<AiDecision> sacrifice(AiTurnContext context, List<AiPrompt> combat) {
        for (AiPrompt prompt : combat) {
            Optional<PromptButton> chosen = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction())
                            && button.handlerId().startsWith(SACRIFICE_PREFIX))
                    .min(Comparator.comparingInt(button -> sacrificeOrder(context, button)));
            if (chosen.isPresent()) {
                return Optional.of(AiDecision.press(prompt, chosen.get(), "destroy a ship for Devotion"));
            }
        }
        return Optional.empty();
    }

    private static int sacrificeOrder(AiTurnContext context, PromptButton button) {
        String[] parts =
                StringUtils.removeStart(button.handlerId(), SACRIFICE_PREFIX).split("_");
        if (parts.length < 2) return SACRIFICES.size() + 1;
        if (planned(context, parts[0], 0).filter(parts[1]::equals).isPresent()) return -1;
        for (int i = 0; i < SACRIFICES.size(); i++) {
            if (SACRIFICES.get(i).plainName().equals(parts[1])) return i;
        }
        return SACRIFICES.size();
    }

    private static Optional<AiDecision> target(AiTurnContext context, List<AiPrompt> combat) {
        for (AiPrompt prompt : combat) {
            for (PromptButton button : prompt.enabledButtons()) {
                if (!button.isOwnedBy(context.faction()) || !button.handlerId().startsWith(HIT_PREFIX)) continue;
                String[] parts =
                        StringUtils.removeStart(button.handlerId(), HIT_PREFIX).split("_");
                if (parts.length < 2) continue;
                Optional<String> target = planned(context, parts[0], 1);
                if (target.isEmpty()) continue;
                Optional<PromptButton> hit = plannedHit(prompt, context.faction(), parts[0], target.get());
                if (hit.isEmpty()) continue;
                context.memory().remove(PLAN_KEY + parts[0]);
                return Optional.of(AiDecision.press(prompt, hit.get(), "hit the " + target.get() + " with Devotion"));
            }
        }
        return Optional.empty();
    }

    private static Optional<PromptButton> plannedHit(AiPrompt prompt, String faction, String position, String unit) {
        String prefix = HIT_PREFIX + position + "_";
        Optional<PromptButton> destroy = prompt.firstEnabled(
                button -> button.isOwnedBy(faction) && button.handlerId().startsWith(prefix + unit + DAMAGED));
        return destroy.or(() -> prompt.firstEnabled(
                button -> button.isOwnedBy(faction) && button.handlerId().startsWith(prefix + unit + "_")));
    }

    private static Optional<String> planned(AiTurnContext context, String position, int field) {
        String[] fields = StringUtils.splitPreserveAllTokens(
                context.memory().get(PLAN_KEY + position).orElse(""), FIELD);
        return fields != null && fields.length == 2 ? Optional.of(fields[field]) : Optional.empty();
    }

    private static Optional<AiDecision> start(AiTurnContext context, List<AiPrompt> combat) {
        Game game = context.game();
        Player seat = context.seat();
        for (String position : positions(context, combat)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) continue;
            int rounds = CombatRules.tracker(game, seat.getFaction(), position, BoardView.SPACE);
            if (rounds == 0 || !roundFinished(game, seat, tile, rounds) || hitsPending(combat, position)) continue;
            String key = KEY + position + "|" + rounds;
            Optional<String> state = context.memory().get(key);
            if (state.isEmpty()) {
                Optional<Trade> trade = worthIt(game, seat, position);
                context.memory().put(key, trade.isPresent() ? USE : DECLINED);
                trade.ifPresent(found -> context.memory()
                        .put(
                                PLAN_KEY + position,
                                found.sacrifice().plainName()
                                        + FIELD
                                        + found.target().plainName()));
                state = context.memory().get(key);
            }
            if (state.filter(USE::equals).isEmpty()
                    && state.filter(REFRESHED::equals).isEmpty()) continue;
            Optional<Match> button = Prompts.owned(combat, context.faction(), (START_PREFIX + position)::equals)
                    .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
            if (button.isPresent()) {
                context.memory().put(key, "pressed");
                return Optional.of(button.get().press("use Devotion after the round"));
            }
            if (state.filter(REFRESHED::equals).isPresent()) continue;
            Optional<Match> refresh = Prompts.unowned(
                    combat, id -> id.startsWith(REFRESH_PREFIX + position + "_") && id.endsWith(SPACE_SUFFIX));
            if (refresh.isEmpty()) continue;
            context.memory().put(key, REFRESHED);
            return Optional.of(refresh.get().press("bring the Devotion button back"));
        }
        return Optional.empty();
    }

    private static Set<String> positions(AiTurnContext context, List<AiPrompt> combat) {
        Set<String> positions = new TreeSet<>();
        for (AiPrompt prompt : combat) {
            for (PromptButton button : prompt.enabledButtons()) {
                String id = button.handlerId();
                if (button.isOwnedBy(context.faction()) && id.startsWith(START_PREFIX)) {
                    positions.add(StringUtils.removeStart(id, START_PREFIX));
                } else if (button.isUnowned() && id.startsWith(REFRESH_PREFIX) && id.endsWith(SPACE_SUFFIX)) {
                    positions.add(StringUtils.substringBefore(StringUtils.removeStart(id, REFRESH_PREFIX), "_"));
                }
            }
        }
        return positions;
    }

    static Optional<Trade> worthIt(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return Optional.empty();
        UnitHolder space = BoardView.space(tile);
        Optional<UnitType> sacrifice = SACRIFICES.stream()
                .filter(type -> BoardView.count(space, seat, type) > 0)
                .min(Comparator.comparingDouble(type -> cost(seat, type)));
        Optional<Player> opponent = game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.hasOwnShips(other, tile))
                .findFirst();
        if (sacrifice.isEmpty() || opponent.isEmpty()) return Optional.empty();
        double unlock = BrotherOmar.unlockBonus(seat);
        return BoardView.ships(space, opponent.get()).keySet().stream()
                .map(target -> new Trade(
                        sacrifice.get(),
                        target,
                        CombatForecast.shipTradeGain(game, seat, position, sacrifice.get(), target)
                                        .gain()
                                + unlock))
                .filter(trade -> trade.gain() >= MIN_GAIN)
                .max(Comparator.comparingDouble(Trade::gain));
    }

    private static boolean roundFinished(Game game, Player seat, Tile tile, int rounds) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && BoardView.hasOwnShips(other, tile))
                .allMatch(other ->
                        CombatRules.tracker(game, other.getFaction(), tile.getPosition(), BoardView.SPACE) >= rounds);
    }

    private static boolean hitsPending(List<AiPrompt> combat, String position) {
        return combat.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream())
                .map(PromptButton::handlerId)
                .anyMatch(id -> id.startsWith(SPACE_HITS_PREFIX) && id.contains("_" + position + "_"));
    }

    private static double cost(Player player, UnitType type) {
        return BoardView.model(player, type).map(UnitModel::getCost).orElse(0f);
    }
}

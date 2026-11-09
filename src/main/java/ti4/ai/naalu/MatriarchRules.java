package ti4.ai.naalu;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.discord.interactions.buttons.ids.AutoAssignGroundHitsButtonIds;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class MatriarchRules {

    static final String FLAGSHIP = "naalu_flagship";
    static final String RETURN_PREFIX = "returnFFToSpace_";
    static final String BY_HAND = "getDamageButtons_%sdeleteThis_groundcombat";
    private static final String LAND_PREFIX = "landUnits_";
    private static final int MOST_PER_BUTTON = 2;
    private static final String HITS_KEY = "matriarchHits|";
    private static final String GROUND_COMBAT = "groundcombat";
    private static final String LATEST_ASSIGN_HITS = "latestAssignHits";
    private static final String DESTROY = "assignHits";
    private static final String SUSTAIN = "assignDamage";
    private static final String DONE = "deleteButtons";
    private static final String ONE = "1";
    private static final int PICK_PARTS = 6;
    private static final List<String> LOSS_ORDER = List.of("ff", "gf", "mf");

    public static Optional<AiDecision> commitFighters(AiTurnContext context, AiPrompt landing, Tile tile) {
        Game game = context.game();
        Player seat = context.seat();
        if (!seat.hasUnit(FLAGSHIP) || BoardView.count(BoardView.space(tile), seat, UnitType.Flagship) == 0) {
            return Optional.empty();
        }
        int fighters = BoardView.count(BoardView.space(tile), seat, UnitType.Fighter);
        if (fighters == 0) return Optional.empty();
        Optional<Planet> invaded = BoardView.planets(tile).stream()
                .filter(planet -> BoardView.groundForces(planet, seat) > 0)
                .filter(planet -> BoardView.enemyGroundForcesOn(game, seat, planet))
                .max(Comparator.comparingInt((Planet planet) -> enemyGroundForces(game, seat, planet))
                        .thenComparing(Planet::getName));
        if (invaded.isEmpty()) return Optional.empty();
        String planet = invaded.get().getName();
        int amount = Math.min(MOST_PER_BUTTON, fighters);
        return landButton(landing, tile, planet, amount, seat)
                .or(() -> landButton(landing, tile, planet, 1, seat))
                .map(button -> AiDecision.press(
                        landing, button, "commit fighters to the invasion of " + planet + " with the Matriarch"));
    }

    public static Optional<AiDecision> returnFighters(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            Optional<Match> back = Prompts.unowned(List.of(prompt), id -> id.startsWith(RETURN_PREFIX))
                    .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
            if (back.isEmpty()) continue;
            Tile tile = game.getTileByPosition(
                    StringUtils.removeStart(back.get().button().handlerId(), RETURN_PREFIX));
            if (tile == null || !fightersOnPlanets(seat, tile) || combatGoesOn(game, seat, tile)) continue;
            return Optional.of(back.get().press("return the committed fighters to space"));
        }
        return Optional.empty();
    }

    public static Optional<AiDecision> assignHitsByHand(
            AiTurnContext context, AiPrompt prompt, PromptButton autoAssign) {
        Player seat = context.seat();
        String id = autoAssign.handlerId();
        if (!seat.hasUnit(FLAGSHIP) || !id.startsWith(AutoAssignGroundHitsButtonIds.PREFIX)) return Optional.empty();
        String planetName = StringUtils.substringBetween(id, AutoAssignGroundHitsButtonIds.PREFIX, "_");
        String hits = StringUtils.substringAfterLast(id, "_");
        Tile tile = planetName == null ? null : context.game().getTileFromPlanet(planetName);
        if (tile == null || !StringUtils.isNumeric(hits)) return Optional.empty();
        UnitHolder planet = tile.getUnitHolders().get(planetName);
        if (BoardView.count(planet, seat, UnitType.Fighter) == 0 || BoardView.groundForces(planet, seat) == 0) {
            return Optional.empty();
        }
        Optional<PromptButton> byHand = prompt.enabledHandler(BY_HAND.formatted(tile.getPosition()));
        if (byHand.isEmpty()) return Optional.empty();
        context.memory()
                .put(HITS_KEY + tile.getPosition(), String.join("|", planetName, hits, String.valueOf(context.now())));
        return Optional.of(AiDecision.press(
                prompt,
                byHand.get(),
                "assign the hits by hand so the committed fighters fall before its ground forces"));
    }

    public static Optional<AiDecision> continueHits(AiTurnContext context) {
        if (!GROUND_COMBAT.equalsIgnoreCase(context.game().getStoredValue(context.faction() + LATEST_ASSIGN_HITS))) {
            return Optional.empty();
        }
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()) continue;
            List<PromptButton> picks = prompt.enabledButtons().stream()
                    .filter(button -> button.isOwnedBy(context.faction()) && isPick(button))
                    .toList();
            Optional<PromptButton> done =
                    prompt.firstEnabled(button -> button.isUnowned() && DONE.equals(button.handlerId()));
            if (picks.isEmpty() || done.isEmpty()) continue;
            String key = HITS_KEY + parts(picks.getFirst())[1];
            String[] state = StringUtils.split(context.memory().get(key).orElse(""), "|");
            if (state.length != 3 || prompt.createdAtMillis() < Long.parseLong(state[2])) continue;
            int left = Integer.parseInt(state[1]);
            Optional<PromptButton> pick = left > 0 ? nextLoss(picks, state[0]) : Optional.empty();
            if (pick.isEmpty()) {
                context.memory().remove(key);
                return Optional.of(AiDecision.press(prompt, done.get(), "finish assigning the ground hits"));
            }
            context.memory().put(key, String.join("|", state[0], String.valueOf(left - 1), state[2]));
            return Optional.of(AiDecision.press(prompt, pick.get(), "take a ground hit, committed fighters first"));
        }
        return Optional.empty();
    }

    private static Optional<PromptButton> nextLoss(List<PromptButton> picks, String planet) {
        List<PromptButton> here = picks.stream()
                .filter(button -> planet.equals(planetOf(button)) && ONE.equals(parts(button)[2]))
                .toList();
        Optional<PromptButton> sustain =
                here.stream().filter(button -> SUSTAIN.equals(parts(button)[0])).findFirst();
        if (sustain.isPresent()) return sustain;
        List<PromptButton> losses =
                here.stream().filter(button -> DESTROY.equals(parts(button)[0])).toList();
        for (String unit : LOSS_ORDER) {
            Optional<PromptButton> loss = losses.stream()
                    .filter(button -> unit.equals(parts(button)[3]))
                    .max(Comparator.comparingInt(button -> parts(button).length));
            if (loss.isPresent()) return loss;
        }
        return losses.stream().findFirst();
    }

    private static boolean isPick(PromptButton button) {
        String[] parts = parts(button);
        return parts.length >= PICK_PARTS
                && (DESTROY.equals(parts[0]) || SUSTAIN.equals(parts[0]))
                && StringUtils.isNumeric(parts[2]);
    }

    private static String planetOf(PromptButton button) {
        String[] parts = parts(button);
        return parts[parts.length - 2];
    }

    private static String[] parts(PromptButton button) {
        return button.handlerId().split("_");
    }

    private static boolean fightersOnPlanets(Player seat, Tile tile) {
        return BoardView.planets(tile).stream().anyMatch(planet -> BoardView.count(planet, seat, UnitType.Fighter) > 0);
    }

    private static boolean combatGoesOn(Game game, Player seat, Tile tile) {
        return BoardView.planets(tile).stream()
                .filter(planet ->
                        BoardView.count(planet, seat, UnitType.Fighter) > 0 || BoardView.groundForces(planet, seat) > 0)
                .anyMatch(planet -> BoardView.enemyGroundForcesOn(game, seat, planet));
    }

    private static int enemyGroundForces(Game game, Player seat, Planet planet) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .mapToInt(other -> BoardView.groundForces(planet, other))
                .sum();
    }

    private static Optional<PromptButton> landButton(
            AiPrompt prompt, Tile tile, String planet, int amount, Player seat) {
        String id = LAND_PREFIX + tile.getPosition() + "_" + amount + UnitType.Fighter.getValue() + "_" + planet + "_"
                + seat.getColor();
        return prompt.enabledHandler(id);
    }
}

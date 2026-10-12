package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.CopiedTechPolicy;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelperAbilities;

@UtilityClass
public class GalacticThreatRules {

    private static final String ABILITY = "galactic_threat";
    private static final String RIDER_SUFFIX = "Galactic Threat Rider";
    private static final String RIDER_PREFIX = "rider_";
    private static final String PLANET_RIDER_PREFIX = "planetRider_";
    private static final String TECH_PREFIX = "getTech_";
    private static final String NO_PAY_SUFFIX = "__noPay";
    private static final String QUEUE_ABILITY = "queueAfter_ability_galactic_threat";
    private static final String SILENCED_REPRESENTATIVES = "AssassinatedReps";
    private static final String DECLINED_AFTERS = "declinedAfters";
    private static final String QUEUE_WINDOW_PHASE = "agendawaiting";

    public static boolean holdsQueuedPrediction(Game game, Player seat) {
        return seat.hasAbility(ABILITY)
                && game.getStoredValue("galacticThreatUsed").isEmpty()
                && game.getStoredValue("queuedAfters").contains(seat.getFaction() + "_");
    }

    public static Optional<AiDecision> next(AiTurnContext context) {
        Player seat = context.seat();
        Game game = context.game();
        if (!seat.hasAbility(ABILITY) || !game.getPhaseOfGame().toLowerCase().startsWith("agenda")) {
            return Optional.empty();
        }
        AgendaClock.seenAt(context);
        Optional<AiDecision> payout = chooseTechnology(context);
        if (payout.isPresent()) return payout;
        if (silencedByPoliticalSecret(game, seat)) return Optional.empty();
        Optional<AiDecision> prediction = predict(context);
        if (prediction.isPresent()) return prediction;
        if (!game.getStoredValue("galacticThreatUsed").isEmpty()) return Optional.empty();
        if (holdsQueuedPrediction(game, seat) && QUEUE_WINDOW_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
            return pressHidden(context, "lockAftersIn", "keep its prediction queued");
        }
        if (!worthPredicting(game, seat)) return Optional.empty();
        return pressHidden(context, QUEUE_ABILITY, "queue Galactic Threat")
                .or(() -> pressHidden(context, "queueAnAfter", "look at its \"after\" options"));
    }

    private static boolean silencedByPoliticalSecret(Game game, Player seat) {
        return game.getStoredValue(SILENCED_REPRESENTATIVES).contains(seat.getFaction())
                && game.getStoredValue(DECLINED_AFTERS).contains(seat.getFaction() + "_");
    }

    private static Optional<AiDecision> pressHidden(AiTurnContext context, String handlerId, String reason) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<PromptButton> button = prompt.enabledHandler(handlerId);
            if (button.isPresent() && !context.alreadyPressed(prompt, button.get())) {
                return Optional.of(AiDecision.press(prompt, button.get(), reason));
            }
        }
        return Optional.empty();
    }

    private static boolean worthPredicting(Game game, Player seat) {
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            if (!ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(seat, other, new ArrayList<>(), game)
                    .isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<AiDecision> predict(AiTurnContext context) {
        String faction = context.faction();
        Game game = context.game();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || !AgendaClock.isInThisAgenda(context, prompt)) continue;
            List<PromptButton> riders = prompt.enabledButtons().stream()
                    .filter(button ->
                            button.isOwnedBy(faction) && button.handlerId().endsWith(RIDER_SUFFIX))
                    .filter(button -> button.handlerId().startsWith(RIDER_PREFIX)
                            || button.handlerId().startsWith(PLANET_RIDER_PREFIX))
                    .filter(button -> !context.alreadyPressed(prompt, button))
                    .toList();
            if (riders.isEmpty()) continue;
            AgendaForecast forecast = AgendaForecast.of(game, context.seat());
            PromptButton choice = riders.stream()
                    .max(Comparator.comparingDouble(button -> likelihood(game, context.seat(), forecast, button)))
                    .orElseThrow();
            return Optional.of(AiDecision.press(prompt, choice, "predict the agenda's outcome"));
        }
        return Optional.empty();
    }

    private static double likelihood(Game game, Player seat, AgendaForecast forecast, PromptButton rider) {
        if (forecast.isEmpty()) return votesBasedLikelihood(game, seat, rider);
        String id = rider.handlerId();
        if (id.startsWith(PLANET_RIDER_PREFIX)) {
            Player owner = game.getPlayerFromColorOrFaction(
                    StringUtils.substringBefore(StringUtils.removeStart(id, PLANET_RIDER_PREFIX), "_"));
            return owner == null
                    ? 0
                    : forecast.expected(outcome -> owner.getPlanets().contains(outcome) ? 1 : 0);
        }
        String target = StringUtils.substringBefore(StringUtils.removeStart(id, RIDER_PREFIX), "_" + RIDER_SUFFIX);
        return forecast.chance(StringUtils.substringAfter(target, ";"));
    }

    private static double votesBasedLikelihood(Game game, Player seat, PromptButton rider) {
        String id = rider.handlerId();
        if (id.startsWith(PLANET_RIDER_PREFIX)) {
            Player owner = game.getPlayerFromColorOrFaction(
                    StringUtils.substringBefore(StringUtils.removeStart(id, PLANET_RIDER_PREFIX), "_"));
            return owner == null ? 0 : votesOf(game, owner);
        }
        String target = StringUtils.substringBefore(StringUtils.removeStart(id, RIDER_PREFIX), "_" + RIDER_SUFFIX);
        String kind = StringUtils.substringBefore(target, ";");
        String outcome = StringUtils.substringAfter(target, ";");
        return switch (kind) {
            case "fa" -> "for".equalsIgnoreCase(outcome) ? 1 : 0;
            case "player" -> {
                Player elected = game.getPlayerFromColorOrFaction(outcome);
                yield elected == null || elected == seat ? -1 : votesOf(game, elected);
            }
            case "planet" -> {
                Planet planet = game.getPlanetsInfo().get(outcome);
                Player owner = planet == null ? null : BoardView.controller(game, outcome);
                yield owner == null ? 0 : votesOf(game, owner) + BoardView.planetValue(planet) / 10;
            }
            default -> 0;
        };
    }

    private static int votesOf(Game game, Player player) {
        Map<String, Integer> votes = AgendaHelper.getAgendaStartVoteCounts(game);
        return votes.getOrDefault(player.getColor(), 0);
    }

    private static Optional<AiDecision> chooseTechnology(AiTurnContext context) {
        Player seat = context.seat();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()
                    || !mentionsSeat(prompt, seat)
                    || !prompt.content().contains("Galactic Threat")) {
                continue;
            }
            List<String> aliases = new ArrayList<>();
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isUnowned() && button.handlerId().startsWith(TECH_PREFIX)) aliases.add(aliasOf(button));
            }
            Optional<String> best = CopiedTechPolicy.best(context.game(), seat, aliases);
            if (best.isEmpty()) continue;
            PromptButton button = buttonFor(prompt, best.get());
            if (button != null) {
                return Optional.of(AiDecision.press(prompt, button, "gain a technology with Galactic Threat"));
            }
        }
        return Optional.empty();
    }

    private static boolean mentionsSeat(AiPrompt prompt, Player seat) {
        return prompt.content().contains(seat.getRepresentation())
                || prompt.content().contains(seat.getRepresentationUnfogged())
                || prompt.content().contains(seat.getRepresentationNoPing());
    }

    @Nullable
    private static PromptButton buttonFor(AiPrompt prompt, String alias) {
        return prompt.enabledButtons().stream()
                .filter(button -> button.isUnowned() && aliasOf(button).equals(alias))
                .findFirst()
                .orElse(null);
    }

    private static String aliasOf(PromptButton button) {
        return StringUtils.removeEnd(StringUtils.removeStart(button.handlerId(), TECH_PREFIX), NO_PAY_SUFFIX);
    }
}

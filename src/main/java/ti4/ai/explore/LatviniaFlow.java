package ti4.ai.explore;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.model.TechnologyModel.TechnologyType;

class LatviniaFlow implements RelicFlow {

    static final String RELIC = "bookoflatvinia";
    private static final String SEEN_KEY = "latviniaSeen";
    private static final String TAKEN_KEY = "latviniaTechnologies";
    private static final String GET_TECH = "getTech_";
    private static final String FREE_SUFFIX = "__noPay__comp";
    private static final int TECHNOLOGIES = 2;
    private static final long WINDOW_MILLIS = 120_000L;

    @Override
    public String name() {
        return "Book of Latvinia";
    }

    @Override
    public String relicId() {
        return RELIC;
    }

    @Override
    public double netValue(AiTurnContext context) {
        return holdsAllSpecialties(context.game(), context.seat()) ? ObjectiveValue.VICTORY_POINT_VALUE : 0;
    }

    static Optional<AiDecision> freeTechnologies(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasRelic(RELIC)) return Optional.empty();
        long seen = context.memory().get(SEEN_KEY).map(Long::parseLong).orElse(context.now());
        context.memory().put(SEEN_KEY, String.valueOf(seen));
        int taken = context.memory().get(TAKEN_KEY).map(Integer::parseInt).orElse(0);
        if (taken >= TECHNOLOGIES || context.now() - seen > WINDOW_MILLIS) return Optional.empty();
        List<Match> options = Prompts.newestFirst(context.prompts()).stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isOwnedBy(context.faction())
                                && button.handlerId().startsWith(GET_TECH)
                                && button.handlerId().endsWith(FREE_SUFFIX)
                                && !context.alreadyPressed(prompt, button))
                        .map(button -> new Match(prompt, button)))
                .filter(match -> !seat.hasTech(alias(match)))
                .toList();
        Optional<String> best = ResearchPolicy.best(
                context.game(), seat, options.stream().map(LatviniaFlow::alias).toList());
        if (best.isEmpty()) return Optional.empty();
        context.memory().put(TAKEN_KEY, String.valueOf(taken + 1));
        return options.stream()
                .filter(match -> alias(match).equals(best.get()))
                .findFirst()
                .map(match -> match.press("research " + best.get() + " from the Book of Latvinia"));
    }

    private static String alias(Match match) {
        return StringUtils.substringBefore(
                StringUtils.removeStart(match.button().handlerId(), GET_TECH), "__");
    }

    static boolean holdsAllSpecialties(Game game, Player seat) {
        Set<String> held = seat.getPlanetsAllianceMode().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Planet.class::isInstance)
                .map(Planet.class::cast)
                .flatMap(planet -> planet.getTechSpecialities().stream())
                .collect(Collectors.toSet());
        return TechnologyType.mainFour.stream().allMatch(type -> held.contains(type.toString()));
    }
}

package ti4.ai.explore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class ExplorationRules {

    private record Offer(PromptButton button, String planet, String trait, double value, boolean scanlink) {}

    static final double SCANLINK_MIN_VALUE = 0;
    private static final String EXPLORE_PREFIX = "movedNExplored_";
    private static final String FILLER = "filler";
    private static final String SCANLINK = "scanlink";
    private static final String CROWN = "crownofemphidiaexplore";
    private static final String DECLINE = "deleteButtons";
    private static final List<String> TRAITS = List.of("cultural", "industrial", "hazardous");

    public static Optional<AiDecision> next(AiTurnContext context) {
        List<AiPrompt> prompts = ExploreWindow.prompts(context);
        return explore(context, prompts)
                .or(() -> crown(context, prompts))
                .or(() -> CardRules.next(context, prompts))
                .or(() -> FreelancersRules.next(context, prompts))
                .or(() -> TokenGainRules.next(context, prompts));
    }

    public static Optional<AiDecision> explore(AiTurnContext context, List<AiPrompt> prompts) {
        ExploreOutlook outlook = new ExploreOutlook(context.game(), context.seat());
        for (AiPrompt prompt : prompts) {
            if (!ExploreWindow.untouched(context, prompt)) continue;
            List<Offer> offers = offersIn(context, prompt, outlook);
            if (offers.isEmpty()) continue;
            Offer best = offers.stream()
                    .max(Comparator.comparingDouble(Offer::value))
                    .orElseThrow();
            if (best.scanlink() && best.value() <= SCANLINK_MIN_VALUE) continue;
            return Optional.of(
                    AiDecision.press(prompt, best.button(), "explore " + best.planet() + " as " + best.trait()));
        }
        return Optional.empty();
    }

    private static List<Offer> offersIn(AiTurnContext context, AiPrompt prompt, ExploreOutlook outlook) {
        List<Offer> offers = new ArrayList<>();
        for (PromptButton button : prompt.enabledButtons()) {
            if (!button.isOwnedBy(context.faction()) || !button.handlerId().startsWith(EXPLORE_PREFIX)) continue;
            String[] parts =
                    StringUtils.removeStart(button.handlerId(), EXPLORE_PREFIX).split("_");
            boolean exploresOnce = parts.length == 3 && TRAITS.contains(parts[2]);
            boolean known = exploresOnce && (FILLER.equals(parts[0]) || SCANLINK.equals(parts[0]));
            if (!known || context.game().getUnitHolderFromPlanet(parts[1]) == null) continue;
            ExploreSite site = ExploreSite.onBoard(context.game(), context.seat(), parts[1], outlook);
            offers.add(new Offer(
                    button, parts[1], parts[2], ExploreDeck.expectedValue(site, parts[2]), SCANLINK.equals(parts[0])));
        }
        return offers;
    }

    private static Optional<AiDecision> crown(AiTurnContext context, List<AiPrompt> prompts) {
        for (AiPrompt prompt : prompts) {
            Optional<PromptButton> explore =
                    prompt.firstEnabled(button -> button.isUnowned() && CROWN.equals(button.handlerId()));
            Optional<PromptButton> decline =
                    prompt.firstEnabled(button -> button.isUnowned() && DECLINE.equals(button.handlerId()));
            if (explore.isEmpty()
                    || decline.isEmpty()
                    || !ExploreWindow.isOwn(context, prompt)
                    || !ExploreWindow.untouched(context, prompt)) {
                continue;
            }
            if (bestPlanetToExplore(context) > 0) {
                return Optional.of(
                        AiDecision.press(prompt, explore.get(), "explore a planet with the Crown of Emphidia"));
            }
            return Optional.of(AiDecision.press(prompt, decline.get(), "not exhaust the Crown of Emphidia"));
        }
        return Optional.empty();
    }

    private static double bestPlanetToExplore(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        ExploreOutlook outlook = new ExploreOutlook(game, seat);
        return seat.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(planet -> planet != null && !planet.getPlanetTypes().isEmpty())
                .mapToDouble(planet ->
                        ExploreDeck.expectedValueOfPlanet(ExploreSite.onBoard(game, seat, planet.getName(), outlook)))
                .max()
                .orElse(0);
    }
}

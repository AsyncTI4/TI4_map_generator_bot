package ti4.ai.expedition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.hacan.Beneficiary;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
public class ThundersEdgeRules {

    public static final String TILE_PREFIX = "placeThundersEdge_";
    private static final String START = "placeThundersEdge";
    private static final Pattern TILE_CHOICE = Pattern.compile("placeThundersEdge_[^_]+");
    private static final Pattern RECIPIENT_CHOICE = Pattern.compile("placeThundersEdge_[^_]+_(.+)");

    static Optional<AiDecision> next(AiTurnContext context) {
        List<AiPrompt> visible = Prompts.newestFirst(context.prompts());
        return chooseRecipient(context, visible)
                .or(() -> chooseTile(context, visible))
                .or(() -> start(context, visible));
    }

    private static Optional<AiDecision> start(AiTurnContext context, List<AiPrompt> visible) {
        return Prompts.owned(visible, context.faction(), START::equals)
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()))
                .map(match -> match.press("start placing Thunder's Edge"));
    }

    private static Optional<AiDecision> chooseTile(AiTurnContext context, List<AiPrompt> visible) {
        Game game = context.game();
        List<Match> tiles = visible.stream()
                .filter(prompt -> Prompts.mentions(prompt, context.seat()))
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isUnowned()
                                && TILE_CHOICE.matcher(button.handlerId()).matches())
                        .filter(button -> !context.alreadyPressed(prompt, button))
                        .map(button -> new Match(prompt, button)))
                .filter(match -> tileOf(game, match.button()).isPresent())
                .toList();
        if (tiles.isEmpty()) return Optional.empty();
        SiteScore scores = SiteScore.of(game, context.seat());
        return tiles.stream()
                .max(Comparator.comparingDouble(match ->
                        scores.thundersEdge(tileOf(game, match.button()).orElseThrow())))
                .map(match -> match.press("place Thunder's Edge near itself and away from aggressive rivals"));
    }

    private static Optional<Tile> tileOf(Game game, PromptButton button) {
        return Optional.ofNullable(game.getTileByPosition(StringUtils.removeStart(button.handlerId(), TILE_PREFIX)));
    }

    private static Optional<AiDecision> chooseRecipient(AiTurnContext context, List<AiPrompt> visible) {
        Game game = context.game();
        List<Match> recipients = visible.stream()
                .flatMap(prompt -> prompt.enabledButtons().stream()
                        .filter(button -> button.isOwnedBy(context.faction())
                                && RECIPIENT_CHOICE.matcher(button.handlerId()).matches())
                        .filter(button -> !context.alreadyPressed(prompt, button))
                        .map(button -> new Match(prompt, button)))
                .toList();
        if (recipients.isEmpty()) return Optional.empty();
        Optional<Match> itself = recipients.stream()
                .filter(match -> factionOf(match.button()).equals(context.faction()))
                .findFirst();
        if (itself.isPresent()) return itself.map(match -> match.press("take Thunder's Edge's infantry itself"));
        List<Player> candidates = recipients.stream()
                .map(match -> game.getPlayerFromColorOrFaction(factionOf(match.button())))
                .toList();
        Optional<Player> chosen = Beneficiary.leastThreatening(game, context.seat(), candidates);
        Match pick = recipients.stream()
                .filter(match -> chosen.isPresent()
                        && factionOf(match.button()).equals(chosen.get().getFaction()))
                .findFirst()
                .orElse(recipients.getFirst());
        return Optional.of(pick.press("give Thunder's Edge's infantry to the least threatening tied player"));
    }

    private static String factionOf(PromptButton button) {
        Matcher matcher = RECIPIENT_CHOICE.matcher(button.handlerId());
        return matcher.matches() ? matcher.group(1) : "";
    }
}

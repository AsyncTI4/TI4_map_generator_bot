package ti4.ai.winnu;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class ReclaimerRules {

    public static final String PREFIX = "winnuStructure_";
    static final String DOCK_PREFIX = PREFIX + Reclamation.SPACE_DOCK + "_";
    static final String PDS_PREFIX = PREFIX + Reclamation.PDS + "_";
    private static final String DONE = "deleteButtons";
    private static final int MAX_PDS_PER_PLANET = 2;

    public static Optional<AiDecision> next(AiTurnContext context) {
        if (!context.isActivePlayer()) return Optional.empty();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            List<PromptButton> offers = prompt.enabledButtons().stream()
                    .filter(button -> button.isUnowned() && button.handlerId().startsWith(PREFIX))
                    .filter(button -> context.seat().getPlanets().contains(planetOf(button)))
                    .toList();
            if (offers.isEmpty()) continue;
            Optional<AiDecision> decision = answer(context, prompt, offers);
            if (decision.isPresent()) return decision;
        }
        return Optional.empty();
    }

    private static Optional<AiDecision> answer(AiTurnContext context, AiPrompt prompt, List<PromptButton> offers) {
        Optional<PromptButton> done =
                prompt.enabledHandler(DONE).filter(button -> !context.alreadyPressed(prompt, button));
        long placed = offers.stream()
                .filter(button -> context.alreadyPressed(prompt, button))
                .count();
        if (placed >= reclaimers(context.game(), context.seat(), planetOf(offers.getFirst()))) {
            return done.map(button -> AiDecision.press(prompt, button, "finish placing Reclaimer structures"));
        }
        Optional<PromptButton> dock = offers.stream()
                .filter(button -> !context.alreadyPressed(prompt, button))
                .filter(button -> button.handlerId().startsWith(DOCK_PREFIX))
                .filter(button -> dockFits(context.game(), context.seat(), planetOf(button)))
                .findFirst();
        if (dock.isPresent()) {
            return Optional.of(AiDecision.press(prompt, dock.get(), "place a space dock with the Reclaimer"));
        }
        Optional<PromptButton> pds = offers.stream()
                .filter(button -> !context.alreadyPressed(prompt, button))
                .filter(button -> button.handlerId().startsWith(PDS_PREFIX))
                .filter(button -> pdsFits(context.game(), context.seat(), planetOf(button)))
                .findFirst();
        if (pds.isPresent()) return Optional.of(AiDecision.press(prompt, pds.get(), "place a PDS with the Reclaimer"));
        return done.map(button -> AiDecision.press(prompt, button, "no structure left for the Reclaimer to place"));
    }

    private static int reclaimers(Game game, Player seat, String planetName) {
        Planet planet = planet(game, planetName);
        return planet == null ? 1 : Math.max(1, BoardView.count(planet, seat, UnitType.Mech));
    }

    static boolean dockFits(Game game, Player seat, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null || !Reclamation.inReinforcements(game, seat, Reclamation.SPACE_DOCK)) return false;
        return BoardView.countInTile(tile, seat, UnitType.Spacedock) == 0;
    }

    static boolean pdsFits(Game game, Player seat, String planetName) {
        Planet planet = planet(game, planetName);
        if (planet == null || !Reclamation.inReinforcements(game, seat, Reclamation.PDS)) return false;
        return BoardView.count(planet, seat, UnitType.Pds) < MAX_PDS_PER_PLANET;
    }

    private static Planet planet(Game game, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile != null && tile.getUnitHolders().get(planetName) instanceof Planet planet) return planet;
        return null;
    }

    static String planetOf(PromptButton button) {
        return StringUtils.substringAfter(StringUtils.removeStart(button.handlerId(), PREFIX), "_");
    }
}

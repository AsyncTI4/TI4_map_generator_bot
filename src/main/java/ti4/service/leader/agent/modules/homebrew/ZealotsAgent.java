package ti4.service.leader.agent.modules.homebrew;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class ZealotsAgent extends TargetedAgent {

    static final String ID = "zealotsagent";
    private static final String PRODUCE_STEP = "produceOneUnitInTile_";
    private static final String PRODUCTION_REASON = "_ZealotsAgent";

    public ZealotsAgent() {
        super(ID, "Priestess Tuh, the Rhodun");
    }

    public static Optional<Button> button(Game game, Player zealots) {
        if (productionSystems(game, zealots).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Buttons.gray(
                ownedButtonId(zealots, ID, zealots.getFaction()),
                useLabel(zealots, ID, "Rhodun Agent"),
                FactionEmojis.zealots));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (productionSystems(game, target).isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor()
                    + " has no home system or technology specialty system to produce a ship in");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> systems = productionSystems(use.game(), target).stream()
                .map(tile -> Buttons.green(
                        ownedStepId(target, PRODUCE_STEP + tile.getPosition() + PRODUCTION_REASON, false),
                        tile.getRepresentationForButtons(use.game(), target)))
                .toList();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", you may produce 1 ship in your home system or in a system with a technology specialty"
                        + " planet you control, due to " + use.agentNameFor(target) + ".",
                systems));
    }

    static List<Tile> productionSystems(Game game, Player player) {
        Map<String, Tile> systems = new LinkedHashMap<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (planet.toLowerCase().contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            Tile tile = game.getTileFromPlanet(planet);
            if (tile == null || systems.containsKey(tile.getPosition())) {
                continue;
            }
            boolean techSpecialtyWithoutEnemyShips = ButtonHelper.checkForTechSkips(game, planet)
                    && !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game);
            if (techSpecialtyWithoutEnemyShips || tile.isHomeSystem(game)) {
                systems.put(tile.getPosition(), tile);
            }
        }
        return List.copyOf(systems.values());
    }
}

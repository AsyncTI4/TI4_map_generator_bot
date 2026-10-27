package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class ArgentAgent extends AgentModule<Tile> {

    static final String ID = "argentagent";

    public ArgentAgent() {
        super(ID, "Trillossa Aun Mirik, the Argent");
    }

    public static Button button(Player argent, Tile productionSystem) {
        return Buttons.blue(
                ownedButtonId(argent, ID, productionSystem.getPosition()),
                useLabel(argent, ID, cardLabel(argent, ID, "Argent Agent")),
                FactionEmojis.Argent);
    }

    @Override
    public Optional<Tile> decode(Game game, Player user, String payload) {
        return Optional.ofNullable(game.getTileByPosition(payload));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Tile> use) {
        Player producer = use.user();
        return AgentOutcome.of(Message.addressedTo(
                producer,
                ", use buttons to place ground forces via " + use.agentName() + ".",
                placementButtons(use.game(), producer, use.payload())));
    }

    private static List<Button> placementButtons(Game game, Player producer, Tile productionSystem) {
        List<Button> buttons = new ArrayList<>();
        for (String position : FoWHelper.getAdjacentTiles(game, productionSystem.getPosition(), producer, false)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) {
                continue;
            }
            for (Planet planet : tile.getPlanetUnitHolders()) {
                String planetName = planet.getName();
                if (!producer.getPlanetsAllianceMode().contains(planetName)) {
                    continue;
                }
                String planetRepresentation = Helper.getPlanetRepresentation(planetName, game);
                buttons.add(Buttons.green(
                        producer.factionButtonChecker() + "place_infantry_" + planetName,
                        "Produce 1 Infantry on " + planetRepresentation,
                        UnitEmojis.infantry));
                buttons.add(Buttons.green(
                        producer.factionButtonChecker() + "place_mech_" + planetName,
                        "Produce Mech on " + planetRepresentation,
                        UnitEmojis.mech));
            }
        }
        buttons.add(Buttons.red(
                producer.factionButtonChecker() + "deleteButtons_spitItOut",
                "Done With " + cardLabel(producer, ID, "Argent Agent")));
        return buttons;
    }
}

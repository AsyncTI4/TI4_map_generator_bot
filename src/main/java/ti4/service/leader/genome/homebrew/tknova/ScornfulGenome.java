package ti4.service.leader.genome.homebrew.tknova;

import java.util.ArrayList;
import java.util.List;
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
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.ArgentAgent;
import ti4.service.leader.genome.LeaningGenome;

public final class ScornfulGenome extends LeaningGenome<Tile> {

    static final String ID = "tknovascornfulagent";

    public ScornfulGenome() {
        super(ID, "Scornful Genome", new ArgentAgent());
    }

    public static Button button(Player producer, Tile productionSystem) {
        return Buttons.blue(
                ownedButtonId(producer, ID, productionSystem.getPosition()),
                useLabel(producer, ID, "Scornful Genome"),
                FactionEmojis.Argent);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Tile> use) {
        Player producer = use.user();
        return AgentOutcome.of(Message.addressedTo(
                producer,
                ", use buttons to place ground forces via " + use.agentName() + ".",
                placementButtons(use.game(), producer)));
    }

    static List<Button> placementButtons(Game game, Player producer) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (producer.getPlanetsAllianceMode().contains(planet.getName())) {
                    buttons.addAll(groundForceButtons(
                            producer, planet.getName(), Helper.getPlanetRepresentation(planet.getName(), game)));
                }
            }
            if (FoWHelper.playerHasShipsInSystem(producer, tile)) {
                buttons.addAll(groundForceButtons(
                        producer, tile.getPosition() + "space", "the space area of " + tile.getPosition()));
            }
        }
        buttons.add(
                Buttons.red(producer.factionButtonChecker() + "deleteButtons_spitItOut", "Done With Scornful Genome"));
        return buttons;
    }

    private static List<Button> groundForceButtons(Player producer, String where, String label) {
        return List.of(
                Buttons.green(
                        producer.factionButtonChecker() + "place_infantry_" + where,
                        "Produce 1 Infantry on " + label,
                        UnitEmojis.infantry),
                Buttons.green(
                        producer.factionButtonChecker() + "place_mech_" + where,
                        "Produce Mech on " + label,
                        UnitEmojis.mech));
    }
}

package ti4.service.leader.genome.homebrew.tknova;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.genome.Genome;

public final class RavenousGenome extends Genome<Tile> {

    static final String ID = "tknovaravenousagent";
    private static final int EXTRA_UNITS = 6;
    private static final int UNITS_PER_BUTTON = 2;

    public RavenousGenome() {
        super(ID, "Ravenous Genome");
    }

    public static Button button(Player producer, Tile productionSystem) {
        return Buttons.blue(
                ownedButtonId(producer, ID, productionSystem.getPosition()),
                useLabel(producer, ID, "Ravenous Genome"),
                FactionEmojis.Cabal);
    }

    @Override
    public Optional<Tile> decode(Game game, Player user, String payload) {
        return Optional.ofNullable(game.getTileByPosition(payload));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Tile> use) {
        Player producer = use.user();
        List<Button> buttons = new ArrayList<>(unitButtons(use.game(), producer, use.payload()));
        buttons.add(Buttons.red(producer.factionButtonChecker() + "deleteButtons", "Done"));
        return AgentOutcome.of(Message.addressedTo(
                producer,
                ", use buttons to also produce up to " + EXTRA_UNITS + " fighters or infantry with "
                        + use.agentName() + ", pressing the " + UNITS_PER_BUTTON + "-unit buttons up to "
                        + EXTRA_UNITS / UNITS_PER_BUTTON
                        + " times in total. They do not count against your PRODUCTION limit.",
                buttons));
    }

    private static List<Button> unitButtons(Game game, Player producer, Tile system) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                producer.factionButtonChecker() + "place_2ff_" + system.getPosition(),
                "Produce 2 Fighters",
                UnitEmojis.fighter));
        for (Planet planet : system.getPlanetUnitHolders()) {
            if (producer.getPlanetsAllianceMode().contains(planet.getName())) {
                buttons.add(Buttons.green(
                        producer.factionButtonChecker() + "place_2gf_" + planet.getName(),
                        "Produce 2 Infantry on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        UnitEmojis.infantry));
            }
        }
        return buttons;
    }
}

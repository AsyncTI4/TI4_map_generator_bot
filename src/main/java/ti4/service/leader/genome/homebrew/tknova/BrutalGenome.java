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

public final class BrutalGenome extends Genome<Tile> {

    static final String ID = "tknovabrutalagent";

    public BrutalGenome() {
        super(ID, "Brutal Genome");
    }

    public static Button button(Player producer, Tile productionSystem) {
        return Buttons.blue(
                ownedButtonId(producer, ID, productionSystem.getPosition()),
                useLabel(producer, ID, "Brutal Genome"),
                FactionEmojis.L1Z1X);
    }

    @Override
    public Optional<Tile> decode(Game game, Player user, String payload) {
        return Optional.ofNullable(game.getTileByPosition(payload));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Tile system) {
        if (mechButtons(game, user, system).isEmpty()) {
            return Optional.of("You control no planet in that system to place a mech on");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Tile> use) {
        Player producer = use.user();
        List<Button> buttons = new ArrayList<>(mechButtons(use.game(), producer, use.payload()));
        buttons.add(Buttons.red(producer.factionButtonChecker() + "deleteButtons", "Done"));
        return AgentOutcome.of(
                Message.addressedTo(producer, ", use buttons to place 1 mech with " + use.agentName() + ".", buttons));
    }

    private static List<Button> mechButtons(Game game, Player producer, Tile system) {
        List<Button> buttons = new ArrayList<>();
        for (Planet planet : system.getPlanetUnitHolders()) {
            if (producer.getPlanetsAllianceMode().contains(planet.getName())) {
                buttons.add(Buttons.green(
                        producer.factionButtonChecker() + "placeOneNDone_skipbuild_mech_" + planet.getName(),
                        "Place Mech on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        UnitEmojis.mech));
            }
        }
        return buttons;
    }
}

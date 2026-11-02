package ti4.service.leader.genome.homebrew.tknova;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.genome.Genome;
import ti4.service.unit.AddUnitService;

public final class RavenousGenome extends Genome<Tile> {

    static final String ID = "tknovaravenousagent";
    private static final String PLACE = "tknovaRavenousPlace_";
    private static final String PLACED_KEY = "tknovaRavenousPlaced_";
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
        use.game().setStoredValue(PLACED_KEY + producer.getFaction(), "0");
        List<Button> buttons = new ArrayList<>(unitButtons(use.game(), producer, use.payload()));
        buttons.add(Buttons.red(producer.factionButtonChecker() + "deleteButtons", "Done"));
        return AgentOutcome.of(Message.addressedTo(
                producer,
                ", use buttons to also place up to " + EXTRA_UNITS + " fighters or infantry with "
                        + use.agentName() + ", pressing the " + UNITS_PER_BUTTON + "-unit buttons up to "
                        + EXTRA_UNITS / UNITS_PER_BUTTON
                        + " times in total. They do not count against your PRODUCTION limit.",
                buttons));
    }

    @ButtonHandler(PLACE)
    static void place(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String key = PLACED_KEY + player.getFaction();
        String stored = game.getStoredValue(key);
        int placed = stored.isEmpty() ? EXTRA_UNITS : Integer.parseInt(stored);
        if (placed >= EXTRA_UNITS) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have already placed all the extra units.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String kind = StringUtils.substringBefore(buttonID.substring(PLACE.length()), "_");
        String where = StringUtils.substringAfter(buttonID.substring(PLACE.length()), "_");
        if ("ff".equals(kind)) {
            AddUnitService.addUnits(event, game.getTileByPosition(where), game, player.getColor(), "2 ff");
        } else if (player.getPlanetsAllianceMode().contains(where) && game.getTileFromPlanet(where) != null) {
            AddUnitService.addUnits(event, game.getTileFromPlanet(where), game, player.getColor(), "2 gf " + where);
        } else {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You no longer control that planet.");
            return;
        }
        game.setStoredValue(key, Integer.toString(placed + UNITS_PER_BUTTON));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + UNITS_PER_BUTTON + " extra "
                        + ("ff".equals(kind) ? "fighters" : "infantry") + " with " + "Ravenous Genome" + ".");
        if (placed + UNITS_PER_BUTTON >= EXTRA_UNITS) {
            ButtonHelper.deleteMessage(event);
        }
    }

    private static List<Button> unitButtons(Game game, Player producer, Tile system) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                producer.factionButtonChecker() + PLACE + "ff_" + system.getPosition(),
                "Place 2 Fighters",
                UnitEmojis.fighter));
        for (Planet planet : system.getPlanetUnitHolders()) {
            if (producer.getPlanetsAllianceMode().contains(planet.getName())) {
                buttons.add(Buttons.green(
                        producer.factionButtonChecker() + PLACE + "gf_" + planet.getName(),
                        "Place 2 Infantry on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        UnitEmojis.infantry));
            }
        }
        return buttons;
    }
}

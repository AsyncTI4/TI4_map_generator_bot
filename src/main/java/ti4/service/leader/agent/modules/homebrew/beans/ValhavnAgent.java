package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class ValhavnAgent extends TargetedAgent {

    static final String ID = "valhavnagent";

    public ValhavnAgent() {
        super(ID, "Thorberg Skafhógg, the Valhavn");
    }

    public static void offerAtEndOfTurn(Game game, Player target) {
        if (!target.isRealPlayer()) {
            return;
        }
        List<Button> placements = placementButtons(game, target);
        if (placements.isEmpty()) {
            return;
        }

        for (Player holder : game.getRealPlayers()) {
            if (!holder.hasUnexhaustedLeader(ID)) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    holder.getCardsInfoThread(),
                    holder.getRepresentationNoPing() + ", "
                            + (game.isFowMode() ? "another player" : target.getRepresentationNoPing())
                            + " ended their turn. You may use Thorberg Skafhógg, the Valhavn agent, to let them place units.",
                    List.of(
                            Buttons.gray(
                                    ownedButtonId(holder, ID, target.getFaction()),
                                    useLabel(holder, ID, "Valhavn Agent"),
                                    FactionEmojis.valhavn),
                            Buttons.DECLINE_DELETE_BUTTONS));
        }
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player holder = use.user();
        Player target = use.payload();
        Message placementPrompt = Message.addressedTo(
                        target,
                        ", choose the placement to resolve with " + use.agentNameFor(target) + ".",
                        placementButtons(use.game(), target))
                .forEveryone()
                .withPagedButtons();
        AgentOutcome outcome = AgentOutcome.of(placementPrompt).deletingPressedMessage();
        if (holder != target) {
            outcome = outcome.and(Message.addressedTo(
                            holder,
                            ", gain 1 command token due to " + use.agentNameFor(holder) + ".",
                            ButtonHelper.getGainCCButtons(holder))
                    .forEveryone());
        }
        return outcome;
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        return placementButtons(game, target).isEmpty()
                ? Optional.of(target.getRepresentationNoPing() + " has no legal Thorberg Skafhógg placements")
                : Optional.empty();
    }

    private static List<Button> placementButtons(Game game, Player target) {
        List<Button> buttons = new ArrayList<>();
        if (hasReinforcement(game, target, UnitType.Pds, 1)) {
            for (String planetName : target.getPlanets()) {
                if (game.getUnitHolderFromPlanet(planetName) == null) {
                    continue;
                }
                buttons.add(Buttons.green(
                        target.factionButtonChecker() + "placeOneNDone_skipbuild_pds_" + planetName,
                        "Place 1 PDS on " + Helper.getPlanetRepresentation(planetName, game)));
            }
        }

        if (hasReinforcement(game, target, UnitType.Infantry, 2)) {
            for (String planetName : pdsPlanets(game, target)) {
                buttons.add(Buttons.green(
                        target.factionButtonChecker() + "placeOneNDone_skipbuild_2gf_" + planetName,
                        "Place 2 Infantry on " + Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        return buttons;
    }

    private static boolean hasReinforcement(Game game, Player player, UnitType unitType, int amount) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unitType.value, true) + amount
                <= player.getUnitCap(unitType.value);
    }

    private static List<String> pdsPlanets(Game game, Player player) {
        LinkedHashSet<String> planets = new LinkedHashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitCount(UnitType.Pds, player) > 0) {
                    planets.add(planet.getName());
                }
            }
        }
        return List.copyOf(planets);
    }
}

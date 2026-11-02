package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.unit.AddUnitService;

public final class CeldauriAgent extends TargetedAgent {

    static final String ID = "celdauriagent";
    private static final String PLACE_STEP = "celdauriAgentStep3_";
    private static final int COST = 2;

    public CeldauriAgent() {
        super(ID, "George Nobin, the Celdauri");
    }

    public static Optional<Button> button(Game game, Player celdauri) {
        if (whyNotUsableOn(game, celdauri).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(Buttons.gray(
                ownedButtonId(celdauri, ID, celdauri.getFaction()),
                useLabel(celdauri, ID, "Celdauri Agent"),
                FactionEmojis.celdauri));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        return whyNotUsableOn(game, target);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player target = use.payload();
        List<String> planets = planetsForASpaceDock(game, target);
        if (planets.size() == 1) {
            return AgentOutcome.of(Message.plain(
                    target, placeSpaceDock(use.event(), game, target, planets.getFirst(), use.agentNameFor(target))));
        }
        List<Button> buttons = planets.stream()
                .map(planet -> Buttons.green(
                        ownedStepId(target, PLACE_STEP + planet, use.viaYssaril()),
                        "Place 1 Space Dock on " + Helper.getPlanetRepresentation(planet, game)))
                .toList();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", please choose the planet you wish to place 1 space dock on with " + use.agentNameFor(target) + ".",
                buttons));
    }

    @ButtonHandler(PLACE_STEP)
    static void placeSpaceDockStep(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        String planet = StringUtils.substringAfter(step.id(), PLACE_STEP);
        if (game.getTileFromPlanet(planet) == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that planet.");
            return;
        }
        if (!canPay(player)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", you no longer have 2 trade goods or 2 commodities to pay.");
            return;
        }
        String agentName = AgentLifecycle.stepAgentName(game, player, ID, step.viaYssaril());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), placeSpaceDock(event, game, player, planet, agentName));
        ButtonHelper.deleteMessage(event);
    }

    static List<String> planetsForASpaceDock(Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            return List.of();
        }
        return activeSystem.getPlanetUnitHolders().stream()
                .filter(planet -> player.getPlanetsAllianceMode().contains(planet.getName()))
                .filter(planet -> planet.getUnitCount(UnitType.Spacedock, player.getColor()) == 0)
                .map(UnitHolder::getName)
                .toList();
    }

    private static Optional<String> whyNotUsableOn(Game game, Player target) {
        if (game.getActivePlayer() != target) {
            return Optional.of(target.getFactionEmojiOrColor() + " is not the active player");
        }
        if (!canPay(target)) {
            return Optional.of(target.getFactionEmojiOrColor() + " cannot pay 2 trade goods or 2 commodities");
        }
        if (planetsForASpaceDock(game, target).isEmpty()) {
            return Optional.of(
                    target.getFactionEmojiOrColor() + " controls no planet without a space dock in the active system");
        }
        return Optional.empty();
    }

    private static boolean canPay(Player player) {
        return player.getCommodities() >= COST || player.getTg() >= COST;
    }

    private static String placeSpaceDock(
            GenericInteractionCreateEvent event, Game game, Player player, String planet, String agentName) {
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 sd " + planet);
        String payment;
        if (player.getCommodities() >= COST) {
            player.setCommodities(player.getCommodities() - COST);
            payment = "2 commodities";
        } else {
            payment = "2 trade goods " + player.gainTG(-COST);
        }
        CommanderUnlockCheckService.checkPlayer(player, "titans", "saar", "rohdhna", "cheiran", "celdauri");
        if (tile != null && tile.isFracture()) {
            CommanderUnlockCheckService.checkPlayer(player, "obsidian");
        }
        AgendaHelper.ministerOfIndustryCheck(player, game, tile, event);
        return player.getFactionEmojiOrColor() + " placed 1 " + UnitEmojis.spacedock + " on "
                + Helper.getPlanetRepresentation(planet, game) + " with " + agentName + ", paying " + payment + ".";
    }
}

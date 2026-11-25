package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class MigoAgent extends TargetedAgent {

    static final String ID = "migoagent";
    private static final String SELECT_PLANET = "migoAgentSelectPlanet_";
    private static final String GAIN_COMMAND_TOKEN = "migoAgentGainCommandToken";
    private static final String GAIN_TRADE_GOODS = "migoAgentGainTradeGoods";

    public MigoAgent() {
        super(ID, "N'ghaath, the Mi-Go");
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (target == user) {
            return Optional.of("N'ghaath must target another player");
        }
        if (eligiblePlanets(game, target).isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no ready planet with a technology specialty");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", choose the technology-specialty planet to exhaust with " + use.agentNameFor(target) + ".",
                planetButtons(use.game(), target)));
    }

    @ButtonHandler(SELECT_PLANET)
    static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(SELECT_PLANET.length());
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (!isEligiblePlanet(player, planet)) {
            return;
        }

        player.exhaustPlanet(planetName);
        List<Button> buttons = new ArrayList<>(researchButtons(player, planet));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + " exhausted " + Helper.getPlanetRepresentation(planetName, game)
                        + " with N'ghaath. Research a technology matching its specialty, then gain 1 command token or 2 trade goods.",
                buttons);

        Player owner = game.getPlayerFromLeader("migoagent");
        List<Button> ownerButtons = new ArrayList<>();
        ownerButtons.add(Buttons.gray(owner.factionButtonChecker() + GAIN_COMMAND_TOKEN, "Gain 1 Command Token"));
        ownerButtons.add(
                Buttons.green(owner.factionButtonChecker() + GAIN_TRADE_GOODS, "Gain 2 Trade Goods", MiscEmojis.tg));
        ownerButtons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentation() + ", after " + player.getRepresentationNoPing()
                        + " has researched their technology, please choose whether to gain 1 command token or 2 trade goods.",
                ownerButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_COMMAND_TOKEN)
    static void gainCommandToken(ButtonInteractionEvent event, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose where to place the command token from N'ghaath.",
                ButtonHelper.getGainCCButtons(player));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(GAIN_TRADE_GOODS)
    static void gainTradeGoods(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainTGs(event, game, player, 2, false);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    private static List<Button> planetButtons(Game game, Player player) {
        return eligiblePlanets(game, player).stream()
                .map(planet -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_PLANET + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
    }

    private static List<Planet> eligiblePlanets(Game game, Player player) {
        return player.getPlanets().stream()
                .map(game.getPlanetsInfo()::get)
                .filter(planet -> isEligiblePlanet(player, planet))
                .toList();
    }

    private static boolean isEligiblePlanet(Player player, Planet planet) {
        return planet != null
                && player.getPlanets().contains(planet.getName())
                && !player.getExhaustedPlanets().contains(planet.getName())
                && TechnologyType.mainFour.stream().anyMatch(planet::hasTechSpecialty);
    }

    private static List<Button> researchButtons(Player player, Planet planet) {
        return TechnologyType.mainFour.stream()
                .filter(planet::hasTechSpecialty)
                .map(type -> researchButton(player, type))
                .toList();
    }

    private static Button researchButton(Player player, TechnologyType type) {
        String id = player.factionButtonChecker() + "getAllTechOfType_" + type + "_noPay";
        String label = "Research a " + type.readableName() + " Technology";
        return switch (type) {
            case PROPULSION -> Buttons.blue(id, label, type.emoji());
            case BIOTIC -> Buttons.green(id, label, type.emoji());
            case WARFARE -> Buttons.red(id, label, type.emoji());
            default -> Buttons.gray(id, label, type.emoji());
        };
    }
}

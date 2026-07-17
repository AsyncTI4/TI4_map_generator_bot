package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.explore.ExploreService;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class RevenantLeadersHandler {
    private static final String REVARCAGENT = "revenantarcanumagent";
    private static final String USE_REVARCAGENT = "useRevArcanumAgent";
    private static final String REVARCAGENT_EXPLORE_OPTIONS = "revArcanumAgentExploreOptions_";
    private static final String REVARCAGENT_PLANET = "useRevArcanumAgentPlanet_";
    private static final String REVARCAGENT_WINDOW = "revArcanumAgentWindow_";
    private static final String REVARCAGENT_TRAIT = "useRevArcanumAgentTrait_";
    private static final Set<String> EXPLORATION_TRAITS =
            Set.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL);

    // Green Revenant Leader Set
    public static void addRevArcanumAgentButtons(List<Button> buttons, Game game, Player player, Planet planet) {
        if (buttons.isEmpty() || game == null || player == null || planet == null) {
            return;
        }

        boolean agentIsReady = game.getRealPlayers().stream().anyMatch(p -> p.hasUnexhaustedLeader(REVARCAGENT));
        if (!agentIsReady) {
            return;
        }

        String key = REVARCAGENT_EXPLORE_OPTIONS + player.getFaction();
        String options = game.getStoredValue(key);
        if (!List.of(options.split(",")).contains(planet.getName())) {
            game.setStoredValue(key, options.isEmpty() ? planet.getName() : options + "," + planet.getName());
        }

        if (player.hasUnexhaustedLeader(REVARCAGENT)
                && buttons.size() < 25
                && buttons.stream().noneMatch(button -> button.getCustomId().endsWith(USE_REVARCAGENT))) {
            buttons.add(Buttons.green(player.factionButtonChecker() + USE_REVARCAGENT, "Use Runebearer Lothos"));
        }
    }

    @ButtonHandler(USE_REVARCAGENT)
    public static void useRevArcanumAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Leader agent = player.getLeader(REVARCAGENT).orElse(null);
        if (agent == null || !player.hasUnexhaustedLeader(REVARCAGENT)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Runebearer Lothos_ is no longer available.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        String targetFaction = buttonID.substring(USE_REVARCAGENT.length());
        if ("_other".equals(targetFaction)) {
            List<Button> targetButtons = new ArrayList<>();
            for (Player target : game.getRealPlayers()) {
                if (target == player
                        || game.getStoredValue(REVARCAGENT_EXPLORE_OPTIONS + target.getFaction())
                                .isEmpty()) {
                    continue;
                }
                targetButtons.add(Buttons.green(
                        player.factionButtonChecker() + USE_REVARCAGENT + "_" + target.getFaction(),
                        target.getFactionNameOrColor()));
            }
            if (targetButtons.isEmpty()) {
                MessageHelper.sendEphemeralMessageToEventChannel(
                        event, "No other player currently has an exploration prompt.");
                return;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", choose a player to use _Runebearer Lothos_ on.",
                    targetButtons);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Player explorer = targetFaction.isEmpty()
                ? player
                : targetFaction.startsWith("_") ? game.getPlayerFromColorOrFaction(targetFaction.substring(1)) : null;
        if (explorer == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player is no longer eligible.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        List<String> eligiblePlanets = new ArrayList<>();
        String options = game.getStoredValue(REVARCAGENT_EXPLORE_OPTIONS + explorer.getFaction());
        for (String planetName : options.split(",")) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || !explorer.getPlanetsAllianceMode().contains(planetName)
                    || planet.getPlanetTypes().stream().noneMatch(EXPLORATION_TRAITS::contains)) {
                continue;
            }

            eligiblePlanets.add(planetName);
            buttons.add(Buttons.green(
                    explorer.factionButtonChecker() + REVARCAGENT_PLANET + planetName,
                    "Explore " + planet.getRepresentation(game)));

            if (buttons.size() == 25) {
                break;
            }
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player has no eligible planets to explore.");
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        game.removeStoredValue(REVARCAGENT_EXPLORE_OPTIONS + explorer.getFaction());
        game.setStoredValue(REVARCAGENT_WINDOW + explorer.getFaction(), String.join(",", eligiblePlanets));
        ExhaustLeaderService.exhaustLeader(game, player, agent);
        if (explorer != player) {
            ActionCardHelper.drawActionCards(player, 1);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                explorer.getRepresentation() + ", choose a planet to explore with _Runebearer Lothos_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REVARCAGENT_PLANET)
    public static void selectRevArcanumAgentPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(REVARCAGENT_PLANET.length());
        String key = REVARCAGENT_WINDOW + player.getFaction();
        if (!List.of(game.getStoredValue(key).split(",")).contains(planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null || !player.getPlanetsAllianceMode().contains(planetName)) {
            game.removeStoredValue(key);
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(key, planetName);
        List<Button> traitButtons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + REVARCAGENT_TRAIT + planetName + "|cultural",
                        "Explore as Cultural"),
                Buttons.green(
                        player.factionButtonChecker() + REVARCAGENT_TRAIT + planetName + "|hazardous",
                        "Explore as Hazardous"),
                Buttons.green(
                        player.factionButtonChecker() + REVARCAGENT_TRAIT + planetName + "|industrial",
                        "Explore as Industrial"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentation() + ", choose how to explore " + planet.getRepresentation(game)
                        + " with _Runebearer Lothos_.",
                traitButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REVARCAGENT_TRAIT)
    public static void resolveRevArcanumAgentTrait(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(REVARCAGENT_TRAIT.length()).split("\\|", 2);
        if (payload.length != 2 || !payload[0].equals(game.getStoredValue(REVARCAGENT_WINDOW + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planetName = payload[0];
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null || !player.getPlanetsAllianceMode().contains(planetName)) {
            game.removeStoredValue(REVARCAGENT_WINDOW + player.getFaction());
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(REVARCAGENT_WINDOW + player.getFaction());
        ButtonHelper.deleteMessage(event);
        ExploreService.explorePlanet(
                event, game.getTileFromPlanet(planetName), planetName, payload[1], player, false, game, 1, false);
    }
}

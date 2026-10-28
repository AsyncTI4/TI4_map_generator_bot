package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.planet.PlanetService;

public final class LanefirAgent extends AgentModule<LanefirAgent.Exploration> {

    static final String ID = "lanefiragent";
    private static final String RESOLVE = "lanefirAgentRes_";
    private static final String DECLINE = "Decline_";
    private static final String ACCEPT = "Accept_";
    private static final String FRONTIER = "frontier";
    private static final String SEPARATOR = "_";

    public record Exploration(String trait, Tile tile, String planet) {

        boolean isFrontier() {
            return planet == null;
        }
    }

    public LanefirAgent() {
        super(ID, "Vassa Hagi, the Lanefir");
    }

    public static boolean canRedraw(Player player) {
        return player.hasUnexhaustedLeader(ID);
    }

    public static String offerMessage(Game game, Player player) {
        return player.getRepresentationUnfogged() + " You have " + AgentLifecycle.agentNameForHolder(game, player, ID)
                + ", and thus may decline this exploration to draw another one instead.";
    }

    public static List<Button> frontierButtons(Player player, String cardID, Tile tile) {
        return offerButtons(
                player,
                cardID,
                String.join(SEPARATOR, FRONTIER, cardID, tile.getPosition()),
                String.join(SEPARATOR, FRONTIER, tile.getPosition()));
    }

    public static List<Button> planetButtons(Player player, String drawColor, String cardID, String planetName) {
        return offerButtons(
                player,
                cardID,
                String.join(SEPARATOR, drawColor, cardID, planetName),
                String.join(SEPARATOR, drawColor, planetName));
    }

    private static List<Button> offerButtons(
            Player player, String cardID, String declinePayload, String acceptPayload) {
        ExploreModel card = Mapper.getExplore(cardID);
        String owner = player.factionButtonChecker();
        return List.of(
                Buttons.green(owner + RESOLVE + DECLINE + declinePayload, "Choose " + card.getName()),
                Buttons.green(owner + RESOLVE + ACCEPT + acceptPayload, useLabel(player, ID, "Lanefir Agent")));
    }

    @ButtonHandler(RESOLVE)
    static void resolveChoice(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String choice = buttonID.substring(RESOLVE.length());
        if (choice.startsWith(ACCEPT)) {
            if (AgentLifecycle.use(game, player, ID, choice.substring(ACCEPT.length()), event)) {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }
        if (choice.startsWith(DECLINE)) {
            keepFirstExploration(event, game, player, choice.substring(DECLINE.length()));
            ButtonHelper.deleteMessage(event);
        }
    }

    private static void keepFirstExploration(ButtonInteractionEvent event, Game game, Player player, String payload) {
        if (payload.startsWith(FRONTIER + SEPARATOR)) {
            String cardAndPosition = payload.substring(FRONTIER.length() + SEPARATOR.length());
            Tile tile = game.getTileByPosition(StringUtils.substringAfterLast(cardAndPosition, SEPARATOR));
            if (tile != null) {
                ExploreService.expFrontAlreadyDone(
                        event, tile, game, player, StringUtils.substringBeforeLast(cardAndPosition, SEPARATOR));
            }
            return;
        }
        String[] parts = payload.split(SEPARATOR, 3);
        if (parts.length != 3) {
            return;
        }
        String drawColor = parts[0];
        String planetName = parts[2];
        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            return;
        }
        String messageText = player.getRepresentation() + " explored the planet "
                + ExploreEmojis.getTraitEmoji(drawColor)
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " in tile "
                + tile.getPosition() + ":";
        ExploreService.resolveExplore(event, parts[1], tile, planetName, messageText, player, game);
        applyPlanetExploreFollowUps(game, player, planetName, event.getMessageChannel());
    }

    @Override
    public Optional<Exploration> decode(Game game, Player user, String payload) {
        String trait = StringUtils.substringBefore(payload, SEPARATOR);
        String location = StringUtils.substringAfter(payload, SEPARATOR);
        if (trait.isEmpty() || location.isEmpty()) {
            return Optional.empty();
        }
        if (FRONTIER.equals(trait)) {
            return Optional.ofNullable(game.getTileByPosition(location))
                    .map(tile -> new Exploration(Constants.FRONTIER, tile, null));
        }
        return Optional.ofNullable(game.getTileFromPlanet(location))
                .map(tile -> new Exploration(trait, tile, location));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Exploration> use) {
        Game game = use.game();
        Player player = use.user();
        Exploration exploration = use.payload();
        GenericInteractionCreateEvent event = use.event();
        MessageChannel channel = event == null ? player.getCorrectChannel() : event.getMessageChannel();
        String cardID = game.drawExplore(exploration.trait());
        if (cardID == null) {
            MessageHelper.sendMessageToChannel(channel, "There is no exploration card left to draw.");
            return AgentOutcome.none();
        }
        if (exploration.isFrontier()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    "Found a " + Mapper.getExplore(cardID).getName() + " in "
                            + exploration.tile().getRepresentation());
            ExploreService.expFrontAlreadyDone(event, exploration.tile(), game, player, cardID);
            return AgentOutcome.none();
        }
        String planetName = exploration.planet();
        String messageText = player.getRepresentation() + " explored "
                + ExploreEmojis.getTraitEmoji(exploration.trait()) + "Planet "
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " *(tile "
                + exploration.tile().getPosition() + ")*:";
        ExploreService.resolveExplore(event, cardID, exploration.tile(), planetName, messageText, player, game);
        applyPlanetExploreFollowUps(game, player, planetName, channel);
        return AgentOutcome.none();
    }

    private static void applyPlanetExploreFollowUps(
            Game game, Player player, String planetName, MessageChannel channel) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && game.getPhaseOfGame().contains("agenda")) {
            PlanetService.refreshPlanet(player, planetName);
            MessageHelper.sendMessageToChannel(
                    channel, "Planet has been readied because of Quaxdol Junitas, the Florzen Commander.");
            AgendaHelper.listVoteCountIfUnfogged(game);
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
            UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
            game.getTileFromPlanet(planetName).getUnitHolders().get(planetName).addUnit(infKey, 1);
            MessageHelper.sendMessageToChannel(
                    channel, "Added 1 infantry to planet because of Master Halbert, the Lanefir Commander.");
        }
        if (player.hasTech("dslaner")) {
            player.setAtsCount(player.getAtsCount() + 1);
            MessageHelper.sendMessageToChannel(
                    channel, player.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
        }
    }
}

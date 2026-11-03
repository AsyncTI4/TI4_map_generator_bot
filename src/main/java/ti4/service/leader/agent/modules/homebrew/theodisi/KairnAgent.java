package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class KairnAgent extends AgentModule<KairnAgent.Explorer> {

    static final String ID = "kairnagent";
    private static final List<String> TRAITS = List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL);
    private static final String CHOOSE_TARGET = "useKairnAgent";
    private static final String LEGACY_USE_DIRECT = "useKairnAgentDirect_";
    private static final String LEGACY_USE_INTERRUPT = "useKairnAgentInterrupt_";
    private static final String LEGACY_SELECT_TARGET = "selectKairnAgentTarget_";
    private static final String SELECT_PLANET = "selectKairnAgentPlanet_";
    private static final String SELECT_TRAIT = "selectKairnAgentTrait_";
    private static final String RESOLVE_EXPLORE = "resolveKairnAgentExplore_";
    private static final String DRAWN = "kairnAgentDraw_";
    private static final String SEPARATOR = "|";
    private static final int CARDS_DRAWN = 3;

    public record Explorer(Player target, Planet planet, String trait) {

        boolean exploresNow() {
            return planet != null;
        }
    }

    public KairnAgent() {
        super(ID, "Draven Callas, the Kairn");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + CHOOSE_TARGET,
                useLabel(holder, ID, "Kairn Agent", " on Another Player"),
                FactionEmojis.kairn);
    }

    public static boolean offerExploreInterrupt(
            ButtonInteractionEvent event, Game game, Player player, String buttonID, String[] exploreData) {
        Planet planet = exploreData.length > 2 && game != null ? game.getUnitHolderFromPlanet(exploreData[1]) : null;
        String trait = exploreData.length > 2 ? exploreData[2] : "";
        if (player == null || planet == null || !player.hasUnexhaustedLeader(ID) || !TRAITS.contains(trait)) {
            return false;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust **Draven Callas** instead of resolving this explore.",
                List.of(
                        interruptButton(player, planet, trait),
                        Buttons.red(
                                player.factionButtonChecker()
                                        + buttonID.replace("movedNExplored_", "movedNExplored_skipKairnAgent_"),
                                "Explore Normally",
                                ExploreEmojis.getTraitEmoji(trait))));
        ButtonHelper.deleteMessage(event);
        return true;
    }

    static Button interruptButton(Player holder, Planet planet, String trait) {
        return Buttons.gray(
                ownedButtonId(holder, ID, planet.getName() + SEPARATOR + trait),
                useLabel(holder, ID, "Draven Callas"),
                ExploreEmojis.getTraitEmoji(trait));
    }

    @ButtonHandler(CHOOSE_TARGET)
    static void chooseTarget(ButtonInteractionEvent event, Game game, Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        List<Button> buttons = targetButtons(game, holder);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No other player controls an eligible planet.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCorrectChannel(),
                holder.getRepresentation() + ", choose the player who will use **Draven Callas**.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    static List<Button> targetButtons(Game game, Player holder) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != holder && !planetButtons(game, holder, target).isEmpty()) {
                buttons.add(Buttons.gray(
                        ownedButtonId(holder, ID, target.getFaction()),
                        "Use on " + target.getColor(),
                        target.getFactionEmojiOrColor()));
            }
        }
        return buttons;
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE_INTERRUPT)
    static void legacyUseInterrupt(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_USE_INTERRUPT.length()));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE_DIRECT)
    static void legacyUseDirect(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_USE_DIRECT.length()));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_SELECT_TARGET)
    static void legacySelectTarget(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_SELECT_TARGET.length()));
    }

    @Override
    public Optional<Explorer> decode(Game game, Player user, String payload) {
        if (!StringUtils.contains(payload, SEPARATOR)) {
            return AgentTargets.player(game, payload).map(target -> new Explorer(target, null, null));
        }
        String[] parts = StringUtils.split(payload, SEPARATOR);
        Planet planet = parts.length == 2 ? game.getUnitHolderFromPlanet(parts[0]) : null;
        if (planet == null) {
            return Optional.empty();
        }
        return Optional.of(new Explorer(user, planet, parts[1]));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Explorer explorer) {
        if (explorer.exploresNow()) {
            if (!TRAITS.contains(explorer.trait())) {
                return Optional.of("Draven Callas can only draw cultural, hazardous or industrial explores");
            }
            return Optional.empty();
        }
        if (explorer.target() == user) {
            return Optional.of("Choose another player for Draven Callas");
        }
        if (planetButtons(game, user, explorer.target()).isEmpty()) {
            return Optional.of(explorer.target().getRepresentationNoPing() + " controls no eligible planet");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Explorer> use) {
        Explorer explorer = use.payload();
        if (explorer.exploresNow()) {
            drawExplores(use.game(), use.user(), use.user(), explorer.planet(), explorer.trait());
            return AgentOutcome.none().deletingPressedMessage();
        }
        Player target = explorer.target();
        return AgentOutcome.of(Message.addressedTo(
                        target,
                        ", " + use.agentNameFor(target)
                                + " was exhausted for you. Choose one of your controlled planets.",
                        planetButtons(use.game(), use.user(), target)))
                .deletingPressedMessage();
    }

    @ButtonHandler(SELECT_PLANET)
    static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(SELECT_PLANET.length());
        Player holder = game.getRealPlayers().stream()
                .filter(candidate -> payload.startsWith(candidate.getFaction() + "_"))
                .findFirst()
                .orElse(null);
        if (holder == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        PlanetTargetSpec targetSpec = planetSpec(player.factionButtonChecker() + SELECT_PLANET + holder.getFaction());
        if (PlanetTargetService.handlePlanetPage(event, game, player, buttonID, targetSpec)) {
            return;
        }
        var resolvedTarget = PlanetTargetService.resolve(
                game,
                player,
                buttonID,
                targetSpec,
                target -> target.owner() == player && player.getPlanets().contains(target.planetId()));
        Planet planet = resolvedTarget == null ? null : resolvedTarget.unitHolder();
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String trait : planet.getPlanetTypes()) {
            if (TRAITS.contains(trait)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker()
                                + SELECT_TRAIT
                                + holder.getFaction()
                                + SEPARATOR
                                + planet.getName()
                                + SEPARATOR
                                + trait,
                        "Draw " + CARDS_DRAWN + " " + StringUtils.capitalize(trait) + " explores",
                        ExploreEmojis.getTraitEmoji(trait)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose an eligible trait of " + planet.getRepresentation(game)
                        + ". You will resolve 1 of " + CARDS_DRAWN
                        + " cards drawn from that deck and shuffle the others back.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TRAIT)
    static void selectTrait(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_TRAIT.length()).split("\\|", 3);
        Player holder = values.length == 3 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Planet planet = values.length == 3 ? game.getUnitHolderFromPlanet(values[1]) : null;
        if (holder == null
                || planet == null
                || !player.getPlanets().contains(planet.getName())
                || !planet.getPlanetTypes().contains(values[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        drawExplores(game, holder, player, planet, values[2]);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_EXPLORE)
    static void resolveExplore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(RESOLVE_EXPLORE.length()).split("\\|", 4);
        Player holder = values.length == 4 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Planet planet = values.length == 4 ? game.getUnitHolderFromPlanet(values[1]) : null;
        String key = holder == null ? "" : drawnKey(holder, player);
        List<String> drawn = key.isEmpty() || game.getStoredValue(key).isEmpty()
                ? List.of()
                : List.of(game.getStoredValue(key).split(","));
        ExploreModel explore = values.length == 4 ? Mapper.getExplore(values[3]) : null;
        Tile tile = planet == null ? null : game.getTileFromPlanet(planet.getName());
        if (planet == null || explore == null || tile == null || !drawn.contains(values[3])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        for (String card : drawn) {
            if (!card.equals(values[3])) {
                game.addExplore(card);
            }
        }
        game.removeStoredValue(key);
        ExploreService.resolveExplore(
                event,
                values[3],
                tile,
                planet.getName(),
                player.getRepresentationNoPing() + " resolved _" + explore.getName() + "_ with **Draven Callas**.",
                player,
                game);
        ButtonHelper.deleteMessage(event);
    }

    private static void drawExplores(Game game, Player holder, Player target, Planet planet, String trait) {
        List<String> drawn = new ArrayList<>();
        for (int index = 0; index < CARDS_DRAWN; index++) {
            String card = game.drawExplore(trait);
            if (card != null) {
                drawn.add(card);
            }
        }
        if (drawn.isEmpty()) {
            MessageHelper.sendMessageToChannel(target.getCorrectChannel(), "There are no exploration cards to draw.");
            return;
        }
        game.setStoredValue(drawnKey(holder, target), String.join(",", drawn));
        List<ExploreModel> explores =
                drawn.stream().map(Mapper::getExplore).filter(Objects::nonNull).toList();
        List<Button> buttons = explores.stream()
                .map(explore -> Buttons.green(
                        target.factionButtonChecker()
                                + RESOLVE_EXPLORE
                                + holder.getFaction()
                                + SEPARATOR
                                + planet.getName()
                                + SEPARATOR
                                + trait
                                + SEPARATOR
                                + explore.getId(),
                        "Resolve " + explore.getName(),
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
        List<MessageEmbed> embeds =
                explores.stream().map(ExploreModel::getRepresentationEmbed).toList();
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", choose 1 of these " + StringUtils.capitalize(trait)
                        + " exploration cards to resolve with **Draven Callas**. The others will be shuffled back.",
                embeds,
                buttons);
    }

    private static String drawnKey(Player holder, Player target) {
        return DRAWN + holder.getFaction() + "_" + target.getFaction();
    }

    private static PlanetTargetSpec planetSpec(String buttonPrefix) {
        return PlanetTargetSpec.of(buttonPrefix)
                .requiringController()
                .where(planet -> planet.getPlanetTypes().stream().anyMatch(TRAITS::contains));
    }

    static List<Button> planetButtons(Game game, Player holder, Player target) {
        String buttonPrefix = target.factionButtonChecker() + SELECT_PLANET + holder.getFaction();
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (game.getPlayerThatControlsPlanet(planet.getName(), true) == target
                        && planet.getPlanetTypes().stream().anyMatch(TRAITS::contains)) {
                    buttons.add(Buttons.gray(
                            buttonPrefix + "_" + planet.getName(), "Choose " + planet.getRepresentation(game)));
                }
            }
        }
        return PlanetTargetService.targetButtons(game, target, planetSpec(buttonPrefix), buttons);
    }
}

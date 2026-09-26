package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.turn.StartTurnService;

@UtilityClass
public class KairnLeadershandler {
    private static final List<String> EXPLORE_TYPES =
            List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL, Constants.FRONTIER);
    private static final String ADD_PURGED_TO_TOP = "addPurgedExploreToTopKairn_";
    private static final String KAIRN_HERO_CARD_COUNT = "kairnHeroPurgedExploreCount_";
    private static final String FINISH_PURGED_EXPLORES = "finishKairnHeroPurgedExplores";
    private static final String KAIRN_HERO_PLANETS = "kairnHeroPlanets_";
    private static final String CHOOSE_KAIRN_HERO_PLANET = "chooseKairnHeroPlanet_";
    private static final String FINISH_KAIRN_HERO = "finishKairnHero";
    private static final String EXPLORE_COMMANDER_PLANET = "explorePlanetWithKairnCommander_";
    private static final String USE_KAIRN_AGENT = "useKairnAgent";
    private static final String USE_KAIRN_AGENT_DIRECT = "useKairnAgentDirect_";
    private static final String SELECT_KAIRN_AGENT_TARGET = "selectKairnAgentTarget_";
    private static final String SELECT_KAIRN_AGENT_PLANET = "selectKairnAgentPlanet_";
    private static final String SELECT_KAIRN_AGENT_TRAIT = "selectKairnAgentTrait_";
    private static final String RESOLVE_KAIRN_AGENT_EXPLORE = "resolveKairnAgentExplore_";
    private static final String KAIRN_AGENT_DRAW = "kairnAgentDraw_";

    // Hero
    private static List<Button> getPurgedExploreButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String exploreId : getPurgedExploreIds(game)) {
            ExploreModel explore = Mapper.getExplore(exploreId);
            if (explore != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + ADD_PURGED_TO_TOP + exploreId,
                        explore.getName() + " (" + explore.getType() + ")"));
            }
        }
        return buttons;
    }

    private static List<String> getPurgedExploreIds(Game game) {
        var deck = Mapper.getDeck(game.getExplorationDeckID());
        if (deck == null) {
            return List.of();
        }

        Set<String> cardsStillInDecksOrDiscards = new HashSet<>();
        for (String type : EXPLORE_TYPES) {
            cardsStillInDecksOrDiscards.addAll(game.getExploreDeck(type));
            cardsStillInDecksOrDiscards.addAll(game.getExploreDiscard(type));
        }
        return deck.getNewDeck().stream()
                .filter(exploreId -> !cardsStillInDecksOrDiscards.contains(exploreId))
                .filter(exploreId -> !isExploreInAPlayersPlayArea(game, exploreId))
                .filter(exploreId -> {
                    ExploreModel explore = Mapper.getExplore(exploreId);
                    return explore != null
                            && !"token".equalsIgnoreCase(explore.getResolution())
                            && !"attach".equalsIgnoreCase(explore.getResolution());
                })
                .toList();
    }

    private static boolean isExploreInAPlayersPlayArea(Game game, String exploreId) {
        ExploreModel explore = Mapper.getExplore(exploreId);
        if (explore == null) {
            return false;
        }
        for (Player player : game.getPlayers().values()) {
            if (player.getFragments().contains(exploreId) || player.getRelics().contains(exploreId)) {
                return true;
            }
            if ("leader".equalsIgnoreCase(explore.getResolution())
                    && player.getLeaderIDs().contains(exploreId.replaceFirst("^gain", ""))) {
                return true;
            }
        }
        return false;
    }

    public static void startKairnHero(GenericInteractionCreateEvent event, Game game, Player player) {
        game.setStoredValue(KAIRN_HERO_CARD_COUNT + player.getFaction(), "0");
        showPurgedExploreChoices(event, game, player);
    }

    public static void clearKairnHeroStoredValues(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(KAIRN_HERO_CARD_COUNT + player.getFaction());
            game.removeStoredValue(KAIRN_HERO_PLANETS + player.getFaction());
        }
    }

    private static void showPurgedExploreChoices(GenericInteractionCreateEvent event, Game game, Player player) {
        int selected = Integer.parseInt(game.getStoredValue(KAIRN_HERO_CARD_COUNT + player.getFaction()));
        if (selected >= 5) {
            showKairnHeroPlanetChoices(event, game, player);
            return;
        }

        List<Button> buttons = getPurgedExploreButtons(game, player);
        if (buttons.isEmpty()) {
            showKairnHeroPlanetChoices(event, game, player);
            return;
        }

        List<Button> extraButtons = List.of(
                Buttons.red(player.factionButtonChecker() + FINISH_PURGED_EXPLORES, "Continue to Planet Explores"));
        String buttonPrefix = player.factionButtonChecker() + ADD_PURGED_TO_TOP;
        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(buttons, extraButtons, buttonPrefix, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose a purged exploration card to place on top of its deck. "
                        + "(" + selected + "/5 chosen)",
                displayedButtons);
    }

    @ButtonHandler(ADD_PURGED_TO_TOP)
    public static void addPurgedExploreToTop(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String countKey = KAIRN_HERO_CARD_COUNT + player.getFaction();
        String countText = game.getStoredValue(countKey);
        if (countText.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int selected = Integer.parseInt(countText);
        List<Button> buttons = getPurgedExploreButtons(game, player);
        List<Button> extraButtons = List.of(
                Buttons.red(player.factionButtonChecker() + FINISH_PURGED_EXPLORES, "Continue to Planet Explores"));
        String message =
                player.getRepresentation() + ", please choose a purged exploration card to place on top of its deck. "
                        + "(" + selected + "/5 chosen)";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                extraButtons,
                message,
                player.factionButtonChecker() + ADD_PURGED_TO_TOP,
                buttonID)) {
            return;
        }

        String exploreId = buttonID.substring(ADD_PURGED_TO_TOP.length());
        if (selected >= 5 || !getPurgedExploreIds(game).contains(exploreId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExploreModel explore = Mapper.getExplore(exploreId);
        if (explore == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.getAllExplores().add(0, exploreId);
        game.setStoredValue(countKey, Integer.toString(selected + 1));

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed _" + explore.getName() + "_ on top of the " + explore.getType()
                        + " exploration deck.");

        ButtonHelper.deleteMessage(event);
        showPurgedExploreChoices(event, game, player);
    }

    @ButtonHandler(FINISH_PURGED_EXPLORES)
    public static void finishPurgedExploreChoices(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || game.getStoredValue(KAIRN_HERO_CARD_COUNT + player.getFaction())
                        .isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(KAIRN_HERO_PLANETS + player.getFaction(), "");
        ButtonHelper.deleteMessage(event);
        showKairnHeroPlanetChoices(event, game, player);
    }

    private static void showKairnHeroPlanetChoices(GenericInteractionCreateEvent event, Game game, Player player) {
        String selectedPlanets = game.getStoredValue(KAIRN_HERO_PLANETS + player.getFaction());
        List<String> selected = selectedPlanets.isEmpty() ? List.of() : List.of(selectedPlanets.split(","));

        List<Button> buttons = getKairnHeroPlanetButtons(game, player, selected);
        List<Button> extraButtons = List.of(
                Buttons.red(player.factionButtonChecker() + FINISH_KAIRN_HERO, "Done Resolving New Ancestral Home"));
        String buttonPrefix = player.factionButtonChecker() + CHOOSE_KAIRN_HERO_PLANET;
        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(buttons, extraButtons, buttonPrefix, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose up to 5 planets you control to explore. " + "("
                        + selected.size() + "/5 chosen)",
                displayedButtons);
    }

    private static List<Button> getKairnHeroPlanetButtons(Game game, Player player, List<String> selected) {
        List<Button> buttons = new ArrayList<>();
        if (selected.size() >= 5) {
            return buttons;
        }
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null || planet.getPlanetTypes().isEmpty() || selected.contains(planetName)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_KAIRN_HERO_PLANET + planetName,
                    "Explore " + planet.getRepresentation(game)));
        }
        return buttons;
    }

    @ButtonHandler(CHOOSE_KAIRN_HERO_PLANET)
    public static void chooseKairnHeroPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetsKey = KAIRN_HERO_PLANETS + player.getFaction();
        List<String> selectedPlanets = game.getStoredValue(planetsKey).isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(List.of(game.getStoredValue(planetsKey).split(",")));

        List<Button> buttons = getKairnHeroPlanetButtons(game, player, selectedPlanets);
        List<Button> extraButtons = List.of(
                Buttons.red(player.factionButtonChecker() + FINISH_KAIRN_HERO, "Done Resolving New Ancestral Home"));
        String message = player.getRepresentation() + ", please choose up to 5 planets you control to explore. " + "("
                + selectedPlanets.size() + "/5 chosen)";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                extraButtons,
                message,
                player.factionButtonChecker() + CHOOSE_KAIRN_HERO_PLANET,
                buttonID)) {
            return;
        }

        String planetName = buttonID.substring(CHOOSE_KAIRN_HERO_PLANET.length());

        Planet planet = game.getPlanetsInfo().get(planetName);
        if (selectedPlanets.size() >= 5
                || selectedPlanets.contains(planetName)
                || planet == null
                || planet.getPlanetTypes().isEmpty()
                || !player.getPlanets().contains(planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        selectedPlanets.add(planetName);
        game.setStoredValue(planetsKey, String.join(",", selectedPlanets));

        List<Button> exploreButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
        if (!exploreButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", please choose how to explore " + planet.getRepresentation(game)
                            + ".",
                    exploreButtons);
        }

        ButtonHelper.deleteMessage(event);
        showKairnHeroPlanetChoices(event, game, player);
    }

    @ButtonHandler(FINISH_KAIRN_HERO)
    public static void finishKairnHero(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        game.removeStoredValue(KAIRN_HERO_CARD_COUNT + player.getFaction());
        game.removeStoredValue(KAIRN_HERO_PLANETS + player.getFaction());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getRepresentation() + " finished resolving _New Ancestral Home_.");

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose whether to end your turn or take another action.",
                StartTurnService.getStartOfTurnButtons(player, game, true, event));

        ButtonHelper.deleteMessage(event);
    }

    // Commander
    public static List<Button> offerSerelVennButtons(Player player, Game game) {
        List<Button> eligiblePlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            if (ButtonHelper.isPlanetLegendaryOrHome(planetName, game, false, player)) {
                continue;
            }

            Planet planet = game.getPlanetsInfo().get(planetName);
            if (planet == null || planet.isFake()) {
                continue;
            }

            eligiblePlanets.add(Buttons.green(
                    player.factionButtonChecker() + EXPLORE_COMMANDER_PLANET + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }

        return eligiblePlanets;
    }

    @ButtonHandler(EXPLORE_COMMANDER_PLANET)
    public static void exploreChosenKairnPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || game == null) {
            return;
        }

        String planetName = buttonID.replace(EXPLORE_COMMANDER_PLANET, "");
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve planet name.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String trait : List.of("cultural", "hazardous", "industrial")) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "movedNExplored_filler_" + planetName + "_" + trait,
                    "Explore " + Helper.getPlanetRepresentation(planetName, game) + " As "
                            + StringUtils.capitalize(trait),
                    ExploreEmojis.getTraitEmoji(trait)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose an exploration trait for "
                        + Helper.getPlanetRepresentation(planetName, game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    // Agent
    public static Button getKairnAgentCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_KAIRN_AGENT,
                "Use Kairn Agent on Another Player",
                FactionEmojis.kairn);
    }

    public static Button getKairnAgentExplorePromptButton(Player player, Planet planet, String trait) {
        if (player == null
                || planet == null
                || !player.hasUnexhaustedLeader("kairnagent")
                || !player.getPlanets().contains(planet.getName())
                || !planet.getPlanetTypes().contains(trait)) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + USE_KAIRN_AGENT_DIRECT + planet.getName() + "|" + trait,
                "Exhaust Draven Callas",
                ExploreEmojis.getTraitEmoji(trait));
    }

    @ButtonHandler(USE_KAIRN_AGENT)
    public static void useKairnAgent(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnexhaustedLeader("kairnagent")) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player
                    && !getKairnAgentPlanetButtons(game, player, target).isEmpty()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT_KAIRN_AGENT_TARGET + target.getFaction(),
                        "Use on " + target.getColor(),
                        target.getFactionEmojiOrColor()));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No other player controls an eligible planet.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose the player who will use **Draven Callas**.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(USE_KAIRN_AGENT_DIRECT)
    public static void useKairnAgentDirect(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(USE_KAIRN_AGENT_DIRECT.length()).split("\\|", 2);
        Planet planet = values.length == 2 && game != null ? game.getUnitHolderFromPlanet(values[0]) : null;
        if (player == null
                || planet == null
                || !player.hasUnexhaustedLeader("kairnagent")
                || !player.getPlanets().contains(planet.getName())
                || !planet.getPlanetTypes().contains(values[1])) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        Leader agent = player.getLeader("kairnagent").orElse(null);
        if (agent == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, player, agent);
        drawKairnAgentExplores(event, game, player, player, planet, values[1]);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_KAIRN_AGENT_TARGET)
    public static void selectKairnAgentTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game == null
                ? null
                : game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_KAIRN_AGENT_TARGET.length()));
        Leader agent = player == null ? null : player.getLeader("kairnagent").orElse(null);
        if (target == null
                || agent == null
                || !player.hasUnexhaustedLeader("kairnagent")
                || target == player
                || getKairnAgentPlanetButtons(game, player, target).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, player, agent);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted **Draven Callas** for you. Choose one of your controlled planets.",
                getKairnAgentPlanetButtons(game, player, target));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_KAIRN_AGENT_PLANET)
    public static void selectKairnAgentPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_KAIRN_AGENT_PLANET.length()).split("_", 2);
        Player agentOwner = values.length == 2 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        PlanetTargetSpec targetSpec = agentOwner == null || player == null
                ? null
                : PlanetTargetSpec.of(
                                player.factionButtonChecker() + SELECT_KAIRN_AGENT_PLANET + agentOwner.getFaction())
                        .requiringController()
                        .where(planet -> planet.getPlanetTypes().stream().anyMatch(trait -> List.of(
                                        Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)
                                .contains(trait)));
        if (targetSpec != null && PlanetTargetService.handlePlanetPage(event, game, player, buttonID, targetSpec)) {
            return;
        }
        var resolvedTarget = targetSpec == null
                ? null
                : PlanetTargetService.resolve(
                        game,
                        player,
                        buttonID,
                        targetSpec,
                        target ->
                                target.owner() == player && player.getPlanets().contains(target.planetId()));
        Planet planet = resolvedTarget == null ? null : resolvedTarget.unitHolder();
        if (agentOwner == null || planet == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String trait : planet.getPlanetTypes()) {
            if (List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)
                    .contains(trait)) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT_KAIRN_AGENT_TRAIT + agentOwner.getFaction() + "|"
                                + planet.getName() + "|" + trait,
                        "Draw 3 " + StringUtils.capitalize(trait) + " explores",
                        ExploreEmojis.getTraitEmoji(trait)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose an eligible trait of " + planet.getRepresentation(game)
                        + ". You will resolve 1 of 3 cards drawn from that deck and shuffle the others back.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_KAIRN_AGENT_TRAIT)
    public static void selectKairnAgentTrait(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_KAIRN_AGENT_TRAIT.length()).split("\\|", 3);
        Player agentOwner = values.length == 3 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Planet planet = values.length == 3 && game != null ? game.getUnitHolderFromPlanet(values[1]) : null;
        if (agentOwner == null
                || planet == null
                || player == null
                || !player.getPlanets().contains(planet.getName())
                || !planet.getPlanetTypes().contains(values[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        drawKairnAgentExplores(event, game, agentOwner, player, planet, values[2]);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_KAIRN_AGENT_EXPLORE)
    public static void resolveKairnAgentExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values =
                buttonID.substring(RESOLVE_KAIRN_AGENT_EXPLORE.length()).split("\\|", 4);
        Player agentOwner = values.length == 4 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        Planet planet = values.length == 4 && game != null ? game.getUnitHolderFromPlanet(values[1]) : null;
        String key = agentOwner == null || player == null
                ? ""
                : KAIRN_AGENT_DRAW + agentOwner.getFaction() + "_" + player.getFaction();
        List<String> drawn = key.isEmpty() || game.getStoredValue(key).isEmpty()
                ? List.of()
                : List.of(game.getStoredValue(key).split(","));
        ExploreModel explore = values.length == 4 ? Mapper.getExplore(values[3]) : null;
        Tile tile = planet == null ? null : game.getTileFromPlanet(planet.getName());
        if (planet == null || explore == null || tile == null || !drawn.contains(values[3])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        for (String card : drawn) if (!card.equals(values[3])) game.addExplore(card);
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

    private static void drawKairnAgentExplores(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target, Planet planet, String trait) {
        List<String> drawn = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            String card = game.drawExplore(trait);
            if (card != null) drawn.add(card);
        }
        if (drawn.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no exploration cards to draw.");
            return;
        }
        game.setStoredValue(
                KAIRN_AGENT_DRAW + agentOwner.getFaction() + "_" + target.getFaction(), String.join(",", drawn));
        List<Button> buttons = drawn.stream()
                .map(Mapper::getExplore)
                .filter(java.util.Objects::nonNull)
                .map(explore -> Buttons.green(
                        target.factionButtonChecker() + RESOLVE_KAIRN_AGENT_EXPLORE + agentOwner.getFaction() + "|"
                                + planet.getName() + "|" + trait + "|" + explore.getId(),
                        "Resolve " + explore.getName(),
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
        List<MessageEmbed> embeds = drawn.stream()
                .map(Mapper::getExplore)
                .filter(java.util.Objects::nonNull)
                .map(ExploreModel::getRepresentationEmbed)
                .toList();
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + ", choose 1 of these " + StringUtils.capitalize(trait)
                        + " exploration cards to resolve with **Draven Callas**. The others will be shuffled back.",
                embeds,
                buttons);
    }

    private static List<Button> getKairnAgentPlanetButtons(Game game, Player agentOwner, Player target) {
        List<Button> buttons = new ArrayList<>();
        if (game == null || agentOwner == null || target == null) return buttons;
        String buttonPrefix = target.factionButtonChecker() + SELECT_KAIRN_AGENT_PLANET + agentOwner.getFaction();
        PlanetTargetSpec targetSpec = PlanetTargetSpec.of(buttonPrefix)
                .requiringController()
                .where(planet -> planet.getPlanetTypes().stream()
                        .anyMatch(trait -> List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)
                                .contains(trait)));
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (game.getPlayerThatControlsPlanet(planet.getName(), true) != target) {
                    continue;
                }
                if (planet.getPlanetTypes().stream()
                        .anyMatch(trait -> List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL)
                                .contains(trait))) {
                    buttons.add(Buttons.gray(
                            buttonPrefix + "_" + planet.getName(), "Choose " + planet.getRepresentation(game)));
                }
            }
        }
        return PlanetTargetService.targetButtons(game, target, targetSpec, buttons);
    }
}

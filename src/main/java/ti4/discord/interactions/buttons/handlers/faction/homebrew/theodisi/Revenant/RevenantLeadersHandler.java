package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.explore.ExploreService;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class RevenantLeadersHandler {
    // Revenant of Arcanum
    private static final String REVARCAGENT = "revenantarcanumagent";
    private static final String USE_REVARCAGENT = "useRevArcanumAgent";
    private static final String REVARCAGENT_EXPLORE_OPTIONS = "revArcanumAgentExploreOptions_";
    private static final String REVARCAGENT_PLANET = "useRevArcanumAgentPlanet_";
    private static final String REVARCAGENT_WINDOW = "revArcanumAgentWindow_";
    private static final String REVARCAGENT_TRAIT = "useRevArcanumAgentTrait_";
    private static final Set<String> EXPLORATION_TRAITS =
            Set.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL);
    // Revenant of Oblivion
    private static final String REVOBLCOMMANDER = "revenantoblivioncommander";
    private static final String USE_ARLIR_MIRRORED = "useArlirMirrored_";
    // Revenant of Kairn
    private static final String CHOOSE_EXP_DECK = "chooseRevKairnExpDeck_";
    private static final String CHOOSE_EXP_CARD = "chooseRevKairnExpCard_";
    private static final String BACK_TO_REV_KAIRN_DECKS = "backToRevKairnDecks";
    private static final String FINISH_REV_KAIRN_DISCARDS = "finishRevKairnDiscards";
    private static final String CHOOSE_REV_KAIRN_TRAIT = "chooseRevKairnTrait_";
    private static final String CHOOSE_REV_KAIRN_PLANET = "chooseRevKairnPlanet_";
    private static final String REV_KAIRN_HERO_ACTIVE = "revKairnHeroActive_";
    private static final String REV_KAIRN_HERO_DECKS = "revKairnHeroDecks_";
    private static final String REV_KAIRN_HERO_TRAITS = "revKairnHeroTraits_";
    private static final List<String> EXPLORE_DECK_TYPES =
            List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL, Constants.FRONTIER);

    // Green Revenant Leader Set
    // Revenant of Arcanum
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
        if (payload.length != 2
                || !EXPLORATION_TRAITS.contains(payload[1])
                || !payload[0].equals(game.getStoredValue(REVARCAGENT_WINDOW + player.getFaction()))) {
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

    // Revenant of Oblivion
    public static void addArlirMirroredButton(
            List<Button> buttons, Game game, Player player, String planetName, String cardId, String drawColor) {
        if (game == null
                || player == null
                || planetName == null
                || cardId == null
                || drawColor == null
                || Constants.FRONTIER.equals(drawColor)) {
            return;
        }

        if (!game.playerHasLeaderUnlockedOrAlliance(player, REVOBLCOMMANDER)) {
            return;
        }

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !planet.hasStructures(player)
                || buttons.size() >= 25) {
            return;
        }

        String buttonId =
                player.factionButtonChecker() + USE_ARLIR_MIRRORED + cardId + "|" + planetName + "|" + drawColor;

        buttons.add(Buttons.green(buttonId, "Use Arlir Mirrored"));
    }

    @ButtonHandler(USE_ARLIR_MIRRORED)
    public static void useArlirMirrored(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_ARLIR_MIRRORED.length()).split("\\|", 3);
        if (payload.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String originalCardId = payload[0];
        String planetName = payload[1];
        String drawColor = payload[2];

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (Constants.FRONTIER.equals(drawColor)
                || !game.playerHasLeaderUnlockedOrAlliance(player, REVOBLCOMMANDER)
                || planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !planet.hasStructures(player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "_Arlir Mirrored_ is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String secondCardId = game.drawExplore(drawColor);
        if (secondCardId == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no more exploration cards to draw.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (secondCardId.equalsIgnoreCase(originalCardId)) {
            secondCardId = game.drawExplore(drawColor);
            if (secondCardId == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no more exploration cards to draw.");
                ButtonHelper.deleteMessage(event);
                return;
            }
        }

        ExploreModel originalCard = Mapper.getExplore(originalCardId);
        ExploreModel secondCard = Mapper.getExplore(secondCardId);
        if (originalCard == null || secondCard == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find one of the exploration cards.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        "resolve_explore_" + originalCardId + "_" + planetName, "Resolve " + originalCard.getName()),
                Buttons.green("resolve_explore_" + secondCardId + "_" + planetName, "Resolve " + secondCard.getName()));

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose 1 exploration card to resolve with _Arlir Mirrored_.",
                List.of(originalCard.getRepresentationEmbed(), secondCard.getRepresentationEmbed()),
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    // Revenant of Kairn
    public static void startRevKairnHero(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        game.setStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction(), "true");
        game.setStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction(), "");
        game.setStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction(), "");
        showRevKairnHeroDecks(event, game, player);
    }

    private static void showRevKairnHeroDecks(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            return;
        }

        List<String> selectedDecks = List.of(
                game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction()).split(","));
        List<Button> buttons = new ArrayList<>();
        for (String trait : EXPLORE_DECK_TYPES) {
            if (selectedDecks.contains(trait) || game.getExploreDiscard(trait).isEmpty()) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_EXP_DECK + trait,
                    "View " + StringUtils.capitalize(trait) + " Discard"));
        }
        buttons.add(
                Buttons.red(player.factionButtonChecker() + FINISH_REV_KAIRN_DISCARDS, "Continue to Planet Explores"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose up to 1 card from each exploration discard pile to shuffle into its deck.",
                buttons);
    }

    @ButtonHandler(CHOOSE_EXP_DECK)
    public static void showRevenantExploreDiscard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String trait = buttonID.substring(CHOOSE_EXP_DECK.length());

        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORE_DECK_TYPES.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction())
                                .split(","))
                        .contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + CHOOSE_EXP_CARD + trait + "|";
        List<Button> buttons = getRevKairnExploreDiscardButtons(game, player, trait, buttonPrefix);
        List<Button> extraButtons =
                List.of(Buttons.red(player.factionButtonChecker() + BACK_TO_REV_KAIRN_DECKS, "Back to Discard Piles"));

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That explore discard pile is empty.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(buttons, extraButtons, buttonPrefix, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose an exploration card from the " + StringUtils.capitalize(trait)
                        + " discard pile.",
                displayedButtons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(BACK_TO_REV_KAIRN_DECKS)
    public static void backToRevKairnDecks(ButtonInteractionEvent event, Game game, Player player) {
        if ("true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            showRevKairnHeroDecks(event, game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_EXP_CARD)
    public static void chooseRevenantExploreCard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(CHOOSE_EXP_CARD.length());
        int traitEnd = payload.indexOf('|');
        if (traitEnd < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = payload.substring(0, traitEnd);
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORE_DECK_TYPES.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction())
                                .split(","))
                        .contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + CHOOSE_EXP_CARD + trait + "|";
        List<Button> buttons = getRevKairnExploreDiscardButtons(game, player, trait, buttonPrefix);
        List<Button> extraButtons =
                List.of(Buttons.red(player.factionButtonChecker() + BACK_TO_REV_KAIRN_DECKS, "Back to Discard Piles"));
        String message = player.getRepresentation() + ", choose an exploration card from the "
                + StringUtils.capitalize(trait) + " discard pile.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, extraButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        String exploreId = payload.substring(traitEnd + 1);
        if (!game.getExploreDiscard(trait).contains(exploreId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExploreModel explore = Mapper.getExplore(exploreId);
        if (explore == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.addExplore(exploreId);
        String selectedDecks = game.getStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction());
        game.setStoredValue(
                REV_KAIRN_HERO_DECKS + player.getFaction(),
                selectedDecks.isEmpty() ? trait : selectedDecks + "," + trait);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " shuffled _" + explore.getName() + "_ into the "
                        + StringUtils.capitalize(trait) + " exploration deck.");
        showRevKairnHeroDecks(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FINISH_REV_KAIRN_DISCARDS)
    public static void finishRevKairnDiscards(ButtonInteractionEvent event, Game game, Player player) {
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        showRevKairnHeroTraits(event, game, player);
    }

    private static void showRevKairnHeroTraits(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))) {
            return;
        }

        List<String> exploredTraits = List.of(
                game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction()).split(","));
        List<Button> buttons = new ArrayList<>();
        for (String trait : EXPLORATION_TRAITS) {
            boolean controlsTraitPlanet = player.getPlanetsAllianceMode().stream()
                    .map(game::getUnitHolderFromPlanet)
                    .anyMatch(
                            planet -> planet != null && planet.getPlanetTypes().contains(trait));
            if (controlsTraitPlanet && !exploredTraits.contains(trait)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + CHOOSE_REV_KAIRN_TRAIT + trait,
                        "Explore a " + StringUtils.capitalize(trait) + " Planet"));
            }
        }

        if (buttons.isEmpty()) {
            game.removeStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction());
            game.removeStoredValue(REV_KAIRN_HERO_DECKS + player.getFaction());
            game.removeStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has finished resolving _Zairos the First_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose a trait to explore for _Zairos the First_:",
                buttons);
    }

    @ButtonHandler(CHOOSE_REV_KAIRN_TRAIT)
    public static void chooseRevKairnTrait(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String trait = buttonID.substring(CHOOSE_REV_KAIRN_TRAIT.length());
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORATION_TRAITS.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction())
                                .split(","))
                        .contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + CHOOSE_REV_KAIRN_PLANET + trait + "|";
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet != null && planet.getPlanetTypes().contains(trait)) {
                buttons.add(Buttons.green(buttonPrefix + planetName, "Explore " + planet.getRepresentation(game)));
            }
        }

        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            showRevKairnHeroTraits(event, game, player);
            return;
        }

        String message = player.getRepresentation() + ", choose a " + StringUtils.capitalize(trait)
                + " planet to explore for _Zairos the First_.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_REV_KAIRN_PLANET)
    public static void chooseRevKairnPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(CHOOSE_REV_KAIRN_PLANET.length());
        int traitEnd = payload.indexOf('|');
        if (traitEnd < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = payload.substring(0, traitEnd);
        String planetName = payload.substring(traitEnd + 1);
        String buttonPrefix = player.factionButtonChecker() + CHOOSE_REV_KAIRN_PLANET + trait + "|";
        List<Button> buttons = new ArrayList<>();
        for (String ownedPlanetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(ownedPlanetName);
            if (planet != null && planet.getPlanetTypes().contains(trait)) {
                buttons.add(Buttons.green(buttonPrefix + ownedPlanetName, "Explore " + planet.getRepresentation(game)));
            }
        }

        String message = player.getRepresentation() + ", choose a " + StringUtils.capitalize(trait)
                + " planet to explore for _Zairos the First_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!"true".equals(game.getStoredValue(REV_KAIRN_HERO_ACTIVE + player.getFaction()))
                || !EXPLORATION_TRAITS.contains(trait)
                || List.of(game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction())
                                .split(","))
                        .contains(trait)
                || planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !planet.getPlanetTypes().contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String exploredTraits = game.getStoredValue(REV_KAIRN_HERO_TRAITS + player.getFaction());
        game.setStoredValue(
                REV_KAIRN_HERO_TRAITS + player.getFaction(),
                exploredTraits.isEmpty() ? trait : exploredTraits + "," + trait);
        ButtonHelper.deleteMessage(event);
        ExploreService.explorePlanet(
                event, game.getTileFromPlanet(planetName), planetName, trait, player, false, game, 1, false);
        showRevKairnHeroTraits(event, game, player);
    }

    private static List<Button> getRevKairnExploreDiscardButtons(
            Game game, Player player, String trait, String buttonPrefix) {
        List<Button> buttons = new ArrayList<>();
        for (String exploreId : game.getExploreDiscard(trait)) {
            ExploreModel explore = Mapper.getExplore(exploreId);
            if (explore != null) {
                buttons.add(Buttons.green(buttonPrefix + exploreId, explore.getName()));
            }
        }
        return buttons;
    }
}

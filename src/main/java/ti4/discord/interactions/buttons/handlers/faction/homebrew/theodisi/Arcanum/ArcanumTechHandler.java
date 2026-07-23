package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ArcanumTechHandler {
    private static final String SEAL_OF_REVELATION = "tharcanumbg";
    private static final String SIGIL_OF_TRANSMUTATION = "tharcanumry";
    private static final String SHUFFLE_PURGED_EXPLORE = "shuffleSealOfRevelation_";
    private static final String USE_SIGIL_OF_TRANSMUTATION = "useSigilOfTransmutation";
    private static final String SIGIL_OF_TRANSMUTATION_TILE = "sigilOfTransmutationTile_";
    private static final List<String> PRIMORDIAL_TECHS =
            List.of("tharcanumpmy", "tharcanumpmg", "tharcanumpmr", "tharcanumpmb");
    private static final List<String> EXPLORE_TYPES =
            List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL, Constants.FRONTIER);
    private static final String MIRACLE = "tharcanumpmg";
    private static final String DISCARD_AC = "miracleAcDiscard";
    private static final String GAIN_CC = "miracleGainCC";
    private static final String PLACE_INF = "miraclePlaceFourInf";
    private static final String SELECT_AC = "miracleSelectAcToDiscard_";
    private static final String PLACE_INFANTRY = "miraclePlaceInfantry_";
    private static final String FINISH_INFANTRY = "miracleFinishInfantry_";
    private static final String INFANTRY_PLACED = "miracleInfantryPlaced_";

    // Seal of Revelation
    public static void resolveSealOfRevelation(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasTech(SEAL_OF_REVELATION)) {
            return;
        }

        List<Button> buttons = getPurgedExploreButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No eligible purged exploration card is available to shuffle back in.");
            return;
        }

        String buttonPrefix = player.factionButtonChecker() + SHUFFLE_PURGED_EXPLORE;
        List<Button> displayedButtons = buttons.size() <= 25
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(buttons, null, buttonPrefix, 25, 0, false);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose 1 purged exploration card to shuffle into its deck.",
                displayedButtons);
    }

    @ButtonHandler(SHUFFLE_PURGED_EXPLORE)
    public static void shufflePurgedExplore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(SEAL_OF_REVELATION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getPurgedExploreButtons(game, player);
        String buttonPrefix = player.factionButtonChecker() + SHUFFLE_PURGED_EXPLORE;
        String message = player.getRepresentation() + ", choose 1 purged exploration card to shuffle into its deck.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        String exploreId = buttonID.substring(SHUFFLE_PURGED_EXPLORE.length());
        if (!getPurgedExploreIds(game).contains(exploreId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExploreModel explore = Mapper.getExplore(exploreId);
        if (explore == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.addExplore(exploreId);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " shuffled _" + explore.getName() + "_ into its exploration deck.");
        offerPlanetExplorationButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getPurgedExploreButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String exploreId : getPurgedExploreIds(game)) {
            ExploreModel explore = Mapper.getExplore(exploreId);
            if (explore != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SHUFFLE_PURGED_EXPLORE + exploreId, explore.getName()));
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
        for (Player otherPlayer : game.getPlayers().values()) {
            cardsStillInDecksOrDiscards.addAll(otherPlayer.getFragments());
            cardsStillInDecksOrDiscards.addAll(otherPlayer.getRelics());
            otherPlayer.getLeaders().forEach(leader -> cardsStillInDecksOrDiscards.add("gain" + leader.getId()));
        }

        return deck.getNewDeck().stream()
                .filter(exploreId -> !cardsStillInDecksOrDiscards.contains(exploreId))
                .filter(exploreId -> {
                    ExploreModel explore = Mapper.getExplore(exploreId);
                    return explore != null
                            && !"token".equalsIgnoreCase(explore.getResolution())
                            && !"attach".equalsIgnoreCase(explore.getResolution());
                })
                .toList();
    }

    public static boolean canUseSealOfRevelation(Game game) {
        return game != null && !getPurgedExploreIds(game).isEmpty();
    }

    private static void offerPlanetExplorationButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        Button done = Buttons.red(
                player.factionButtonChecker() + "finishComponentAction", "Done Resolving Seal of Revelation");
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButton(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no eligible planet to explore.",
                    done);
            return;
        }
        buttons.add(done);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), player.getRepresentation() + ", choose a planet to explore.", buttons);
    }

    // Forbidden Knowledge
    public static boolean hasFourTechsMatchingPrimordial(Player player) {
        if (player == null) {
            return false;
        }

        TechnologyModel.TechnologyType primordialType = null;
        for (String primordialTech : PRIMORDIAL_TECHS) {
            if (!player.hasTech(primordialTech)) {
                continue;
            }

            TechnologyModel techModel = Mapper.getTech(primordialTech);
            if (techModel != null) {
                primordialType = techModel.getFirstType();
            }
            break;
        }
        if (primordialType == null) {
            return false;
        }

        int matchingTechs = 0;
        for (String tech : player.getTechs()) {
            TechnologyModel techModel = Mapper.getTech(tech);
            if (techModel != null && techModel.getTypes().contains(primordialType)) {
                matchingTechs++;
            }
        }
        return matchingTechs >= 4;
    }

    // Sigil of Transmutation
    public static void offerSigilOfTransmutation(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        if (event == null
                || game == null
                || player == null
                || tile == null
                || player != game.getActivePlayer()
                || !tile.getPosition().equals(game.getActiveSystem())
                || tile.isHomeSystem(game)
                || !player.hasTechReady(SIGIL_OF_TRANSMUTATION)) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", you may exhaust _Sigil of Transmutation_ to give "
                        + tile.getRepresentationForButtons(game, player)
                        + " **SPACE CANNON 5 (x3)** and **PRODUCTION 3** for this tactical action.\n\nThis is technically the wrong timing. In async, this needs to be prompted on system activation so production buttons appear. A reminder that ships **MUST BE MOVED** in order to exhaust this tech.",
                List.of(
                        Buttons.blue(
                                player.factionButtonChecker() + USE_SIGIL_OF_TRANSMUTATION,
                                "Exhaust Sigil of Transmutation",
                                TechEmojis.CyberneticTech),
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler(USE_SIGIL_OF_TRANSMUTATION)
    public static void useSigilOfTransmutation(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game == null ? null : game.getTileByPosition(game.getActiveSystem());
        if (player == null
                || tile == null
                || player != game.getActivePlayer()
                || tile.isHomeSystem(game)
                || !player.hasTechReady(SIGIL_OF_TRANSMUTATION)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        boolean spaceCannonPromptAlreadyAvailable = FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                && !ButtonHelper.tileHasPDS2Cover(player, game, tile.getPosition())
                        .isEmpty();
        player.exhaustTech(SIGIL_OF_TRANSMUTATION);
        game.setStoredValue(SIGIL_OF_TRANSMUTATION_TILE + player.getFaction(), tile.getPosition());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " exhausted _Sigil of Transmutation_. "
                        + tile.getRepresentationForButtons(game, player)
                        + " has **SPACE CANNON 5 (x3)** and **PRODUCTION 3** until this tactical action ends.");
        if (!spaceCannonPromptAlreadyAvailable && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            StartCombatService.sendSpaceCannonButtonsToThread(event.getMessageChannel(), game, player, tile);
        }
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasSigilOfTransmutation(Game game, Player player, Tile tile) {
        return game != null
                && player != null
                && tile != null
                && player == game.getActivePlayer()
                && tile.getPosition().equals(game.getActiveSystem())
                && tile.getPosition().equals(game.getStoredValue(SIGIL_OF_TRANSMUTATION_TILE + player.getFaction()));
    }

    public static UnitModel getSigilOfTransmutationSpaceCannon(Game game, Player player, Tile tile) {
        if (!hasSigilOfTransmutation(game, player, tile)) {
            return null;
        }

        UnitModel cannon = new UnitModel();
        cannon.setSpaceCannonHitsOn(5);
        cannon.setSpaceCannonDieCount(3);
        cannon.setName("Sigil of Transmutation");
        cannon.setAsyncId(SIGIL_OF_TRANSMUTATION);
        cannon.setId(SIGIL_OF_TRANSMUTATION);
        cannon.setBaseType("pds");
        cannon.setDeepSpaceCannon(false);
        cannon.setFaction(player.getFaction());
        return cannon;
    }

    public static void clearSigilOfTransmutation(Game game) {
        if (game == null) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(SIGIL_OF_TRANSMUTATION_TILE + player.getFaction());
        }
    }

    public static void resetSigilOfTransmutationForNewDestination(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        String key = SIGIL_OF_TRANSMUTATION_TILE + player.getFaction();
        if (!game.getStoredValue(key).isEmpty()) {
            game.removeStoredValue(key);
            player.refreshTech(SIGIL_OF_TRANSMUTATION);
        }
    }

    // Power Word: Miracle
    public static void resolvePowerWordMiracle(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + DISCARD_AC, "Discard 1 AC and Draw 2", CardEmojis.getACEmoji(game)));
        buttons.add(Buttons.green(player.factionButtonChecker() + GAIN_CC, "Gain 1 Command Token"));
        buttons.add(Buttons.green(player.factionButtonChecker() + PLACE_INF, "Place 4 Infantry", UnitEmojis.infantry));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", use these buttons to resolve _Power Word: Miracle_:",
                buttons);
    }

    @ButtonHandler(DISCARD_AC)
    public static void getAcDiscardButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player)) {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);

        List<Button> discardButtons = new ArrayList<>();

        for (Map.Entry<String, Integer> actionCard : player.getActionCards().entrySet()) {
            discardButtons.add(Buttons.blue(
                    player.factionButtonChecker() + SELECT_AC + actionCard.getValue(),
                    "(" + actionCard.getValue() + ") "
                            + Mapper.getActionCard(actionCard.getKey()).getName(),
                    CardEmojis.getACEmoji(game)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please discard an action card:",
                discardButtons);
    }

    @ButtonHandler(SELECT_AC)
    public static void resolveMiracleAcDiscard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || game == null || !player.hasTech(MIRACLE) || !hasFourTechsMatchingPrimordial(player)) {
            return;
        }

        int handIndex;

        try {
            handIndex = Integer.parseInt(buttonID.substring(SELECT_AC.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.getExhaustedTechs().contains("tharcanumpmg")
                || !player.getActionCards().containsValue(handIndex)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ActionCardHelper.discardAC(event, game, player, handIndex);
        ActionCardHelper.drawActionCards(player, 2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_CC)
    public static void resolveMiracleCCGain(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null || game == null || !player.hasTech(MIRACLE) || !hasFourTechsMatchingPrimordial(player)) {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", use these buttons to gain 1 command token:",
                ButtonHelper.getGainCCButtons(player));
    }

    @ButtonHandler(PLACE_INF)
    public static void getMiracleInfantryPlacementButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player)) {
            return;
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        int round = game.getRound();
        game.setStoredValue(INFANTRY_PLACED + player.getFaction() + "_" + round, "0");
        List<Button> planetButtons = getMiracleInfantryButtons(game, player, round);
        if (planetButtons.isEmpty()) {
            game.removeStoredValue(INFANTRY_PLACED + player.getFaction() + "_" + round);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentation() + " has no eligible planet for the infantry.");
            return;
        }

        String message = player.getRepresentation()
                + ", choose planets to place up to 4 infantry with _Power Word: Miracle_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_INFANTRY + round + "_";
        Button done = Buttons.red(
                player.factionButtonChecker() + FINISH_INFANTRY + round, "Done Placing Infantry");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(planetButtons, List.of(done), buttonPrefix, 25, 0, false));
    }

    @ButtonHandler(PLACE_INFANTRY)
    public static void placeMiracleInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(MIRACLE) || !player.getExhaustedTechs().contains(MIRACLE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String placementData = buttonID.substring(PLACE_INFANTRY.length());
        int separator = placementData.indexOf('_');
        if (separator < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int round;
        try {
            round = Integer.parseInt(placementData.substring(0, separator));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String stateKey = INFANTRY_PLACED + player.getFaction() + "_" + round;
        String placedValue = game.getStoredValue(stateKey);
        if (round != game.getRound() || placedValue.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> planetButtons = getMiracleInfantryButtons(game, player, round);
        String message = player.getRepresentation()
                + ", choose planets to place up to 4 infantry with _Power Word: Miracle_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_INFANTRY + round + "_";
        Button done = Buttons.red(
                player.factionButtonChecker() + FINISH_INFANTRY + round, "Done Placing Infantry");
        String planetName = placementData.substring(separator + 1);
        if (planetName.startsWith("page")) {
            try {
                int page = Integer.parseInt(planetName.substring("page".length()));
                NewStuffHelper.sendOrEditButtons(
                        event,
                        event.getMessageChannel(),
                        message,
                        NewStuffHelper.buttonPagination(planetButtons, List.of(done), buttonPrefix, 25, page, false));
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }

        int placed;
        try {
            placed = Integer.parseInt(placedValue);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (placed >= 4) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has already placed all 4 infantry. Press **Done Placing Infantry** when finished.");
            return;
        }
        if (planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || planet.isSpaceStation(game)
                || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 gf " + planetName);
        game.setStoredValue(stateKey, String.valueOf(placed + 1));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed 1 infantry on " + Helper.getPlanetRepresentation(planetName, game)
                        + " with _Power Word: Miracle_ (" + (placed + 1) + "/4)."
                        + (placed == 3 ? " Press **Done Placing Infantry** when finished." : ""));
    }

    @ButtonHandler(FINISH_INFANTRY)
    public static void finishMiracleInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        try {
            int round = Integer.parseInt(buttonID.substring(FINISH_INFANTRY.length()));
            game.removeStoredValue(INFANTRY_PLACED + player.getFaction() + "_" + round);
        } catch (NumberFormatException ignored) {
            // The message is still safe to remove if an old or malformed completion button is pressed.
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getMiracleInfantryButtons(Game game, Player player, int round) {
        List<Button> planetButtons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || planet.isSpaceStation(game)
                    || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
                continue;
            }
            planetButtons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_INFANTRY + round + "_" + planetName,
                    "Add 1 infantry to " + Helper.getPlanetRepresentation(planetName, game),
                    UnitEmojis.infantry));
        }
        return planetButtons;
    }
}

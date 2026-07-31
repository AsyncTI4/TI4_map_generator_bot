package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class ArcanumLeadersHandler {
    private static final String PLACE_COMMANDER_FIGHTER = "arcanumCommanderPlaceFighter_";
    private static final String RETURN_TECH_HERO = "arcanumHeroReturnTech_";
    private static final String DONE_RETURNING_TECHS = "arcanumHeroFinishTechReturn_";
    private static final String GAIN_PURGED_FRAGMENT_HERO = "arcanumHeroGainPurgedFragment_";
    private static final String USE_VEYLA = "arcanumVeylaUse_";
    private static final String USE_VEYLA_ON_OTHER = "arcanumVeylaUseOnOther";
    private static final String CHOOSE_VEYLA_TARGET = "arcanumVeylaTarget_";
    private static final String SELECT_VEYLA_RETURN_TECH = "arcanumVeylaReturnTech_";
    private static final String GAIN_VEYLA_TECH = "arcanumVeylaGainTech_";
    private static final List<String> EXPLORE_TYPES =
            List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL, Constants.FRONTIER);

    // Commander
    public static void offerArcanumTechExhaustCommanderButtons(Player player) {
        if (player == null
                || player.getGame() == null
                || !player.getGame().playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")) {
            return;
        }

        Game game = player.getGame();
        List<Button> buttons = getCommanderFighterButtons(player, game);

        if (buttons.isEmpty()) {
            return;
        }

        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons, extraButtons, player.factionButtonChecker() + PLACE_COMMANDER_FIGHTER, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), getCommanderFighterMessage(player), displayedButtons);
    }

    @ButtonHandler(PLACE_COMMANDER_FIGHTER)
    public static void placeCommanderFighter(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")) {
            return;
        }

        List<Button> buttons = getCommanderFighterButtons(player, game);
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                extraButtons,
                getCommanderFighterMessage(player),
                player.factionButtonChecker() + PLACE_COMMANDER_FIGHTER,
                buttonID)) {
            return;
        }

        String tilePosition = buttonID.substring(PLACE_COMMANDER_FIGHTER.length());
        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null
                || !ButtonHelper.getTilesWithShipsInTheSystem(player, game).contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelperModifyUnits.placeUnitAndDeleteButton(
                "placeOneNDone_skipbuild_ff_" + tilePosition, event, game, player);
    }

    private static List<Button> getCommanderFighterButtons(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + PLACE_COMMANDER_FIGHTER + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }

    private static String getCommanderFighterMessage(Player player) {
        return player.getRepresentation()
                + ", you may use Orthis Lithon, the Arcanum commander, to place 1 fighter in a system that contains 1 or more of your ships.";
    }

    // Agent
    public static void offerVeylaTheKeeperButtons(Game game, Player target, String gainedTechID) {
        TechnologyModel gainedTech = Mapper.getTech(gainedTechID);
        if (game == null
                || target == null
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || gainedTech == null
                || gainedTech.isFactionTech()
                || !target.hasTech(gainedTechID)
                || !target.hasUnexhaustedLeader("arcanumagent")
                || getVeylaReplacementButtons(game, target, gainedTechID).isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + " gained "
                        + gainedTech.getNameRepresentation()
                        + ". You may use _Veyla, the Arcanum agent_ to replace it.",
                List.of(
                        Buttons.gray(
                                target.factionButtonChecker() + USE_VEYLA + target.getFaction() + "|" + gainedTechID,
                                "Use Arcanum Agent"),
                        Buttons.red("deleteButtons", "Decline")));
    }

    public static Button getVeylaCardsInfoButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_VEYLA_ON_OTHER, "Use Arcanum Agent");
    }

    @ButtonHandler(USE_VEYLA_ON_OTHER)
    public static void selectVeylaTarget(ButtonInteractionEvent event, Game game, Player agentOwner) {
        if (game == null
                || agentOwner == null
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || !agentOwner.hasUnexhaustedLeader("arcanumagent")) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<Button> targetButtons = game.getRealPlayers().stream()
                .filter(target -> target != agentOwner)
                .filter(target ->
                        !getVeylaReturnTechButtons(game, agentOwner, target).isEmpty())
                .map(target -> Buttons.green(
                        agentOwner.factionButtonChecker() + CHOOSE_VEYLA_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.getFactionEmojiOrColor()))
                .toList();
        if (targetButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No other player has an eligible technology to return.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                agentOwner.getCardsInfoThread(),
                agentOwner.getRepresentationUnfogged()
                        + ", choose a player to use _Veyla, the Arcanum agent_ on.",
                targetButtons);
    }

    @ButtonHandler(CHOOSE_VEYLA_TARGET)
    public static void chooseVeylaTarget(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (game == null
                || agentOwner == null
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || !agentOwner.hasUnexhaustedLeader("arcanumagent")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(CHOOSE_VEYLA_TARGET.length()));
        List<Button> returnTechButtons = getVeylaReturnTechButtons(game, agentOwner, target);
        if (target == null || returnTechButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (returnTechButtons.size() == 1) {
            String returnedTechID = getVeylaReturnedTechID(returnTechButtons.getFirst());
            resolveVeylaTheKeeper(event, game, agentOwner, target, returnedTechID);
            return;
        }

        String message = target.getRepresentation()
                + ", _Veyla, the Arcanum agent_ is being used on you. Choose a non-faction technology to return.";
        String buttonPrefix = target.factionButtonChecker() + SELECT_VEYLA_RETURN_TECH + agentOwner.getFaction() + "|";
        List<Button> buttons = returnTechButtons.size() <= 25
                ? returnTechButtons
                : NewStuffHelper.buttonPagination(returnTechButtons, buttonPrefix, 0);
        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_VEYLA_RETURN_TECH)
    public static void selectVeylaTechToReturn(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(SELECT_VEYLA_RETURN_TECH.length()).split("\\|", 2);
        if (game == null || target == null || payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player agentOwner = game.getPlayerFromColorOrFaction(payload[0]);
        if (agentOwner == null
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || !agentOwner.hasUnexhaustedLeader("arcanumagent")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> returnTechButtons = getVeylaReturnTechButtons(game, agentOwner, target);
        String message = target.getRepresentation()
                + ", _Veyla, the Arcanum agent_ is being used on you. Choose a non-faction technology to return.";
        String buttonPrefix = target.factionButtonChecker() + SELECT_VEYLA_RETURN_TECH + agentOwner.getFaction() + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), returnTechButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        resolveVeylaTheKeeper(event, game, agentOwner, target, payload[1]);
    }

    @ButtonHandler(USE_VEYLA)
    public static void useVeylaTheKeeper(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        String[] payload = buttonID.substring(USE_VEYLA.length()).split("\\|", 2);
        if (game == null
                || agentOwner == null
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(payload[0]);
        String returnedTechID = payload[1];
        if (target != agentOwner) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        resolveVeylaTheKeeper(event, game, agentOwner, target, returnedTechID);
    }

    @ButtonHandler(GAIN_VEYLA_TECH)
    public static void gainVeylaTheKeeperTech(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(GAIN_VEYLA_TECH.length()).split("\\|", 3);
        if (game == null || target == null || payload.length != 3 || !Objects.equals(target.getFaction(), payload[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String returnedTechID = payload[1];
        String selectedTechID = payload[2];
        List<Button> replacementButtons = getVeylaReplacementButtons(game, target, returnedTechID);
        String message =
                target.getRepresentation()
                        + ", choose a non-unit-upgrade technology to gain from _Veyla, the Arcanum agent_.";
        String buttonPrefix =
                target.factionButtonChecker() + GAIN_VEYLA_TECH + target.getFaction() + "|" + returnedTechID + "|";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), replacementButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        if (!game.getTechnologyDeck().contains(selectedTechID)
                || replacementButtons.stream()
                        .noneMatch(button -> button.getCustomId().endsWith("|" + selectedTechID))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        PlayerTechService.addTech(event, game, target, selectedTechID);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getVeylaReplacementButtons(Game game, Player target, String returnedTechID) {
        TechnologyModel returnedTech = Mapper.getTech(returnedTechID);
        if (game == null || target == null || returnedTech == null) {
            return List.of();
        }

        int maximumRequirements = returnedTech.getRequirements().orElse("").length() + 1;
        return game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !tech.isFactionTech() && !tech.isUnitUpgrade())
                .filter(tech -> !target.hasTech(tech.getAlias()))
                .filter(tech -> tech.getRequirements().orElse("").length() <= maximumRequirements)
                .map(tech -> Buttons.green(
                        target.factionButtonChecker()
                                + GAIN_VEYLA_TECH
                                + target.getFaction()
                                + "|"
                                + returnedTechID
                                + "|"
                                + tech.getAlias(),
                        tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static List<Button> getVeylaReturnTechButtons(Game game, Player agentOwner, Player target) {
        if (game == null || agentOwner == null || target == null) {
            return List.of();
        }

        return target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !tech.isFactionTech())
                .filter(tech -> !getVeylaReplacementButtons(game, target, tech.getAlias())
                        .isEmpty())
                .map(tech -> Buttons.green(
                        target.factionButtonChecker()
                                + SELECT_VEYLA_RETURN_TECH
                                + agentOwner.getFaction()
                                + "|"
                                + tech.getAlias(),
                        "Return " + tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static String getVeylaReturnedTechID(Button button) {
        int payloadStart = button.getCustomId().indexOf(SELECT_VEYLA_RETURN_TECH) + SELECT_VEYLA_RETURN_TECH.length();
        String[] payload = button.getCustomId().substring(payloadStart).split("\\|", 2);
        return payload.length == 2 ? payload[1] : "";
    }

    private static void resolveVeylaTheKeeper(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target, String returnedTechID) {
        TechnologyModel returnedTech = Mapper.getTech(returnedTechID);
        Leader agent = agentOwner.getLeader("arcanumagent").orElse(null);
        List<Button> replacementButtons = getVeylaReplacementButtons(game, target, returnedTechID);
        if (target == null
                || agent == null
                || agent.isExhausted()
                || returnedTech == null
                || returnedTech.isFactionTech()
                || !target.hasTech(returnedTechID)
                || replacementButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        target.removeTech(returnedTechID);
        ButtonHelper.deleteMessage(event);

        String message = target.getRepresentation()
                + ", _Veyla, the Arcanum agent_ returned "
                + returnedTech.getNameRepresentation()
                + " to the technology deck. Choose a non-unit-upgrade technology with at most "
                + (returnedTech.getRequirements().orElse("").length() + 1)
                + " prerequisites to gain.";
        String buttonPrefix =
                target.factionButtonChecker() + GAIN_VEYLA_TECH + target.getFaction() + "|" + returnedTechID + "|";
        List<Button> buttons = replacementButtons.size() <= 25
                ? replacementButtons
                : NewStuffHelper.buttonPagination(replacementButtons, buttonPrefix, 0);
        MessageHelper.sendMessageToChannelWithButtons(target.getCorrectChannel(), message, buttons);
    }

    // Hero
    public static void startArcanumHero(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> techButtons = getArcanumHeroTechButtons(player, 0);
        Button done = getArcanumHeroDoneButton(player, 0);
        List<Button> buttons = techButtons.size() <= 24
                ? new ArrayList<>(techButtons)
                : NewStuffHelper.buttonPagination(
                        techButtons,
                        List.of(done),
                        player.factionButtonChecker() + RETURN_TECH_HERO + "0_",
                        25,
                        0,
                        false);
        if (techButtons.size() <= 24) {
            buttons.add(done);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), getArcanumHeroTechReturnMessage(player, 0), buttons);
    }

    @ButtonHandler(RETURN_TECH_HERO)
    public static void returnArcanumHeroTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] payload = buttonID.substring(RETURN_TECH_HERO.length()).split("_", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int returnedCount;
        try {
            returnedCount = Integer.parseInt(payload[0]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> techButtons = getArcanumHeroTechButtons(player, returnedCount);
        Button done = getArcanumHeroDoneButton(player, returnedCount);
        String message = getArcanumHeroTechReturnMessage(player, returnedCount);
        String buttonPrefix = player.factionButtonChecker() + RETURN_TECH_HERO + returnedCount + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), techButtons, List.of(done), message, buttonPrefix, buttonID)) {
            return;
        }

        String techID = payload[1];
        if (returnedCount >= 6 || !player.hasTech(techID) || Mapper.getTech(techID) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removeTech(techID);
        showArcanumHeroTechReturnPrompt(event, player, returnedCount + 1);
    }

    @ButtonHandler(DONE_RETURNING_TECHS)
    public static void finishArcanumHeroTechReturn(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int returnedCount;
        try {
            returnedCount = Integer.parseInt(buttonID.substring(DONE_RETURNING_TECHS.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (returnedCount < 0 || returnedCount > 6) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (returnedCount == 0) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " returned no technologies for _Power Conduits - True Resurrection_.");
            return;
        }

        showArcanumHeroFragmentPrompt(event, game, player, returnedCount, 0);
    }

    private static void showArcanumHeroTechReturnPrompt(
            GenericInteractionCreateEvent event, Player player, int returnedCount) {
        List<Button> techButtons = getArcanumHeroTechButtons(player, returnedCount);
        Button done = getArcanumHeroDoneButton(player, returnedCount);
        String message = getArcanumHeroTechReturnMessage(player, returnedCount);
        List<Button> buttons = techButtons.size() <= 24
                ? new ArrayList<>(techButtons)
                : NewStuffHelper.buttonPagination(
                        techButtons,
                        List.of(done),
                        player.factionButtonChecker() + RETURN_TECH_HERO + returnedCount + "_",
                        25,
                        0,
                        false);
        if (techButtons.size() <= 24) {
            buttons.add(done);
        }

        if (event instanceof ButtonInteractionEvent buttonEvent) {
            MessageHelper.editMessageWithButtons(buttonEvent, message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
    }

    private static List<Button> getArcanumHeroTechButtons(Player player, int returnedCount) {
        return player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + RETURN_TECH_HERO + returnedCount + "_" + tech.getAlias(),
                        tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static Button getArcanumHeroDoneButton(Player player, int returnedCount) {
        return Buttons.red(
                player.factionButtonChecker() + DONE_RETURNING_TECHS + returnedCount,
                "Done Returning Techs (" + returnedCount + "/6)");
    }

    private static String getArcanumHeroTechReturnMessage(Player player, int returnedCount) {
        return player.getRepresentation()
                + ", please choose up to 6 technologies to return to your technology deck. "
                + "You have returned "
                + returnedCount
                + ".";
    }

    @ButtonHandler(GAIN_PURGED_FRAGMENT_HERO)
    public static void gainArcanumHeroPurgedFragment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] payload =
                buttonID.substring(GAIN_PURGED_FRAGMENT_HERO.length()).split("_", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remainingCount;
        try {
            remainingCount = Integer.parseInt(payload[0]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (payload[1].startsWith("page")) {
            try {
                showArcanumHeroFragmentPrompt(
                        event, game, player, remainingCount, Integer.parseInt(payload[1].substring(4)));
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }

        String fragmentID = payload[1];
        if (remainingCount < 1 || !getPurgedRelicFragments(game).contains(fragmentID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addFragment(fragmentID);
        game.setNumberOfPurgedFragments(Math.max(0, game.getNumberOfPurgedFragments() - 1));
        if (remainingCount == 1) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " gained the selected purged relic fragments for _Power Conduits - True Resurrection_.");
            return;
        }

        showArcanumHeroFragmentPrompt(event, game, player, remainingCount - 1, 0);
    }

    private static void showArcanumHeroFragmentPrompt(
            GenericInteractionCreateEvent event, Game game, Player player, int remainingCount, int page) {
        List<Button> fragmentButtons = getPurgedRelicFragments(game).stream()
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .map(fragment -> Buttons.green(
                        player.factionButtonChecker()
                                + GAIN_PURGED_FRAGMENT_HERO
                                + remainingCount
                                + "_"
                                + fragment.getAlias(),
                        fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())))
                .toList();
        if (fragmentButtons.isEmpty()) {
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                ButtonHelper.deleteMessage(buttonEvent);
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " cannot gain another purged relic fragment because none are available.");
            return;
        }

        String message = player.getRepresentation()
                + ", choose a purged relic fragment to gain for _Power Conduits - True Resurrection_. "
                + remainingCount
                + " remaining.";
        List<Button> buttons = fragmentButtons.size() <= 25
                ? fragmentButtons
                : NewStuffHelper.buttonPagination(
                        fragmentButtons,
                        null,
                        player.factionButtonChecker() + GAIN_PURGED_FRAGMENT_HERO + remainingCount + "_",
                        25,
                        page,
                        false);
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            MessageHelper.editMessageWithButtons(buttonEvent, message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
    }

    private static List<String> getPurgedRelicFragments(Game game) {
        if (game == null) {
            return List.of();
        }
        var explorationDeck = Mapper.getDeck(game.getExplorationDeckID());
        if (explorationDeck == null) {
            return List.of();
        }

        Set<String> unavailableFragments = new HashSet<>();
        for (String exploreType : EXPLORE_TYPES) {
            unavailableFragments.addAll(game.getExploreDeck(exploreType));
            unavailableFragments.addAll(game.getExploreDiscard(exploreType));
        }
        for (Player player : game.getPlayers().values()) {
            unavailableFragments.addAll(player.getFragments());
            unavailableFragments.addAll(player.getRelics());
        }

        return explorationDeck.getNewDeck().stream()
                .distinct()
                .filter(fragmentID -> !unavailableFragments.contains(fragmentID))
                .filter(fragmentID -> {
                    ExploreModel fragment = Mapper.getExplore(fragmentID);
                    return fragment != null && Constants.FRAGMENT.equalsIgnoreCase(fragment.getResolution());
                })
                .toList();
    }
}

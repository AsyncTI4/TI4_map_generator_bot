package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class NetrunnersBreakthroughHandler {
    private static final String BREAKTHROUGH = "netrunnersbt";
    private static final String TARGET = "netrunnersEmotetTarget_";
    private static final String PAY = "netrunnersEmotetPay_";
    private static final String DECLINE = "netrunnersEmotetDecline_";
    private static final String TECH = "netrunnersEmotetTech_";
    private static final String TECH_NAV = "netrunnersEmotetTechPage_";
    private static final String RETURN_TOKEN = "netrunnersEmotetReturnToken_";
    private static final String DECLINE_RETURN = "netrunnersEmotetDeclineReturn";
    private static final String PLANET = "netrunnersEmotetPlanet_";

    public static void offerEmotetTargetButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = getTargetButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There is no player to select for _Emotet_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a player for _Emotet_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + TARGET, 0));
    }

    @ButtonHandler(TARGET)
    public static void selectEmotetTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getTargetButtons(game, player);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                player.getRepresentationNoPing() + ", choose a player for _Emotet_.",
                player.factionButtonChecker() + TARGET,
                buttonID)) return;

        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(TARGET.length()));
        if (!player.hasUnlockedBreakthrough(BREAKTHROUGH)
                || target == null
                || target == player
                || target.isEliminated()) return;

        List<Button> paymentButtons = new java.util.ArrayList<>();
        if (target.getTg() >= 2) {
            paymentButtons.add(
                    Buttons.green(target.factionButtonChecker() + PAY + player.getFaction(), "Spend 2 Trade Goods"));
        }
        paymentButtons.add(Buttons.red(target.factionButtonChecker() + DECLINE + player.getFaction(), "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + ", " + player.getFactionEmojiOrColor()
                        + " used _Emotet_. You may spend 2 trade goods to research 1 non-faction technology.",
                paymentButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PAY)
    @ButtonHandler(DECLINE)
    public static void resolveEmotetPayment(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        Player owner = game.getPlayerFromColorOrFaction(buttonID.substring(buttonID.indexOf('_') + 1));
        if (owner == null || !owner.hasUnlockedBreakthrough(BREAKTHROUGH)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (buttonID.startsWith(DECLINE)) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(), target.getRepresentationNoPing() + " declined _Emotet_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (target.getTg() < 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You no longer have 2 trade goods to spend.");
            return;
        }
        target.setTg(target.getTg() - 2);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(), target.getRepresentationNoPing() + " spent 2 trade goods for _Emotet_.");
        sendTechChoices(owner, target);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(TECH)
    public static void researchEmotetTech(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(TECH.length()).split("\\|", 2);
        Player owner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        TechnologyModel technology = payload.length == 2 ? Mapper.getTech(payload[1]) : null;
        if (owner == null
                || !owner.hasUnlockedBreakthrough(BREAKTHROUGH)
                || technology == null
                || !isResearchableBy(target, technology)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That technology is no longer eligible for _Emotet_.");
            return;
        }
        PlayerTechService.getTech(game, target, event, "getTech_" + technology.getAlias() + "__noPay");
        if (owner.getDebtTokenCount(target.getColor(), Constants.NETRUNNERS_DEBT_POOL) < 1) return;

        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + ", " + target.getFactionEmojiOrColor() + " researched "
                        + technology.getNameRepresentation()
                        + " with _Emotet_. You may return their control token to gain it.",
                List.of(
                        Buttons.green(
                                owner.factionButtonChecker() + RETURN_TOKEN + target.getFaction() + "|"
                                        + technology.getAlias(),
                                "Return Control Token and Gain Technology"),
                        Buttons.red(owner.factionButtonChecker() + DECLINE_RETURN, "Decline")));
    }

    @ButtonHandler(TECH_NAV)
    public static void turnEmotetTechPage(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String ownerFaction = buttonID.substring(TECH_NAV.length()).split("\\|", 2)[0];
        Player owner = game.getPlayerFromColorOrFaction(ownerFaction);
        if (owner == null || !owner.hasUnlockedBreakthrough(BREAKTHROUGH)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                techChoiceMessage(target),
                () -> getTechButtons(owner, target),
                List.of(),
                target.factionButtonChecker() + TECH_NAV + owner.getFaction() + "|");
    }

    @ButtonHandler(RETURN_TOKEN)
    public static void returnEmotetControlToken(
            ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] payload = buttonID.substring(RETURN_TOKEN.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        TechnologyModel technology = payload.length == 2 ? Mapper.getTech(payload[1]) : null;
        if (!owner.hasUnlockedBreakthrough(BREAKTHROUGH)
                || target == null
                || technology == null
                || !target.hasTech(technology.getAlias())
                || owner.getDebtTokenCount(target.getColor(), Constants.NETRUNNERS_DEBT_POOL) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That control token is no longer available.");
            return;
        }
        owner.removeDebtTokens(target.getColor(), 1, Constants.NETRUNNERS_DEBT_POOL);
        PlayerTechService.getTech(game, owner, event, "getTech_" + technology.getAlias() + "__noPay");
        MessageHelper.sendMessageToChannel(
                owner.getCorrectChannel(),
                owner.getRepresentationNoPing() + " resolved _Emotet_: " + target.getRepresentationNoPing()
                        + " researched " + technology.getNameRepresentation() + ", and "
                        + owner.getRepresentationNoPing() + " returned " + target.getFactionEmojiOrColor()
                        + "'s control token from the Hacker Man pool to gain that technology.");
        List<Button> planetButtons = getPlanetButtons(game, owner, target);
        if (planetButtons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCorrectChannel(),
                planetChoiceMessage(owner, target),
                NewStuffHelper.buttonPagination(
                        planetButtons, owner.factionButtonChecker() + PLANET + target.getFaction() + "|", 0));
    }

    @ButtonHandler(DECLINE_RETURN)
    public static void declineEmotetControlToken(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLANET)
    public static void placeEmotetInfantry(ButtonInteractionEvent event, Game game, Player owner, String buttonID) {
        String[] payload = buttonID.substring(PLANET.length()).split("\\|", 2);
        Player target = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        List<Button> buttons = target == null ? List.of() : getPlanetButtons(game, owner, target);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                target == null ? "Choose a planet for _Emotet_." : planetChoiceMessage(owner, target),
                owner.factionButtonChecker() + PLANET + payload[0] + "|",
                buttonID)) return;

        Tile tile = payload.length == 2 ? game.getTileFromPlanet(payload[1]) : null;
        if (!owner.hasUnlockedBreakthrough(BREAKTHROUGH)
                || target == null
                || tile == null
                || !target.getPlanets().contains(payload[1])
                || tile.isHomeSystem(game)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, owner.getColor(), "1 infantry " + payload[1]);
        } finally {
            if (coexistenceFlag.isEmpty()) game.removeStoredValue("coexistFlag");
            else game.setStoredValue("coexistFlag", coexistenceFlag);
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                owner.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(payload[1], game) + " with _Emotet_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getTargetButtons(Game game, Player owner) {
        return game.getRealPlayersExcludingThis(owner).stream()
                .filter(target -> !target.isEliminated())
                .map(target -> FoWHelper.fogSafeTargetButton(
                        owner.factionButtonChecker() + TARGET + target.getFaction(), "gray", target))
                .toList();
    }

    private static void sendTechChoices(Player owner, Player target) {
        List<Button> buttons = getTechButtons(owner, target);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no researchable non-faction technology.");
            return;
        }
        NewStuffHelper.sendPaginated(
                target.getCorrectChannel(),
                techChoiceMessage(target),
                buttons,
                List.of(),
                target.factionButtonChecker() + TECH_NAV + owner.getFaction() + "|");
    }

    private static String techChoiceMessage(Player target) {
        return target.getRepresentationNoPing() + ", choose a non-faction technology to research with _Emotet_.";
    }

    private static String planetChoiceMessage(Player owner, Player target) {
        return owner.getRepresentationNoPing() + ", choose a non-home planet controlled by "
                + target.getFactionEmojiOrColor() + " to place 1 infantry into coexistence.";
    }

    private static List<Button> getTechButtons(Player owner, Player target) {
        return Mapper.getTechs().values().stream()
                .filter(technology -> isResearchableBy(target, technology))
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .map(technology -> Buttons.green(
                        target.factionButtonChecker() + TECH + owner.getFaction() + "|" + technology.getAlias(),
                        technology.getName(),
                        technology.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static List<Button> getPlanetButtons(Game game, Player owner, Player target) {
        return target.getPlanets().stream()
                .filter(planet -> {
                    Tile tile = game.getTileFromPlanet(planet);
                    return tile != null && !tile.isHomeSystem(game);
                })
                .sorted()
                .map(planet -> Buttons.green(
                        owner.factionButtonChecker() + PLANET + target.getFaction() + "|" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
    }

    private static boolean isResearchableBy(Player player, TechnologyModel technology) {
        return !technology.isFactionTech()
                && player.getGame().getTechnologyDeck().contains(technology.getAlias())
                && !player.hasTech(technology.getAlias())
                && ListTechService.isTechResearchable(technology, player);
    }
}

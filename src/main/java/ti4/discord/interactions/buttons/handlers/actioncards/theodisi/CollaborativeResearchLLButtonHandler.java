package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class CollaborativeResearchLLButtonHandler {
    private static final String RESOLVE = "resolveCollaborativeResearch";
    private static final String TARGET = "collaborativeResearchTarget_";
    private static final String OWNER_SPEND = "collaborativeResearchOwnerSpend";
    private static final String OWNER_DECLINE = "collaborativeResearchOwnerDecline";
    private static final String TARGET_SPEND = "collaborativeResearchTargetSpend_";
    private static final String TARGET_DECLINE = "collaborativeResearchTargetDecline_";
    private static final String RESEARCH = "collaborativeResearchTech_";
    private static final String STATE = "collaborativeResearch_";

    @ButtonHandler(RESOLVE)
    public static void resolveCollaborativeResearch(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player && !target.isEliminated()) {
                String id = player.factionButtonChecker() + TARGET + target.getFaction();
                buttons.add(FoWHelper.fogSafeTargetButton(id, "gray", target));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " has no eligible player to select for _Collaborative Research_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose a player for _Collaborative Research_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + TARGET, 0));
    }

    @ButtonHandler(TARGET)
    public static void selectTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player && !target.isEliminated()) {
                String id = player.factionButtonChecker() + TARGET + target.getFaction();
                buttons.add(FoWHelper.fogSafeTargetButton(id, "gray", target));
            }
        }
        String message = player.getRepresentationNoPing() + ", choose a player for _Collaborative Research_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + TARGET, buttonID)) {
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(TARGET.length()));
        if (target == null || target == player || target.isEliminated()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player is no longer eligible.");
            return;
        }
        game.setStoredValue(STATE + player.getFaction(), target.getFaction() + "|pending|pending");
        ButtonHelper.deleteMessage(event);
        sendPaymentButtons(game, player, target, event);
    }

    @ButtonHandler(OWNER_SPEND)
    @ButtonHandler(OWNER_DECLINE)
    public static void chooseOwnerPayment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolvePayment(event, game, player, player, OWNER_SPEND.equals(buttonID));
    }

    @ButtonHandler(TARGET_SPEND)
    @ButtonHandler(TARGET_DECLINE)
    public static void chooseTargetPayment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String ownerFaction = buttonID.substring(buttonID.lastIndexOf('_') + 1);
        Player owner = game.getPlayerFromColorOrFaction(ownerFaction);
        resolvePayment(event, game, owner, player, buttonID.startsWith(TARGET_SPEND));
    }

    @ButtonHandler(RESEARCH)
    public static void researchTechnology(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|", 3);
        Player target = state.length == 3 ? game.getPlayerFromColorOrFaction(state[0]) : null;
        List<Button> buttons = getResearchButtons(game, player, target);
        String message = player.getRepresentationNoPing()
                + ", choose a technology to research for _Collaborative Research_. The selected player will gain it after you do.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + RESEARCH,
                buttonID)) {
            return;
        }

        String techID = buttonID.substring(RESEARCH.length());
        TechnologyModel tech = Mapper.getTech(techID);
        if (state.length != 3
                || target == null
                || !"spent".equals(state[1])
                || !"spent".equals(state[2])
                || tech == null
                || !isEligibleTech(game, player, target, tech)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That technology is no longer eligible for _Collaborative Research_.");
            return;
        }

        game.removeStoredValue(STATE + player.getFaction());
        PlayerTechService.getTech(game, player, event, "getTech_" + techID + "__noPay");
        PlayerTechService.addTech(event, game, target, techID);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " researched " + tech.getNameRepresentation()
                        + " with _Collaborative Research_, so " + target.getRepresentationNoPing()
                        + " gained it as well.");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendPaymentButtons(Game game, Player owner, Player target, ButtonInteractionEvent event) {
        List<Button> ownerButtons = new ArrayList<>();
        if (owner.getTg() >= 2) {
            ownerButtons.add(Buttons.green(owner.factionButtonChecker() + OWNER_SPEND, "Spend 2 Trade Goods"));
        }
        ownerButtons.add(Buttons.red(owner.factionButtonChecker() + OWNER_DECLINE, "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCardsInfoThread(),
                owner.getRepresentationNoPing()
                        + ", choose whether to spend 2 trade goods for _Collaborative Research_.",
                ownerButtons);

        List<Button> targetButtons = new ArrayList<>();
        if (target.getTg() >= 2) {
            targetButtons.add(Buttons.green(
                    target.factionButtonChecker() + TARGET_SPEND + owner.getFaction(), "Spend 2 Trade Goods"));
        }
        targetButtons.add(Buttons.red(target.factionButtonChecker() + TARGET_DECLINE + owner.getFaction(), "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationNoPing() + ", " + owner.getRepresentationNoPing()
                        + " selected you for _Collaborative Research_. Choose whether to spend 2 trade goods.",
                targetButtons);
    }

    private static void resolvePayment(
            ButtonInteractionEvent event, Game game, Player owner, Player decidingPlayer, boolean spendsTradeGoods) {
        if (owner == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This _Collaborative Research_ offer has expired.");
            return;
        }
        String[] state = game.getStoredValue(STATE + owner.getFaction()).split("\\|", 3);
        Player target = state.length == 3 ? game.getPlayerFromColorOrFaction(state[0]) : null;
        boolean ownerDecision = decidingPlayer == owner;
        if (state.length != 3
                || target == null
                || (ownerDecision && !"pending".equals(state[1]))
                || (!ownerDecision && (decidingPlayer != target || !"pending".equals(state[2])))) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This _Collaborative Research_ offer has expired.");
            return;
        }
        if (spendsTradeGoods && decidingPlayer.getTg() < 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You no longer have 2 trade goods to spend.");
            return;
        }

        if (spendsTradeGoods) decidingPlayer.setTg(decidingPlayer.getTg() - 2);
        state[ownerDecision ? 1 : 2] = spendsTradeGoods ? "spent" : "declined";
        game.setStoredValue(STATE + owner.getFaction(), String.join("|", state));
        ButtonHelper.deleteMessage(event);

        if ("declined".equals(state[1]) || "declined".equals(state[2])) {
            game.removeStoredValue(STATE + owner.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "_Collaborative Research_ ended because " + decidingPlayer.getRepresentationNoPing()
                            + " declined to spend 2 trade goods.");
            return;
        }
        if (!"spent".equals(state[1]) || !"spent".equals(state[2])) return;

        List<Button> buttons = getResearchButtons(game, owner, target);
        if (buttons.isEmpty()) {
            game.removeStoredValue(STATE + owner.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Both players spent 2 trade goods for _Collaborative Research_, but "
                            + owner.getRepresentationNoPing() + " has no eligible technology to research.");
            return;
        }
        String message = owner.getRepresentationNoPing()
                + ", choose a technology to research for _Collaborative Research_. The selected player will gain it after you do.";
        MessageHelper.sendMessageToChannelWithButtons(
                owner.getCardsInfoThread(),
                message,
                NewStuffHelper.buttonPagination(buttons, owner.factionButtonChecker() + RESEARCH, 0));
    }

    private static List<Button> getResearchButtons(Game game, Player player, Player target) {
        List<Button> buttons = new ArrayList<>();
        if (target == null) return buttons;
        for (TechnologyModel tech : Mapper.getTechs().values()) {
            if (!isEligibleTech(game, player, target, tech)) continue;
            String buttonID = player.factionButtonChecker() + RESEARCH + tech.getAlias();
            String emoji = tech.getCondensedReqsEmojis(true);
            buttons.add(
                    switch (tech.getFirstType()) {
                        case PROPULSION -> Buttons.blue(buttonID, tech.getName(), emoji);
                        case BIOTIC -> Buttons.green(buttonID, tech.getName(), emoji);
                        case WARFARE -> Buttons.red(buttonID, tech.getName(), emoji);
                        default -> Buttons.gray(buttonID, tech.getName(), emoji);
                    });
        }
        buttons.sort((first, second) -> first.getLabel().compareToIgnoreCase(second.getLabel()));
        return buttons;
    }

    private static boolean isEligibleTech(Game game, Player player, Player target, TechnologyModel tech) {
        return !tech.isFactionTech()
                && game.getTechnologyDeck().contains(tech.getAlias())
                && !player.hasTech(tech.getAlias())
                && !target.hasTech(tech.getAlias())
                && ListTechService.isTechResearchable(tech, player);
    }
}

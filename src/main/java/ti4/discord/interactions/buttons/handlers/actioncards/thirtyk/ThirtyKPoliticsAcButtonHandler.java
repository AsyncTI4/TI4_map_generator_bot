package ti4.discord.interactions.buttons.handlers.actioncards.thirtyk;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.thirtyk.ThirtyKAcState;
import ti4.helpers.thirtyk.ThirtyKTransactions;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.service.fow.PlanetTargetService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.objectives.DrawSecretService;
import ti4.service.strategycard.StrategyCardSecondaryButtonService;

@UtilityClass
public class ThirtyKPoliticsAcButtonHandler {

    public static final String DENOUNCE = "thkAcDenounceStart";
    public static final String DEVISIVE = "thkAcDevisive_";
    public static final String EXTENDED_CAMPAIGNING = "thkAcExtCampaign_";
    public static final String TRADE_SHIPS = "thkAcTradeShips_";
    public static final String RELIC_REVEAL = "thkAcRelicReveal_";
    public static final String RESOURCES = "res";
    public static final String INFLUENCE = "inf";
    public static final String COMMAND_TOKENS = "cc";
    private static final String DENOUNCE_PN = "thkAcDenouncePn_";
    private static final String DEVISIVE_SC = "thkAcDevisiveSc_";
    private static final String READY_AGENT = "thkAcReadyAgent_";
    private static final String RELIC_GAIN = "thkAcRelicGain_";
    private static final String RELIC_GAIN_CC = "thkAcRelicGainCc_";
    private static final String RELIC_BOTTOM = "thkAcRelicBottom_";

    @ButtonHandler(DENOUNCE)
    public static void denounceLeadership(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String note : player.getPromissoryNotesInPlayArea()) {
            if (!note.endsWith("_sftt")) continue;
            PromissoryNoteModel model = Mapper.getPromissoryNote(note);
            String owner = game.getPNOwner(note) == null
                    ? ""
                    : " (" + game.getPNOwner(note).getColor() + ")";
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + DENOUNCE_PN + note,
                    "Purge " + (model == null ? note : model.getName()) + owner));
        }
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Denounce Leadership");
            return;
        }
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged()
                        + ", choose the _Support for the Throne_ in your play area to purge.",
                buttons);
    }

    @ButtonHandler(DENOUNCE_PN)
    public static void denouncePurge(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String note = buttonID.substring(DENOUNCE_PN.length());
        if (!note.endsWith("_sftt") || !player.getPromissoryNotesInPlayArea().contains(note)) return;
        Player owner = game.getPNOwner(note);
        player.removePromissoryNote(note);
        game.setPurgedPN(note);
        if (owner != null) {
            owner.removePromissoryNote(note);
            owner.removeOwnedPromissoryNoteByID(note);
        }
        ButtonHelper.deleteMessage(event);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        String purged = player.getRepresentationUnfogged() + ", you publicly denounce your patron: _Support for the"
                + " Throne_ is purged and you draw a secret objective (_Denounce Leadership_).";
        if (owner != null && owner != player) {
            FoWHelper.notifyPlayerAndAffectedInFog(
                    game,
                    player,
                    purged,
                    owner,
                    owner.getRepresentationUnfogged() + ", your _Support for the Throne_ was torn up in a public"
                            + " denunciation and purged (_Denounce Leadership_).");
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), purged);
        }
        DrawSecretService.drawSO(event, game, player);
    }

    public static List<Button> devisivePlotButtons(Game game, Player player) {
        return ThirtyKAcTargets.otherPlayerButtons(
                game, player, DEVISIVE, other -> !unplayedCards(game, other).isEmpty());
    }

    private static List<Integer> unplayedCards(Game game, Player holder) {
        List<Integer> cards = new ArrayList<>();
        for (Integer sc : holder.getSCs()) {
            if (!Boolean.TRUE.equals(game.getScPlayed().get(sc))) cards.add(sc);
        }
        return cards;
    }

    @ButtonHandler(DEVISIVE)
    public static void devisivePlot(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, DEVISIVE);
        if (target == null || target == player) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        List<Integer> cards = game.isFowMode()
                ? game.getSCList().stream()
                        .filter(sc -> !Boolean.TRUE.equals(game.getScPlayed().get(sc)))
                        .toList()
                : unplayedCards(game, target);
        List<Button> buttons = new ArrayList<>();
        for (Integer sc : cards) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + DEVISIVE_SC + target.getColor() + "_" + sc,
                    sc + " " + game.getSCName(sc)));
        }
        if (buttons.isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "Divisive Plot");
            return;
        }
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player, player.getRepresentationUnfogged() + ", choose their unplayed strategy card.", buttons);
    }

    @ButtonHandler(DEVISIVE_SC)
    public static void devisiveSecondary(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, DEVISIVE_SC);
        int sc = NumberUtils.toInt(ThirtyKAcTargets.afterTarget(buttonID, DEVISIVE_SC), -1);
        if (target == null || !unplayedCards(game, target).contains(sc)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        if (player.getStrategicCC() < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "_Divisive Plot_ needs a command token from your strategy pool, and yours is empty.");
            return;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = new ArrayList<>(StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, sc));
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Done Resolving"));
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", your agents subvert " + game.getSCName(sc)
                        + ": 1 command token is spent from your strategy pool; resolve its secondary ability"
                        + " (_Divisive Plot_)."
                        + (buttons.size() == 1 ? " This card has no secondary buttons; resolve it manually." : ""),
                buttons);
    }

    public static List<Button> extendedCampaigningButtons(Game game, Player player) {
        return ThirtyKAcTargets.otherPlayerButtons(
                game, player, EXTENDED_CAMPAIGNING, other -> !player.isNeighboursWith(other));
    }

    @ButtonHandler(EXTENDED_CAMPAIGNING)
    public static void extendedCampaigning(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, EXTENDED_CAMPAIGNING);
        if (target == null || target == player || player.isNeighboursWith(target)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        grantTransaction(event, game, player, target, "Extended Campaigning");
        ThirtyKAcState.setExtendedCampaigning(game, player, target);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", " + FoWHelper.factionEmojiOrAnon(game, player, "a player")
                        + " sends envoys bearing gifts. If you accept a transaction with them this turn, you may ready"
                        + " your agent (_Extended Campaigning_).");
    }

    public static void offerAgentReady(Game game, Player target) {
        List<Button> agents = new ArrayList<>();
        for (Leader leader : target.getLeaders()) {
            if (!"agent".equalsIgnoreCase(leader.getType()) || !leader.isExhausted()) continue;
            agents.add(Buttons.green(
                    target.factionButtonChecker() + READY_AGENT + leader.getId(), "Ready " + leader.getName()));
        }
        agents.add(Buttons.gray(target.factionButtonChecker() + "deleteButtons", "No Agent To Ready"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", the deal is struck, so you may ready your agent (_Extended Campaigning_).",
                agents);
    }

    public static List<Button> tradeShipsButtons(Game game, Player player) {
        return ThirtyKAcTargets.otherPlayerButtons(game, player, TRADE_SHIPS, null);
    }

    @ButtonHandler(TRADE_SHIPS)
    public static void tradeShips(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = ThirtyKAcTargets.target(game, buttonID, TRADE_SHIPS);
        if (target == null || target == player) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        grantTransaction(event, game, player, target, "Trade Ships");
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", " + FoWHelper.factionEmojiOrAnon(game, player, "a player")
                        + " sends trade ships your way; you may transact this turn as if you were neighbors"
                        + " (_Trade Ships_).");
    }

    private static void grantTransaction(
            ButtonInteractionEvent event, Game game, Player player, Player target, String cardName) {
        ThirtyKTransactions.allowForThisTurn(game, player, target);
        ButtonHelper.deleteMessage(event);
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", a trade corridor opens to "
                        + ThirtyKAcTargets.name(game, target, player)
                        + "; you may transact this turn as if you were neighbors (_" + cardName + "_).",
                List.of(Buttons.green(
                        player.factionButtonChecker() + "transactWith_" + target.getColor(), "Start Transaction")));
    }

    @ButtonHandler(READY_AGENT)
    public static void readyAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String leaderId = buttonID.substring(READY_AGENT.length());
        Leader agent = player.getLeaders().stream()
                .filter(leader -> leader.getId().equals(leaderId))
                .findFirst()
                .orElse(null);
        ButtonHelper.deleteMessage(event);
        if (agent == null || !agent.isExhausted()) return;
        RefreshLeaderService.refreshLeader(player, agent, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", " + agent.getName()
                        + " is back in the field and ready (_Extended Campaigning_).");
    }

    @ButtonHandler(RELIC_REVEAL)
    public static void relicReveal(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String mode = buttonID.substring(RELIC_REVEAL.length());
        if (game.getAllRelics().isEmpty()) {
            ThirtyKAcTargets.noTarget(event, player, "relic reveal");
            return;
        }
        String relic = game.getAllRelics().getFirst();
        RelicModel model = Mapper.getRelic(relic.replace("extra1", "").replace("extra2", ""));
        ButtonHelper.deleteMessage(event);
        String reveal = "unearthed the top card of the relic deck:";
        if (model != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.isFowMode() ? player.getCorrectChannel() : game.getMainGameChannel(),
                    FoWHelper.actorOrAnon(game, player, "You") + " " + reveal,
                    model.getRepresentationEmbed());
        }
        List<Button> buttons = new ArrayList<>();
        String ffcc = player.factionButtonChecker();
        if (COMMAND_TOKENS.equals(mode)) {
            if (player.getTacticalCC() + player.getFleetCC() + player.getStrategicCC() >= 2) {
                buttons.add(Buttons.green(ffcc + RELIC_GAIN_CC + relic, "Remove 2 Command Tokens And Gain It"));
            }
        } else {
            buttons.addAll(ButtonHelper.getExhaustButtonsWithTG(game, player, mode));
            buttons.add(Buttons.green(
                    ffcc + RELIC_GAIN + relic,
                    "Done Paying 5 " + (RESOURCES.equals(mode) ? "Resources" : "Influence")));
        }
        buttons.add(Buttons.red(ffcc + RELIC_BOTTOM + relic, "Put It On The Bottom"));
        ThirtyKAcTargets.send(
                player,
                player.getRepresentationUnfogged() + ", gain the revealed relic by paying for it, or put it on the"
                        + " bottom of the relic deck.",
                buttons);
    }

    @ButtonHandler(RELIC_GAIN)
    public static void relicGain(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        gainTopRelic(event, game, player, buttonID.substring(RELIC_GAIN.length()));
    }

    @ButtonHandler(RELIC_GAIN_CC)
    public static void relicGainForTokens(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!gainTopRelic(event, game, player, buttonID.substring(RELIC_GAIN_CC.length()))) return;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", remove 2 command tokens from your command sheet.",
                ButtonHelper.getLoseCCButtons(player));
    }

    private static boolean gainTopRelic(ButtonInteractionEvent event, Game game, Player player, String relic) {
        ButtonHelper.deleteMessage(event);
        if (game.getAllRelics().isEmpty() || !relic.equals(game.getAllRelics().getFirst())) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), PlanetTargetService.fizzleMessage());
            return false;
        }
        String relicID = game.drawRelic(0).replace("extra1", "").replace("extra2", "");
        RelicHelper.gainRelicAndNotify(player, event, game, relicID, "gained");
        return true;
    }

    @ButtonHandler(RELIC_BOTTOM)
    public static void relicBottom(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring(RELIC_BOTTOM.length());
        ButtonHelper.deleteMessage(event);
        List<String> deck = game.getAllRelics();
        if (deck.isEmpty() || !relic.equals(deck.getFirst())) return;
        deck.removeFirst();
        deck.add(relic);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", the relic is sealed away again at the bottom of the relic deck.");
    }
}

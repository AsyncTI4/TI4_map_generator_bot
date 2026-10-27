package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;
import ti4.service.actioncard.ForceGiveActionCardService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.PlayHeroService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.strategycard.StrategyCardSecondaryButtonService;

@UtilityClass
public class MorphaLeadersHandler {
    private static final String AGENT = "morphaagent";
    private static final String USE_AGENT = "useMorphaAgent";
    private static final String SELECT_TARGET = "morphaAgentTarget_";
    private static final String TAKE_DISCARD = "morphaAgentTakeDiscard_";
    private static final String HERO = "morphahero";
    private static final String HERO_PENDING = "morphaHeroPending";
    private static final String HERO_DISCARDS = "morphaHeroDiscards";
    private static final String HERO_DISCARD = "morphaHeroDiscard_";
    private static final String HERO_DONE_DISCARDING = "morphaHeroDoneDiscarding";
    private static final String HERO_CANCEL = "morphaHeroCancel";
    private static final String HERO_PRIMARY = "morphaHeroPrimary_";
    private static final String HERO_SECONDARY = "morphaHeroSecondary_";
    private static final String HERO_GIVE_ACTION_CARD = "morphaHeroGiveActionCard_";

    public static Button getGolgiCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_AGENT, "Use Golgi, the Morpha Agent", FactionEmojis.morpha);
    }

    public static void startMorphaHero(Game game, Player player) {
        if (!player.hasLeaderUnlocked(HERO) || player.getActionCards().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " needs at least 1 Action Card to use _Perfect Adaptation_.");
            return;
        }
        player.setStoredValue(HERO_PENDING, "true");
        player.removeStoredValue(HERO_DISCARDS);
        sendHeroDiscardButtons(player);
    }

    @ButtonHandler(HERO_DISCARD)
    public static void discardForMorphaHero(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        if (!"true".equals(player.getStoredValue(HERO_PENDING))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                heroDiscardMessage(player),
                () -> getHeroDiscardButtons(player),
                getHeroDiscardPinnedButtons(player),
                player.factionButtonChecker() + HERO_DISCARD)) return;

        Integer actionCardNumber = parseNumber(buttonID.substring(HERO_DISCARD.length()));
        if (actionCardNumber == null || !game.discardActionCard(player.getUserID(), actionCardNumber, false)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.addToStoredList(HERO_DISCARDS, actionCardNumber.toString());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(HERO_DONE_DISCARDING)
    public static void chooseMorphaHeroPrimary(ButtonInteractionEvent event, Game game, Player player) {
        int discardCount = player.getStoredList(HERO_DISCARDS).size();
        if (!"true".equals(player.getStoredValue(HERO_PENDING)) || discardCount < 1) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "Discard at least 1 Action Card before choosing a strategy card.");
            return;
        }
        List<Button> buttons = getHeroPrimaryButtons(game, player, discardCount);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "There is no eligible readied strategy card for this many discarded cards.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose a strategy card primary to resolve for _Perfect Adaptation_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_CANCEL)
    public static void cancelMorphaHero(ButtonInteractionEvent event, Player player) {
        player.removeStoredValue(HERO_PENDING);
        player.removeStoredValue(HERO_DISCARDS);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_PRIMARY)
    public static void resolveMorphaHeroPrimary(
            ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        Integer strategyCard = parseNumber(buttonID.substring(HERO_PRIMARY.length()));
        int discardCount = player.getStoredList(HERO_DISCARDS).size();
        if (!"true".equals(player.getStoredValue(HERO_PENDING))
                || strategyCard == null
                || !getEligibleHeroStrategyCards(game, discardCount).contains(strategyCard)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setStoredValue(HERO_PENDING, "resolve");
        PlayHeroService.playHero(event, game, player, player.unsafeGetLeader(HERO));
        player.removeStoredValue(HERO_PENDING);
        player.removeStoredValue(HERO_DISCARDS);
        PlayStrategyCardService.playSC(event, strategyCard, game, game.getMainGameChannel(), player, true, false);
        offerMorphaHeroSecondaries(game, player, strategyCard);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_SECONDARY)
    public static void offerMorphaHeroActionCard(
            ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String[] payload = buttonID.substring(HERO_SECONDARY.length()).split("\\|", 2);
        Player heroOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Integer strategyCard = payload.length == 2 ? parseNumber(payload[1]) : null;
        if (heroOwner == null
                || strategyCard == null
                || player == heroOwner
                || player.getActionCards().isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendMorphaHeroActionCardButtons(player, heroOwner, strategyCard);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(HERO_GIVE_ACTION_CARD)
    public static void giveActionCardForMorphaHeroSecondary(
            ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String[] payload = buttonID.substring(HERO_GIVE_ACTION_CARD.length()).split("\\|", 3);
        Player heroOwner = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Integer strategyCard = payload.length == 3 ? parseNumber(payload[1]) : null;
        Integer actionCardNumber = payload.length == 3 ? parseNumber(payload[2]) : null;
        String actionCard = getActionCardByNumber(player, actionCardNumber);
        if (heroOwner == null || strategyCard == null || actionCard == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ActionCardHelper.sendActionCard(event, game, player, heroOwner, actionCard);
        List<Button> buttons =
                new ArrayList<>(StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, strategyCard));
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Done Resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " may resolve the secondary ability of **"
                        + game.getSCName(strategyCard)
                        + "** without spending a command token for _Perfect Adaptation_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_AGENT)
    public static void chooseGolgiTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) return;

        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayersExcludingThis(player)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_TARGET + target.getFaction(),
                    target.getFactionNameOrColor(),
                    target.getFactionEmojiOrColor()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no other players to select.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), "Choose a player for Golgi, the Morpha agent.", buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void selectGolgiTarget(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET.length()));
        Leader agent = player.getLeaderByID(AGENT).orElse(null);
        if (target == null
                || target == player
                || !game.getRealPlayersExcludingThis(player).contains(target)
                || agent == null
                || !player.hasUnexhaustedLeader(AGENT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (getDiscardButtons(game, target, player).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "There are no non-purged Action Cards in the discard pile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        AgentLifecycle.spend(game, player, AGENT);
        game.setStoredValue(targetKey(player), target.getFaction());
        sendDiscardSelection(game, target, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(TAKE_DISCARD)
    public static void takeDiscardedActionCard(
            ButtonInteractionEvent event, String buttonID, Game game, Player target) {
        String[] payload = buttonID.substring(TAKE_DISCARD.length()).split("\\|", 2);
        Player agentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (agentOwner == null || !target.getFaction().equals(game.getStoredValue(targetKey(agentOwner)))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String message = target.getRepresentationNoPing()
                + ", choose the discarded Action Card to take for Golgi, the Morpha agent.";
        String navPrefix = target.factionButtonChecker() + TAKE_DISCARD + agentOwner.getFaction() + "|";
        if (NewStuffHelper.turnPage(
                event, buttonID, message, () -> getDiscardButtons(game, target, agentOwner), List.of(), navPrefix))
            return;

        Integer actionCardNumber = parseNumber(payload[1]);
        if (actionCardNumber == null || !game.pickActionCard(target.getUserID(), actionCardNumber)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ForceGiveActionCardService.sendGiveACButtons(
                agentOwner,
                target,
                game,
                target.getRepresentationNoPing() + ", choose 1 Action Card to give to "
                        + agentOwner.getRepresentationNoPing()
                        + " for Golgi, the Morpha agent.");
        game.removeStoredValue(targetKey(agentOwner));
        ButtonHelper.deleteMessage(event);
    }

    private static void sendDiscardSelection(Game game, Player target, Player agentOwner) {
        NewStuffHelper.sendPaginated(
                target.getCorrectChannel(),
                target.getRepresentationNoPing()
                        + ", choose the discarded Action Card to take for Golgi, the Morpha agent.",
                getDiscardButtons(game, target, agentOwner),
                List.of(),
                target.factionButtonChecker() + TAKE_DISCARD + agentOwner.getFaction() + "|");
    }

    private static void sendHeroDiscardButtons(Player player) {
        NewStuffHelper.sendPaginated(
                player.getCardsInfoThread(),
                heroDiscardMessage(player),
                getHeroDiscardButtons(player),
                getHeroDiscardPinnedButtons(player),
                player.factionButtonChecker() + HERO_DISCARD);
    }

    private static String heroDiscardMessage(Player player) {
        return player.getRepresentationNoPing()
                + ", discard any number of Action Cards for _Perfect Adaptation_, then choose a strategy card primary with initiative up to that number.";
    }

    private static List<Button> getHeroDiscardButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : player.getActionCards().entrySet()) {
            ActionCardModel model = Mapper.getActionCard(entry.getKey());
            String name = model == null ? entry.getKey() : model.getName();
            buttons.add(
                    Buttons.red(player.factionButtonChecker() + HERO_DISCARD + entry.getValue(), "Discard " + name));
        }
        return buttons;
    }

    private static List<Button> getHeroDiscardPinnedButtons(Player player) {
        return List.of(
                Buttons.green(player.factionButtonChecker() + HERO_DONE_DISCARDING, "Done Discarding"),
                Buttons.red(player.factionButtonChecker() + HERO_CANCEL, "Cancel"));
    }

    private static List<Button> getHeroPrimaryButtons(Game game, Player player, int discardCount) {
        return getEligibleHeroStrategyCards(game, discardCount).stream()
                .map(strategyCard -> Buttons.gray(
                        player.factionButtonChecker() + HERO_PRIMARY + strategyCard,
                        "Primary of " + game.getSCName(strategyCard),
                        CardEmojis.getSCBackFromInteger(strategyCard.intValue())))
                .toList();
    }

    private static List<Integer> getEligibleHeroStrategyCards(Game game, int discardCount) {
        return game.getSCList().stream()
                .filter(strategyCard -> strategyCard > 0)
                .filter(strategyCard -> strategyCard <= discardCount)
                .filter(strategyCard -> !Boolean.TRUE.equals(game.getScPlayed().get(strategyCard)))
                .toList();
    }

    private static void offerMorphaHeroSecondaries(Game game, Player heroOwner, int strategyCard) {
        for (Player player : game.getRealPlayersExcludingThis(heroOwner)) {
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may give an Action Card to "
                            + heroOwner.getRepresentationNoPing()
                            + " to resolve the secondary ability of **"
                            + game.getSCName(strategyCard)
                            + "** without spending a command token for _Perfect Adaptation_.",
                    Buttons.green(
                            player.factionButtonChecker() + HERO_SECONDARY + heroOwner.getFaction() + "|"
                                    + strategyCard,
                            "Give an Action Card"));
        }
    }

    private static void sendMorphaHeroActionCardButtons(Player player, Player heroOwner, int strategyCard) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : player.getActionCards().entrySet()) {
            ActionCardModel model = Mapper.getActionCard(entry.getKey());
            String name = model == null ? entry.getKey() : model.getName();
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + HERO_GIVE_ACTION_CARD + heroOwner.getFaction() + "|" + strategyCard
                            + "|" + entry.getValue(),
                    "Give " + name));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", choose 1 Action Card to give to "
                        + heroOwner.getRepresentationNoPing()
                        + " for _Perfect Adaptation_.",
                buttons);
    }

    private static List<Button> getDiscardButtons(Game game, Player target, Player agentOwner) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : game.getDiscardActionCards().entrySet()) {
            if (game.getDiscardACStatus().get(entry.getKey()) == ACStatus.purged) continue;
            ActionCardModel model = Mapper.getActionCard(entry.getKey());
            String name = model == null ? entry.getKey() : model.getName();
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + TAKE_DISCARD + agentOwner.getFaction() + "|" + entry.getValue(),
                    "Take " + name));
        }
        return buttons;
    }

    private static Integer parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getActionCardByNumber(Player player, Integer actionCardNumber) {
        if (actionCardNumber == null) return null;
        return player.getActionCards().entrySet().stream()
                .filter(entry -> entry.getValue().equals(actionCardNumber))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static String targetKey(Player agentOwner) {
        return "morphaAgentTarget_" + agentOwner.getFaction();
    }
}

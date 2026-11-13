package ti4.service.franken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.draft.FrankenDraft;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.DraftErrataModel;
import ti4.service.fow.GMService;

@UtilityClass
public class AuctionFrankenDraftService {

    public static final String ACTION_PREFIX = "auctionDraft";
    public static final String BID_MODAL = "auctionDraftBid";
    private static final String PREFIX = "auctionDraft.";
    private static final int STARTING_VOTES = 30;
    private static final int STARTING_BANS = 1;
    private static final int STARTING_MULLIGANS = 1;
    private static final int EXTRA_COMPONENTS = 2;

    public void configure(Game game, int votes, int bans, int mulligans, int extraComponents) {
        set(game, "setting.votes", String.valueOf(votes));
        set(game, "setting.bans", String.valueOf(bans));
        set(game, "setting.mulligans", String.valueOf(mulligans));
        set(game, "setting.extra", String.valueOf(extraComponents));
    }

    public void start(Game game) {
        clear(game);
        snapshotCategoryLimits(game);
        List<Player> players = new ArrayList<>(game.getRealPlayers());
        if (players.isEmpty()) return;
        int first = (int) (Math.random() * players.size());
        Collections.rotate(players, -first);
        setList(game, "players", players.stream().map(Player::getFaction).toList());
        set(game, "phase", "nominate");
        set(game, "nomination", "1");
        set(game, "nominator", players.getFirst().getFaction());
        for (Player player : players) {
            set(game, playerKey("votes", player), String.valueOf(getSetting(game, "votes", STARTING_VOTES)));
            set(game, playerKey("bans", player), String.valueOf(getSetting(game, "bans", STARTING_BANS)));
            set(
                    game,
                    playerKey("mulligans", player),
                    String.valueOf(getSetting(game, "mulligans", STARTING_MULLIGANS)));
        }
        createDraftedComponentsThread(game);
        MessageHelper.sendMessageToChannel(auctionChannel(game), "# " + game.getPing() + " Auction Draft has started.");
        MessageHelper.sendMessageToChannel(
                auctionChannel(game),
                "__Auction Draft__\n"
                        + "Each round, the nominator chooses a component category. They may keep the offered pool, ban one component and replace it, or mulligan the whole pool.\n"
                        + "Everyone then privately bids from their remaining votes to determine the pick order. Players draft one component in that order, then a new nominator begins the next round.\n"
                        + "Your votes, ban, and mulligan are shared across the whole draft. You may rank components for auto-pick in your `#cards-info` thread.");
        MessageHelper.sendMessageToChannel(
                auctionChannel(game), players.getFirst().getRepresentationNoPing() + " is the first nominator.");
        replaceAuctionCardsInfoForPlayers(game, () -> {
            sendDraftStartWarnings(game);
            sendNominationPrompt(game);
        });
    }

    public void nominate(
            Game game, Player player, ButtonInteractionEvent event, int nomination, DraftCategory category) {
        if (!canNominate(game, category)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That category cannot be nominated again.");
            return;
        }
        List<DraftItem> pool = drawPool(game, category, getPoolSize(game), Set.of());
        if (pool.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are not enough eligible components in that category.");
            return;
        }
        set(game, "category", category.name());
        setList(game, "pool", aliases(pool));
        setList(game, "setAside", List.of());
        set(game, "nominator", player.getFaction());
        set(game, "phase", "review");
        ButtonHelper.deleteMessage(event);
        if (number(game, playerKey("bans", player)) == 0 && number(game, playerKey("mulligans", player)) == 0) {
            MessageHelper.sendMessageToChannel(
                    auctionLogChannel(game),
                    player.getRepresentationNoPing() + " has no bans or mulligans remaining, so the pool is kept.");
            beginBidding(game);
            return;
        }
        sendReviewPrompts(game);
    }

    public void review(
            Game game, Player player, ButtonInteractionEvent event, int nomination, String action, String alias) {
        if (!isCurrent(game, "review", nomination)) return;
        if ("ban".equals(action)) {
            if (number(game, playerKey("bans", player)) < 1 || !pool(game).contains(alias)) return;
            DraftItem item = DraftItem.generateFromAlias(alias);
            addToList(game, "removed", alias);
            set(game, playerKey("bans", player), String.valueOf(number(game, playerKey("bans", player)) - 1));
            replaceBannedItem(game, alias);
            MessageHelper.sendMessageToChannel(
                    auctionLogChannel(game),
                    player.getRepresentationNoPing() + " used a ban on " + item.getTitle(game) + ".");
        } else if ("mulligan".equals(action)) {
            if (number(game, playerKey("mulligans", player)) < 1) return;
            set(game, playerKey("mulligans", player), String.valueOf(number(game, playerKey("mulligans", player)) - 1));
            List<String> oldPool = pool(game);
            setList(game, "setAside", oldPool);
            List<DraftItem> replacement = drawPool(game, category(game), oldPool.size(), new LinkedHashSet<>(oldPool));
            if (replacement.size() == oldPool.size()) {
                setList(game, "pool", aliases(replacement));
            }
            MessageHelper.sendMessageToChannel(
                    auctionLogChannel(game),
                    player.getRepresentationNoPing() + " used a mulligan on the " + auctionCategoryTitle(game)
                            + " pool.\n" + poolItemsDescription(game, oldPool));
        } else if (!"pass".equals(action)) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        beginBidding(game);
    }

    public void showBidModal(Game game, Player player, ButtonInteractionEvent event, int nomination) {
        if (!isCurrent(game, "bid", nomination)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This bidding prompt is no longer current.");
            return;
        }
        if (locked(game).contains(player.getFaction())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Your bids are already locked.");
            return;
        }
        int votes = number(game, playerKey("votes", player));
        TextInput first = bidInput("first", "First-choice bid", votes);
        TextInput second = optionalBidInput("second", "Second-choice bid", votes);
        TextInput third = optionalBidInput("third", "Later-choice bid", votes);
        Modal modal = Modal.create(BID_MODAL + ";" + nomination, "Auction Bids")
                .addComponents(
                        Label.of("First pick", first), Label.of("Second pick", second), Label.of("Later pick", third))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public void toggleAutoPick(Game game, Player player, ButtonInteractionEvent event, int nomination) {
        if (!isCurrent(game, "bid", nomination)) return;
        String key = playerKey("auto", player);
        boolean enabled = !"true".equals(value(game, key));
        set(game, key, Boolean.toString(enabled));
        MessageHelper.sendEphemeralMessageToEventChannel(
                event,
                enabled
                        ? "You will automatically take the first remaining component in the pool."
                        : "Auto-pick disabled.");
    }

    public void submitZeroBid(Game game, Player player, ButtonInteractionEvent event, int nomination) {
        if (!isCurrent(game, "bid", nomination) || number(game, playerKey("votes", player)) != 0) return;
        set(game, playerKey("bids", player), "0,0,0");
        addToList(game, "locked", player.getFaction());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Your bids are locked at 0 / 0 / 0.");
        if (locked(game).containsAll(playerIds(game))) resolveBids(game);
    }

    public void rank(Game game, Player player, ButtonInteractionEvent event, int nomination, String alias) {
        if (!isCurrent(game, "bid", nomination)
                || locked(game).contains(player.getFaction())
                || !pool(game).contains(alias)) {
            return;
        }
        String key = playerKey("rank", player);
        List<String> rankings = list(game, key);
        if (rankings.contains(alias)) return;
        rankings.add(alias);
        setList(game, key, rankings);
        MessageHelper.editMessageWithButtons(
                event, rankedComponentsMessage(rankings), rankButtons(game, player, nomination));
    }

    public void resetRankings(Game game, Player player, ButtonInteractionEvent event, int nomination) {
        if (!isCurrent(game, "bid", nomination) || locked(game).contains(player.getFaction())) return;
        set(game, playerKey("rank", player), "");
        MessageHelper.editMessageWithButtons(event, rankingPromptMessage(), rankButtons(game, player, nomination));
    }

    public void showDraftedComponents(Game game, Player player, ButtonInteractionEvent event) {
        ThreadChannel thread = draftedComponentsThread(game);
        if (thread == null && event.getMessageChannel() instanceof ThreadChannel eventThread) thread = eventThread;
        if (thread == null) return;
        refreshDraftedComponentsLedger(game, thread);
    }

    public void submitBid(Game game, Player player, ModalInteractionEvent event, int nomination) {
        if (!isCurrent(game, "bid", nomination)) {
            respondToBidSubmission(event, "This bidding prompt is no longer current.");
            return;
        }
        if (locked(game).contains(player.getFaction())) {
            respondToBidSubmission(event, "Your bids are already locked.");
            return;
        }
        int votes = number(game, playerKey("votes", player));
        Integer first = bid(event, "first", votes);
        Integer second = optionalBid(event, "second", votes);
        Integer third = optionalBid(event, "third", votes);
        if (first == null || second == null || third == null) {
            respondToBidSubmission(event, "Each bid must be a whole number from 0 to " + votes + ".");
            return;
        }
        set(game, playerKey("bids", player), first + "," + second + "," + third);
        addToList(game, "locked", player.getFaction());
        respondToBidSubmission(event, "Your bids are locked.");
        if (locked(game).containsAll(playerIds(game))) resolveBids(game);
    }

    private void respondToBidSubmission(ModalInteractionEvent event, String message) {
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    public void pick(Game game, Player player, ButtonInteractionEvent event, int nomination, String alias) {
        if (!pool(game).contains(alias)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That component is no longer available.");
            return;
        }
        set(game, "picker", player.getFaction());
        completePick(game, player, alias);
        ButtonHelper.deleteMessage(event);
        advancePick(game, event);
    }

    private void completePick(Game game, Player player, String alias) {
        DraftItem item = DraftItem.generateFromAlias(alias);
        player.getDraftHand().Contents.add(item);
        List<String> pool = pool(game);
        pool.remove(alias);
        setList(game, "pool", pool);
        addToList(game, "removed", alias);
        addToList(game, playerKey("drafted", player), alias);
        int price = number(game, "price." + player.getFaction());
        set(game, playerKey("votes", player), String.valueOf(number(game, playerKey("votes", player)) - price));
        GameManager.save(game, "Auction draft pick");
        String draftedMessage = player.getRepresentationNoPing() + " drafted " + item.getTitle(game) + ".";
        MessageHelper.sendMessageToChannel(auctionChannel(game), draftedMessage);
    }

    private void advancePick(Game game, GenericInteractionCreateEvent event) {
        List<String> order = list(game, "order");
        int pickIndex = order.indexOf(value(game, "picker")) + 1;
        if (pickIndex == 0) pickIndex = number(game, "pickIndex") + 1;
        set(game, "pickIndex", String.valueOf(pickIndex));
        if (pickIndex >= order.size()) {
            finishNomination(game, event);
            return;
        }
        Player nextPicker = player(game, order.get(pickIndex));
        if (nextPicker == null) return;
        set(game, "picker", nextPicker.getFaction());
        sendPickPrompt(game);
    }

    private TextInput bidInput(String id, String label, int votes) {
        return TextInput.create(id, TextInputStyle.SHORT)
                .setPlaceholder("0 to " + votes)
                .setRequired(true)
                .setMaxLength(String.valueOf(votes).length())
                .build();
    }

    private TextInput optionalBidInput(String id, String label, int votes) {
        return TextInput.create(id, TextInputStyle.SHORT)
                .setPlaceholder("0 to " + votes + " (defaults to 0)")
                .setRequired(false)
                .setMaxLength(String.valueOf(votes).length())
                .build();
    }

    private Integer bid(ModalInteractionEvent event, String id, int maximum) {
        try {
            if (event.getValue(id) == null) return null;
            int result = Integer.parseInt(event.getValue(id).getAsString());
            return result >= 0 && result <= maximum ? result : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer optionalBid(ModalInteractionEvent event, String id, int maximum) {
        return event.getValue(id) == null || event.getValue(id).getAsString().isBlank() ? 0 : bid(event, id, maximum);
    }

    private void beginBidding(Game game) {
        set(game, "phase", "bid");
        set(game, "locked", "");
        for (Player player : players(game)) {
            set(game, playerKey("bids", player), "");
            set(game, playerKey("rank", player), "");
            set(game, playerKey("auto", player), "false");
        }
        int nomination = currentNomination(game);
        replaceAuctionCardsInfoForPlayers(game, () -> sendBiddingPrompts(game, nomination));
    }

    private void sendBiddingPrompts(Game game, int nomination) {
        if (!isCurrent(game, "bid", nomination)) return;
        for (Player player : players(game)) {
            sendComponentDisplays(player.getCardsInfoThread(), game, player, pool(game));
            if (number(game, playerKey("votes", player)) == 0) {
                MessageHelper.sendMessageToChannelWithButton(
                        player.getCardsInfoThread(),
                        player.getPing()
                                + ", you have no votes. You may rank or auto-pick before submitting 0 / 0 / 0.",
                        Buttons.green(
                                player.factionButtonChecker() + ACTION_PREFIX + ";zeroBid;" + nomination,
                                "Submit 0 Bids"));
            } else {
                MessageHelper.sendMessageToChannelWithButton(
                        player.getCardsInfoThread(),
                        player.getPing() + ", submit your three private bids for " + auctionCategoryTitle(game)
                                + ".\n-# Remaining votes: " + number(game, playerKey("votes", player)),
                        Buttons.green(
                                player.factionButtonChecker() + ACTION_PREFIX + "~MDL;bid;" + nomination,
                                "Submit Private Bids"));
            }
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCardsInfoThread(),
                    "You may have the bot select the first remaining component when it is your turn.",
                    Buttons.gray(
                            player.factionButtonChecker() + ACTION_PREFIX + ";auto;" + nomination, "Toggle Auto-pick"));
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "Optionally rank components for auto-pick, in preference order.",
                    rankButtons(game, player, nomination));
        }
    }

    private List<net.dv8tion.jda.api.components.buttons.Button> rankButtons(Game game, Player player, int nomination) {
        List<String> rankings = list(game, playerKey("rank", player));
        List<net.dv8tion.jda.api.components.buttons.Button> buttons = pool(game).stream()
                .filter(alias -> !rankings.contains(alias))
                .map(alias -> {
                    DraftItem item = DraftItem.generateFromAlias(alias);
                    return Buttons.gray(
                            player.factionButtonChecker() + ACTION_PREFIX + ";rank;" + nomination + ";" + alias,
                            "Rank " + item.getShortDescription(),
                            item.getItemEmoji());
                })
                .collect(Collectors.toCollection(ArrayList::new));
        if (!rankings.isEmpty()) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + ACTION_PREFIX + ";resetRankings;" + nomination,
                    "Reset Ranked Picks"));
        }
        return buttons;
    }

    private String rankedComponentsMessage(List<String> rankings) {
        StringBuilder message = new StringBuilder("Your auto-pick ranking:\n");
        for (int index = 0; index < rankings.size(); index++) {
            DraftItem item = DraftItem.generateFromAlias(rankings.get(index));
            message.append(index + 1)
                    .append(". ")
                    .append(item.getShortDescription())
                    .append('\n');
        }
        return message + "Select the next component in your preference order.";
    }

    private String rankingPromptMessage() {
        return "Optionally rank components for auto-pick, in preference order.";
    }

    private void resolveBids(Game game) {
        List<Player> remaining = new ArrayList<>(players(game));
        List<Player> order = new ArrayList<>();
        List<Integer> prices = new ArrayList<>();
        chooseHighestBidder(remaining, order, prices, 0);
        if (!remaining.isEmpty()) chooseHighestBidder(remaining, order, prices, 1);
        while (!remaining.isEmpty()) chooseHighestBidder(remaining, order, prices, 2);
        setList(game, "order", order.stream().map(Player::getFaction).toList());
        for (int i = 0; i < order.size(); i++)
            set(game, "price." + order.get(i).getFaction(), String.valueOf(prices.get(i)));
        set(game, "pickIndex", "0");
        set(game, "picker", order.getFirst().getFaction());
        set(game, "phase", "pick");
        String result = order.stream()
                .map(player -> player.getRepresentationNoPing() + " (" + number(game, "price." + player.getFaction())
                        + " votes)")
                .collect(Collectors.joining("\n> ", "__Auction Order__\n> ", ""));
        MessageHelper.sendMessageToChannel(auctionChannel(game), result);
        MessageHelper.sendMessageToChannel(auctionChannel(game), bidSummary(game));
        Player picker = player(game, value(game, "picker"));
        if (picker != null) {
            int nomination = currentNomination(game);
            replaceAuctionCardsInfoForPlayers(game, () -> sendPickPromptAfterClearing(game, picker, nomination));
        }
    }

    private void chooseHighestBidder(List<Player> remaining, List<Player> order, List<Integer> prices, int bidIndex) {
        int highBid = remaining.stream()
                .mapToInt(player -> bids(player)[bidIndex])
                .max()
                .orElse(0);
        List<Player> tied = remaining.stream()
                .filter(player -> bids(player)[bidIndex] == highBid)
                .toList();
        Player chosen = tied.get((int) (Math.random() * tied.size()));
        remaining.remove(chosen);
        order.add(chosen);
        prices.add(highBid);
    }

    private void sendPickPrompt(Game game) {
        Player picker = player(game, value(game, "picker"));
        if (picker == null) return;
        sendPickPrompt(game, picker);
    }

    private void sendPickPrompt(Game game, Player picker) {
        int nomination = currentNomination(game);
        replaceAuctionCardsInfo(picker, () -> sendPickPromptAfterClearing(game, picker, nomination));
    }

    private void sendPickPromptAfterClearing(Game game, Player picker, int nomination) {
        if (!isCurrent(game, "pick", nomination) || !picker.getFaction().equals(value(game, "picker"))) return;
        List<String> rankings = list(game, playerKey("rank", picker));
        String rankedPick =
                rankings.stream().filter(pool(game)::contains).findFirst().orElse(null);
        if (rankedPick != null) {
            completePick(game, picker, rankedPick);
            advancePick(game, null);
            return;
        }
        if ("true".equals(value(game, playerKey("auto", picker))) && !pool(game).isEmpty()) {
            completePick(game, picker, pool(game).get((int)
                    (Math.random() * pool(game).size())));
            advancePick(game, null);
            return;
        }
        sendComponentDisplays(picker.getCardsInfoThread(), game, picker, pool(game));
        List<net.dv8tion.jda.api.components.buttons.Button> buttons = pool(game).stream()
                .map(alias -> {
                    DraftItem item = DraftItem.generateFromAlias(alias);
                    return Buttons.green(
                            picker.factionButtonChecker() + ACTION_PREFIX + ";pick;" + nomination + ";" + alias,
                            item.getShortDescription(),
                            item.getItemEmoji());
                })
                .toList();
        MessageHelper.sendMessageToChannel(
                picker.getCardsInfoThread(),
                picker.getPing() + ", choose one " + auctionCategoryTitle(game) + " component.\n-# Cost: "
                        + number(game, "price." + picker.getFaction()) + " votes\n-# Remaining votes: "
                        + number(game, playerKey("votes", picker)),
                buttons);
    }

    private void finishNomination(Game game, GenericInteractionCreateEvent event) {
        sendRoundDraftedComponents(game);
        retireUnchosenComponents(game);
        DraftCategory category = category(game);
        set(game, "count." + category.name(), String.valueOf(number(game, "count." + category.name()) + 1));
        int current = playerIds(game).indexOf(value(game, "nominator"));
        List<String> players = playerIds(game);
        set(game, "nominator", players.get((current + 1) % players.size()));
        set(game, "phase", "nominate");
        set(game, "pool", "");
        set(game, "setAside", "");
        if (allNominationsComplete(game)) {
            set(game, "phase", "complete");
            MessageHelper.sendMessageToChannel(auctionChannel(game), "Auction Draft is complete.");
            MessageHelper.sendMessageToChannel(
                    auctionLogChannel(game),
                    game.getPing()
                            + " the auction draft is complete. Choose how to set up the map. Once the map is finalized, select your components from your drafted hand.",
                    List.of(
                            Buttons.green("startFrankenSliceBuild", "Randomize Your Slices (Sorta)"),
                            Buttons.green("startFrankenMantisBuild", "Mantis Build Slices")));
            FrankenDraftBagService.applyDraftBags(event, game);
            return;
        }
        set(game, "nomination", String.valueOf(currentNomination(game) + 1));
        Player nominator = player(game, value(game, "nominator"));
        if (nominator != null) {
            MessageHelper.sendMessageToChannel(
                    auctionChannel(game), nominator.getRepresentationNoPing() + " is the next nominator.");
        }
        replaceAuctionCardsInfoForPlayers(game, () -> sendNominationPrompt(game));
    }

    private void sendNominationPrompt(Game game) {
        Player nominator = player(game, value(game, "nominator"));
        if (nominator == null) return;
        int nomination = currentNomination(game);
        sendNominationPromptAfterClearing(game, nominator, nomination);
    }

    private void sendDraftStartWarnings(Game game) {
        for (Player player : players(game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "**Warning: Take your time when pressing buttons please. Moving through prompts too quickly may cause the bot to break.**");
        }
    }

    private void sendNominationPromptAfterClearing(Game game, Player nominator, int nomination) {
        if (!isCurrent(game, "nominate", nomination) || !nominator.getFaction().equals(value(game, "nominator")))
            return;
        for (Player player : players(game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getPing() + ", you have " + number(game, playerKey("votes", player)) + " votes remaining.");
        }
        List<net.dv8tion.jda.api.components.buttons.Button> buttons = auctionCategories().stream()
                .filter(category -> canNominate(game, category))
                .map(category -> Buttons.green(
                        nominator.factionButtonChecker() + ACTION_PREFIX + ";nominate;" + nomination + ";"
                                + category.name(),
                        nominationCategoryLabel(game, category),
                        category.emoji(game)))
                .toList();
        MessageHelper.sendMessageToChannel(
                nominator.getCardsInfoThread(),
                nominator.getPing() + ", choose a category to nominate for auction.",
                buttons);
    }

    private void sendReviewPrompts(Game game) {
        int nomination = currentNomination(game);
        Player nominator = player(game, value(game, "nominator"));
        if (nominator == null) return;
        replaceAuctionCardsInfo(nominator, () -> sendReviewPromptsAfterClearing(game, nominator, nomination));
    }

    private void sendReviewPromptsAfterClearing(Game game, Player nominator, int nomination) {
        List<net.dv8tion.jda.api.components.buttons.Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(
                nominator.factionButtonChecker() + ACTION_PREFIX + ";review;" + nomination + ";pass", "Keep Pool"));
        if (number(game, playerKey("mulligans", nominator)) > 0) {
            buttons.add(Buttons.red(
                    nominator.factionButtonChecker() + ACTION_PREFIX + ";review;" + nomination + ";mulligan",
                    "Mulligan Pool"));
        }
        if (number(game, playerKey("bans", nominator)) > 0) {
            for (String alias : pool(game)) {
                DraftItem item = DraftItem.generateFromAlias(alias);
                buttons.add(Buttons.red(
                        nominator.factionButtonChecker() + ACTION_PREFIX + ";review;" + nomination + ";ban;" + alias,
                        "Ban " + item.getShortDescription(),
                        item.getItemEmoji()));
            }
        }
        MessageHelper.sendMessageToChannel(
                nominator.getCardsInfoThread(),
                poolDescription(game) + "\nChoose whether to keep, ban from, or mulligan this pool.\n-# Bans: "
                        + number(game, playerKey("bans", nominator)) + " | Mulligans: "
                        + number(game, playerKey("mulligans", nominator)),
                buttons);
        sendComponentDisplays(nominator.getCardsInfoThread(), game, nominator, pool(game));
    }

    private String poolDescription(Game game) {
        return "__Auction Pool__\n" + poolItemsDescription(game, pool(game));
    }

    private String poolItemsDescription(Game game, List<String> aliases) {
        StringBuilder text = new StringBuilder();
        for (String alias : aliases) {
            DraftItem item = DraftItem.generateFromAlias(alias);
            text.append("> ")
                    .append(item.getItemEmoji())
                    .append(' ')
                    .append(item.getShortDescription())
                    .append('\n');
        }
        return text.toString();
    }

    private String bidSummary(Game game) {
        StringBuilder text = new StringBuilder("__Auction Bids__\n");
        for (Player player : players(game)) {
            int[] bids = bids(player);
            text.append("> ")
                    .append(player.getRepresentationNoPing())
                    .append(": ")
                    .append(bids[0])
                    .append(" / ")
                    .append(bids[1])
                    .append(" / ")
                    .append(bids[2])
                    .append('\n');
        }
        return text.toString();
    }

    private void retireUnchosenComponents(Game game) {
        for (String alias : pool(game)) addToList(game, "removed", alias);
        for (String alias : list(game, "setAside")) addToList(game, "removed", alias);
    }

    private void sendComponentDisplays(MessageChannel channel, Game game, Player player, List<String> aliases) {
        MessageV2Builder builder = new MessageV2Builder(channel);
        for (String alias : aliases) {
            DraftItem item = DraftItem.generateFromAlias(alias);
            builder.append(Container.of(item.getTextDisplays(game, player, true)));
        }
        builder.send();
    }

    private ThreadChannel draftedComponentsThread(Game game) {
        if (game.isFowMode()) return null;
        String threadId = value(game, "draftedThread");
        ThreadChannel thread = threadId.isBlank() ? null : JdaService.jda.getThreadChannelById(threadId);
        if (thread == null && game.getMainGameChannel() != null) {
            thread = game.getMainGameChannel().getThreadChannels().stream()
                    .filter(candidate -> "Drafted Components".equals(candidate.getName()))
                    .max(java.util.Comparator.comparing(ThreadChannel::getTimeCreated))
                    .orElse(null);
            if (thread != null) {
                set(game, "draftedThread", thread.getId());
                GameManager.save(game, "Auction drafted components thread recovery");
            }
        }
        if (thread != null) {
            if (thread.isArchived())
                thread.getManager().setArchived(false).queue(Consumers.nop(), BotLogger::catchRestError);
            return thread;
        }
        return null;
    }

    private void createDraftedComponentsThread(Game game) {
        if (game.isFowMode()) return;
        if (!value(game, "draftedThread").isBlank()) return;
        ThreadChannelAction action = game.getMainGameChannel()
                .createThreadChannel("Drafted Components", false)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK);
        action.queue(
                createdThread -> {
                    set(game, "draftedThread", createdThread.getId());
                    GameManager.save(game, "Auction drafted components thread");
                    MessageHelper.sendMessageToChannel(createdThread, game.getPing() + " Drafted Components");
                    refreshDraftedComponentsLedger(game, createdThread);
                },
                BotLogger::catchRestError);
    }

    private void sendAllDraftedComponents(Game game, ThreadChannel thread) {
        boolean hasDraftedComponents = false;
        for (Player draftedPlayer : game.getPlayers().values()) {
            if (draftedPlayer.getDraftHand() == null) continue;
            List<String> drafted = draftedPlayer.getDraftHand().Contents.stream()
                    .map(DraftItem::getAlias)
                    .toList();
            if (drafted.isEmpty()) continue;
            hasDraftedComponents = true;
            MessageHelper.sendMessageToChannelWithButton(
                    thread,
                    draftedPlayer.getRepresentationNoPing() + " has drafted:\n"
                            + draftedComponentsSummary(game, drafted),
                    Buttons.gray(ACTION_PREFIX + ";showDrafted", "Show Components"));
        }
        if (!hasDraftedComponents) MessageHelper.sendMessageToChannel(thread, "No components have been drafted yet.");
    }

    private String draftedComponentsSummary(Game game, List<String> aliases) {
        return aliases.stream()
                .map(DraftItem::generateFromAlias)
                .map(item -> draftedComponentSummary(game, item))
                .collect(Collectors.joining("\n"));
    }

    private String draftedComponentSummary(Game game, DraftItem item) {
        StringBuilder summary = new StringBuilder("> ").append(item.getTitle(game));
        if (item.hasAdditionalComponents()) {
            appendLinkedComponents(summary, "Also adds", item.getErrata().getAdditionalComponents(), game);
        }
        appendLinkedComponents(summary, "Optional swaps", item.getAvailableOptionalSwaps(game), game);
        return summary.toString();
    }

    private void appendLinkedComponents(
            StringBuilder summary, String label, List<DraftErrataModel> linkedComponents, Game game) {
        if (linkedComponents.isEmpty()) return;
        summary.append("\n> - *").append(label).append(": ");
        summary.append(linkedComponents.stream()
                .map(DraftErrataModel::getAlias)
                .map(DraftItem::generateFromAlias)
                .map(item -> item.getTitle(game))
                .collect(Collectors.joining(", ")));
        summary.append("*");
    }

    private void refreshDraftedComponentsLedger(Game game, ThreadChannel thread) {
        thread.getHistory()
                .retrievePast(100)
                .queue(
                        messages -> {
                            messages.stream()
                                    .filter(message -> message.getAuthor().isBot())
                                    .forEach(message ->
                                            message.delete().queue(Consumers.nop(), BotLogger::catchRestError));
                            postDraftedComponentsLedger(game, thread);
                        },
                        error -> {
                            BotLogger.catchRestError(error);
                            postDraftedComponentsLedger(game, thread);
                        });
    }

    private void postDraftedComponentsLedger(Game game, ThreadChannel thread) {
        MessageHelper.sendMessageToChannelWithButton(
                thread, "__Drafted Components__", Buttons.gray(ACTION_PREFIX + ";showDrafted", "Show Components"));
        sendAllDraftedComponents(game, thread);
    }

    private void sendRoundDraftedComponents(Game game) {
        ThreadChannel thread = draftedComponentsThread(game);
        if (thread == null) return;
        refreshDraftedComponentsLedger(game, thread);
    }

    private void replaceAuctionCardsInfoForPlayers(Game game, Runnable action) {
        List<Player> players = players(game);
        if (players.isEmpty()) {
            action.run();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(players.size());
        for (Player player : players) {
            replaceAuctionCardsInfo(player, () -> {
                if (remaining.decrementAndGet() == 0) action.run();
            });
        }
    }

    private void replaceAuctionCardsInfo(Player player, Runnable action) {
        Game game = player.getGame();
        String key = playerKey("cardsInfoPhaseStart", player);
        long previousPhaseStart = longValue(game, key);
        if (previousPhaseStart == 0) {
            set(game, key, String.valueOf(System.currentTimeMillis()));
            action.run();
            return;
        }
        player.getCardsInfoThread()
                .getHistory()
                .retrievePast(100)
                .queue(
                        messages -> {
                            List<Message> auctionMessages = messages.stream()
                                    .filter(message -> message.getAuthor().isBot())
                                    .filter(message ->
                                            message.getTimeCreated().toInstant().toEpochMilli() >= previousPhaseStart)
                                    .toList();
                            runAfterCardsInfoCleanup(game, key, action, auctionMessages);
                        },
                        error -> {
                            BotLogger.catchRestError(error);
                            set(game, key, String.valueOf(System.currentTimeMillis()));
                            action.run();
                        });
    }

    private void runAfterCardsInfoCleanup(Game game, String key, Runnable action, List<Message> messages) {
        if (messages.isEmpty()) {
            set(game, key, String.valueOf(System.currentTimeMillis()));
            action.run();
            return;
        }
        AtomicInteger remaining = new AtomicInteger(messages.size());
        for (Message message : messages) {
            message.delete().queue(ignored -> runAfterCardsInfoDeletion(game, key, action, remaining), error -> {
                BotLogger.catchRestError(error);
                runAfterCardsInfoDeletion(game, key, action, remaining);
            });
        }
    }

    private void runAfterCardsInfoDeletion(Game game, String key, Runnable action, AtomicInteger remaining) {
        if (remaining.decrementAndGet() != 0) return;
        set(game, key, String.valueOf(System.currentTimeMillis()));
        action.run();
    }

    private List<DraftItem> drawPool(Game game, DraftCategory category, int amount, Set<String> excluded) {
        List<DraftItem> candidates =
                new ArrayList<>(FrankenDraft.getDraftableItems(game).getOrDefault(category, List.of()));
        Set<String> unavailable = new LinkedHashSet<>(list(game, "removed"));
        unavailable.addAll(excluded);
        unavailable.addAll(list(game, "setAside"));
        candidates.removeIf(item -> unavailable.contains(item.getAlias()));
        Collections.shuffle(candidates);
        int reserve = remainingRequired(game, category);
        int count = Math.min(amount, Math.max(0, candidates.size() - reserve));
        return new ArrayList<>(candidates.subList(0, count));
    }

    private int remainingRequired(Game game, DraftCategory category) {
        int remainingAuctions = remainingNominations(game, category) - 1;
        return Math.max(0, remainingAuctions) * players(game).size();
    }

    private boolean canNominate(Game game, DraftCategory category) {
        return remainingNominations(game, category) > 0
                && drawPool(game, category, 1, Set.of()).size() == 1;
    }

    private boolean allNominationsComplete(Game game) {
        return auctionCategories().stream().allMatch(category -> remainingNominations(game, category) == 0);
    }

    private int remainingNominations(Game game, DraftCategory category) {
        int playerCount = players(game).size();
        if (playerCount == 0) return 0;
        int requiredComponents = categoryLimit(game, category) * playerCount;
        int remainingComponents = Math.max(0, requiredComponents - draftedComponentCount(game, category));
        return (remainingComponents + playerCount - 1) / playerCount;
    }

    private int draftedComponentCount(Game game, DraftCategory category) {
        return players(game).stream()
                .map(Player::getDraftHand)
                .filter(java.util.Objects::nonNull)
                .mapToInt(hand -> (int) hand.Contents.stream()
                        .filter(item -> item.getItemCategory() == category)
                        .count())
                .sum();
    }

    private int categoryLimit(Game game, DraftCategory category) {
        String limit = value(game, "limit." + category.name());
        return limit.isBlank() ? FrankenDraft.getItemLimitForCategory(category, game) : Integer.parseInt(limit);
    }

    private void snapshotCategoryLimits(Game game) {
        for (DraftCategory category : auctionCategories()) {
            set(game, "limit." + category.name(), String.valueOf(FrankenDraft.getItemLimitForCategory(category, game)));
        }
    }

    private List<DraftCategory> auctionCategories() {
        return List.of(
                DraftCategory.ABILITY,
                DraftCategory.TECH,
                DraftCategory.BREAKTHROUGH,
                DraftCategory.AGENT,
                DraftCategory.COMMANDER,
                DraftCategory.HERO,
                DraftCategory.COMMODITIES,
                DraftCategory.FLAGSHIP,
                DraftCategory.MECH,
                DraftCategory.BLUETILE,
                DraftCategory.REDTILE,
                DraftCategory.HOMESYSTEM,
                DraftCategory.PN,
                DraftCategory.STARTINGFLEET,
                DraftCategory.STARTINGTECH);
    }

    private String categoryLabel(DraftCategory category) {
        return switch (category) {
            case ABILITY -> "Faction Abilities";
            case TECH -> "Faction Techs";
            case BREAKTHROUGH -> "Breakthroughs";
            case AGENT -> "Agents";
            case COMMANDER -> "Commanders";
            case HERO -> "Heroes";
            case COMMODITIES -> "Commodities";
            case FLAGSHIP -> "Flagships";
            case MECH -> "Mechs";
            case BLUETILE -> "Blue Tiles";
            case REDTILE -> "Red Tiles";
            case HOMESYSTEM -> "Home Systems";
            case PN -> "Promissory Notes";
            case STARTINGFLEET -> "Starting Fleets";
            case STARTINGTECH -> "Starting Techs";
            default -> category.name();
        };
    }

    private String nominationCategoryLabel(Game game, DraftCategory category) {
        int remainingAuctions = remainingNominations(game, category);
        return categoryLabel(category) + " (" + remainingAuctions + " Remaining)";
    }

    private String auctionCategoryTitle(Game game) {
        return category(game).title(game).replaceFirst("^##\\s*", "");
    }

    private DraftCategory category(Game game) {
        return DraftCategory.valueOf(value(game, "category"));
    }

    private int getPoolSize(Game game) {
        return Math.min(23, players(game).size() + getSetting(game, "extra", EXTRA_COMPONENTS));
    }

    private MessageChannel auctionChannel(Game game) {
        return game.isFowMode() ? GMService.getGMChannel(game) : game.getMainGameChannel();
    }

    private MessageChannel auctionLogChannel(Game game) {
        return game.isFowMode() ? GMService.getGMChannel(game) : game.getActionsChannel();
    }

    private int getSetting(Game game, String setting, int defaultValue) {
        int settingValue = number(game, "setting." + setting);
        return settingValue == 0
                        && game.getStoredValue(PREFIX + "setting." + setting).isEmpty()
                ? defaultValue
                : settingValue;
    }

    private int currentNomination(Game game) {
        return number(game, "nomination");
    }

    private boolean isCurrent(Game game, String phase, int nomination) {
        return phase.equals(value(game, "phase")) && nomination == number(game, "nomination");
    }

    private int[] bids(Player player) {
        String[] raw = value(player.getGame(), playerKey("bids", player)).split(",");
        if (raw.length != 3) return new int[] {0, 0, 0};
        return new int[] {Integer.parseInt(raw[0]), Integer.parseInt(raw[1]), Integer.parseInt(raw[2])};
    }

    private List<String> pool(Game game) {
        return new ArrayList<>(list(game, "pool"));
    }

    private List<String> locked(Game game) {
        return list(game, "locked");
    }

    private List<String> playerIds(Game game) {
        return list(game, "players");
    }

    private List<Player> players(Game game) {
        return playerIds(game).stream()
                .map(id -> player(game, id))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Player player(Game game, String faction) {
        Player player = game.getPlayerFromColorOrFaction(faction);
        return player == null ? game.getPlayer(faction) : player;
    }

    private String playerKey(String key, Player player) {
        return key + "." + player.getFaction();
    }

    private List<String> aliases(List<DraftItem> items) {
        return items.stream().map(DraftItem::getAlias).toList();
    }

    private void replaceBannedItem(Game game, String alias) {
        List<String> current = pool(game);
        current.remove(alias);
        List<DraftItem> replacement = drawPool(game, category(game), 1, new LinkedHashSet<>(current));
        if (!replacement.isEmpty()) current.add(replacement.getFirst().getAlias());
        setList(game, "pool", current);
    }

    private void clear(Game game) {
        game.getStoredValueMap()
                .keySet()
                .removeIf(key -> key.startsWith(PREFIX) && !key.startsWith(PREFIX + "setting."));
    }

    private void addToList(Game game, String key, String value) {
        List<String> values = list(game, key);
        if (!values.contains(value)) values.add(value);
        setList(game, key, values);
    }

    private List<String> list(Game game, String key) {
        String raw = value(game, key);
        return raw.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(raw.split(Constants.FIN_SEPARATOR)));
    }

    private void setList(Game game, String key, List<String> values) {
        set(game, key, String.join(Constants.FIN_SEPARATOR, values));
    }

    private int number(Game game, String key) {
        String value = value(game, key);
        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private long longValue(Game game, String key) {
        String value = value(game, key);
        return value.isEmpty() ? 0 : Long.parseLong(value);
    }

    private String value(Game game, String key) {
        return game.getStoredValue(PREFIX + key);
    }

    private void set(Game game, String key, String value) {
        game.setStoredValue(PREFIX + key, value);
    }
}

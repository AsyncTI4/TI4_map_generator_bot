package ti4.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import org.apache.commons.lang3.function.Consumers;
import org.jetbrains.annotations.Nullable;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftBagService;

public abstract class BagDraft {
    private static final Pattern FORWARD_SLASH_PATTERN = Pattern.compile("/");
    private final Game owner;
    private final int picksFromFirstBag;
    private final int picksFromNextBags;

    public static BagDraft generateDraft(String draftType, Game game) {
        return switch (draftType) {
            case "franken" -> new FrankenDraft(game);
            case "powered_franken" -> new PoweredFrankenDraft(game);
            case "onepick_franken" -> new OnePickFrankenDraft(game);
            case "overdraft_franken" -> new OverdraftFrankenDraft(game);
            case "poweredonepick_franken" -> new PoweredOnePickFrankenDraft(game);
            case "powered_overdraft_franken" -> new PoweredOverdraftFrankenDraft(game);
            case "frankendraz" -> new FrankenDrazDraft(game);
            case "twilights_fall" -> new TwilightsFallFrankenDraft(game);
            case "inaugural_splice" -> new InauguralSpliceFrankenDraft(game);
            default -> null;
        };
    }

    BagDraft(Game owner, int picksFromFirstBag, int picksFromNextBags) {
        this.owner = owner;
        this.picksFromFirstBag = picksFromFirstBag;
        this.picksFromNextBags = picksFromNextBags;
    }

    Game getOwner() {
        return owner;
    }

    public abstract int getItemLimitForCategory(DraftCategory category);

    public abstract int getKeptItemLimitForCategory(DraftCategory category);

    public abstract String getSaveString();

    @Nullable
    public abstract List<DraftBag> generateBags(Game game);

    public abstract int getBagSize();

    public int getPicksFromFirstBag() {
        return picksFromFirstBag;
    }

    public int getPicksFromNextBags() {
        return picksFromNextBags;
    }

    static int effectiveItemLimit(BagDraft draft, DraftCategory category, Game game) {
        if (draft instanceof FrankenDraft) {
            return FrankenDraft.getItemLimitForCategory(category, game);
        }
        return draft.getItemLimitForCategory(category);
    }

    @Nullable
    protected List<DraftBag> fillBags(
            Game game, Map<DraftCategory, List<DraftItem>> allDraftableItems, String draftName) {
        List<DraftBag> bags = new ArrayList<>();
        Map<DraftCategory, Integer> missingItems = new HashMap<>();
        for (int i = 0; i < game.getRealPlayers().size(); i++) {
            DraftBag bag = new DraftBag();
            for (Map.Entry<DraftCategory, List<DraftItem>> draftableCollection : allDraftableItems.entrySet()) {
                DraftCategory category = draftableCollection.getKey();
                int categoryLimit = effectiveItemLimit(this, category, game);
                for (int j = 0; j < categoryLimit; j++) {
                    if (!draftableCollection.getValue().isEmpty()) {
                        bag.Contents.add(draftableCollection.getValue().removeFirst());
                    } else {
                        missingItems.merge(category, 1, Integer::sum);
                    }
                }
            }

            if (!missingItems.isEmpty()) {
                StringBuilder issue = new StringBuilder(
                        game.getPing() + " an issue was encountered while building the " + draftName + " draft.");
                issue.append("\nOne or more bags are missing components.");
                for (var e : missingItems.entrySet()) {
                    issue.append("\n> ")
                            .append(e.getKey().toString())
                            .append(" is missing ")
                            .append(e.getValue())
                            .append(" components.");
                }
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), issue.toString());
                return null;
            }
            bags.add(bag);
        }
        return bags;
    }

    public boolean isDraftStageComplete() {
        return owner.getRealPlayers().stream()
                .allMatch(p -> p.getDraftHand().Contents.size() == owner.getFrankenBagSize());
    }

    public void passBags() {
        List<Player> players = owner.getRealPlayers();
        DraftBag firstPlayerBag = players.getFirst().getCurrentDraftBag();
        for (int i = 0; i < players.size() - 1; i++) {
            giveBagToPlayer(players.get(i + 1).getCurrentDraftBag(), players.get(i));
        }
        giveBagToPlayer(firstPlayerBag, players.getLast());
    }

    public static void giveBagToPlayer(DraftBag bag, Player player) {
        player.setCurrentDraftBag(bag);
        boolean newBagCanBeDraftedFrom = false;
        for (DraftItem item : bag.Contents) {
            if (item.isDraftable(player)) {
                newBagCanBeDraftedFrom = true;
                break;
            }
        }
        player.setReadyToPassBag(!newBagCanBeDraftedFrom);
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " you have been passed a new draft bag!",
                Buttons.gray(FrankenDraftBagService.ACTION_NAME + "show_bag", "Click here to show your current bag"));
    }

    public boolean allPlayersReadyToPass() {
        for (Player p : owner.getRealPlayers()) {
            if (!playerHasDraftableItemInBag(p) && !playerHasItemInQueue(p) && !p.isReadyToPassBag()) {
                markReadyToPass(p);
            } else if (draftableItemsInBag(p).size() == 1 && !playerHasItemInQueue(p) && !p.isReadyToPassBag()) {
                List<DraftItem> draftableItems = draftableItemsInBag(p);
                p.getDraftHand().Contents.addAll(draftableItems);
                clearBagChannel(p);
                p.getCurrentDraftBag().Contents.removeAll(draftableItems);
                markReadyToPass(p);
            }
        }
        return owner.getRealPlayers().stream().allMatch(Player::isReadyToPassBag);
    }

    private void markReadyToPass(Player p) {
        setPlayerReadyToPass(p, true);
        MessageHelper.sendMessageToChannel(
                findExistingBagChannel(p),
                "Your Draft Bag is ready to pass and you are waiting for the other players to finish drafting.");
        MessageHelper.sendMessageToChannel(
                p.getCardsInfoThread(),
                "You are passing the following cards to your right:\n" + FrankenDraftBagService.getBagReceipt(p));
        FrankenDraftBagService.displayPlayerHand(owner, p);
    }

    public static boolean playerHasDraftableItemInBag(Player player) {
        return player.getCurrentDraftBag().Contents.stream().anyMatch(draftItem -> draftItem.isDraftable(player));
    }

    private static List<DraftItem> draftableItemsInBag(Player player) {
        return player.getCurrentDraftBag().Contents.stream()
                .filter(draftItem -> draftItem.isDraftable(player))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void setPlayerReadyToPass(Player player, boolean ready) {
        if (ready && !player.isReadyToPassBag()) {
            player.setReadyToPassBag(true);
            FrankenDraftBagService.updateDraftStatusMessage(owner);
        }
        player.setReadyToPassBag(ready);
    }

    public String getLongBagRepresentation(DraftBag bag, Game game) {
        StringBuilder sb = new StringBuilder();
        for (DraftCategory cat : DraftCategory.values()) {
            if (effectiveItemLimit(this, cat, game) > 0) {
                sb.append(FrankenDraftBagService.getLongCategoryRepresentation(this, bag, cat, game));
            }
        }
        sb.append("**Total Cards: ").append(bag.Contents.size()).append("**\n");
        return sb.toString();
    }

    public void clearBagChannel(Player player) {
        ThreadChannel channel = findExistingBagChannel(player);
        channel.getHistory()
                .retrievePast(100)
                .flatMap(m -> !m.isEmpty(), m -> {
                    if (m.size() == 1)
                        return channel.deleteMessageById(m.getFirst().getId());
                    return channel.deleteMessages(m);
                })
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public ThreadChannel regenerateBagChannel(Player player) {
        TextChannel actionsChannel = owner.getMainGameChannel();
        if (actionsChannel == null) {
            BotLogger.warning(
                    new LogOrigin(player),
                    "`Helper.getBagChannel`: actionsChannel is null for game, or community game private channel not set: "
                            + owner.getName());
            return null;
        }

        ThreadChannel existingChannel = findExistingBagChannel(player);

        if (existingChannel != null) {
            if (existingChannel.isArchived()) {
                existingChannel.getManager().setArchived(false).submit().join();
            }

            // Clear out all messages from the existing thread
            clearBagChannel(player);
            return existingChannel;
        }

        // CREATE NEW THREAD
        // Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!owner.isFowMode());
        if (owner.getName().contains("pbd100") || owner.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = player.getCorrectChannel()
                .createThreadChannel(getBagChannelThreadName(player), isPrivateChannel)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
        if (isPrivateChannel) {
            threadAction = threadAction.setInvitable(false);
        }

        // Must `complete` if we're using this channel as part of an interaction that saves the game
        ThreadChannel threadChannel = threadAction.complete();
        player.setBagInfoThreadID(threadChannel.getId());
        return threadChannel;
    }

    private String getBagChannelThreadName(Player player) {
        String fowardSlash = FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("");
        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-" + fowardSlash;
        if (owner.isFowMode()) {
            threadName = owner.getName() + "-" + "bag-info-" + fowardSlash + "-private";
        }
        return threadName;
    }

    public ThreadChannel findExistingBagChannel(Player player) {
        String threadName = getBagChannelThreadName(player);
        return findExistingBagChannel(player, threadName);
    }

    private static ThreadChannel findExistingBagChannel(Player player, String threadName) {
        ThreadChannel byId = findBagThread(
                player,
                thread -> thread.getId().equals(player.getBagInfoThreadID()),
                "`Player.getBagInfoThread`: Could not find existing Bag Info thead using ID: "
                        + player.getBagInfoThreadID() + " for potential thread name: " + threadName);
        if (byId != null) return byId;
        return findBagThread(
                player,
                thread -> thread.getName().equals(threadName),
                "`Player.getBagInfoThread`: Could not find existing Bag Info thead using name: " + threadName);
    }

    private static ThreadChannel findBagThread(Player player, Predicate<ThreadChannel> matches, String errorMessage) {
        TextChannel actionsChannel = player.getCorrectChannel();
        String bagInfoThread = player.getBagInfoThreadID();
        try {
            if (bagInfoThread != null && !bagInfoThread.isBlank() && !"null".equals(bagInfoThread)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = JdaService.jda.getThreadChannelById(bagInfoThread);
                if (threadChannel != null) return threadChannel;

                ThreadChannel open = findAndRemember(player, threadChannels, matches);
                if (open != null) return open;

                // Must `complete` if we're using this channel as part of an interaction that saves the game
                return findAndRemember(
                        player,
                        actionsChannel.retrieveArchivedPrivateThreadChannels().complete(),
                        matches);
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), errorMessage, e);
        }
        return null;
    }

    private static ThreadChannel findAndRemember(
            Player player, List<ThreadChannel> threadChannels, Predicate<ThreadChannel> matches) {
        for (ThreadChannel threadChannel : threadChannels) {
            if (matches.test(threadChannel)) {
                player.setBagInfoThreadID(threadChannel.getId());
                return threadChannel;
            }
        }
        return null;
    }

    public static boolean playerHasItemInQueue(Player p) {
        return !p.getDraftQueue().Contents.isEmpty();
    }

    public boolean playerQueueIsFull(Player player) {
        Game game = player.getGame();
        int draftQueueCount = player.getDraftQueue().Contents.size();
        boolean isFirstDraft = player.getDraftHand().Contents.isEmpty();
        boolean isQueueFull = draftQueueCount >= getPicksFromNextBags();
        if (!game.getStoredValue("frankenLimitLATERPICK").isEmpty()) {
            isQueueFull = draftQueueCount >= Integer.parseInt(game.getStoredValue("frankenLimitLATERPICK"));
        }
        if (isFirstDraft) {
            isQueueFull = draftQueueCount >= getPicksFromFirstBag();
            if (!game.getStoredValue("frankenLimitFIRSTPICK").isEmpty()) {
                isQueueFull = draftQueueCount >= Integer.parseInt(game.getStoredValue("frankenLimitFIRSTPICK"));
            }
        }
        return isQueueFull;
    }

    @JsonIgnore
    public String getDraftStatusMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("### __Draft Status__:\n");
        for (Player player : owner.getRealPlayers()) {
            sb.append("> ");
            if (player.isReadyToPassBag()) {
                sb.append("✅");

            } else {
                sb.append("❌");
            }
            if (owner.getRealPlayers().size() > 10) {
                sb.append(player.getFactionEmoji());
            } else {
                sb.append(player.getRepresentationNoPing());
            }
            sb.append(" (")
                    .append(player.getDraftHand().Contents.size())
                    .append('/')
                    .append(owner.getFrankenBagSize())
                    .append(")");
            if (player.isReadyToPassBag()) {
                sb.append(" (passing a bag of size ")
                        .append(player.getCurrentDraftBag().Contents.size())
                        .append(")");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @JsonIgnore
    public String getFinishedBuildingMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("### __Faction Building Status__:\n");
        String key = "frankenBuilt";
        for (Player player : owner.getRealPlayers()) {

            String finished = "n".equals(player.getStoredValue(key)) ? "❌" : "✅";
            String ident =
                    owner.getRealPlayers().size() > 10 ? player.getFactionEmoji() : player.getRepresentationNoPing();

            sb.append("> ").append(finished).append(' ').append(ident).append('\n');
        }
        return sb.toString();
    }
}

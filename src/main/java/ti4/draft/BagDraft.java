package ti4.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.franken.FrankenDraftBagService;

public abstract class BagDraft {
    private static final Pattern FORWARD_SLASH_PATTERN = Pattern.compile("/");
    private final Game owner;

    public static BagDraft GenerateDraft(String draftType, Game game) {
        if ("franken".equals(draftType)) {
            return new FrankenDraft(game);
        }
        if ("powered_franken".equals(draftType)) {
            return new PoweredFrankenDraft(game);
        }
        if ("onepick_franken".equals(draftType)) {
            return new OnePickFrankenDraft(game);
        }
        if ("poweredonepick_franken".equals(draftType)) {
            return new PoweredOnePickFrankenDraft(game);
        }
        return null;
    }

    BagDraft(Game owner) {
        this.owner = owner;
    }

    public abstract int getItemLimitForCategory(DraftItem.Category category);

    public abstract String getSaveString();

    public abstract List<DraftBag> generateBags(Game game);

    public abstract int getBagSize();

    public int getPicksFromFirstBag() {
        return 3;
    }

    public int getPicksFromNextBags() {
        return 2;
    }

    public boolean isDraftStageComplete() {
        List<Player> players = owner.getRealPlayers();

        for (Player p : players) {
            if (!p.getCurrentDraftBag().Contents.isEmpty()
                    || !p.getDraftQueue().Contents.isEmpty()) {
                if (p.getDraftHand().Contents.size() != owner.getFrankenBagSize()) {
                    return false;
                }
            }
            if (p.getDraftHand().Contents.size() != owner.getFrankenBagSize()) {
                return false;
            }
        }
        return true;
    }

    public void passBags() {
        List<Player> players = owner.getRealPlayers();
        DraftBag firstPlayerBag = players.getFirst().getCurrentDraftBag();
        for (int i = 0; i < players.size() - 1; i++) {
            giveBagToPlayer(players.get(i + 1).getCurrentDraftBag(), players.get(i));
        }
        giveBagToPlayer(firstPlayerBag, players.getLast());
    }

    public void giveBagToPlayer(DraftBag bag, Player player) {
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
            if (!playerHasDraftableItemInBag(p) && !playerHasItemInQueue(p)) {
                setPlayerReadyToPass(p, true);
            }
        }
        return owner.getRealPlayers().stream().allMatch(Player::isReadyToPassBag);
    }

    public boolean playerHasDraftableItemInBag(Player player) {
        return player.getCurrentDraftBag().Contents.stream().anyMatch(draftItem -> draftItem.isDraftable(player));
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
        for (DraftItem.Category cat : DraftItem.Category.values()) {
            sb.append(FrankenDraftBagService.getLongCategoryRepresentation(this, bag, cat, game));
        }
        sb.append("**Total Cards: ").append(bag.Contents.size()).append("**\n");
        return sb.toString();
    }

    public ThreadChannel regenerateBagChannel(Player player) {
        TextChannel actionsChannel = owner.getMainGameChannel();
        if (actionsChannel == null) {
            BotLogger.warning(
                    player,
                    "`Helper.getBagChannel`: actionsChannel is null for game, or community game private channel not set: "
                            + owner.getName());
            return null;
        }

        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-"
                + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("");
        if (owner.isFowMode()) {
            threadName = owner.getName() + "-" + "bag-info-"
                    + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("") + "-private";
        }

        ThreadChannel existingChannel = findExistingBagChannel(player, threadName);

        if (existingChannel != null) {
            if (existingChannel.isArchived()) {
                existingChannel.getManager().setArchived(false).submit().join();
            }

            // Clear out all messages from the existing thread
            existingChannel
                    .getHistory()
                    .retrievePast(100)
                    .submit()
                    .thenAccept(m -> {
                        if (m.size() > 1) {
                            existingChannel.deleteMessages(m).submit().join();
                        }
                    })
                    .join();
            return existingChannel;
        }

        // CREATE NEW THREAD
        // Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!owner.isFowMode());
        if (owner.getName().contains("pbd100") || owner.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = ((TextChannel) player.getCorrectChannel())
                .createThreadChannel(threadName, isPrivateChannel)
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
        if (isPrivateChannel) {
            threadAction = threadAction.setInvitable(false);
        }
        ThreadChannel threadChannel =
                threadAction
                        .complete(); // Must `complete` if we're using this channel as part of an interaction that saves
        // the
        // game
        player.setBagInfoThreadID(threadChannel.getId());
        return threadChannel;
    }

    public ThreadChannel findExistingBagChannel(Player player) {
        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-"
                + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("");
        if (owner.isFowMode()) {
            threadName = owner.getName() + "-" + "bag-info-"
                    + FORWARD_SLASH_PATTERN.matcher(player.getUserName()).replaceAll("") + "-private";
        }
        return findExistingBagChannel(player, threadName);
    }

    private ThreadChannel findExistingBagChannel(Player player, String threadName) {
        TextChannel actionsChannel = (TextChannel) player.getCorrectChannel();
        // ATTEMPT TO FIND BY ID
        String bagInfoThread = player.getBagInfoThreadID();
        try {
            if (bagInfoThread != null && !bagInfoThread.isBlank() && !"null".equals(bagInfoThread)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(bagInfoThread);
                if (threadChannel != null) return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getId().equals(bagInfoThread)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                // Must `complete` if we're using this channel as part of an interaction that saves the game
                List<ThreadChannel> hiddenThreadChannels =
                        actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getId().equals(bagInfoThread)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error(
                    player,
                    "`Player.getBagInfoThread`: Could not find existing Bag Info thead using ID: " + bagInfoThread
                            + " for potential thread name: " + threadName,
                    e);
        }

        // ATTEMPT TO FIND BY NAME
        try {
            if (bagInfoThread != null && !bagInfoThread.isBlank() && !"null".equals(bagInfoThread)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(bagInfoThread);
                if (threadChannel != null) return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                // Must `complete` if we're using this channel as part of an interaction that saves the game
                List<ThreadChannel> hiddenThreadChannels =
                        actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error(
                    player,
                    "`Player.getBagInfoThread`: Could not find existing Bag Info thead using name: " + threadName,
                    e);
        }
        return null;
    }

    public boolean playerHasItemInQueue(Player p) {
        return !p.getDraftQueue().Contents.isEmpty();
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
                    .append("/")
                    .append(owner.getFrankenBagSize())
                    .append(")");
            sb.append("\n");
        }
        return sb.toString();
    }
}

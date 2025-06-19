package ti4.draft;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftBagService;

public abstract class BagDraft {
    protected final Game owner;

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

    public BagDraft(Game owner) {
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
            boolean bagIsNoneOrEmpty = p.getCurrentDraftBag().isEmpty() || p.getCurrentDraftBag().get().Contents.isEmpty();
            if (!bagIsNoneOrEmpty || !p.getDraftItemSelection().Contents.isEmpty()) {
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

    /** Take player's current bag and enqueue it with the next player. */
    public void passBag(Player player) {
        DraftBag bag = dequeueBag(player);
        assert bag != null;
        // TODO maybe report somewhere when empty bags are dropped?
        if (!bag.Contents.isEmpty()) {
            Player nextPlayer = getNextPlayer(player);
            BotLogger.info("Passing bag from " + player.getRepresentationNoPing() + " to " + nextPlayer.getRepresentationNoPing());
            enqueueBag(nextPlayer, bag);
        }
    }

    /** Take player's current bag, and set their next queued bag as their current bag. */
    private DraftBag dequeueBag(Player player) {
        assert player.getCurrentDraftBag().isPresent();
        DraftBag oldBag = player.getDraftBagQueue().poll();
        if (player.getCurrentDraftBag().isPresent()) {
            playerHasNewBag(player);
        }
        return oldBag;
    }

    /** Enqueue a bag with a player. */
    public void enqueueBag(Player player, DraftBag bag) {
        BotLogger.info("Enqueueing bag for "+ player.getRepresentationNoPing());
        boolean hadCurrentBag = player.getCurrentDraftBag().isPresent();
        player.getDraftBagQueue().add(bag);
        if (!hadCurrentBag) {
            playerHasNewBag(player);
        }
    }

    public Player getNextPlayer(Player player) {
        List<Player> players = owner.getRealPlayers();
        int index = players.indexOf(player);
        if (index == -1) {
            // TODO throw something interesting probably
            return null;
        }
        int nextIndex = (index + 1) % players.size();
        return players.get(nextIndex);
    }

    private void playerHasNewBag(Player player) {
        // The player got a new bag, maybe because their old bag was dequeued, or because a new bag was enqueued.
        if (playerHasDraftableItemInBag(player)) {
            MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " you have been passed a new draft bag!",
                Buttons.gray(FrankenDraftBagService.ACTION_NAME + "show_bag", "Click here to show your current bag"));
        } else {
            // TODO since we're passing the unusable bag recursively, the failure state where no-one can draft from it isn't caught anyhere and just crashes with infinite depth. Fix that. Maybe look ahead to find the correct destination player? But can't pass directly to them, that would change the order of bags. In any case, restore the old error message.
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " you have been passed a new draft bag, but nothing in it is draftable for you.");
            passBag(player);
        }
        // TODO where else could this showPlayerBag() call be?
        FrankenDraftBagService.showPlayerBag(owner, player);
    }

    public boolean playerHasDraftableItemInBag(Player player) {
        if (player.getCurrentDraftBag().isEmpty()) {
            return false;
        }
        // Safety: just checked the bag exists
        return player.getCurrentDraftBag().get().Contents.stream().anyMatch(draftItem -> draftItem.isDraftable(player));
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
            BotLogger.warning(new BotLogger.LogMessageOrigin(player), "`Helper.getBagChannel`: actionsChannel is null for game, or community game private channel not set: " + owner.getName());
            return null;
        }

        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-" + player.getUserName().replaceAll("/", "");
        if (owner.isFowMode()) {
            threadName = owner.getName() + "-" + "bag-info-" + player.getUserName().replaceAll("/", "") + "-private";
        }

        ThreadChannel existingChannel = findExistingBagChannel(player, threadName);

        if (existingChannel != null) {
            if (existingChannel.isArchived()) {
                existingChannel.getManager().setArchived(false).submit().join();
            }

            // Clear out all messages from the existing thread
            existingChannel.getHistory().retrievePast(100).submit().thenAccept(m -> {
                if (m.size() > 1) {
                    existingChannel.deleteMessages(m).submit().join();
                }
            }).join();
            return existingChannel;
        }

        // CREATE NEW THREAD
        //Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!owner.isCommunityMode() && !owner.isFowMode());
        if (owner.getName().contains("pbd100") || owner.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = ((TextChannel) player.getCorrectChannel())
            .createThreadChannel(threadName, isPrivateChannel)
            .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
        if (isPrivateChannel) {
            threadAction = threadAction.setInvitable(false);
        }
        ThreadChannel threadChannel = threadAction.complete(); // Must `complete` if we're using this channel as part of an interaction that saves the game
        player.setBagInfoThreadID(threadChannel.getId());
        return threadChannel;
    }

    public ThreadChannel findExistingBagChannel(Player player) {
        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-" + player.getUserName().replaceAll("/", "");
        if (owner.isFowMode()) {
            threadName = owner.getName() + "-" + "bag-info-" + player.getUserName().replaceAll("/", "") + "-private";
        }
        return findExistingBagChannel(player, threadName);
    }

    private ThreadChannel findExistingBagChannel(Player player, String threadName) {
        TextChannel actionsChannel = (TextChannel) player.getCorrectChannel();
        //ATTEMPT TO FIND BY ID
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
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getId().equals(bagInfoThread)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(player), "`Player.getBagInfoThread`: Could not find existing Bag Info thead using ID: " + bagInfoThread + " for potential thread name: " + threadName, e);
        }

        //ATTEMPT TO FIND BY NAME
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
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(player), "`Player.getBagInfoThread`: Could not find existing Bag Info thead using name: " + threadName, e);
        }
        return null;
    }

    public boolean playerHasItemSelected(Player p) {
        return !p.getDraftItemSelection().Contents.isEmpty();
    }

    @JsonIgnore
    public String getDraftStatusMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("### __Draft Status__:\n");
        for (Player player : owner.getRealPlayers()) {
            sb.append("> ");
            sb.append(player.getRepresentationNoPing());
            sb.append(" (").append(player.getDraftHand().Contents.size()).append("/").append(owner.getFrankenBagSize()).append(")");
            player.getDraftBagQueue().forEach(bag -> sb.append("💰"));
            sb.append("\n");
        }
        return sb.toString();
    }
}

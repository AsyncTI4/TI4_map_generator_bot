package ti4.draft;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.List;

public abstract class BagDraft {
    protected Game owner;

    public static BagDraft GenerateDraft(String draftType, Game game) {
        if (draftType.equals("franken")) {
            return new FrankenDraft(game);
        } else if (draftType.equals("powered_franken")) {
            return new PoweredFrankenDraft(game);
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

    public boolean isDraftStageComplete() {
        List<Player> players = owner.getRealPlayers();
        for (Player p : players) {
            if (!p.getCurrentDraftBag().Contents.isEmpty() || !p.getDraftQueue().Contents.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void passBags() {
        List<Player> players = owner.getRealPlayers();
        DraftBag firstPlayerBag = players.get(0).getCurrentDraftBag();
        for (int i = 0; i < players.size() - 1; i++) {
            giveBagToPlayer(players.get(i + 1).getCurrentDraftBag(), players.get(i));
        }
        giveBagToPlayer(firstPlayerBag, players.get(players.size() - 1));
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
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), ButtonHelper.getTrueIdentity(player, owner) + " you have been passed a new draft bag!",
            Button.secondary(FrankenDraftHelper.ActionName + "show_bag", "Click here to show your current bag"));
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
            MessageHelper.sendMessageToChannel(owner.getActionsChannel(), player.getUserName() + " is ready to pass draft bags.");
        }
        player.setReadyToPassBag(ready);
    }

    public String getShortBagRepresentation(DraftBag bag) {
        StringBuilder sb = new StringBuilder();
        for (DraftItem.Category cat : DraftItem.Category.values()) {
            sb.append("### ").append(cat.toString()).append(" (");
            sb.append(bag.getCategoryCount(cat)).append("/").append(getItemLimitForCategory(cat));
            sb.append("):\n");
            for (DraftItem item : bag.Contents) {
                if (item.ItemCategory != cat) {
                    continue;
                }
                sb.append(" - ").append(item.getItemEmoji()).append(item.getShortDescription()).append("\n");
            }
        }
        sb.append("**Total Cards: ").append(bag.Contents.size()).append("**\n");
        return sb.toString();
    }

    public String getLongBagRepresentation(DraftBag bag) {
        StringBuilder sb = new StringBuilder();
        for (DraftItem.Category cat : DraftItem.Category.values()) {
            sb.append("### ").append(cat.toString()).append(" (");
            sb.append(bag.getCategoryCount(cat)).append("/").append(getItemLimitForCategory(cat));
            sb.append("):\n");
            for (DraftItem item : bag.Contents) {
                if (item.ItemCategory != cat) {
                    continue;
                }
                sb.append("- ").append(item.getShortDescription()).append("\n");
                sb.append(" - ").append(item.getLongDescription()).append("\n");
            }
        }
        sb.append("**Total Cards: ").append(bag.Contents.size()).append("**\n");
        return sb.toString();
    }

    public ThreadChannel regenerateBagChannel(Player player) {
        TextChannel actionsChannel = owner.getMainGameChannel();
        if (actionsChannel == null) {
            BotLogger.log("`Helper.getBagChannel`: actionsChannel is null for game, or community game private channel not set: " + owner.getName());
            return null;
        }

        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-" + player.getUserName().replaceAll("/", "");
        if (owner.isFoWMode()) {
            threadName = owner.getName() + "-" + "bag-info-" + player.getUserName().replaceAll("/", "") + "-private";
        }

        ThreadChannel existingChannel = findExistingBagChannel(player, threadName);

        if (existingChannel != null) {
            existingChannel.delete().queue();
        }

        // CREATE NEW THREAD
        //Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!owner.isCommunityMode() && !owner.isFoWMode());
        if (owner.getName().contains("pbd100") || owner.getName().contains("pbd500")) {
            isPrivateChannel = true;
        }
        ThreadChannelAction threadAction = actionsChannel.createThreadChannel(threadName, isPrivateChannel);
        threadAction.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
        if (isPrivateChannel) {
            threadAction.setInvitable(false);
        }
        ThreadChannel threadChannel = threadAction.complete();
        player.setBagInfoThreadID(threadChannel.getId());
        return threadChannel;
    }

    public ThreadChannel findExistingBagChannel(Player player) {

        String threadName = Constants.BAG_INFO_THREAD_PREFIX + owner.getName() + "-" + player.getUserName().replaceAll("/", "");
        if (owner.isFoWMode()) {
            threadName = owner.getName() + "-" + "bag-info-" + player.getUserName().replaceAll("/", "") + "-private";
        }
        return findExistingBagChannel(player, threadName);
    }

    private ThreadChannel findExistingBagChannel(Player player, String threadName) {
        TextChannel actionsChannel = owner.getActionsChannel();
        //ATTEMPT TO FIND BY ID
        String bagInfoThread = player.getBagInfoThreadID();
        try {
            if (bagInfoThread != null && !bagInfoThread.isBlank() && !bagInfoThread.isEmpty() && !"null".equals(bagInfoThread)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;

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
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getId().equals(bagInfoThread)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getBagInfoThread`: Could not find existing Bag Info thead using ID: " + bagInfoThread + " for potential thread name: " + threadName, e);
        }

        //ATTEMPT TO FIND BY NAME
        try {
            if (bagInfoThread != null && !bagInfoThread.isBlank() && !bagInfoThread.isEmpty() && !"null".equals(bagInfoThread)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;

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
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getBagInfoThread`: Could not find existing Bag Info thead using name: " + threadName, e);
        }
        return null;
    }

    public boolean playerHasItemInQueue(Player p) {
        return !p.getDraftQueue().Contents.isEmpty();
    }
}

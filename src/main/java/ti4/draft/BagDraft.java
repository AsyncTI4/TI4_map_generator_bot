package ti4.draft;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;

import java.util.List;

public abstract class BagDraft {
    public static BagDraft GenerateDraft(String draftType) {
        if (draftType.equals("franken")) {
            return new FrankenDraft();
        }
        else if (draftType.equals("powered_franken")) {
            return new PoweredFrankenDraft();
        }

        return null;
    }

    public abstract int GetItemLimitForCategory(DraftItem.Category category);
    public abstract String getSaveString();
    public abstract List<DraftBag> generateBags(Game game);

    public ThreadChannel getDraftBagChannel(Game activeGame, Player player) {
        TextChannel actionsChannel = activeGame.getMainGameChannel();
        if (actionsChannel == null) {
            BotLogger.log("`Helper.getPlayerCardsInfoThread`: actionsChannel is null for game, or community game private channel not set: " + activeGame.getName());
            return null;
        }

        String threadName = Constants.BAG_INFO_THREAD_PREFIX + activeGame.getName() + "-" + player.getUserName().replaceAll("/", "");
        if (activeGame.isFoWMode()) {
            threadName = activeGame.getName() + "-" + "bag-info-" + player.getUserName().replaceAll("/", "") + "-private";
        }

        //ATTEMPT TO FIND BY ID
        String cardsInfoThreadID = player.getBagInfoThreadID();
        try {
            if (cardsInfoThreadID != null && !cardsInfoThreadID.isBlank() && !cardsInfoThreadID.isEmpty() && !"null".equals(cardsInfoThreadID)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
                if (threadChannel != null) return threadChannel;

                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }

                // SEARCH FOR EXISTING CLOSED/ARCHIVED THREAD
                List<ThreadChannel> hiddenThreadChannels = actionsChannel.retrieveArchivedPrivateThreadChannels().complete();
                for (ThreadChannel threadChannel_ : hiddenThreadChannels) {
                    if (threadChannel_.getId().equals(cardsInfoThreadID)) {
                        player.setBagInfoThreadID(threadChannel_.getId());
                        return threadChannel_;
                    }
                }
            }
        } catch (Exception e) {
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using ID: " + cardsInfoThreadID + " for potential thread name: " + threadName, e);
        }

        //ATTEMPT TO FIND BY NAME
        try {
            if (cardsInfoThreadID != null && !cardsInfoThreadID.isBlank() && !cardsInfoThreadID.isEmpty() && !"null".equals(cardsInfoThreadID)) {
                List<ThreadChannel> threadChannels = actionsChannel.getThreadChannels();
                if (threadChannels == null) return null;

                ThreadChannel threadChannel = AsyncTI4DiscordBot.jda.getThreadChannelById(cardsInfoThreadID);
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
            BotLogger.log("`Player.getCardsInfoThread`: Could not find existing Cards Info thead using name: " + threadName, e);
        }

        // CREATE NEW THREAD
        //Make card info thread a public thread in community mode
        boolean isPrivateChannel = (!activeGame.isCommunityMode() && !activeGame.isFoWMode());
        if (activeGame.getName().contains("pbd100") || activeGame.getName().contains("pbd500")) {
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
}

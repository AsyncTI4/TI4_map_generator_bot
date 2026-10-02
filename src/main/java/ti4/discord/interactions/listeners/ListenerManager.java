package ti4.discord.interactions.listeners;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.JDA;

@UtilityClass
public class ListenerManager {

    public static void registerListeners(JDA jda) {
        jda.addEventListener(
                // Priority Listeners First
                new GuildWhitelistListener(),
                new BotRuntimeStatsListener(),
                new MessageListener(),
                new LazaxMinigameReactionListener(),
                new SlashCommandListener(),
                new ContextInteractionListener(),
                ButtonListener.getInstance(),
                new UserJoinServerListener(),
                new AutoCompleteListener(),
                new BanListener(),
                new ThreadCreateListener(),

                // Non-Priority Listeners
                new DeletionListener(),
                new SelectionMenuListener(),
                new ChannelCreationListener(),
                new UserLeaveServerListener(),
                // ModalListener has a long init time
                ModalListener.getInstance());
    }
}

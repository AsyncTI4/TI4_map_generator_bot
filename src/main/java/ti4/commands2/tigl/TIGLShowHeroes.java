package ti4.commands2.tigl;

import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Subcommand;
import ti4.helpers.Emojis;
import ti4.helpers.TIGLHelper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.message.MessageHelper;

public class TIGLShowHeroes extends Subcommand {

    public TIGLShowHeroes() {
        super("show_heroes", "Display a list of the reigning Heroes");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showTIGLHeroes(event);
    }

    public static void showTIGLHeroes(GenericInteractionCreateEvent event) {
        StringBuilder sb = new StringBuilder("__**TIGL Heroes**__\n");
        List<TIGLRank> heroRanks = TIGLHelper.getAllHeroTIGLRanks();

        for (TIGLRank rank : heroRanks) {
            Role role = rank.getRole();
            if (role == null) continue;
            String faction = StringUtils.substringAfter(rank.toString(),"_");
            String factionIcon = Emojis.getFactionIconFromDiscord(faction);
            String heroEmoji = Emojis.getEmojiFromDiscord(faction + "hero");
            List<Member> members = AsyncTI4DiscordBot.guildPrimary.getMembersWithRoles(role);

            sb.append("> ").append(factionIcon);
            for (Member member : members) {
                sb.append(member.getEffectiveName());
            }
            sb.append("  ").append(heroEmoji).append(rank.getShortName());
            sb.append("\n");
        }

        MessageHelper.sendMessageToThread(event.getMessageChannel(), "Async Rank - Reigning Heroes", sb.toString());
    }
    
}

package ti4.service.testbed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.utility.DiscordChannelUtility;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.CreateFoWGameService;

@UtilityClass
public class TestBedChannelService {

    static final String CREATED_CHANNELS_KEY = "testBedChannels";
    private static final long SEAT_CHANNEL_PERMISSIONS =
            Permission.VIEW_CHANNEL.getRawValue() | Permission.PIN_MESSAGES.getRawValue();

    public static void createFogPrivateChannel(Game game, Player seat, Member developer) {
        if (seat.getPrivateChannelID() != null && !seat.getPrivateChannelID().isBlank()) return;
        if (TestBedService.isVirtualSeat(seat)) {
            createVirtualSeatChannel(game, seat, developer);
        } else {
            CreateFoWGameService.createPrivateChannelForPlayer(developer, game);
        }
        recordCreatedChannel(game, seat.getPrivateChannelID());
    }

    private static void createVirtualSeatChannel(Game game, Player seat, Member developer) {
        String seatName = seat.getUserName();
        TextChannel channel = game.getGuild()
                .createTextChannel(
                        game.getName() + "-" + seatName.toLowerCase() + "-private",
                        game.getMainGameChannel().getParentCategory())
                .syncPermissionOverrides()
                .addMemberPermissionOverride(developer.getIdLong(), SEAT_CHANNEL_PERMISSIONS, 0)
                .complete();
        seat.setPrivateChannelID(channel.getId());
        MessageHelper.sendMessageToChannel(
                channel, "Private channel of virtual seat **" + seatName + "** (developer test bed).");
    }

    public static boolean grantGameMasterRole(Game game, Member developer) {
        List<Role> roles = game.getGuild().getRolesByName(game.getName() + " GM", true);
        if (roles.isEmpty()) return false;
        Role gmRole = roles.getFirst();
        if (!developer.getRoles().contains(gmRole)) {
            game.getGuild().addRoleToMember(developer, gmRole).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        return true;
    }

    public static void shareCardsInfoThread(Player seat, String developerId) {
        ThreadChannel thread = seat.getCardsInfoThread();
        if (thread == null) return;
        recordCreatedChannel(seat.getGame(), thread.getId());
        thread.addThreadMemberById(developerId).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    static void recordCreatedChannel(Game game, @Nullable String channelId) {
        if (channelId == null || channelId.isBlank()) return;
        List<String> ids = createdChannelIds(game);
        if (ids.contains(channelId)) return;
        ids.add(channelId);
        game.setStoredValue(CREATED_CHANNELS_KEY, String.join(",", ids));
    }

    static List<String> createdChannelIds(Game game) {
        String stored = game.getStoredValue(CREATED_CHANNELS_KEY);
        if (stored.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(stored.split(",")));
    }

    public static int deleteCreatedChannels(Game game) {
        Guild guild = game.getGuild();
        int missing = 0;
        for (String channelId : createdChannelIds(game)) {
            GuildChannel channel = guild == null ? null : findChannel(guild, channelId);
            if (channel == null) {
                missing++;
                continue;
            }
            channel.delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
        game.removeStoredValue(CREATED_CHANNELS_KEY);
        return missing;
    }

    @Nullable
    private static GuildChannel findChannel(Guild guild, String channelId) {
        GuildChannel cached = guild.getGuildChannelById(channelId);
        if (cached != null) return cached;
        try {
            return DiscordChannelUtility.retrieveThreadChannelById(guild, Long.parseLong(channelId))
                    .complete();
        } catch (Exception e) {
            return null;
        }
    }
}

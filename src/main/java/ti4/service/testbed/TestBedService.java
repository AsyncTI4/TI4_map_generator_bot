package ti4.service.testbed;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.settings.GlobalSettings.ImplementedSettings;

@UtilityClass
public class TestBedService {

    static final String TEST_BED_KEY = "testBed";
    static final String ACTING_AS_PREFIX = "testBedActingAs_";
    public static final String VIRTUAL_SEAT_ID_PREFIX = "90000000000000";

    public static boolean isEnabled() {
        return ImplementedSettings.TESTBED_ENABLED.getAsBoolean(false);
    }

    public static boolean isTestBed(@Nullable Game game) {
        return isEnabled() && isMarkedAsTestBed(game);
    }

    static boolean isMarkedAsTestBed(@Nullable Game game) {
        return game != null && "true".equals(game.getStoredValue(TEST_BED_KEY));
    }

    public static void markAsTestBed(Game game, boolean testBed) {
        if (testBed) {
            game.setStoredValue(TEST_BED_KEY, "true");
        } else {
            game.removeStoredValue(TEST_BED_KEY);
        }
    }

    public static boolean isDeveloper(@Nullable Member member) {
        return hasAnyRole(member, JdaService.developerRoles);
    }

    public static boolean isDeveloperId(@Nullable Guild guild, String userId) {
        return guild != null && isDeveloper(guild.getMemberById(userId));
    }

    @Nullable
    public static Player findNonDeveloper(@Nullable Guild guild, Collection<Player> players) {
        return players.stream()
                .filter(player -> !isVirtualSeat(player) && !isDeveloperId(guild, player.getUserID()))
                .findFirst()
                .orElse(null);
    }

    public static boolean isVirtualSeat(Player player) {
        return isVirtualSeatId(player.getUserID());
    }

    static boolean isVirtualSeatId(@Nullable String userId) {
        return userId != null && userId.startsWith(VIRTUAL_SEAT_ID_PREFIX);
    }

    public static void clearAllActingAs(Game game) {
        List<String> keys = game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(ACTING_AS_PREFIX))
                .toList();
        keys.forEach(game::removeStoredValue);
    }

    private static boolean hasAnyRole(@Nullable Member member, Collection<Role> roles) {
        return member != null && member.getRoles().stream().anyMatch(roles::contains);
    }

    public static void setActingAs(Game game, String userId, @Nullable Player seat) {
        if (seat == null) {
            game.removeStoredValue(ACTING_AS_PREFIX + userId);
        } else {
            game.setStoredValue(ACTING_AS_PREFIX + userId, seat.getFaction());
        }
    }

    @Nullable
    public static Player getActingAs(Game game, String userId) {
        String faction = game.getStoredValue(ACTING_AS_PREFIX + userId);
        return faction.isEmpty() ? null : game.getPlayerFromColorOrFaction(faction);
    }

    @Nullable
    public static Player resolveActingPlayer(
            Game game,
            @Nullable Member member,
            String userId,
            @Nullable String channelId,
            @Nullable Player defaultPlayer) {
        if (!isTestBed(game) || !isDeveloper(member)) return defaultPlayer;
        return resolveForDeveloper(game, userId, channelId, defaultPlayer);
    }

    @Nullable
    static Player resolveForDeveloper(
            Game game, String userId, @Nullable String channelId, @Nullable Player defaultPlayer) {
        Player seatOfChannel = seatOwningChannel(game, channelId);
        if (seatOfChannel != null) return seatOfChannel;
        Player actingAs = getActingAs(game, userId);
        return actingAs != null ? actingAs : defaultPlayer;
    }

    @Nullable
    private static Player seatOwningChannel(Game game, @Nullable String channelId) {
        if (channelId == null) return null;
        for (Player player : game.getRealPlayers()) {
            if (channelId.equals(player.getPrivateChannelID()) || channelId.equals(player.getCardsInfoThreadID())) {
                return player;
            }
        }
        return null;
    }
}

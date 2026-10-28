package ti4.service.fow;

import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class FogStandardService {

    public static final List<FOWOption> FOWPLUS_EXTRAS = List.of(
            FOWOption.MANAGED_COMMS,
            FOWOption.NEW_TRANSACTIONS,
            FOWOption.FOG_QOL_01,
            FOWOption.GM_TURN_MAP,
            FOWOption.HIDE_AC_DISCARD,
            FOWOption.MAP_CONNECTIONS,
            FOWOption.GHOST_HEXES);

    public static void apply(Game game) {
        FOWPlusService.enable(game);
        FOWPLUS_EXTRAS.forEach(option -> game.setFowOption(option, true));
    }

    public static void announce(Game game, MessageChannel gmChannel, MessageChannel announcementsChannel) {
        announceToGm(game, gmChannel);
        MessageHelper.sendMessageToChannel(announcementsChannel, ghostHexNote());
    }

    public static void reapply(Game game, MessageChannel gmChannel, @Nullable MessageChannel announcementsChannel) {
        boolean ghostHexesWereOn = game.getFowOption(FOWOption.GHOST_HEXES);
        apply(game);
        announceToGm(game, gmChannel);
        if (!ghostHexesWereOn && announcementsChannel != null) {
            MessageHelper.sendMessageToChannel(announcementsChannel, ghostHexNote());
        }
    }

    public static void announceToGm(Game game, MessageChannel gmChannel) {
        MessageHelper.sendMessageToChannelWithEmbed(
                gmChannel, gmOverviewText(), FogGameSummaryService.fogOptionsEmbed(game));
    }

    static String gmOverviewText() {
        return "### Fog standard applied\n"
                + "This game starts with the fog standard: FoW+ and the options below.\n"
                + "**FoW+ changes in play:**\n"
                + FOWPlusService.FOWPLUS_PLAY_CHANGES
                + "\n**Explore deck:** FoW+ has its own explore deck, `" + FOWPlusService.FOWPLUS_EXPLORE_DECK + "`."
                + " Setup steps such as a game mode, homebrew or a draft's deck settings can replace it."
                + " Check `/game info` before the game starts, and use `/game set_deck` to put it back if you want it."
                + "\nChange single options with `/fow fow_options`, or pick another preset in the setup wizard's"
                + " fog type step.";
    }

    static String ghostHexNote() {
        return "Faint numbered rings on unexplored hexes next to known space (ghost hexes) are on in this game. "
                + "Each player can hide them for themselves with `/" + Constants.USER + " "
                + Constants.FOG_GHOST_HEXES + " show:False`.";
    }
}

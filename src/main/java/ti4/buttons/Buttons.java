package ti4.buttons;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.Constants;
import ti4.message.BotLogger;

public class Buttons {
    public static final Button GET_A_TECH = green("acquireATech", "Get a Technology");
    public static final Button GET_A_FREE_TECH = green("acquireAFreeTech", "Get a Technology");
    public static final Button REDISTRIBUTE_CCs = green("redistributeCCButtons", "Redistribute Command Tokens");
    public static final Button DONE_DELETE_BUTTONS = gray("deleteButtons", "Done");
    public static final Button FACTION_EMBED = green("factionEmbedRefresh", "Refresh Faction Display");

    // Cards Info Buttons
    public static final Button EDIT_SUMMARIES = blue("editEndOfRoundSummaries", "Edit round summaries");
    public static final Button EDIT_NOTEPAD = blue("notepadEdit~MDL", "Edit your notepad");
    public static final Button POST_NOTEPAD = blue("notepadPost", "Post your notepad");
    public static final Button REFRESH_INFO = green("refreshInfoButtons", "Other Info");
    public static final Button REFRESH_AC_INFO = green("refreshACInfo", "Action Card Info");
    public static final Button REFRESH_PN_INFO = green("refreshPNInfo", "Promissory Notes Info");
    public static final Button REFRESH_SO_INFO = green("refreshSOInfo", "Secret Objectives Info");
    public static final Button REFRESH_ABILITY_INFO = green("refreshAbilityInfo", "Ability Info");
    public static final Button REFRESH_RELIC_INFO = green(Constants.REFRESH_RELIC_INFO, "Relic Info");
    public static final Button REFRESH_LEADER_INFO = green(Constants.REFRESH_LEADER_INFO, "Leader Info");
    public static final Button REFRESH_UNIT_INFO = green(Constants.REFRESH_UNIT_INFO, "Unit Info");
    public static final Button REFRESH_ALL_UNIT_INFO = green(Constants.REFRESH_ALL_UNIT_INFO, "Show All Units");
    public static final Button REFRESH_TECH_INFO = green(Constants.REFRESH_TECH_INFO, "Technology Info");
    public static final Button REFRESH_PLANET_INFO = green(Constants.REFRESH_PLANET_INFO, "Planet Info");

    public static final List<Button> REFRESH_INFO_BUTTONS = List.of(
        REFRESH_AC_INFO,
        REFRESH_PN_INFO,
        REFRESH_SO_INFO,
        REFRESH_ABILITY_INFO,
        REFRESH_RELIC_INFO,
        REFRESH_LEADER_INFO,
        REFRESH_UNIT_INFO,
        REFRESH_TECH_INFO,
        REFRESH_PLANET_INFO,
        FACTION_EMBED);

    public static Button blue(String buttonID, String buttonLabel) {
        return Button.primary(buttonID, buttonLabel);
    }

    public static Button blue(String buttonID, String buttonLabel, String emoji) {
        return Button.primary(buttonID, buttonLabel).withEmoji(getEmoji(emoji));
    }

    public static Button gray(String buttonID, String buttonLabel) {
        return Button.secondary(buttonID, buttonLabel);
    }

    public static Button gray(String buttonID, String buttonLabel, String emoji) {
        return Button.secondary(buttonID, buttonLabel).withEmoji(getEmoji(emoji));
    }

    public static Button green(String buttonID, String buttonLabel) {
        return Button.success(buttonID, buttonLabel);
    }

    public static Button green(String buttonID, String buttonLabel, String emoji) {
        return Button.success(buttonID, buttonLabel).withEmoji(getEmoji(emoji));
    }

    public static Button red(String buttonID, String buttonLabel) {
        return Button.danger(buttonID, buttonLabel);
    }

    public static Button red(String buttonID, String buttonLabel, String emoji) {
        return Button.danger(buttonID, buttonLabel).withEmoji(getEmoji(emoji));
    }

    private static Emoji getEmoji(String emoji) {
        if (StringUtils.isBlank(emoji)) return null; // no need to error on null/blank
        try {
            Emoji output = Emoji.fromFormatted(emoji);
            return output;
        } catch (Exception e) {
            BotLogger.log("Failed to load emoji [" + emoji + "]", e);
        }
        return null;
    }
}

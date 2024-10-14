package ti4.buttons;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.message.BotLogger;

public class Buttons {
    public static final Button GET_A_TECH = green("acquireATech", "Get a Tech");
    public static final Button GET_A_TECH_WITH_INF = green("acquireATechWithInf", "Get a Tech");
    public static final Button GET_A_FREE_TECH = green("acquireAFreeTech", "Get a Tech");
    public static final Button REDISTRIBUTE_CCs = green("redistributeCCButtons", "Redistribute CCs");
    public static final Button DONE_DELETE_BUTTONS = gray("deleteButtons", "Done");
    public static final Button FACTION_EMBED = green("factionEmbedRefresh", "Refresh Faction Display");

    // Cards Info Buttons
    public static final Button EDIT_SUMMARIES = blue("editEndOfRoundSummaries", "Edit Summaries");
    public static final Button EDIT_NOTEPAD = blue("notepadEdit~MDL", "Edit Notes");
    public static final Button POST_NOTEPAD = blue("notepadPost", "Post Notes");
    public static final Button REFRESH_INFO = green("refreshInfoButtons", "Other Info");
    public static final Button REFRESH_AC_INFO = green("refreshACInfo", "Action Card Info", Emojis.ActionCard);
    public static final Button REFRESH_PN_INFO = green("refreshPNInfo", "Promissory Notes Info", Emojis.PN);
    public static final Button REFRESH_SO_INFO = green("refreshSOInfo", "Secret Objectives Info", Emojis.SecretObjective);
    public static final Button REFRESH_ABILITY_INFO = green("refreshAbilityInfo", "Ability Info");
    public static final Button REFRESH_RELIC_INFO = green(Constants.REFRESH_RELIC_INFO, "Relic Info", Emojis.Relic);
    public static final Button REFRESH_LEADER_INFO = green(Constants.REFRESH_LEADER_INFO, "Leader Info", Emojis.Hero);
    public static final Button REFRESH_UNIT_INFO = green(Constants.REFRESH_UNIT_INFO, "Unit Info", Emojis.UnitUpgradeTech);
    public static final Button REFRESH_ALL_UNIT_INFO = green(Constants.REFRESH_ALL_UNIT_INFO, "Show All Units");
    public static final Button REFRESH_TECH_INFO = green(Constants.REFRESH_TECH_INFO, "Tech Info");
    public static final Button REFRESH_PLANET_INFO = green(Constants.REFRESH_PLANET_INFO, "Planet Info", Emojis.SemLor);

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

    /**
     * A blue button (primary style)
     */
    public static Button blue(String buttonID, String buttonLabel) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
        return Button.primary(buttonID, buttonLabel);
    }

    /**
     * A blue button (primary style) with an emoji
     */
    public static Button blue(String buttonID, String buttonLabel, String emoji) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
        return Button.primary(buttonID, buttonLabel).withEmoji(getEmoji(emoji));
    }

    /**
     * A gray button (secondary style)
     */
    public static Button gray(String buttonID, String buttonLabel) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
        return Button.secondary(buttonID, buttonLabel);
    }

    /**
     * A gray button (secondary style) with an emoji
     */
    public static Button gray(String buttonID, String buttonLabel, String emoji) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
        return Button.secondary(buttonID, buttonLabel).withEmoji(getEmoji(emoji));
    }

    /**
     * A green button (success style)
     */
    public static Button green(String buttonID, String buttonLabel) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
        return Button.success(buttonID, buttonLabel);
    }

    /**
     * A green button (success style) with an emoji
     */
    public static Button green(String buttonID, String buttonLabel, String emoji) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
        return Button.success(buttonID, buttonLabel).withEmoji(getEmoji(emoji));
    }

    /**
     * A red button (danger style) with an emoji
     */
    public static Button red(String buttonID, String buttonLabel) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
        return Button.danger(buttonID, buttonLabel);
    }

    /**
     * A red button (danger style) with an emoji
     */
    public static Button red(String buttonID, String buttonLabel, String emoji) {
        if (StringUtils.isEmpty(buttonLabel)) {
            buttonLabel = " ";
        }
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

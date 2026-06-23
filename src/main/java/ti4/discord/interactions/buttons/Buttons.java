package ti4.discord.interactions.buttons;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.website.AsyncTi4WebsiteHelper;

public final class Buttons {

    public enum ButtonColor {
        green,
        red,
        gray,
        blue
    }

    public static final Button GET_A_TECH = green("acquireATech", "Get a Technology");
    public static final Button GET_A_UNIT_TECH_WITH_INF =
            green("acquireAUnitTechWithInf", "Get a Unit Upgrade Technology");
    public static final Button GET_A_FREE_TECH = green("acquireAFreeTech", "Get a Technology");
    public static final Button REDISTRIBUTE_CCs = green("redistributeCCButtons", "Redistribute Command Tokens");
    public static final Button DONE_DELETE_BUTTONS = gray("deleteButtons", "Done");
    public static final Button CANCEL = gray("deleteButtons", "Cancel");
    public static final Button FACTION_EMBED = green("factionEmbedRefresh", "Refresh Faction Display");
    public static final Button DEAL_2_SO =
            green("deal2SOToAll", "Deal 2 Secret Objectives To All", CardEmojis.SecretObjectiveAlt);

    // Cards Info Buttons
    public static final Button EDIT_SUMMARIES = blue("editEndOfRoundSummaries", "Edit Summaries");
    public static final Button EDIT_NOTEPAD = blue("notepadEdit~MDL", "Edit Notes");
    public static final Button POST_NOTEPAD = blue("notepadPost", "Post Notes");
    public static final Button REFRESH_INFO = green("refreshInfoButtons", "Other Info");
    private static final Button REFRESH_AC_INFO = green("refreshACInfo", "Action Card Info", CardEmojis.ActionCard);
    private static final Button REFRESH_AC_INFO_TF =
            green("refreshACInfo", "Action Card Info", CardEmojis.TF_Action_Card);
    private static final Button REFRESH_PN_INFO = green("refreshPNInfo", "Promissory Notes Info", CardEmojis.PN);
    private static final Button REFRESH_SO_INFO =
            green("refreshSOInfo", "Secret Objectives Info", CardEmojis.SecretObjective);
    private static final Button REFRESH_ABILITY_INFO = green("refreshAbilityInfo", "Ability Info");
    private static final Button REFRESH_BREAKTHROUGH_INFO =
            green(Constants.REFRESH_BREAKTHROUGH_INFO, "Breakthrough Info");
    public static final Button REFRESH_RELIC_INFO =
            green(Constants.REFRESH_RELIC_INFO, "Relic Info", ExploreEmojis.Relic);
    public static final Button REFRESH_LEADER_INFO =
            green(Constants.REFRESH_LEADER_INFO, "Leader Info", LeaderEmojis.Hero);
    public static final Button REFRESH_UNIT_INFO =
            green(Constants.REFRESH_UNIT_INFO, "Unit Info", TechEmojis.UnitUpgradeTech);
    public static final Button REFRESH_ALL_UNIT_INFO = green(Constants.REFRESH_ALL_UNIT_INFO, "Show All Units");
    public static final Button REFRESH_TECH_INFO = green(Constants.REFRESH_TECH_INFO, "Technology Info");
    public static final Button REFRESH_PLANET_INFO =
            green(Constants.REFRESH_PLANET_INFO, "Planet Info", PlanetEmojis.SemLor);

    public static final Button OFFER_PING_OPTIONS_BUTTON =
            gray("playerPref_personalPingInterval", "Personal Ping Interval");

    // Map buttons
    private static final Button REFRESH_CARDS_INFO = green("cardsInfo", "Cards Info");
    public static final Button SHOW_DECKS = blue("offerDeckButtons", "Show Decks");
    public static final Button REFRESH_MAP = gray("showGameAgain", "Refresh Map");
    private static final Button PLAYER_INFO = green("gameInfoButtons", "Player Info");

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
    public static final List<Button> REFRESH_INFO_BUTTONS_TE = List.of(
            REFRESH_AC_INFO,
            REFRESH_PN_INFO,
            REFRESH_SO_INFO,
            REFRESH_ABILITY_INFO,
            REFRESH_RELIC_INFO,
            REFRESH_LEADER_INFO,
            REFRESH_UNIT_INFO,
            REFRESH_TECH_INFO,
            REFRESH_BREAKTHROUGH_INFO,
            REFRESH_PLANET_INFO,
            FACTION_EMBED);
    public static final List<Button> REFRESH_INFO_BUTTONS_TF = List.of(
            REFRESH_AC_INFO,
            REFRESH_SO_INFO,
            REFRESH_ABILITY_INFO,
            REFRESH_RELIC_INFO,
            REFRESH_LEADER_INFO,
            REFRESH_UNIT_INFO,
            REFRESH_TECH_INFO,
            REFRESH_PLANET_INFO,
            FACTION_EMBED);

    public static List<Button> mapImageButtons(Game game) {
        List<Button> buttonsWeb = new ArrayList<>();
        if (game != null && !game.isFowMode()) {
            if (AsyncTi4WebsiteHelper.uploadsEnabled()) {
                String url = "https://asyncti4.com/game/" + game.getName() + "/newui";
                buttonsWeb.add(Button.link(url, "Website View"));
            }
            buttonsWeb.add(PLAYER_INFO);
        }
        buttonsWeb.add(REFRESH_CARDS_INFO);
        buttonsWeb.add(SHOW_DECKS);
        if (game != null && game.isTwilightsFallMode()) {
            buttonsWeb.add(green("showSpliceDecks", "Show Twilight Fall Decks"));
        }
        buttonsWeb.add(REFRESH_MAP);
        return buttonsWeb;
    }

    private static final int PAGE_SIZE = 20;

    /** A blue button (primary style) */
    public static Button blue(String buttonID, String buttonLabel) {
        return makeButton(ButtonStyle.PRIMARY, buttonID, buttonLabel, null);
    }

    /** A blue button (primary style) with an emoji */
    public static Button blue(String buttonID, String buttonLabel, String emoji) {
        return makeButton(ButtonStyle.PRIMARY, buttonID, buttonLabel, emoji);
    }

    /** A blue button (primary style) with an emoji */
    public static Button blue(String buttonID, String buttonLabel, TI4Emoji emoji) {
        return makeButton(ButtonStyle.PRIMARY, buttonID, buttonLabel, emoji == null ? null : emoji.toString());
    }

    /** A gray button (secondary style) */
    public static Button gray(String buttonID, String buttonLabel) {
        return makeButton(ButtonStyle.SECONDARY, buttonID, buttonLabel, null);
    }

    /** A gray button (secondary style) with an emoji */
    public static Button gray(String buttonID, String buttonLabel, String emoji) {
        return makeButton(ButtonStyle.SECONDARY, buttonID, buttonLabel, emoji);
    }

    /** A gray button (secondary style) with an emoji */
    public static Button gray(String buttonID, String buttonLabel, TI4Emoji emoji) {
        return makeButton(ButtonStyle.SECONDARY, buttonID, buttonLabel, emoji == null ? null : emoji.toString());
    }

    /** A green button (success style) */
    public static Button green(String buttonID, String buttonLabel) {
        return makeButton(ButtonStyle.SUCCESS, buttonID, buttonLabel, null);
    }

    /** A green button (success style) with an emoji */
    public static Button green(String buttonID, String buttonLabel, String emoji) {
        return makeButton(ButtonStyle.SUCCESS, buttonID, buttonLabel, emoji);
    }

    /** A green button (success style) with an emoji */
    public static Button green(String buttonID, String buttonLabel, TI4Emoji emoji) {
        return makeButton(ButtonStyle.SUCCESS, buttonID, buttonLabel, emoji == null ? null : emoji.toString());
    }

    /** A red button (danger style) */
    public static Button red(String buttonID, String buttonLabel) {
        return makeButton(ButtonStyle.DANGER, buttonID, buttonLabel, null);
    }

    /** A red button (danger style) with an emoji */
    public static Button red(String buttonID, String buttonLabel, String emoji) {
        return makeButton(ButtonStyle.DANGER, buttonID, buttonLabel, emoji);
    }

    /** A red button (danger style) with an emoji */
    public static Button red(String buttonID, String buttonLabel, TI4Emoji emoji) {
        return makeButton(ButtonStyle.DANGER, buttonID, buttonLabel, emoji == null ? null : emoji.toString());
    }

    public static Button rgToggle(boolean isDisable, String buttonID, String buttonLabel, TI4Emoji emoji) {
        if (isDisable) return red(buttonID, "Disable " + buttonLabel, emoji);
        return green(buttonID, "Enable " + buttonLabel, emoji);
    }

    private static Button makeButton(ButtonStyle style, String id, String label, String emoji) {
        Emoji e = getEmoji(emoji);
        if (id == null || id.isBlank()) {
            BotLogger.error("Illegal button attempt", new Throwable("Illegal button attempt"));
            return null;
        }
        if (e == null && (label == null || label.isBlank())) {
            // BotLogger.log("Button sanitized: " + id);
            return Button.of(style, id, " ", null);
        }
        if (label != null && label.length() > 80 && label.contains("Mez Lo Orz Fei Zsha/Rep Lo Orz Qet")) {
            label = label.replace("Mez Lo Orz Fei Zsha/Rep Lo Orz Qet", "Mez Lo O.F.Z./Rep Lo O.Q.");
        }
        if (label != null && label.length() > 80) {
            BotLogger.info("Button [" + id + "] label too long (" + label.length() + "), truncating: " + label);
            label = label.substring(0, 77) + "...";
        }
        return Button.of(style, id, label, e);
    }

    private static Emoji getEmoji(String emoji) {
        if (StringUtils.isBlank(emoji)) return null; // no need to error on null/blank
        try {
            return Emoji.fromFormatted(emoji);
        } catch (Exception e) {
            BotLogger.error("Failed to load emoji [" + emoji + "]", e);
        }
        return null;
    }

    public static Button declineAndEdit(String buttonLabel, String editMessage) {
        return gray("editMessage_" + editMessage, buttonLabel);
    }

    public static Button declineAndNotify(String buttonLabel, String notificationMessage) {
        return gray("deleteMessage_" + notificationMessage, buttonLabel);
    }

    public static List<ActionRow> paginateButtons(
            List<Button> mainButtons, List<Button> persistentButtons, int page, String pageButtonId) {
        Page pagination = paginate(mainButtons, page);

        List<ActionRow> rows = new ArrayList<>(partitionIntoRows(pagination.items()));

        List<Button> navRow = buildPersistentAndNavigationButtonsRow(persistentButtons, pagination, pageButtonId);
        if (!navRow.isEmpty()) {
            rows.add(ActionRow.of(navRow));
        }

        return rows;
    }

    private static Page paginate(List<Button> buttons, int page) {
        if (buttons.isEmpty()) return new Page(1, 0, List.of());

        int total = (int) Math.ceil((double) buttons.size() / PAGE_SIZE);
        int current = Math.clamp(page, 1, total);
        int from = (current - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, buttons.size());
        return new Page(current, total, buttons.subList(from, to));
    }

    private static List<ActionRow> partitionIntoRows(List<Button> buttons) {
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }
        return rows;
    }

    private static List<Button> buildPersistentAndNavigationButtonsRow(
            List<Button> persistentButtons, Page page, String pageButtonId) {
        List<Button> navRow = new ArrayList<>();

        if (persistentButtons != null) {
            persistentButtons.stream().limit(3).forEach(navRow::add);
        }

        if (page.isMultiPage()) {
            if (page.hasPrevious()) {
                navRow.add(gray(pageButtonId + "_page" + (page.current() - 1), "Previous Page", "⏪"));
            }
            if (page.hasNext()) {
                navRow.add(gray(pageButtonId + "_page" + (page.current() + 1), "Next Page", "⏩"));
            }
        }

        return navRow;
    }

    private record Page(int current, int total, List<Button> items) {
        boolean hasPrevious() {
            return current > 1;
        }

        boolean hasNext() {
            return current < total;
        }

        boolean isMultiPage() {
            return total > 1;
        }
    }
}

package ti4.buttons;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.logging.BotLogger;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.website.AsyncTi4WebsiteHelper;

public class Buttons {

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
    public static final Button FACTION_EMBED = green("factionEmbedRefresh", "Refresh Faction Display");

    // Cards Info Buttons
    public static final Button EDIT_SUMMARIES = blue("editEndOfRoundSummaries", "Edit Summaries");
    public static final Button EDIT_NOTEPAD = blue("notepadEdit~MDL", "Edit Notes");
    public static final Button POST_NOTEPAD = blue("notepadPost", "Post Notes");
    public static final Button REFRESH_INFO = green("refreshInfoButtons", "Other Info");
    private static final Button REFRESH_AC_INFO = green("refreshACInfo", "Action Card Info", CardEmojis.ActionCard);
    private static final Button REFRESH_PN_INFO = green("refreshPNInfo", "Promissory Notes Info", CardEmojis.PN);
    private static final Button REFRESH_SO_INFO =
            green("refreshSOInfo", "Secret Objectives Info", CardEmojis.SecretObjective);
    private static final Button REFRESH_ABILITY_INFO = green("refreshAbilityInfo", "Ability Info");
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

    /**
     * Check if a game is standard PoK or only uses 4/4/4 homebrew
     */
    private static boolean isStandardPoKOrOnly444(Game game) {
        if (game == null) return false;

        // FIRST: Check that NO other homebrew elements are present
        if (game.isHomebrew() // explicit homebrew flag
                || game.isExtraSecretMode()
                || game.isFowMode()
                || game.isAgeOfExplorationMode()
                || game.isFacilitiesMode()
                || game.isMinorFactionsMode()
                || game.isLightFogMode()
                || game.isRedTapeMode()
                || game.isDiscordantStarsMode()
                || game.isFrankenGame()
                || game.isMiltyModMode()
                || game.isAbsolMode()
                || game.isVotcMode()
                || game.isPromisesPromisesMode()
                || game.isFlagshippingMode()
                || game.isAllianceMode()
                || (game.getSpinMode() != null && !"OFF".equalsIgnoreCase(game.getSpinMode()))
                || game.isHomebrewSCMode()
                || game.isCommunityMode()
                || game.getPlayerCountForMap() < 3
                || game.getPlayerCountForMap() > 8) {

            return false; // Has other homebrew elements, not standard
        }

        // Check decks, tiles, and factions are official
        try {
            if (!game.checkAllDecksAreOfficial()
                    || !game.checkAllTilesAreOfficial()
                    || game.getFactions().stream()
                            .map(Mapper::getFaction)
                            .filter(Objects::nonNull)
                            .anyMatch(faction -> !faction.getSource().isOfficial())) {
                return false;
            }
        } catch (Exception e) {
            return false; // If we can't verify, assume not standard
        }

        return true;
    }

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
        int totalPages = (int) Math.ceil((double) mainButtons.size() / PAGE_SIZE);
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = (currentPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, mainButtons.size());

        List<Button> pageButtons = new ArrayList<>(mainButtons.subList(fromIndex, toIndex));

        // Split main buttons into rows of max 5 (Discord limit)
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < pageButtons.size(); i += 5) {
            rows.add(ActionRow.of(pageButtons.subList(i, Math.min(i + 5, pageButtons.size()))));
        }

        // Prepare persistent + navigation buttons for their own row
        List<Button> persistentAndNav = new ArrayList<>();
        if (persistentButtons != null && !persistentButtons.isEmpty()) {
            for (int i = 0; i < Math.min(3, persistentButtons.size()); i++) {
                persistentAndNav.add(persistentButtons.get(i));
            }
        }
        // Add navigation buttons if more than one page
        if (totalPages > 1) {
            if (currentPage > 1) {
                persistentAndNav.add(gray(pageButtonId + "_page" + (currentPage - 1), "Previous Page", "⏪"));
            }
            if (currentPage < totalPages) {
                persistentAndNav.add(gray(pageButtonId + "_page" + (currentPage + 1), "Next Page", "⏩"));
            }
        }
        if (!persistentAndNav.isEmpty()) {
            rows.add(ActionRow.of(persistentAndNav));
        }

        return rows;
    }
}

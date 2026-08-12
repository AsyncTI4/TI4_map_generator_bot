package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ActionCardHelper.ACStatus;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.decks.ShowActionCardsService;
import ti4.service.option.FOWOptionService.FOWOption;

/**
 * TEMPORARY live-testing harness for {@link FOWOption#HIDE_AC_DISCARD}. Posts a button panel that seeds a
 * deterministic mix of played / forced-discarded / Garbozia / Data Skimmer / purged action cards, then exercises the
 * real production code paths (not reimplementations) so a developer can visually confirm the visibility gate and its
 * fixes on a live bot, in a real game, instead of only in unit tests.
 *
 * <p>The "Peek Data Skimmer" and "Reengineer: Take Last Discard" buttons carry the exact custom IDs the real
 * production buttons use ({@code peekDataSkimmer}, {@code reengineerTakeDiscard}), so clicking them dispatches
 * straight into {@code DataSkimmerService} / {@code ReengineerAcd2ButtonHandler} through the normal button router,
 * not a copy of their logic.
 *
 * <p>To remove: delete this class and its entry in {@link DeveloperCommand}. Nothing else references it.
 */
class TestHideAcDiscard extends GameStateSubcommand {

    private static final String PREFIX = "devHideAc_";

    // One card per branch of the visibility gate. Real base-game action card IDs, chosen so they exist regardless
    // of which AC deck the game is using.
    private static final String PLAYED_ID = "mb1"; // played -> stays visible
    private static final String FORCED_ID = "mb2"; // forced discard -> hidden
    private static final String GARBOZIA_ID = "mb3"; // sitting on Garbozia -> hidden from the general pile
    private static final String SKIMMER_ID = "mb4"; // sitting on Data Skimmer -> hidden from the general pile
    private static final String PURGED_PLAYED_ID = "sh1"; // purged after being played -> stays visible in purge list
    private static final String PURGED_FORCED_ID = "sh2"; // purged without being played -> hidden from purge list
    private static final List<String> SEED_IDS =
            List.of(PLAYED_ID, FORCED_ID, GARBOZIA_ID, SKIMMER_ID, PURGED_PLAYED_ID, PURGED_FORCED_ID);

    TestHideAcDiscard() {
        super("test_hide_ac_discard", "TEMPORARY: post buttons to live-test HIDE_AC_DISCARD", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), describeState(getGame(), getPlayer()), buttons());
    }

    private static List<Button> buttons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(PREFIX + "state", "Show State"));
        buttons.add(Buttons.blue(PREFIX + "seed", "Seed Test Discards"));
        buttons.add(Buttons.red(PREFIX + "wipe", "Wipe Test Discards"));
        buttons.add(Buttons.gray(PREFIX + "toggleOption", "Toggle HIDE_AC_DISCARD"));
        buttons.add(Buttons.gray(PREFIX + "toggleFow", "Toggle FoW Mode"));
        buttons.add(Buttons.gray(PREFIX + "toggleGarbozia", "Toggle My Garbozia Ownership"));
        buttons.add(Buttons.gray(PREFIX + "toggleSkimmer", "Toggle My Data Skimmer Breakthrough"));
        buttons.add(Buttons.green(PREFIX + "showDiscard", "Show Discard (production)"));
        buttons.add(Buttons.green(PREFIX + "showUnplayed", "Show Unplayed (production)"));
        buttons.add(Buttons.green(PREFIX + "showPurged", "Show Purged (mirrors production)"));
        buttons.add(Buttons.green(PREFIX + "showGarbozia", "Show Garbozia (mirrors production gate)"));
        // Real production custom IDs - these dispatch straight into the real handlers, not a copy.
        buttons.add(Buttons.green("peekDataSkimmer", "Peek Data Skimmer (real handler)"));
        buttons.add(Buttons.green("reengineerTakeDiscard", "Reengineer: Take Last Discard (real handler)"));
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler(PREFIX)
    public static void handleTestButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!CommandHelper.hasRole(event, JdaService.developerRoles)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "These test buttons are for developers only.");
            return;
        }

        String action = buttonID.replace(PREFIX, "");
        StringBuilder log = new StringBuilder("### `test_hide_ac_discard` → `")
                .append(action)
                .append("`\n");
        switch (action) {
            case "state" -> log.setLength(0);
            case "seed" -> log.append(seed(game, player));
            case "wipe" -> log.append(wipe(game, player));
            case "toggleOption" -> {
                boolean newValue = !game.getFowOption(FOWOption.HIDE_AC_DISCARD);
                game.setFowOption(FOWOption.HIDE_AC_DISCARD, newValue);
                log.append("`HIDE_AC_DISCARD` → **").append(newValue).append("**\n");
            }
            case "toggleFow" -> {
                boolean newValue = !game.isFowMode();
                game.setFowMode(newValue);
                log.append("`fowMode` → **").append(newValue).append("**\n");
            }
            case "toggleGarbozia" -> log.append(toggleGarbozia(player));
            case "toggleSkimmer" -> log.append(toggleSkimmer(player));
            case "showDiscard" -> {
                ShowActionCardsService.showDiscard(game, event, false);
                log.append("Posted the real `/cards_ac show_discard_list` output above.\n");
            }
            case "showUnplayed" -> {
                ShowActionCardsService.showUnplayedACs(game, event, false);
                log.append("Posted the real `/cards_ac show_unplayed` output above.\n");
            }
            case "showPurged" -> log.append(showPurgedMirror(game, player));
            case "showGarbozia" -> log.append(showGarboziaMirror(game, player));
            default -> log.append("Unknown action `").append(action).append("`.\n");
        }
        log.append('\n').append(describeState(game, player));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), log.toString());
    }

    /** Puts one card behind each branch of the visibility gate. Idempotent - skips IDs already seeded. */
    private static String seed(Game game, Player player) {
        StringBuilder sb = new StringBuilder();
        seedDiscard(game, player, PLAYED_ID, true, null, sb, "played, general pile");
        seedDiscard(game, player, FORCED_ID, false, null, sb, "forced discard, general pile");
        seedDiscard(game, player, GARBOZIA_ID, false, ACStatus.garbozia, sb, "sitting on Garbozia");
        seedDiscard(game, player, SKIMMER_ID, false, ACStatus.ralnelbt, sb, "sitting on Data Skimmer");
        seedPurged(game, player, PURGED_PLAYED_ID, true, sb, "purged, was played");
        seedPurged(game, player, PURGED_FORCED_ID, false, sb, "purged, was a forced discard");
        if (sb.isEmpty()) {
            sb.append("Nothing to seed - all test cards are already present. Use Wipe first to reset.\n");
        }
        return sb.toString();
    }

    private static void seedDiscard(
            Game game, Player player, String acID, boolean played, ACStatus status, StringBuilder sb, String label) {
        if (game.getDiscardActionCards().containsKey(acID)) return;
        player.setActionCard(acID);
        game.discardActionCard(player.getUserID(), player.getActionCards().get(acID), played);
        if (status != null) {
            game.getDiscardACStatus().put(acID, status);
        }
        sb.append("- seeded `").append(acID).append("` (").append(label).append(")\n");
    }

    private static void seedPurged(
            Game game, Player player, String acID, boolean played, StringBuilder sb, String label) {
        if (game.getDiscardActionCards().containsKey(acID)) return;
        player.setActionCard(acID);
        game.purgedActionCard(player.getUserID(), player.getActionCards().get(acID), played);
        sb.append("- seeded `").append(acID).append("` (").append(label).append(")\n");
    }

    /** Removes all seeded test cards from hand, discard, status and played bookkeeping. */
    private static String wipe(Game game, Player player) {
        int removed = 0;
        for (String acID : SEED_IDS) {
            if (game.getDiscardActionCards().remove(acID) != null) removed++;
            game.getDiscardACStatus().remove(acID);
            game.getPlayedActionCards().remove(acID);
            player.getActionCards().remove(acID);
        }
        return "Removed " + removed + " test cards from the discard/status/played maps (and hand, if any left).\n";
    }

    private static String toggleGarbozia(Player player) {
        if (player.hasPlanet("garbozia")) {
            player.removePlanet("garbozia");
            return "Removed `garbozia` planet from you.\n";
        }
        player.addPlanet("garbozia");
        return "Added `garbozia` planet to you (you are now the Garbozia controller).\n";
    }

    private static String toggleSkimmer(Player player) {
        if (player.hasUnlockedBreakthrough("ralnelbt")) {
            player.removeBreakthrough("ralnelbt");
            return "Removed the `ralnelbt` (Data Skimmer) breakthrough from you.\n";
        }
        player.addBreakthrough("ralnelbt");
        player.setBreakthroughUnlocked("ralnelbt", true);
        return "Added and unlocked the `ralnelbt` (Data Skimmer) breakthrough for you.\n";
    }

    /** Mirrors {@code ShowPurgedActionCards.showPurged} - that command class can't be invoked directly. */
    private static String showPurgedMirror(Game game, Player player) {
        boolean hideUnplayed = ActionCardHelper.hidesUnplayedDiscards(game, player);
        StringBuilder sb = new StringBuilder("Action card purge list (mirrors `/cards_ac show_purged_list`):\n");
        int index = 1;
        for (Map.Entry<String, Integer> ac : game.getPurgedActionCards().entrySet()) {
            if (!ActionCardHelper.isDiscardVisible(game, hideUnplayed, ac.getKey())) continue;
            sb.append('`')
                    .append(index)
                    .append(".")
                    .append(Helper.leftpad("(" + ac.getValue(), 4))
                    .append(")` - ")
                    .append(Mapper.getActionCard(ac.getKey()).getRepresentation(game))
                    .append('\n');
            index++;
        }
        if (index == 1) sb.append("(none visible)\n");
        return sb.toString();
    }

    /** Mirrors the gate in {@code ShowGarboziaActionCards} - that command class can't be invoked directly. */
    private static String showGarboziaMirror(Game game, Player player) {
        if (ActionCardHelper.hidesUnplayedDiscards(game, player) && !player.hasPlanet("garbozia")) {
            return "Blocked: `/cards_ac show_garbozia` would refuse you (not the Garbozia controller, and HIDE_AC_DISCARD is active).\n";
        }
        String text = ShowActionCardsService.getGarboziaDiscardText(game, false);
        return Objects.requireNonNullElse(text, "No Action Cards on Garbozia.") + "\n";
    }

    private static String describeState(Game game, Player player) {
        boolean hidden = ActionCardHelper.hidesUnplayedDiscards(game, player);
        StringBuilder sb = new StringBuilder("**HIDE_AC_DISCARD test state — `")
                .append(game.getName())
                .append("`**\n");
        sb.append("- FoW mode: `").append(game.isFowMode()).append("`\n");
        sb.append("- HIDE_AC_DISCARD option: `")
                .append(game.getFowOption(FOWOption.HIDE_AC_DISCARD))
                .append("`\n");
        sb.append("- you: isGM=`")
                .append(player.isGM())
                .append("`, hasPlanet(garbozia)=`")
                .append(player.hasPlanet("garbozia"))
                .append("`, hasUnlockedBreakthrough(ralnelbt)=`")
                .append(player.hasUnlockedBreakthrough("ralnelbt"))
                .append("`\n");
        sb.append("- effective for you right now: `hidesUnplayedDiscards` = **")
                .append(hidden)
                .append("** (unplayed/forced-discard cards are ")
                .append(hidden ? "hidden from you" : "visible to you")
                .append(")\n\n");

        sb.append(pileLine(
                game,
                player,
                "General discard (status=null)",
                ac -> game.getDiscardACStatus().get(ac) == null));
        sb.append(
                pileLine(game, player, "Purged", ac -> game.getDiscardACStatus().get(ac) == ACStatus.purged));
        sb.append(pileLine(
                game, player, "On Garbozia", ac -> game.getDiscardACStatus().get(ac) == ACStatus.garbozia));
        sb.append(pileLine(
                game, player, "On Data Skimmer", ac -> game.getDiscardACStatus().get(ac) == ACStatus.ralnelbt));
        return sb.toString();
    }

    private static String pileLine(Game game, Player player, String title, Predicate<String> statusFilter) {
        boolean hideUnplayed = ActionCardHelper.hidesUnplayedDiscards(game, player);
        List<String> lines = game.getDiscardActionCards().entrySet().stream()
                .filter(e -> statusFilter.test(e.getKey()))
                .sorted(Map.Entry.comparingByValue())
                .map(e -> {
                    String acID = e.getKey();
                    boolean played = game.getPlayedActionCards().contains(acID);
                    boolean visible = ActionCardHelper.isDiscardVisible(game, hideUnplayed, acID);
                    return "  - `" + Mapper.getActionCard(acID).getName() + "` (" + e.getValue() + ") played=`" + played
                            + "` visible-to-you=`" + visible + "`";
                })
                .toList();
        StringBuilder sb = new StringBuilder("**")
                .append(title)
                .append("** (")
                .append(lines.size())
                .append(")\n");
        if (lines.isEmpty()) sb.append("  - none\n");
        else lines.forEach(l -> sb.append(l).append('\n'));
        return sb.append('\n').toString();
    }
}

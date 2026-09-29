package ti4.service.tigl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.Interaction;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.TIGLHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class TiglSetupService {

    public static final String LADDER_BUTTON_PREFIX = "tiglSetLadder_";
    public static final String CASUAL = "casual";
    public static final String STANDARD = "standard";
    public static final String FRACTURED = "fractured";

    private static final String USE_OLD_POK = "useOldPok";
    private static final int LEAGUE_PLAYER_COUNT = 6;
    private static final int FRACTURED_MAX_PLAYER_COUNT = 8;
    private static final int FRACTURED_MIN_VICTORY_POINTS = 10;
    private static final int STANDARD_SECRET_COUNT = 3;
    private static final int STANDARD_STAGE_COUNT = 5;
    private static final Set<Integer> STANDARD_VICTORY_POINTS = Set.of(10, 12, 14);

    public static List<Button> ladderButtons() {
        return List.of(
                Buttons.green(LADDER_BUTTON_PREFIX + STANDARD, "TIGL - Standard Ladder", MiscEmojis.TIGL),
                Buttons.blue(LADDER_BUTTON_PREFIX + FRACTURED, "TIGL - Fractured Ladder", MiscEmojis.TIGL),
                Buttons.gray(LADDER_BUTTON_PREFIX + CASUAL, "Remove TIGL"));
    }

    public static void postLadderPrompt(Game game, MessageChannel channel) {
        StringBuilder sb = new StringBuilder();
        if (game.isCompetitiveTIGLGame()) {
            sb.append("Marked automatically because `tigl` is in the game name.");
            sb.append(" Confirm which ladder this game belongs to, or remove the TIGL flag.\n");
        } else {
            sb.append("# ").append(MiscEmojis.TIGL).append("Is this a TIGL game?\n");
            sb.append("Choose the ladder it belongs to, or leave it as a casual game.\n");
        }

        if (game.hasTiglIncompatibleMode()) {
            sb.append("> This game uses Alliance or Community mode, so it cannot be a TIGL game on either ladder.\n");
        } else if (game.hasStandardLadderIncompatibleMode()) {
            sb.append("> This game uses modes that the Standard Ladder does not allow,")
                    .append(" so only the **Fractured** ladder applies.\n");
        }
        sb.append("> Not seated in this game? Use the `/tigl enable` command instead.");

        MessageHelper.sendMessageToChannelWithButtons(channel, sb.toString(), ladderButtons());
    }

    public static boolean mayChangeLadder(Game game, boolean isBothelper) {
        return game.getRound() <= 1 || isBothelper;
    }

    public static boolean mayChangeLadder(Game game, Interaction interaction) {
        return mayChangeLadder(game, CommandHelper.hasRole(interaction, JdaService.bothelperRoles));
    }

    public static String ladderLockedMessage() {
        return "Only a Bothelper can change the TIGL status once the game has started. Ask a Bothelper if this game"
                + " is marked incorrectly.";
    }

    public record LadderChoice(boolean accepted, String message) {}

    public static LadderChoice applyLadderChoice(Game game, String choice) {
        if (CASUAL.equals(choice)) {
            game.setCompetitiveTIGLGame(false);
            return new LadderChoice(
                    true, "The TIGL flag has been removed. This game will not be reported to the league.");
        }

        if (game.hasTiglIncompatibleMode()) {
            return new LadderChoice(
                    false,
                    "TIGL games cannot be mixed with Alliance or Community mode. Choose **Remove TIGL**, or turn"
                            + " those modes off first.");
        }

        boolean fractured = FRACTURED.equals(choice);
        if (!game.canBeCompetitiveTIGLGame(fractured)) {
            return new LadderChoice(
                    false,
                    "TIGL games in the Standard Ladder cannot be mixed with other game modes."
                            + " Choose **TIGL - Fractured Ladder**, or turn those modes off first.");
        }
        TIGLHelper.initializeTIGLGame(game, fractured);

        StringBuilder sb = new StringBuilder();
        sb.append("This game is marked as **TIGL - ")
                .append(fractured ? "Fractured" : "Standard")
                .append(" Ladder**.");
        appendViolations(sb, game, fractured);
        return new LadderChoice(true, sb.toString());
    }

    public static void enforceLadderRules(Game game, MessageChannel channel) {
        if (!mayChangeLadder(game, false) || notYetCheckable(game)) {
            return;
        }

        boolean fractured = TIGLHelper.isFracturedTIGLGame(game);
        List<String> violations = validateLadder(game, fractured);
        if (violations.isEmpty()) {
            TIGLHelper.initializeRanksAsync(game, channel);
            MessageHelper.sendMessageToChannel(
                    channel, MiscEmojis.TIGL + "TIGL " + ladderName(fractured) + " Ladder successfully enabled.");
            return;
        }
        remediate(game, fractured, violations);
    }

    public static void recheckLadder(Game game) {
        if (notYetCheckable(game)) {
            return;
        }

        boolean fractured = TIGLHelper.isFracturedTIGLGame(game);
        List<String> violations = validateLadder(game, fractured);
        if (violations.isEmpty()) {
            return;
        }
        remediate(game, fractured, violations);
    }

    private static boolean notYetCheckable(Game game) {
        return !game.isCompetitiveTIGLGame() || game.getRealPlayers().isEmpty();
    }

    private static void remediate(Game game, boolean fractured, List<String> violations) {
        boolean movedToFractured = !fractured
                && !game.hasTiglIncompatibleMode()
                && validateLadder(game, true).isEmpty();
        if (movedToFractured) {
            TIGLHelper.initializeTIGLGame(game, true);
        } else {
            game.setCompetitiveTIGLGame(false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(game.getPing()).append('\n');
        sb.append("### ⚠️ TIGL incompatibility detected\n");
        sb.append(
                        movedToFractured
                                ? "This game does not meet the Standard Ladder ruleset, so it has been moved to the"
                                        + " **Fractured Ladder**."
                                : "This game does not meet the TIGL ruleset, so **TIGL has been removed** from it.")
                .append("\n\nWhat did not match:");
        for (String violation : violations) {
            sb.append("\n- ").append(violation);
        }
        sb.append("\n\nPing a Bothelper if you believe this is a mistake.");
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb.toString());
    }

    private static String ladderName(boolean fractured) {
        return fractured ? "Fractured" : "Standard";
    }

    private static void appendViolations(StringBuilder sb, Game game, boolean fractured) {
        if (game.getRealPlayers().isEmpty()) {
            sb.append("\n-# The ruleset is checked when secret objectives are dealt, once everyone has a faction.");
            return;
        }
        List<String> violations = validateLadder(game, fractured);
        if (violations.isEmpty()) {
            return;
        }
        sb.append("\n\n⚠️ **").append(fractured ? "Fractured" : "Standard").append(" Ladder rules check**\n");
        sb.append("This game does not match the ruleset:");
        for (String violation : violations) {
            sb.append("\n- ").append(violation);
        }
    }

    public static boolean looksLikeTiglGame(Game game) {
        return looksLikeTiglGame(game.getCustomName());
    }

    public static boolean looksLikeTiglGame(String gameName) {
        return StringUtils.containsIgnoreCase(gameName, "tigl");
    }

    public static List<String> validateStandardLadder(Game game) {
        return validateLadder(game, false);
    }

    public static List<String> validateLadder(Game game, boolean fractured) {
        return fractured ? validateFracturedLadder(game) : validateStandardLadderRules(game);
    }

    private static List<String> validateFracturedLadder(Game game) {
        List<String> violations = new ArrayList<>();

        int playerCount = game.getRealPlayers().size();
        if (playerCount < LEAGUE_PLAYER_COUNT || playerCount > FRACTURED_MAX_PLAYER_COUNT) {
            violations.add(playerCount + " players (Fractured Ladder is " + LEAGUE_PLAYER_COUNT + " to "
                    + FRACTURED_MAX_PLAYER_COUNT + ")");
        }
        if (game.getVp() < FRACTURED_MIN_VICTORY_POINTS) {
            violations.add("Victory points are " + game.getVp() + " (Fractured Ladder is "
                    + FRACTURED_MIN_VICTORY_POINTS + " or more)");
        }

        return violations;
    }

    private static List<String> validateStandardLadderRules(Game game) {
        List<String> violations = new ArrayList<>();

        int playerCount = game.getRealPlayers().size();
        if (playerCount != LEAGUE_PLAYER_COUNT) {
            violations.add(playerCount + " players (Standard Ladder is " + LEAGUE_PLAYER_COUNT + ")");
        }

        if (!game.isProphecyOfKings() || game.isBaseGameMode()) {
            violations.add("Prophecy of Kings is off (Standard Ladder needs both expansions)");
        }
        if (!game.isThundersEdge()) {
            violations.add("Thunder's Edge is off (Standard Ladder needs both expansions)");
        } else if (usesOldProphecyOfKingsComponents(game)) {
            violations.add(
                    "This game uses the old Prophecy of Kings components" + " (Standard Ladder needs both expansions)");
        }
        if (!STANDARD_VICTORY_POINTS.contains(game.getVp())) {
            violations.add("Victory points are " + game.getVp() + " (Standard Ladder is 10, 12 or 14)");
        }
        if (objectiveSetupIsStillIntact(game)) {
            addStageViolation(violations, game.getPublicObjectives1Peekable().size(), 1);
            addStageViolation(violations, game.getPublicObjectives2Peekable().size(), 2);
        }
        if (game.getMaxSOCountPerPlayer() != STANDARD_SECRET_COUNT) {
            violations.add("Secret objectives per player are " + game.getMaxSOCountPerPlayer() + " (Standard Ladder is "
                    + STANDARD_SECRET_COUNT + ")");
        }
        if (StringUtils.isNotBlank(game.getEventDeckID())) {
            violations.add("Galactic Events are enabled (Standard Ladder has no events)");
        }

        if (!game.isNormalGame() && playerCount >= 3 && playerCount <= 8) {
            violations.add("This game uses homebrew, scenario or Galactic Event modes (Standard Ladder allows none)");
        }

        return violations;
    }

    private static boolean usesOldProphecyOfKingsComponents(Game game) {
        return StringUtils.isNotBlank(game.getStoredValue(USE_OLD_POK));
    }

    private static boolean objectiveSetupIsStillIntact(Game game) {
        return game.getRevealedPublicObjectives().keySet().stream().allMatch(Constants.CUSTODIAN::equals);
    }

    private static void addStageViolation(List<String> violations, int actual, int stage) {
        if (actual != STANDARD_STAGE_COUNT) {
            violations.add(
                    actual + " stage " + stage + " objectives (Standard Ladder is " + STANDARD_STAGE_COUNT + ")");
        }
    }
}

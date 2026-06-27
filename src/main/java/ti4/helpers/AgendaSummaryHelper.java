package ti4.helpers;

import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class AgendaSummaryHelper {

    public static String getSummaryOfVotes(Game game, boolean capitalize) {
        return getSummaryOfVotes(game, capitalize, false, false);
    }

    static String getSummaryOfVotes(Game game, boolean capitalize, boolean overwriteFog, boolean redactFactionInfo) {
        Map<String, String> outcomes = game.getCurrentAgendaVotes();
        String agendaName = resolveAgendaName(game.getCurrentAgendaInfo());

        if (outcomes.isEmpty()) {
            return "# _" + agendaName + "_\nNo current riders or votes have been cast yet.";
        }

        StringBuilder summaryBuilder =
                new StringBuilder("# _" + agendaName + "_\nCurrent status of votes and outcomes is: \n");

        for (Entry<String, String> entry : outcomes.entrySet()) {
            String outcome = entry.getKey();
            String outcomeName = AgendaHelper.getAgendaOutcomeName(game, outcome, capitalize);
            StringBuilder outcomeSummaryBuilder = new StringBuilder();
            int totalVotes = 0;

            StringTokenizer voteInfo = new StringTokenizer(entry.getValue(), ";");
            while (voteInfo.hasMoreTokens()) {
                String specificVote = voteInfo.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf('_'));
                String vote = specificVote.substring(specificVote.indexOf('_') + 1);

                if (NumberUtils.isDigits(vote)) {
                    totalVotes += Integer.parseInt(vote);
                }

                if (capitalize) {
                    appendCapitalizedVote(game, overwriteFog, outcomeSummaryBuilder, faction, vote);
                } else {
                    appendPlainVote(outcomeSummaryBuilder, faction, vote);
                }
            }

            String outcomeSummary = outcomeSummaryBuilder.toString();

            if (capitalize) {
                appendCapitalizedOutcome(
                        game, summaryBuilder, outcome, outcomeName, totalVotes, outcomeSummary, redactFactionInfo);
            } else {
                summaryBuilder
                        .append("- ")
                        .append(outcomeName)
                        .append(": Total votes ")
                        .append(totalVotes);
                if (!redactFactionInfo) {
                    summaryBuilder.append(". ").append(outcomeSummary);
                }
                summaryBuilder.append('\n');
            }
        }
        return summaryBuilder.toString();
    }

    private static String resolveAgendaName(String agendaDetails) {
        int underscores = StringUtils.countMatches(agendaDetails, "_");
        if (underscores <= 2) {
            return "Not Currently Tracked";
        }
        if (underscores > 3) {
            return Mapper.getAgendaTitleNoCap(
                    StringUtils.substringAfter(agendaDetails, agendaDetails.split("_")[2] + "_"));
        }
        return Mapper.getAgendaTitleNoCap(agendaDetails.split("_")[3]);
    }

    private static void appendCapitalizedVote(
            Game game, boolean overwriteFog, StringBuilder builder, String faction, String vote) {
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        String factionLabel = FactionEmojis.getFactionIcon(faction).toString();
        if (p2 != null) {
            factionLabel = p2.getFactionEmoji();
        }
        if (game.isFowMode() && !overwriteFog) {
            factionLabel = "Someone";
        }

        builder.append(factionLabel).append("-").append(vote);

        if (!game.isFowMode() && p2 != null) {
            if (NumberUtils.isDigits(vote) && p2.hasTech("dskyrog")) {
                builder.append(" (_Indoctrination Teams_)");
            }
            if (p2.hasAbility("future_sight")
                    && game.getStoredValue("executiveOrder").isEmpty()) {
                builder.append(" (**Future Sight**)");
            }
            if (p2.hasTech("dsatokcr") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, "cruiser", true) < 8) {
                builder.append(" (Mirrorshard DEPLOY)");
            }
            if (p2.hasUnit("kaltrim_mech") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, "mech", true) < 4) {
                builder.append(" (Mech DEPLOY)");
            }
        }
        builder.append(", ");
    }

    private static void appendPlainVote(StringBuilder builder, String faction, String vote) {
        if (NumberUtils.isDigits(vote)) {
            builder.append(faction).append(" voted ").append(vote).append(" votes. ");
        } else {
            builder.append(faction).append(" cast a ").append(vote).append(". ");
        }
    }

    private static void appendCapitalizedOutcome(
            Game game,
            StringBuilder summaryBuilder,
            String outcome,
            String outcomeName,
            int totalVotes,
            String outcomeSummary,
            boolean redactFactionInfo) {
        if (outcomeSummary.length() > 2) {
            outcomeSummary = outcomeSummary.substring(0, outcomeSummary.length() - 2);
        }

        String agendaInfo = game.getCurrentAgendaInfo();

        if (!game.isFowMode() && agendaInfo.contains("Elect Player")) {
            String emoji =
                    FactionEmojis.getFactionIcon(outcomeName.toLowerCase()).toString();
            Player outcomerP = game.getPlayerFromColorOrFaction(outcomeName.toLowerCase());
            if (outcomerP != null) {
                emoji = outcomerP.getFactionEmoji();
            }
            summaryBuilder
                    .append("- ")
                    .append(emoji)
                    .append(' ')
                    .append(outcomeName)
                    .append(": ")
                    .append(totalVotes);
        } else if (!game.isHomebrewSCMode()
                && agendaInfo.contains("Elect Strategy Card")
                && NumberUtils.isDigits(outcome)) {
            int scNumber = Integer.parseInt(outcome);
            summaryBuilder
                    .append("- ")
                    .append(CardEmojis.getSCFrontFromInteger(scNumber))
                    .append(" **")
                    .append(Helper.getSCName(scNumber, game))
                    .append("**: ")
                    .append(totalVotes);
        } else {
            summaryBuilder.append("- ").append(outcomeName).append(": ").append(totalVotes);
        }

        if (!redactFactionInfo) {
            summaryBuilder.append(". (").append(outcomeSummary).append(")");
        }
        summaryBuilder.append('\n');
    }
}

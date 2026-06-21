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
        String agendaDetails = game.getCurrentAgendaInfo();
        String agendaName;
        if (StringUtils.countMatches(agendaDetails, "_") > 2)
            if (StringUtils.countMatches(agendaDetails, "_") > 3) {
                agendaName = Mapper.getAgendaTitleNoCap(
                        StringUtils.substringAfter(agendaDetails, agendaDetails.split("_")[2] + "_"));
            } else {
                agendaName = Mapper.getAgendaTitleNoCap(agendaDetails.split("_")[3]);
            }
        else {
            agendaName = "Not Currently Tracked";
        }

        if (outcomes.isEmpty()) return "# _" + agendaName + "_\nNo current riders or votes have been cast yet.";

        StringBuilder summaryBuilder =
                new StringBuilder("# _" + agendaName + "_\nCurrent status of votes and outcomes is: \n");
        for (Entry<String, String> entry : outcomes.entrySet()) {
            String outcome = entry.getKey();
            int totalVotes = 0;
            StringTokenizer voteInfo = new StringTokenizer(entry.getValue(), ";");
            String outcomeSummary;
            String outcomeName = AgendaHelper.getAgendaOutcomeName(game, outcome, capitalize);
            StringBuilder outcomeSummaryBuilder = new StringBuilder();
            while (voteInfo.hasMoreTokens()) {
                String specificVote = voteInfo.nextToken();
                String faction = specificVote.substring(0, specificVote.indexOf('_'));
                if (capitalize) {
                    Player p2 = game.getPlayerFromColorOrFaction(faction);
                    faction = FactionEmojis.getFactionIcon(faction).toString();
                    if (p2 != null) {
                        faction = p2.getFactionEmoji();
                    }
                    if (game.isFowMode() && !overwriteFog) {
                        faction = "Someone";
                    }
                    String vote = specificVote.substring(specificVote.indexOf('_') + 1);
                    if (NumberUtils.isDigits(vote)) {
                        totalVotes += Integer.parseInt(vote);
                    }
                    outcomeSummaryBuilder.append(faction).append("-").append(vote);
                    if (NumberUtils.isDigits(vote) && !game.isFowMode() && p2.hasTech("dskyrog")) {
                        outcomeSummaryBuilder.append(" (_Indoctrination Teams_)");
                    }
                    if (!game.isFowMode()
                            && p2 != null
                            && p2.hasAbility("future_sight")
                            && game.getStoredValue("executiveOrder").isEmpty()) {
                        outcomeSummaryBuilder.append(" (**Future Sight**)");
                    }
                    if (!game.isFowMode()
                            && p2 != null
                            && p2.hasTech("dsatokcr")
                            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, "cruiser", true) < 8) {
                        outcomeSummaryBuilder.append(" (Mirrorshard DEPLOY)");
                    }
                    if (!game.isFowMode()
                            && p2 != null
                            && p2.hasUnit("kaltrim_mech")
                            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, "mech", true) < 4) {
                        outcomeSummaryBuilder.append(" (Mech DEPLOY)");
                    }
                    outcomeSummaryBuilder.append(", ");
                } else {
                    String vote = specificVote.substring(specificVote.indexOf('_') + 1);
                    if (NumberUtils.isDigits(vote)) {
                        totalVotes += Integer.parseInt(vote);
                        outcomeSummaryBuilder
                                .append(faction)
                                .append(" voted ")
                                .append(vote)
                                .append(" votes. ");
                    } else {
                        outcomeSummaryBuilder
                                .append(faction)
                                .append(" cast a ")
                                .append(vote)
                                .append(". ");
                    }
                }
            }
            outcomeSummary = outcomeSummaryBuilder.toString();
            if (capitalize) {
                if (outcomeSummary.length() > 2) {
                    outcomeSummary = outcomeSummary.substring(0, outcomeSummary.length() - 2);
                }

                if (!game.isFowMode() && game.getCurrentAgendaInfo().contains("Elect Player")) {
                    String emoji = FactionEmojis.getFactionIcon(outcomeName.toLowerCase())
                            .toString();
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
                    if (!redactFactionInfo) {
                        summaryBuilder.append(". (").append(outcomeSummary).append(")\n");
                    } else {
                        summaryBuilder.append('\n');
                    }

                } else if (!game.isHomebrewSCMode()
                        && game.getCurrentAgendaInfo().contains("Elect Strategy Card")
                        && NumberUtils.isDigits(outcome)) {
                    int scNumber = Integer.parseInt(outcome);
                    summaryBuilder
                            .append("- ")
                            .append(CardEmojis.getSCFrontFromInteger(scNumber))
                            .append(" **")
                            .append(Helper.getSCName(scNumber, game))
                            .append("**: ")
                            .append(totalVotes);
                    if (!redactFactionInfo) {
                        summaryBuilder.append(". (").append(outcomeSummary).append(")\n");
                    } else {
                        summaryBuilder.append('\n');
                    }
                } else {
                    summaryBuilder.append("- ").append(outcomeName).append(": ").append(totalVotes);
                    if (!redactFactionInfo) {
                        summaryBuilder.append(". (").append(outcomeSummary).append(")\n");
                    } else {
                        summaryBuilder.append('\n');
                    }
                }
            } else {
                summaryBuilder
                        .append("- ")
                        .append(outcomeName)
                        .append(": Total votes ")
                        .append(totalVotes);
                if (!redactFactionInfo) {
                    summaryBuilder.append(". ").append(outcomeSummary).append('\n');
                } else {
                    summaryBuilder.append('\n');
                }
            }
        }
        return summaryBuilder.toString();
    }
}

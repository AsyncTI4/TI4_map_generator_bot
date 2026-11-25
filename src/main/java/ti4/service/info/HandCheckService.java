package ti4.service.info;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.AgendaWhensAftersHelper;
import ti4.model.ActionCardModel;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.service.actioncard.SabotageService;

@UtilityClass
public class HandCheckService {

    public static final String SABOTAGE = "sabotage";
    public static final String WHENS = "whens";
    public static final String DEADLY = "deadly";
    public static final String CONFUSING = "confusing";
    public static final String SECRET = "secret";
    public static final String ACS = "acs";
    public static final String PNS = "pns";
    public static final String RELICS = "relics";
    public static final String LEADERS = "leaders";

    public static List<String> reports(Game game, String option) {
        return switch (option) {
            case SABOTAGE -> List.of(sabotageReport(game));
            case DEADLY -> List.of(whoHas(game, "deadly_plot"), whoHas(game, "bribery"));
            case CONFUSING -> List.of(whoHas(game, "confusing"), whoHas(game, "confounding"));
            case WHENS -> whensAndAfters(game);
            case SECRET -> List.of(unscoredSecrets(game));
            case ACS -> List.of(perPlayer(game, HandCheckService::actionCards));
            case PNS -> List.of(perPlayer(game, HandCheckService::promissoryNotes));
            case RELICS -> List.of(perPlayer(game, HandCheckService::relics));
            case LEADERS -> List.of(perPlayer(game, player -> leaders(game, player)));
            default -> List.of();
        };
    }

    private static String sabotageReport(Game game) {
        StringBuilder sb = new StringBuilder(whoHas(game, "sabo"));
        for (Player player : game.getRealPlayers()) {
            if (SabotageService.couldUseWatcherMech(player, game)) {
                sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(" has Watcher mechs\n");
            }
            if (SabotageService.couldUseInstinctTraining(player)) {
                sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(" has Instinct Training\n");
            }
        }
        return sb.toString();
    }

    private static String whoHas(Game game, String acIdPrefix) {
        StringBuilder sb = new StringBuilder("Following players have **");
        sb.append(acIdPrefix).append("** in hand:\n");
        for (Player player : game.getRealPlayers()) {
            if (player.getActionCards().keySet().stream().anyMatch(ac -> ac.startsWith(acIdPrefix))) {
                sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append('\n');
            }
        }
        return sb.toString();
    }

    private static List<String> whensAndAfters(Game game) {
        StringBuilder whens = new StringBuilder("Following players have \"when\"s in hand:\n");
        StringBuilder afters = new StringBuilder("Following players have \"after\"s in hand:\n");
        for (Player player : game.getRealPlayers()) {
            appendNamedList(whens, player, AgendaWhensAftersHelper.getPossibleWhenNames(player));
            appendNamedList(afters, player, AgendaWhensAftersHelper.getPossibleAfterNames(player));
        }
        return List.of(whens.toString(), afters.toString());
    }

    private static void appendNamedList(StringBuilder sb, Player player, List<String> names) {
        if (names.isEmpty()) return;
        sb.append("> ")
                .append(player.getRepresentationUnfoggedNoPing())
                .append(": ")
                .append(String.join(", ", names))
                .append('\n');
    }

    private static String unscoredSecrets(Game game) {
        StringBuilder sb = new StringBuilder();
        for (Player player : game.getRealPlayers()) {
            String allSecrets = SecretObjectiveInfoService.getSecretObjectiveCardInfo(game, player);
            sb.append("__")
                    .append(player.getRepresentationUnfoggedNoPing())
                    .append(" Unscored")
                    .append(StringUtils.substringAfter(allSecrets, "Unscored"))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String perPlayer(Game game, Function<Player, String> lines) {
        StringBuilder sb = new StringBuilder();
        for (Player player : game.getRealPlayers()) {
            sb.append("__").append(player.getRepresentationUnfoggedNoPing()).append("__\n");
            sb.append(lines.apply(player));
        }
        return sb.toString();
    }

    private static String actionCards(Player player) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : player.getActionCards().entrySet()) {
            ActionCardModel card = Mapper.getActionCard(entry.getKey());
            String name = card == null ? entry.getKey() : card.getNameRepresentation();
            sb.append("> ").append(name).append(" (").append(entry.getValue()).append(")\n");
        }
        return sb.toString();
    }

    private static String promissoryNotes(Player player) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : player.getPromissoryNotes().entrySet()) {
            PromissoryNoteModel note = Mapper.getPromissoryNote(entry.getKey());
            String name = note == null ? entry.getKey() : note.getNameRepresentation();
            sb.append("> ").append(name).append(" (").append(entry.getValue()).append(")\n");
        }
        return sb.toString();
    }

    private static String relics(Player player) {
        if (player.getRelics().isEmpty()) return "> None\n";
        StringBuilder sb = new StringBuilder();
        for (String relicId : player.getRelics()) {
            RelicModel relic = Mapper.getRelic(relicId);
            sb.append("> ").append(relic == null ? relicId : relic.getNameRepresentation());
            if (player.getExhaustedRelics().contains(relicId)) {
                sb.append(" (exhausted)");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String leaders(Game game, Player player) {
        if (player.getLeaders().isEmpty()) return "> None\n";
        StringBuilder sb = new StringBuilder();
        List<Leader> sorted = player.getLeaders().stream()
                .sorted(Comparator.comparing(Leader::getType, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (Leader leader : sorted) {
            sb.append("> ")
                    .append(StringUtils.capitalize(Objects.toString(leader.getType(), "leader")))
                    .append(": _")
                    .append(leader.getName(game))
                    .append("_")
                    .append(leaderStatus(leader))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String leaderStatus(Leader leader) {
        StringBuilder status = new StringBuilder();
        if (leader.isLocked()) status.append(" (locked)");
        if (leader.isExhausted()) status.append(" (exhausted)");
        if (leader.isActive()) status.append(" (active)");
        if (leader.getTgCount() > 0)
            status.append(" (").append(leader.getTgCount()).append(" TG)");
        return status.toString();
    }
}

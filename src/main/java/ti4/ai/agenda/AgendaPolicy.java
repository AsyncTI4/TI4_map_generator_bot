package ti4.ai.agenda;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.image.Mapper;
import ti4.model.AgendaModel;

@UtilityClass
public class AgendaPolicy {

    private static final String TIE_PREFIX = "resolveAgendaVote_outcomeTie*";
    private static final String DRIVE_THE_DEBATE = "dtd";
    private static final Map<String, String> REQUIRED_TRAIT = Map.of(
            "core_mining", "hazardous",
            "rt_warfare", "hazardous",
            "terraforming_initiative", "hazardous",
            "demilitarized_zone", "cultural",
            "holy_planet_of_ixth", "cultural",
            "senate_sanctuary", "cultural",
            "rt_biotic", "industrial",
            "rt_cybernetic", "industrial",
            "rt_propulsion", "industrial");
    private static final double DRIVE_THE_DEBATE_SCORE = 3;
    private static final String TIED_PLANETS_PREFIX = "tiedPlanets_" + TIE_PREFIX;
    private static final Set<String> GOOD_FOR_ELECTED_PLAYER = Set.of(
            "secret",
            "committee",
            "arbiter",
            "minister_commerce",
            "minister_exploration",
            "minister_industry",
            "minister_peace",
            "minister_policy",
            "minister_sciences",
            "minister_war",
            "minister_antiquities",
            "prophecy",
            "shard_of_the_throne",
            "crown_of_emphidia",
            "crown_of_thalnos",
            "censure",
            "grant_reallocation",
            "warrant");
    private static final Set<String> BAD_FOR_ELECTED_PLAYER = Set.of("execution");
    private static final Set<String> GOOD_FOR_PLANET_OWNER = Set.of(
            "holy_planet_of_ixth",
            "senate_sanctuary",
            "terraforming_initiative",
            "rt_biotic",
            "rt_cybernetic",
            "rt_propulsion",
            "rt_warfare");
    private static final Set<String> BAD_FOR_PLANET_OWNER =
            Set.of("core_mining", "demilitarized_zone", "disarmament", "redistribution");
    private static final String ELECT_PLAYER = "elect player";

    public static boolean isTieOutcome(PromptButton button) {
        return button.handlerId().startsWith(TIE_PREFIX);
    }

    public static boolean isTiedPlanetOwner(PromptButton button) {
        return button.handlerId().startsWith(TIED_PLANETS_PREFIX);
    }

    public static Optional<PromptButton> chooseOutcome(Game game, Player speaker, List<PromptButton> outcomes) {
        if (outcomes.isEmpty()) return Optional.empty();
        String agenda = currentAgenda(game);
        return outcomes.stream()
                .max(Comparator.comparingDouble(button -> score(game, speaker, agenda, outcomeOf(button))));
    }

    public static Optional<PromptButton> choosePlanetOwner(Game game, Player speaker, List<PromptButton> owners) {
        String agenda = currentAgenda(game);
        if (GOOD_FOR_PLANET_OWNER.contains(agenda)) {
            Optional<PromptButton> self = owners.stream()
                    .filter(button -> speaker == ownerOf(game, button))
                    .findFirst();
            if (self.isPresent()) return self;
        }
        if (BAD_FOR_PLANET_OWNER.contains(agenda)) {
            return owners.stream()
                    .filter(button -> speaker != ownerOf(game, button))
                    .max(Comparator.comparingInt(button -> victoryPoints(ownerOf(game, button))));
        }
        return owners.stream().max(Comparator.comparingInt(button -> planetCount(ownerOf(game, button))));
    }

    public static double predictedElectionScore(Game game, Player seat, @Nullable Player silenced) {
        String agenda = currentAgenda(game);
        if (!electsPlayer(agenda)) return 0;
        Optional<Player> elected =
                BAD_FOR_ELECTED_PLAYER.contains(agenda) ? pointsLeader(game, seat) : mostVotes(game, silenced);
        return elected.map(player -> score(game, seat, agenda, player.getFaction()))
                .orElse(0.0);
    }

    private static boolean electsPlayer(String agenda) {
        AgendaModel model = agenda.isBlank() ? null : Mapper.getAgenda(agenda);
        return model != null
                && model.getTarget() != null
                && model.getTarget().toLowerCase().contains(ELECT_PLAYER);
    }

    private static Optional<Player> pointsLeader(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .max(Comparator.comparingInt(Player::getTotalVictoryPoints)
                        .thenComparing((Player player) -> player == seat));
    }

    private static Optional<Player> mostVotes(Game game, @Nullable Player silenced) {
        Map<String, Integer> votes = AgendaHelper.getAgendaStartVoteCounts(game);
        return game.getRealPlayers().stream()
                .filter(player -> player != silenced && votes.getOrDefault(player.getColor(), 0) > 0)
                .max(Comparator.comparingInt((Player player) -> votes.getOrDefault(player.getColor(), 0)));
    }

    static String currentAgenda(Game game) {
        String info = game.getCurrentAgendaInfo();
        if (info == null) return "";
        String[] parts = StringUtils.split(info, "_", 4);
        return parts.length == 4 ? parts[3] : "";
    }

    static double score(Game game, Player speaker, String agenda, String outcome) {
        double score = outcomeScore(game, speaker, agenda, outcome);
        if (speaker.getSecretsUnscored().containsKey(DRIVE_THE_DEBATE) && electsSelf(game, speaker, outcome)) {
            return Math.max(score, DRIVE_THE_DEBATE_SCORE);
        }
        return score;
    }

    static boolean hasRequiredTrait(String agenda, Planet planet) {
        String trait = REQUIRED_TRAIT.get(agenda);
        return trait == null || planet.getPlanetTypes().contains(trait);
    }

    static boolean electsSelf(Game game, Player speaker, String outcome) {
        return game.getPlayerFromColorOrFaction(outcome) == speaker
                || speaker.getPlanets().contains(outcome);
    }

    private static double outcomeScore(Game game, Player speaker, String agenda, String outcome) {
        Player elected = game.getPlayerFromColorOrFaction(outcome);
        if (elected != null && game.getRealPlayers().contains(elected)) {
            if (GOOD_FOR_ELECTED_PLAYER.contains(agenda)) return elected == speaker ? 2 : -victoryPoints(elected);
            if (BAD_FOR_ELECTED_PLAYER.contains(agenda)) return elected == speaker ? -10 : victoryPoints(elected);
            return 0;
        }
        Planet planet = game.getPlanetsInfo().get(outcome);
        if (planet != null) {
            boolean ours = speaker.getPlanets().contains(outcome);
            double worth = BoardView.planetValue(planet);
            if (!hasRequiredTrait(agenda, planet)) return 0;
            if (GOOD_FOR_PLANET_OWNER.contains(agenda)) return ours ? 1 + worth : -worth;
            if (BAD_FOR_PLANET_OWNER.contains(agenda)) return ours ? -worth : 1 + worth;
            return 0;
        }
        if ("seed_empire".equals(agenda)) return seedOfAnEmpire(game, speaker, outcome);
        return 0;
    }

    private static double seedOfAnEmpire(Game game, Player speaker, String outcome) {
        int mine = speaker.getTotalVictoryPoints();
        int most = game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .max()
                .orElse(mine);
        int fewest = game.getRealPlayers().stream()
                .mapToInt(Player::getTotalVictoryPoints)
                .min()
                .orElse(mine);
        if ("for".equalsIgnoreCase(outcome)) return mine == most ? 2 : -1;
        if ("against".equalsIgnoreCase(outcome)) return mine == fewest ? 2 : 1;
        return 0;
    }

    private static String outcomeOf(PromptButton button) {
        String outcome = StringUtils.removeStart(button.handlerId(), TIE_PREFIX);
        return StringUtils.stripStart(outcome, "_ ");
    }

    @Nullable
    private static Player ownerOf(Game game, PromptButton tiedPlanetsButton) {
        return game.getPlayerFromColorOrFaction(StringUtils.substringAfterLast(tiedPlanetsButton.handlerId(), "_"));
    }

    private static int victoryPoints(@Nullable Player player) {
        return player == null ? -1 : player.getTotalVictoryPoints();
    }

    private static int planetCount(@Nullable Player player) {
        return player == null ? -1 : player.getPlanets().size();
    }
}

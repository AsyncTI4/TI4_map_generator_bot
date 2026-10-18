package ti4.service.game;

import java.awt.Color;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.model.AgendaModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.StrategyCardSetModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

@UtilityClass
public class GameSummaryService {

    public static final String NONE = "None";
    public static final String MISSING = "⚠️ missing";

    private static final Color EMBED_COLOR = new Color(0x4B5D78);
    private static final Set<String> EXPANSION_MODES =
            Set.of("Base Game", "Prophecy of Kings", "Thunder's Edge", "Thunder's Edge Demo", "Twilight's Fall");
    private static final Set<String> HOMEBREW_MODES = Set.of(
            "Absol",
            "Discordant Stars",
            "Twilight Discordant Stars",
            "Blue Reverie",
            "Uncharted Space",
            "Milty Mod",
            "Homebrew Strategy Cards");
    private static final Set<String> SCENARIO_MODES = Set.of("Ordinian", "Liberation", "Erwan's Gambit", "Alliance");
    private static final Set<String> MODES_SHOWN_AS_VARIANT =
            Set.of("Fog of War", "Light Fog", "Franken", "Homebrew", "Normal");

    public record ModeBreakdown(
            List<String> expansions, List<String> homebrew, List<String> scenarios, List<String> other) {

        public static ModeBreakdown of(Game game) {
            Set<String> modes = GameModeService.getModes(game);
            return new ModeBreakdown(
                    sortedMatching(modes, EXPANSION_MODES::contains),
                    sortedMatching(modes, HOMEBREW_MODES::contains),
                    sortedMatching(modes, SCENARIO_MODES::contains),
                    sortedMatching(modes, GameSummaryService::isOtherMode));
        }
    }

    private static boolean isOtherMode(String mode) {
        return !EXPANSION_MODES.contains(mode)
                && !HOMEBREW_MODES.contains(mode)
                && !SCENARIO_MODES.contains(mode)
                && !MODES_SHOWN_AS_VARIANT.contains(mode);
    }

    private static List<String> sortedMatching(Collection<String> modes, Predicate<String> filter) {
        return modes.stream().filter(filter).sorted().toList();
    }

    public static String displayName(Game game) {
        String customName = game.getCustomName();
        return StringUtils.isBlank(customName) ? game.getName() : game.getName() + " (" + customName + ")";
    }

    public static EmbedBuilder overview(Game game, String variant) {
        EmbedBuilder eb = baseEmbed(displayName(game));
        eb.setDescription("**" + variant + "** · " + (game.isHasEnded() ? "ended" : "in progress"));
        inline(eb, "Owner", game.getOwnerName());
        inline(
                eb,
                "Created",
                game.getCreationDateTime() > 0
                        ? Helper.getDateRepresentation(game.getCreationDateTime())
                        : game.getCreationDate());
        inline(eb, "Ended", game.isHasEnded() ? Helper.getDateRepresentation(game.getEndedDate()) : "No");
        inline(eb, "Round", String.valueOf(game.getRound()));
        inline(eb, "Phase", game.getPhaseOfGame());
        inline(eb, "VP goal", String.valueOf(game.getVp()));
        inline(eb, "Secret objectives", String.valueOf(game.getMaxSOCountPerPlayer()));
        inline(eb, "Players", String.valueOf(game.getRealAndEliminatedPlayers().size()));
        inline(eb, "Map template", present(game.getMapTemplateID()));
        inline(eb, "Tiles", String.valueOf(game.getTileMap().size()));
        inline(eb, "Strategy cards", strategyCardSetName(game));
        inline(eb, "SCs per player", String.valueOf(game.getStrategyCardsPerPlayer()));
        inline(eb, "Last activity", Helper.getDateRepresentation(game.getLastModifiedDate()));
        Player speaker = game.getSpeaker();
        inline(eb, "Speaker", speaker == null ? NONE : speaker.getRepresentationNoPing());
        if (!game.isHasEnded()) {
            Player active = game.getActivePlayer();
            inline(eb, "Active player", active == null ? NONE : active.getRepresentationNoPing());
        }
        if (game.getSpinMode() != null && !"OFF".equalsIgnoreCase(game.getSpinMode())) {
            inline(eb, "Spin mode", game.getSpinMode());
        }
        if (game.hasWinner()) {
            eb.addField(
                    "Winners",
                    fieldValue(game.getWinners().stream()
                            .map(Player::getRepresentationNoPing)
                            .collect(Collectors.joining(", "))),
                    false);
        }
        return eb;
    }

    public static String strategyCardSetName(Game game) {
        StrategyCardSetModel set = game.getStrategyCardSet();
        return set == null || StringUtils.isBlank(set.getName()) ? present(game.getScSetID()) : set.getName();
    }

    public static MessageEmbed contentEmbed(Game game) {
        ModeBreakdown modes = ModeBreakdown.of(game);
        EmbedBuilder eb = baseEmbed("Content");
        eb.addField("Expansions", joined(modes.expansions()), true);
        eb.addField("Homebrew", joined(modes.homebrew()), true);
        eb.addField("Scenarios", joined(modes.scenarios()), true);
        eb.addField("Other modes & events", joined(modes.other()), false);
        return eb.build();
    }

    public static EmbedBuilder progress(Game game, boolean listObjectiveNames) {
        EmbedBuilder eb = baseEmbed("Progress");
        Map<Integer, List<String>> revealedByStage = game.getRevealedPublicObjectives().keySet().stream()
                .collect(Collectors.groupingBy(GameSummaryService::objectiveStage));
        inline(
                eb,
                "Stage 1 objectives",
                CardEmojis.Public1 + " "
                        + objectiveField(
                                revealedByStage.getOrDefault(1, List.of()),
                                game.getPublicObjectives1Peekable(),
                                listObjectiveNames));
        inline(
                eb,
                "Stage 2 objectives",
                CardEmojis.Public2 + " "
                        + objectiveField(
                                revealedByStage.getOrDefault(2, List.of()),
                                game.getPublicObjectives2Peekable(),
                                listObjectiveNames));
        List<String> other = revealedByStage.getOrDefault(0, List.of());
        inline(
                eb,
                "Other objectives",
                listObjectiveNames && !other.isEmpty()
                        ? other.stream().map(GameSummaryService::objectiveName).collect(Collectors.joining("\n"))
                        : other.size() + " revealed\n(public secrets, custom)");
        List<String> laws = game.getLaws().keySet().stream()
                .map(GameSummaryService::agendaName)
                .toList();
        addChunkedField(eb, "Laws in play", laws);
        return eb;
    }

    private static String objectiveField(List<String> revealed, List<String> staged, boolean listNames) {
        if (!listNames || revealed.isEmpty()) {
            return revealed.size() + " revealed\n" + staged.size() + " staged";
        }
        return revealed.stream().map(GameSummaryService::objectiveName).collect(Collectors.joining("\n")) + "\n*"
                + staged.size() + " staged*";
    }

    private static int objectiveStage(String objectiveId) {
        PublicObjectiveModel objective = Mapper.getPublicObjective(objectiveId);
        if (objective == null || objective.getPoints() == null) {
            return 0;
        }
        return objective.getPoints() == 1 || objective.getPoints() == 2 ? objective.getPoints() : 0;
    }

    private static String objectiveName(String objectiveId) {
        PublicObjectiveModel objective = Mapper.getPublicObjective(objectiveId);
        return objective == null || StringUtils.isBlank(objective.getName()) ? objectiveId : objective.getName();
    }

    public static String agendaName(String agendaId) {
        AgendaModel agenda = Mapper.getAgenda(agendaId);
        return agenda == null || StringUtils.isBlank(agenda.getName()) ? agendaId : agenda.getName();
    }

    public static MessageEmbed decksEmbed(Game game) {
        EmbedBuilder eb = baseEmbed("Decks");
        boolean setUp = game.getActionCards() != null;
        if (!setUp) {
            eb.setDescription("Decks are not set up yet; card counts appear once they are.");
        }
        inline(
                eb,
                "Action cards",
                deckField(
                        CardEmojis.getACEmoji(game),
                        game.getAcDeckID(),
                        setUp,
                        game::getActionCardDeckSize,
                        game::getActionCardFullDeckSize));
        inline(
                eb,
                "Secret objectives",
                deckField(
                        CardEmojis.SecretObjective,
                        game.getSoDeckID(),
                        setUp,
                        game::getSecretObjectiveDeckSize,
                        game::getSecretObjectiveFullDeckSize));
        inline(
                eb,
                "Agendas",
                deckField(
                        CardEmojis.Agenda,
                        game.getAgendaDeckID(),
                        setUp,
                        game::getAgendaDeckSize,
                        game::getAgendaFullDeckSize));
        inline(
                eb,
                "Stage 1",
                deckField(
                        CardEmojis.Public1,
                        game.getStage1PublicDeckID(),
                        setUp,
                        game::getPublicObjectives1DeckSize,
                        game::getPublicObjectives1FullDeckSize));
        inline(
                eb,
                "Stage 2",
                deckField(
                        CardEmojis.Public2,
                        game.getStage2PublicDeckID(),
                        setUp,
                        game::getPublicObjectives2DeckSize,
                        game::getPublicObjectives2FullDeckSize));
        inline(
                eb,
                "Relics",
                deckField(
                        CardEmojis.RelicCard,
                        game.getRelicDeckID(),
                        setUp,
                        game::getRelicDeckSize,
                        game::getRelicFullDeckSize));
        inline(eb, "Explores", exploreField(game, setUp));
        inline(eb, "Technologies", TechEmojis.NonUnitTechSkip + " `" + present(game.getTechnologyDeckID()) + "`");
        String eventDeck = game.getEventDeckID();
        if (StringUtils.isNotBlank(eventDeck) && !"null".equals(eventDeck)) {
            inline(
                    eb,
                    "Events",
                    deckField(CardEmojis.Event, eventDeck, setUp, game::getEventDeckSize, game::getEventFullDeckSize));
        }
        return eb.build();
    }

    private static String deckField(TI4Emoji emoji, String deckId, boolean setUp, IntSupplier left, IntSupplier total) {
        String id = "`" + present(deckId) + "`";
        return emoji + " " + (setUp ? "**" + left.getAsInt() + "** / " + total.getAsInt() + " left\n" + id : id);
    }

    private static String exploreField(Game game, boolean setUp) {
        String id = "`" + present(game.getExplorationDeckID()) + "`";
        if (!setUp) {
            return id;
        }
        return id
                + exploreLine(
                        CardEmojis.IndustrialCard,
                        "Industrial",
                        game.getIndustrialExploreDeckSize(),
                        game.getIndustrialExploreFullDeckSize())
                + exploreLine(
                        CardEmojis.HazardousCard,
                        "Hazardous",
                        game.getHazardousExploreDeckSize(),
                        game.getHazardousExploreFullDeckSize())
                + exploreLine(
                        CardEmojis.CulturalCard,
                        "Cultural",
                        game.getCulturalExploreDeckSize(),
                        game.getCulturalExploreFullDeckSize())
                + exploreLine(
                        CardEmojis.FrontierCard,
                        "Frontier",
                        game.getFrontierExploreDeckSize(),
                        game.getFrontierExploreFullDeckSize());
    }

    private static String exploreLine(TI4Emoji emoji, String name, int left, int total) {
        return "\n" + emoji + " " + name + " **" + left + "** / " + total;
    }

    public static StringBuilder playerLine(Player player) {
        StringBuilder line = new StringBuilder();
        line.append(player.getFactionEmoji())
                .append(' ')
                .append(player.getFaction())
                .append(" · ")
                .append(player.getColor())
                .append(" · ")
                .append(player.getUserName())
                .append(" · ")
                .append(player.getTotalVictoryPoints())
                .append(" VP");
        if (player.getGame() != null && player == player.getGame().getSpeaker()) {
            line.append(" · 🗣 speaker");
        }
        if (!player.getSCs().isEmpty()) {
            line.append(" · SC ")
                    .append(player.getSCs().stream()
                            .sorted()
                            .map(String::valueOf)
                            .collect(Collectors.joining(", ")));
        }
        if (player.isPassed()) {
            line.append(" · passed");
        }
        if (player.isAFK()) {
            line.append(" · AFK");
        }
        if (player.isEliminated()) {
            line.append(" · eliminated");
        }
        return line;
    }

    public static String mention(TextChannel channel) {
        return channel == null ? MISSING : channel.getAsMention();
    }

    public static void addChunkedField(EmbedBuilder eb, String name, List<String> lines) {
        if (lines.isEmpty()) {
            eb.addField(name, NONE, false);
            return;
        }
        StringBuilder chunk = new StringBuilder();
        String fieldName = name;
        for (String line : lines) {
            if (!chunk.isEmpty() && chunk.length() + line.length() + 1 > MessageEmbed.VALUE_MAX_LENGTH) {
                eb.addField(fieldName, chunk.toString(), false);
                chunk.setLength(0);
                fieldName = name + " (cont.)";
            }
            if (!chunk.isEmpty()) {
                chunk.append('\n');
            }
            chunk.append(fieldValue(line));
        }
        eb.addField(fieldName, chunk.toString(), false);
    }

    public static EmbedBuilder baseEmbed(String title) {
        return new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle(StringUtils.abbreviate(title, MessageEmbed.TITLE_MAX_LENGTH));
    }

    public static void inline(EmbedBuilder eb, String name, String value) {
        eb.addField(name, fieldValue(value), true);
    }

    public static String joined(List<String> values) {
        return values.isEmpty() ? NONE : fieldValue(String.join(", ", values));
    }

    public static String fieldValue(String value) {
        return StringUtils.isBlank(value) ? NONE : StringUtils.abbreviate(value, MessageEmbed.VALUE_MAX_LENGTH);
    }

    public static String present(String value) {
        return StringUtils.isBlank(value) || "null".equals(value) ? NONE : value;
    }

    public static String autoPing(Game game) {
        int hours = game.getAutoPingSpacer();
        return hours == 0 ? "Off" : "every " + hours + " h";
    }

    public static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}

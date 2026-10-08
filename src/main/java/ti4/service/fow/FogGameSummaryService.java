package ti4.service.fow;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.image.BoardPosition;
import ti4.image.CompactOverviewGenerator;
import ti4.image.GalaxyNames;
import ti4.image.MapRenderPipeline;
import ti4.image.MapSegment;
import ti4.message.MessageHelper;
import ti4.service.game.GameModeService;
import ti4.service.option.FOWOptionService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.option.FOWOptionService.FOWOptionCategory;

@UtilityClass
public class FogGameSummaryService {

    public static final String SETTINGS_LOG_CHANNEL = "fow-game-settings-log";

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
    private static final Color EMBED_COLOR = new Color(0x4B5D78);
    private static final String NONE = "None";
    private static final String MISSING = "⚠️ missing";

    public record ModeBreakdown(
            List<String> expansions, List<String> homebrew, List<String> scenarios, List<String> other) {

        public static ModeBreakdown of(Game game) {
            Set<String> modes = GameModeService.getModes(game);
            return new ModeBreakdown(
                    sortedMatching(modes, EXPANSION_MODES::contains),
                    sortedMatching(modes, HOMEBREW_MODES::contains),
                    sortedMatching(modes, SCENARIO_MODES::contains),
                    sortedMatching(modes, FogGameSummaryService::isOtherMode));
        }
    }

    public static String fogVariant(Game game) {
        String variant = baseFogVariant(game);
        return game.isFrankenGame() ? "Franken " + variant : variant;
    }

    public static List<FOWOption> enabledOptions(Game game) {
        return Arrays.stream(FOWOption.values())
                .filter(option -> FOWOptionService.isEnabled(game, option))
                .toList();
    }

    public static List<MessageEmbed> buildEmbeds(Game game, boolean includeChannels) {
        return buildEmbeds(game, includeChannels, game.getPlayersWithGMRole());
    }

    public static List<MessageEmbed> buildEmbeds(Game game, boolean includeChannels, List<Player> gameMasters) {
        List<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(overviewEmbed(game));
        embeds.add(galaxiesEmbed(game));
        embeds.add(contentEmbed(game));
        embeds.add(fogOptionsEmbed(game));
        embeds.add(peopleEmbed(game, includeChannels, gameMasters));
        if (includeChannels) {
            embeds.add(channelsEmbed(game));
        }
        return embeds;
    }

    public static void postSettingsLog(Game game, List<Player> gameMasters) {
        TextChannel channel = settingsLogChannel();
        if (channel == null) {
            return;
        }
        MessageHelper.sendMessageToChannelWithEmbeds(
                channel, "## Fog game ended: " + displayName(game), buildEmbeds(game, false, gameMasters));
        MapRenderPipeline.queueImage(
                game,
                "Fog settings log overview",
                () -> CompactOverviewGenerator.gmOverview(game),
                upload -> MessageHelper.sendFileUploadToChannel(channel, upload));
        MapRenderPipeline.queueUnfoggedWithoutWebsiteUpload(
                game, DisplayType.all, upload -> MessageHelper.sendFileUploadToChannel(channel, upload));
    }

    public static int galaxyCount(Game game) {
        return GalaxyNames.inUse(game).size();
    }

    public static boolean usesSectors(Game game) {
        return !MapSegment.stored(game).isEmpty() || MapSegment.isAutoSectors(game);
    }

    public static String displayName(Game game) {
        String customName = game.getCustomName();
        return StringUtils.isBlank(customName) ? game.getName() : game.getName() + " (" + customName + ")";
    }

    private static TextChannel settingsLogChannel() {
        if (JdaService.guildFogOfWar == null) {
            return null;
        }
        List<TextChannel> channels = JdaService.guildFogOfWar.getTextChannelsByName(SETTINGS_LOG_CHANNEL, true);
        return channels.isEmpty() ? null : channels.getFirst();
    }

    private static String baseFogVariant(Game game) {
        if (!game.isFowMode()) {
            return game.isLightFogMode() ? "Light Fog" : "No Fog";
        }
        return FOWPlusService.isActive(game) ? "Fog+" : "Fog";
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

    private static MessageEmbed overviewEmbed(Game game) {
        EmbedBuilder eb = baseEmbed(displayName(game));
        eb.setDescription("**" + fogVariant(game) + "** · " + (game.isHasEnded() ? "ended" : "in progress"));
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
        inline(eb, "Map template", game.getMapTemplateID());
        inline(eb, "Tiles", String.valueOf(game.getTileMap().size()));
        inline(eb, "Strategy cards", game.getScSetID());
        if (!game.isHasEnded()) {
            Player active = game.getActivePlayer();
            inline(eb, "Active player", active == null ? NONE : active.getRepresentationNoPing());
        }
        if (game.hasWinner()) {
            eb.addField(
                    "Winners",
                    fieldValue(game.getWinners().stream()
                            .map(Player::getRepresentationNoPing)
                            .collect(Collectors.joining(", "))),
                    false);
        }
        return eb.build();
    }

    private static MessageEmbed galaxiesEmbed(Game game) {
        EmbedBuilder eb = baseEmbed("Galaxies & sectors");
        Map<Character, Long> tilesPerBoard = game.getTileMap().keySet().stream()
                .collect(Collectors.groupingBy(BoardPosition::boardOf, Collectors.counting()));
        List<String> galaxyLines = GalaxyNames.inUse(game).stream()
                .map(id -> galaxyLine(game, id, tilesPerBoard))
                .toList();
        addChunkedField(eb, "Galaxies", galaxyLines);
        List<String> sectorLines = MapSegment.all(game).stream()
                .map(FogGameSummaryService::sectorLine)
                .toList();
        addChunkedField(eb, "Sectors", sectorLines);
        inline(eb, "Auto sectors", yesNo(MapSegment.isAutoSectors(game)));
        inline(eb, "Sector gap", String.valueOf(MapSegment.gap(game)));
        inline(
                eb,
                "Default sector",
                MapSegment.defaultSegment(game).map(MapSegment::name).orElse(NONE));
        inline(eb, "Separate Fracture", yesNo(MapSegment.isFractureSeparate(game)));
        return eb.build();
    }

    private static String galaxyLine(Game game, String id, Map<Character, Long> tilesPerBoard) {
        char board = GalaxyNames.MAIN_ID.equals(id) ? BoardPosition.MAIN_BOARD : id.charAt(0);
        String name = GalaxyNames.isMultiGalaxy(game) ? GalaxyNames.name(game, id) : MapSegment.MAIN;
        String renamed = GalaxyNames.isManual(game, id) ? " (renamed)" : "";
        return "`" + id + "` " + name + renamed + " · " + tilesPerBoard.getOrDefault(board, 0L) + " tiles";
    }

    private static String sectorLine(MapSegment segment) {
        String kind = segment.kind().name().toLowerCase(Locale.ROOT);
        if (segment.kind() == MapSegment.Kind.CIRCLE) {
            return segment.name() + " · " + kind + " around `" + segment.centre() + "` r" + segment.radius();
        }
        return segment.name() + " · " + kind;
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static MessageEmbed contentEmbed(Game game) {
        ModeBreakdown modes = ModeBreakdown.of(game);
        EmbedBuilder eb = baseEmbed("Content");
        eb.addField("Expansions", joined(modes.expansions()), true);
        eb.addField("Homebrew", joined(modes.homebrew()), true);
        eb.addField("Scenarios", joined(modes.scenarios()), true);
        eb.addField("Other modes & events", joined(modes.other()), false);
        eb.addField("Decks", fieldValue(deckLines(game)), false);
        return eb.build();
    }

    private static String deckLines(Game game) {
        List<String> lines = new ArrayList<>();
        lines.add("Action cards: `" + game.getAcDeckID() + "`");
        lines.add("Secret objectives: `" + game.getSoDeckID() + "`");
        lines.add("Stage 1: `" + game.getStage1PublicDeckID() + "` · Stage 2: `" + game.getStage2PublicDeckID() + "`");
        lines.add("Agendas: `" + game.getAgendaDeckID() + "`");
        lines.add("Technologies: `" + game.getTechnologyDeckID() + "`");
        lines.add("Relics: `" + game.getRelicDeckID() + "` · Explores: `" + game.getExplorationDeckID() + "`");
        String eventDeck = game.getEventDeckID();
        if (StringUtils.isNotBlank(eventDeck) && !"null".equals(eventDeck)) {
            lines.add("Events: `" + eventDeck + "`");
        }
        return String.join("\n", lines);
    }

    public static MessageEmbed fogOptionsEmbed(Game game) {
        EmbedBuilder eb = baseEmbed("Fog options");
        for (FOWOptionCategory category : FOWOptionCategory.values()) {
            String lines = Arrays.stream(FOWOption.values())
                    .filter(option -> option.isVisible() && option.getCategory() == category)
                    .map(option -> optionLine(game, option))
                    .collect(Collectors.joining("\n"));
            if (!lines.isEmpty()) {
                eb.addField(category.getTitle(), fieldValue(lines), true);
            }
        }
        String hiddenEnabled = enabledOptions(game).stream()
                .filter(option -> !option.isVisible())
                .map(option -> optionLine(game, option))
                .collect(Collectors.joining("\n"));
        if (!hiddenEnabled.isEmpty()) {
            eb.addField("Hidden", hiddenEnabled, true);
        }
        return eb.build();
    }

    private static String optionLine(Game game, FOWOption option) {
        return FOWOptionService.valueRepresentation(FOWOptionService.isEnabled(game, option)) + " " + option.getTitle();
    }

    private static MessageEmbed peopleEmbed(Game game, boolean includeChannels, List<Player> gms) {
        EmbedBuilder eb = baseEmbed("People");
        eb.addField(
                "Game masters",
                gms.isEmpty()
                        ? NONE
                        : fieldValue(
                                joined(gms.stream().map(Player::getUserName).toList())),
                false);
        List<String> playerLines = game.getRealAndEliminatedPlayers().stream()
                .map(player -> playerLine(player, includeChannels))
                .toList();
        addChunkedField(eb, "Players", playerLines);
        List<String> observers = new ArrayList<>(game.getNotRealPlayers().stream()
                .filter(player -> !gms.contains(player) && player.isSpectator())
                .map(Player::getUserName)
                .toList());
        if (includeChannels) {
            observers.addAll(channelObservers(game, gms));
        }
        if (!observers.isEmpty()) {
            addChunkedField(eb, "Observers", observers);
        }
        List<String> otherSeats = game.getNotRealPlayers().stream()
                .filter(player -> !gms.contains(player) && !player.isSpectator())
                .map(Player::getUserName)
                .toList();
        if (!otherSeats.isEmpty()) {
            eb.addField("Other seats", fieldValue(joined(otherSeats)), false);
        }
        return eb.build();
    }

    private static List<String> channelObservers(Game game, List<Player> gms) {
        TextChannel mainChannel = game.getMainGameChannel();
        if (mainChannel == null) {
            return List.of();
        }
        Set<String> seated = game.getPlayers().keySet();
        Set<String> gmIds = gms.stream().map(Player::getUserID).collect(Collectors.toSet());
        String botId =
                JdaService.jda == null ? "" : JdaService.jda.getSelfUser().getId();
        return mainChannel.getMemberPermissionOverrides().stream()
                .filter(override -> override.getAllowed().contains(Permission.VIEW_CHANNEL))
                .map(PermissionOverride::getId)
                .filter(id -> !seated.contains(id) && !gmIds.contains(id) && !id.equals(botId))
                .map(id -> observerName(mainChannel.getGuild(), id))
                .toList();
    }

    private static String observerName(Guild guild, String userId) {
        Member member = guild.getMemberById(userId);
        return member == null ? "<@" + userId + ">" : member.getEffectiveName();
    }

    private static String playerLine(Player player, boolean includeChannels) {
        StringBuilder line = new StringBuilder();
        line.append(player.getFactionEmoji())
                .append(' ')
                .append(player.getFaction())
                .append(" · ")
                .append(player.getColor())
                .append(" · ")
                .append(player.getUserName());
        if (player.isEliminated()) {
            line.append(" · eliminated");
        }
        if (includeChannels) {
            TextChannel privateChannel = player.getPrivateChannel();
            line.append(" · ").append(privateChannel == null ? MISSING : privateChannel.getAsMention());
        }
        return line.toString();
    }

    private static MessageEmbed channelsEmbed(Game game) {
        EmbedBuilder eb = baseEmbed("Channels");
        Guild guild = game.getGuild();
        inline(eb, "Server", guild == null ? MISSING : guild.getName());
        inline(eb, "GM room", guild == null ? MISSING : mention(GMService.gmRoomOrNull(game)));
        inline(eb, "Main", mention(game.getMainGameChannel()));
        inline(eb, "Actions", mention(game.getActionsChannel()));
        inline(eb, "Table talk", mention(game.getTableTalkChannel()));
        String mapThreadId = game.getBotMapUpdatesThreadID();
        inline(eb, "Map thread", StringUtils.isNumeric(mapThreadId) ? "<#" + mapThreadId + ">" : NONE);
        long withPrivateChannel = game.getRealAndEliminatedPlayers().stream()
                .map(Player::getPrivateChannel)
                .filter(Objects::nonNull)
                .count();
        inline(
                eb,
                "Private channels",
                withPrivateChannel + "/" + game.getRealAndEliminatedPlayers().size());
        return eb.build();
    }

    private static String mention(TextChannel channel) {
        return channel == null ? MISSING : channel.getAsMention();
    }

    private static void addChunkedField(EmbedBuilder eb, String name, List<String> lines) {
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

    private static EmbedBuilder baseEmbed(String title) {
        return new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle(StringUtils.abbreviate(title, MessageEmbed.TITLE_MAX_LENGTH));
    }

    private static void inline(EmbedBuilder eb, String name, String value) {
        eb.addField(name, fieldValue(value), true);
    }

    private static String joined(List<String> values) {
        return values.isEmpty() ? NONE : fieldValue(String.join(", ", values));
    }

    private static String fieldValue(String value) {
        return StringUtils.isBlank(value) ? NONE : StringUtils.abbreviate(value, MessageEmbed.VALUE_MAX_LENGTH);
    }
}

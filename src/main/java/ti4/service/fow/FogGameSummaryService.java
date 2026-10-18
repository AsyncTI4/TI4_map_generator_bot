package ti4.service.fow;

import static ti4.service.game.GameSummaryService.MISSING;
import static ti4.service.game.GameSummaryService.NONE;
import static ti4.service.game.GameSummaryService.addChunkedField;
import static ti4.service.game.GameSummaryService.baseEmbed;
import static ti4.service.game.GameSummaryService.fieldValue;
import static ti4.service.game.GameSummaryService.inline;
import static ti4.service.game.GameSummaryService.joined;
import static ti4.service.game.GameSummaryService.mention;
import static ti4.service.game.GameSummaryService.present;
import static ti4.service.game.GameSummaryService.yesNo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import ti4.image.BoardPosition;
import ti4.image.CompactOverviewGenerator;
import ti4.image.GalaxyNames;
import ti4.image.MapRenderPipeline;
import ti4.image.MapSegment;
import ti4.message.MessageHelper;
import ti4.service.game.GameSummaryService;
import ti4.service.option.FOWOptionService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.option.FOWOptionService.FOWOptionCategory;

@UtilityClass
public class FogGameSummaryService {

    public static final String SETTINGS_LOG_CHANNEL = "fow-game-settings-log";

    private static final String GAME_MASTERS_LOST = "Unknown (GM role removed at game end)";
    private static final String GAME_MASTER_IDS_KEY = "fogGameMasterIds";

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
        return buildEmbeds(game, includeChannels, gameMasters(game));
    }

    public static void rememberGameMasters(Game game, List<Player> gameMasters) {
        if (gameMasters.isEmpty()) {
            return;
        }
        game.setStoredValue(
                GAME_MASTER_IDS_KEY, gameMasters.stream().map(Player::getUserID).collect(Collectors.joining(",")));
    }

    public static List<Player> gameMasters(Game game) {
        List<Player> withRole = game.getPlayersWithGMRole();
        if (!withRole.isEmpty()) {
            return withRole;
        }
        return Arrays.stream(StringUtils.split(game.getStoredValue(GAME_MASTER_IDS_KEY), ','))
                .map(game::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean gameMastersLost(Game game) {
        return game.isHasEnded() && StringUtils.isBlank(game.getStoredValue(GAME_MASTER_IDS_KEY));
    }

    public static List<MessageEmbed> buildEmbeds(Game game, boolean includeChannels, List<Player> gameMasters) {
        List<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(GameSummaryService.overview(game, fogVariant(game)).build());
        embeds.add(galaxiesEmbed(game));
        embeds.add(GameSummaryService.contentEmbed(game));
        embeds.add(GameSummaryService.progress(game, false).build());
        embeds.add(GameSummaryService.decksEmbed(game));
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
                channel,
                "## Fog game ended: " + GameSummaryService.displayName(game),
                buildEmbeds(game, false, gameMasters));
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

    private static MessageEmbed galaxiesEmbed(Game game) {
        EmbedBuilder eb = baseEmbed("Galaxies & sectors");
        if (GalaxyNames.isMultiGalaxy(game)) {
            Map<Character, Long> tilesPerBoard = game.getTileMap().keySet().stream()
                    .collect(Collectors.groupingBy(BoardPosition::boardOf, Collectors.counting()));
            List<String> galaxyLines = GalaxyNames.inUse(game).stream()
                    .map(id -> galaxyLine(game, id, tilesPerBoard))
                    .toList();
            addChunkedField(eb, "Galaxies", galaxyLines);
        }
        Set<String> placed = game.getTileMap().keySet();
        List<String> sectorLines = MapSegment.all(game).stream()
                .map(segment -> sectorLine(segment, placed))
                .toList();
        addChunkedField(eb, "Sectors", sectorLines);
        inline(eb, "Auto sectors", yesNo(MapSegment.isAutoSectors(game)));
        inline(eb, "Sector gap", String.valueOf(MapSegment.gap(game)));
        inline(
                eb,
                "Default sector",
                MapSegment.defaultSegment(game).map(MapSegment::name).orElse(NONE));
        inline(eb, "Fracture", fractureState(game));
        return eb.build();
    }

    static String fractureState(Game game) {
        boolean inPlay = game.getTileMap().keySet().stream().anyMatch(MapSegment::isFracturePosition);
        if (!inPlay) {
            return "Not in play";
        }
        return MapSegment.isFractureSeparate(game) ? "Separate map" : "On the main map";
    }

    private static String galaxyLine(Game game, String id, Map<Character, Long> tilesPerBoard) {
        char board = GalaxyNames.MAIN_ID.equals(id) ? BoardPosition.MAIN_BOARD : id.charAt(0);
        String name = GalaxyNames.isMultiGalaxy(game) ? GalaxyNames.name(game, id) : MapSegment.MAIN;
        String renamed = GalaxyNames.isManual(game, id) ? " (renamed)" : "";
        return "`" + id + "` " + name + renamed + " · " + tilesPerBoard.getOrDefault(board, 0L) + " tiles";
    }

    private static String sectorLine(MapSegment segment, Set<String> placed) {
        String kind = segment.kind().name().toLowerCase(Locale.ROOT);
        long systems = segment.positions().stream().filter(placed::contains).count();
        String size = " · " + systems + (systems == 1 ? " system" : " systems");
        if (segment.kind() == MapSegment.Kind.CIRCLE) {
            return segment.name() + " · " + kind + " around `" + segment.centre() + "` r" + segment.radius() + size;
        }
        return segment.name() + " · " + kind + size;
    }

    private static String settingsLines(Game game) {
        List<String> lines = new ArrayList<>();
        lines.add("Auto-ping: " + GameSummaryService.autoPing(game));
        lines.add("Beta features: " + yesNo(game.isTestBetaFeaturesMode()));
        lines.add("Output verbosity: " + present(game.getOutputVerbosity()));
        lines.add("Lore entries: " + LoreService.getGameLore(game).size());
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
        eb.addField("Settings", fieldValue(settingsLines(game)), true);
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
                        ? (gameMastersLost(game) ? GAME_MASTERS_LOST : NONE)
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
        StringBuilder line = GameSummaryService.playerLine(player);
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
        TextChannel main = game.getMainGameChannel();
        inline(eb, "Main", mention(main));
        TextChannel actions = game.getActionsChannel();
        if (actions == null ? !game.isFowMode() : !actions.equals(main)) {
            inline(eb, "Actions", mention(actions));
        }
        TextChannel tableTalk = game.getTableTalkChannel();
        if (tableTalk != null || !game.isFowMode()) {
            inline(eb, "Table talk", mention(tableTalk));
        }
        String mapThreadId = game.getBotMapUpdatesThreadID();
        if (StringUtils.isNumeric(mapThreadId)) {
            inline(eb, "Map thread", "<#" + mapThreadId + ">");
        }
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
}

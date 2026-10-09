package ti4.service.game;

import static ti4.service.game.GameSummaryService.addChunkedField;
import static ti4.service.game.GameSummaryService.baseEmbed;
import static ti4.service.game.GameSummaryService.inline;
import static ti4.service.game.GameSummaryService.yesNo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.image.Mapper;
import ti4.model.EventModel;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class ModernGameInfoService {

    public static List<MessageEmbed> buildEmbeds(Game game, boolean privateGame, boolean showPlayersAndMap) {
        List<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(overviewEmbed(game, privateGame, showPlayersAndMap));
        embeds.add(GameSummaryService.contentEmbed(game));
        embeds.add(progressEmbed(game));
        embeds.add(GameSummaryService.decksEmbed(game));
        if (showPlayersAndMap) {
            embeds.add(playersEmbed(game));
        }
        embeds.add(settingsEmbed(game));
        return embeds;
    }

    private static MessageEmbed overviewEmbed(Game game, boolean privateGame, boolean showPlayersAndMap) {
        EmbedBuilder eb = GameSummaryService.overview(game, variant(game));
        inline(eb, "Private game", yesNo(privateGame));
        if (game.isCompetitiveTIGLGame()) {
            TIGLRank rank = game.getMinimumTIGLRankAtGameStart();
            inline(eb, "TIGL rank", rank == null ? "Unknown" : rank.getName());
        }
        eb.addField(
                "Map",
                showPlayersAndMap
                        ? "Use `/map show_map_string` for the map string."
                        : "The map string is hidden from you in this game.",
                false);
        if (game.isFowMode()) {
            eb.setFooter("GMs: /fow game_info has the full fog view.");
        }
        return eb.build();
    }

    private static String variant(Game game) {
        if (game.isFowMode()) {
            return "Fog of War";
        }
        if (game.isLightFogMode()) {
            return "Light Fog";
        }
        return game.isFrankenGame() ? "Franken" : "Normal";
    }

    private static MessageEmbed progressEmbed(Game game) {
        EmbedBuilder eb = GameSummaryService.progress(game, !game.isFowMode());
        List<String> events = game.getEventsInEffect().keySet().stream()
                .map(ModernGameInfoService::eventName)
                .toList();
        if (!events.isEmpty()) {
            addChunkedField(eb, "Events in effect", events);
        }
        String tradeGoods = scTradeGoods(game.getScTradeGoods());
        if (!tradeGoods.isEmpty()) {
            eb.addField("Trade goods on strategy cards", tradeGoods, false);
        }
        return eb.build();
    }

    private static String eventName(String eventId) {
        EventModel event = Mapper.getEvent(eventId);
        return event == null || StringUtils.isBlank(event.getName()) ? eventId : event.getName();
    }

    static String scTradeGoods(Map<Integer, Integer> tradeGoods) {
        return tradeGoods.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "SC " + entry.getKey() + ": " + entry.getValue() + " TG")
                .collect(Collectors.joining(" · "));
    }

    private static MessageEmbed playersEmbed(Game game) {
        EmbedBuilder eb = baseEmbed("Players");
        List<String> lines = game.getRealAndEliminatedPlayers().stream()
                .map(player -> playerLine(game, player))
                .toList();
        addChunkedField(eb, "Players", lines);
        List<String> others =
                game.getNotRealPlayers().stream().map(Player::getUserName).toList();
        if (!others.isEmpty()) {
            addChunkedField(eb, "Other seats & spectators", others);
        }
        return eb.build();
    }

    private static String playerLine(Game game, Player player) {
        StringBuilder line = GameSummaryService.playerLine(player);
        int ping = UserSettingsManager.get(player.getUserID()).getPersonalPingInterval();
        line.append(" · ping ").append(ping == 0 ? "off" : ping + "h");
        Role communityRole = player.getRoleForCommunity();
        if (communityRole != null) {
            line.append(" · role ").append(communityRole.getName());
        }
        if (game.isCompetitiveTIGLGame()) {
            TIGLRank rank = player.getPlayerTIGLRankAtGameStart();
            line.append(" · TIGL ").append(rank == null ? "unknown" : rank.getName());
        }
        return line.toString();
    }

    private static MessageEmbed settingsEmbed(Game game) {
        EmbedBuilder eb = baseEmbed("Settings & channels");
        inline(eb, "Auto-ping", GameSummaryService.autoPing(game));
        inline(eb, "Beta features", yesNo(game.isTestBetaFeaturesMode()));
        inline(eb, "Text size", game.getTextSize());
        inline(eb, "Full text output", yesNo(game.isShowFullComponentTextEmbeds()));
        inline(eb, "Output verbosity", GameSummaryService.present(game.getOutputVerbosity()));
        inline(eb, "Show map setup", yesNo(game.isShowMapSetup()));
        addChannel(eb, "Table talk", game.getTableTalkChannel());
        addChannel(eb, "Actions", game.getActionsChannel());
        addChannel(eb, "Map thread", game.getBotMapUpdatesThread());
        addChannel(eb, "Launch post", game.getLaunchPostThread());
        inline(eb, "Map images generated", String.valueOf(game.getMapImageGenerationCount()));
        inline(eb, "Buttons pressed", String.valueOf(game.getButtonPressCount()));
        return eb.build();
    }

    private static void addChannel(EmbedBuilder eb, String name, GuildChannel channel) {
        if (channel != null) {
            inline(eb, name, channel.getAsMention());
        }
    }
}

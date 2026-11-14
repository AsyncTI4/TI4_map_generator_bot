package ti4.service.fow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaWhensAftersHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.ThreadGetter;
import ti4.helpers.adjacency.AdjacencyPurpose;
import ti4.image.map.MapRenderPipeline;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.MapPositionMapper;
import ti4.service.ShowGameService;
import ti4.service.emoji.CardEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.info.HandCheckService;
import ti4.service.option.FOWOptionService.FOWOption;

public final class GMService {

    private static final List<Button> GMBUTTONS = Arrays.asList(
            Buttons.REFRESH_MAP,
            Buttons.SHOW_DECKS,
            Buttons.green("gmShowGameAs_", "Show Game As..."),
            Buttons.green("gmCheckPlayerHands_", "Check Player Hands for..."),
            Buttons.green("gmWhoCanSee~MDL", "Who Can See Position..."),
            Buttons.EDIT_SUMMARIES,
            Buttons.green("gmLore", "Manage Lore"),
            Buttons.gray("gmRefresh", "Refresh"));

    private static final List<Button> HAND_CHECK_BUTTONS = Arrays.asList(
            Buttons.gray("gmCheckPlayerHands_sabotage", "Sabotages", CardEmojis.ActionCard),
            Buttons.gray("gmCheckPlayerHands_whens", "Whens/Afters", CardEmojis.ActionCard),
            Buttons.gray("gmCheckPlayerHands_deadly", "Deadlies/Briberies", CardEmojis.ActionCard),
            Buttons.gray("gmCheckPlayerHands_confusing", "Confusing/Confounding", CardEmojis.ActionCard),
            Buttons.gray("gmCheckPlayerHands_secret", "Unscored SOs", CardEmojis.SecretObjective),
            Buttons.gray("gmCheckPlayerHands_acs", "All ACs", CardEmojis.ActionCard),
            Buttons.gray("gmCheckPlayerHands_pns", "All PNs", CardEmojis.PN),
            Buttons.DONE_DELETE_BUTTONS);

    private static final String ACTIVITY_LOG_THREAD = "-activity-log";
    private static final String STATUS_SUMMARY_THREAD = "Status Summaries";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss");

    private static final List<String> GM = List.of(
            "Galactic Mechanic",
            "Grape Muncher",
            "Giant Moth",
            "Gremlin Manager",
            "Gnome Monarch",
            "Gravity Manipulator",
            "Giraffe Magician",
            "Goblin Motivator",
            "Gummybear Manufacturer",
            "Gondola Maestro");

    public static void showGMButtons(Game game) {
        if (!game.isFowMode()) return;

        String title = RandomHelper.isOneInX(20) ? RandomHelper.pickRandomFromList(GM) : "Game Master";
        MessageHelper.sendMessageToChannelWithButtons(getGMChannel(game), title + " Buttons", GMBUTTONS);
    }

    public static TextChannel getGMChannel(Game game) {
        TextChannel gmRoom = gmRoomOrNull(game);
        return gmRoom == null ? game.getMainGameChannel() : gmRoom;
    }

    public static TextChannel gmRoomOrNull(Game game) {
        Guild guild = game.getGuild();
        if (guild == null) return null;
        List<TextChannel> channels = guild.getTextChannelsByName(game.getName() + "-gm-room", true);
        return channels.isEmpty() ? null : channels.getFirst();
    }

    private static void withActivityThread(Game game, Consumer<ThreadChannel> action) {
        TextChannel gmRoom = gmRoomOrNull(game);
        if (gmRoom == null) return;
        ThreadGetter.getThreadInChannel(gmRoom, game.getName() + ACTIVITY_LOG_THREAD, true, false, action);
    }

    public static void sendMessageToGMRoom(Game game, String msg) {
        TextChannel gmRoom = gmRoomOrNull(game);
        if (gmRoom != null) {
            MessageHelper.sendMessageToChannel(gmRoom, msg);
        }
    }

    public static void sendMessageToGMChannel(Game game, String msg, boolean ping) {
        if (ping) {
            msg += " - " + gmPing(game);
        }
        MessageHelper.sendMessageToChannel(getGMChannel(game), msg);
    }

    public static String gmPing(Game game) {
        if (game.isFowMode()) {
            List<Role> gmRoles = game.getGuild().getRolesByName(game.getName() + " GM", false);
            if (!gmRoles.isEmpty()) {
                return gmRoles.getFirst().getAsMention();
            }
        }
        return "";
    }

    public static void logActivity(Game game, String eventLog, boolean ping) {
        logPlayerActivity(game, null, eventLog, null, ping);
    }

    public static void logPlayerActivity(Game game, Player player, String eventLog) {
        logPlayerActivity(game, player, eventLog, null, false);
    }

    public static void logPlayerActivity(Game game, Player player, String eventLog, String jumpUrl, boolean ping) {
        if (!game.isFowMode()) return;

        String timestamp = "`[" + LocalDateTime.now().format(formatter) + "]` ";
        String log = timestamp + eventLog + (ping ? " - " + gmPing(game) : "");
        withActivityThread(game, threadChannel -> {
            if (jumpUrl != null) {
                MessageHelper.sendMessageToChannel(threadChannel, log + " - " + jumpUrl);
            } else if (player == null) {
                MessageHelper.sendMessageToChannel(threadChannel, log);
            } else {
                jumpToLatestMessage(
                        player,
                        latestJumpUrl ->
                                MessageHelper.sendMessageToChannel(threadChannel, log + " - " + latestJumpUrl));
            }
        });
    }

    /** Posts a message to the FoW activity-log thread (inside the GM channel). No-op outside FoW. */
    public static void postToActivityThread(Game game, String message) {
        if (!game.isFowMode()) return;
        withActivityThread(game, threadChannel -> MessageHelper.sendMessageToChannel(threadChannel, message));
    }

    /** Posts a components-V2 component to the FoW activity-log thread (inside the GM channel). No-op outside FoW. */
    public static void postToActivityThread(Game game, MessageTopLevelComponent component) {
        if (!game.isFowMode()) return;
        withActivityThread(game, threadChannel -> {
            MessageV2Builder builder = new MessageV2Builder(threadChannel);
            builder.append(component);
            builder.send();
        });
    }

    /**
     * Renders the full (unfogged) map and posts it into the FoW activity-log thread. The thread lives
     * in the GM-only channel, so the unfogged view leaks nothing. A null render event keeps the map
     * unfogged (see {@code FogView.of}). No-op outside FoW.
     */
    public static void refreshMapInActivityThread(Game game) {
        if (!game.isFowMode()) return;
        MapRenderPipeline.queue(
                game,
                null,
                DisplayType.all,
                fileUpload -> withActivityThread(
                        game, threadChannel -> MessageHelper.sendFileUploadToChannel(threadChannel, fileUpload)));
    }

    private static void jumpToLatestMessage(Player player, Consumer<String> callback) {
        MessageChannel privateChannel = player != null ? player.getPrivateChannel() : null;
        if (privateChannel != null) {
            privateChannel
                    .getHistory()
                    .retrievePast(1)
                    .queue(
                            messages -> callback.accept(messages.getFirst().getJumpUrl()),
                            throwable -> callback.accept("No latest message."));
        } else {
            callback.accept("No private channel.");
        }
    }

    @ButtonHandler(value = "gmRefresh", save = false)
    public static void refreshGMButtons(ButtonInteractionEvent event, Game game) {
        showGMButtons(game);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(value = "gmShowGameAs_", save = false)
    public static void showGameAs(ButtonInteractionEvent event, String buttonID, Game game) {
        String faction = buttonID.replace("gmShowGameAs_", "");
        if (!faction.isEmpty()) {
            Player showAs = game.getPlayerFromColorOrFaction(faction);
            ShowGameService.simpleShowGame(
                    game, new UserOverridenGenericInteractionCreateEvent(event, showAs.getMember()));
        } else {
            List<Button> factionButtons = new ArrayList<>();
            for (Player player : game.getRealPlayers()) {
                factionButtons.add(Buttons.green(
                        "gmShowGameAs_" + player.getFaction(),
                        StringUtils.capitalize(player.getColor()) + ", " + player.getUserName(),
                        player.getFactionEmoji()));
            }
            factionButtons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(), "Please choose the player who to view the game as:", factionButtons);
        }
    }

    @ButtonHandler(value = "gmCheckPlayerHands_", save = false)
    public static void checkPlayerHands(ButtonInteractionEvent event, String buttonID, Game game) {
        String option = buttonID.replace("gmCheckPlayerHands_", "");
        List<String> reports = HandCheckService.reports(game, option);
        if (reports.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(), "Please choose what to look for:", HAND_CHECK_BUTTONS);
            return;
        }
        reports.forEach(report -> MessageHelper.sendMessageToChannel(event.getChannel(), report));
    }

    @ButtonHandler(value = "gmWhoCanSee~MDL", save = false)
    public static void whoCanSeePosition(ButtonInteractionEvent event) {
        TextInput position = TextInput.create(Constants.POSITION, TextInputStyle.SHORT)
                .setPlaceholder("000")
                .setRequiredRange(3, 4)
                .build();
        Modal modal = Modal.create("gmWhoCanSeeResolve", "Who Can See Position")
                .addComponents(Label.of("Position", position))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("gmWhoCanSeeResolve")
    public static void resolveWhoCanSeePosition(ModalInteractionEvent event, Game game) {
        String position = event.getValue(Constants.POSITION).getAsString();
        if (!MapPositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Position '" + position + "' is invalid.");
            return;
        }

        StringBuilder sb = new StringBuilder("Following players can see system **");
        sb.append(position).append("**:\n");
        for (Player player : FoWHelper.getAdjacentPlayers(game, position, false, AdjacencyPurpose.VISION)) {
            sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append('\n');
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    @ButtonHandler(value = "gmQolSettings~MDL", save = false)
    public static void qolSettings(ButtonInteractionEvent event, Game game) {
        TextInput base = TextInput.create(FowAutoDeclineService.BASE_HOURS_KEY, TextInputStyle.SHORT)
                .setValue(String.valueOf(FowAutoDeclineService.baseHours(game)))
                .setRequiredRange(1, 6)
                .build();
        TextInput spread = TextInput.create(FowAutoDeclineService.SPREAD_HOURS_KEY, TextInputStyle.SHORT)
                .setValue(String.valueOf(FowAutoDeclineService.spreadHours(game)))
                .setRequiredRange(1, 6)
                .build();
        Modal modal = Modal.create("gmQolSettingsResolve", "Fog QoL 01 Settings")
                .addComponents(
                        Label.of("Auto-decline delay (hours)", base), Label.of("Random spread +/- (hours)", spread))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("gmQolSettingsResolve")
    public static void resolveQolSettings(ModalInteractionEvent event, Game game) {
        double base = FowAutoDeclineService.parseHours(
                event.getValue(FowAutoDeclineService.BASE_HOURS_KEY).getAsString(),
                FowAutoDeclineService.DEFAULT_BASE_HOURS);
        double spread = FowAutoDeclineService.parseHours(
                event.getValue(FowAutoDeclineService.SPREAD_HOURS_KEY).getAsString(),
                FowAutoDeclineService.DEFAULT_SPREAD_HOURS);
        game.setStoredValue(FowAutoDeclineService.BASE_HOURS_KEY, String.valueOf(base));
        game.setStoredValue(FowAutoDeclineService.SPREAD_HOURS_KEY, String.valueOf(spread));
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Players who cannot follow a strategy card are auto-declined after " + base + " hours +/- " + spread
                        + " hours (random), or as soon as they react. Needs the **Fog QoL 01** option.");
    }

    public static void createFOWStatusSummary(Game game) {
        if (!game.isFowMode() || !game.getFowOption(FOWOption.STATUS_SUMMARY) || FOWPlusService.isActive(game)) return;

        ThreadGetter.getThreadInChannel(
                game.getMainGameChannel(), STATUS_SUMMARY_THREAD, true, false, threadChannel -> {
                    MessageHelper.sendMessageToChannel(
                            threadChannel, "# Round " + game.getRound() + " Status Summary " + game.getPing());
                    ExploreService.secondHalfOfExpInfo(
                            Arrays.asList(
                                    Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER),
                            threadChannel,
                            null,
                            game,
                            true,
                            false);

                    RelicHelper.showRemaining(threadChannel, true, game, null);
                });
    }

    public static void addForcePassWhenButtonForFowGM(Game game, Player player, List<Button> buttons) {
        if (!game.isFowMode()) return;
        buttons.add(Buttons.gray("declineToQueueAWhenFowGM_" + player.getFaction(), "Force Pass as GM"));
    }

    public static void addForcePassAfterButtonForFowGM(Game game, Player player, List<Button> buttons) {
        if (!game.isFowMode()) return;
        buttons.add(Buttons.gray("declineToQueueAnAfterFowGM_" + player.getFaction(), "Force Pass as GM"));
    }

    @ButtonHandler("declineToQueueAWhenFowGM_")
    public static void declineToQueueAWhenFowGM(Game game, ButtonInteractionEvent event, Player gm, String buttonID) {
        if (!gm.isGM()) return;
        Player player = game.getPlayerFromColorOrFaction(buttonID.replace("declineToQueueAWhenFowGM_", ""));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " GM has forced you to pass on \"when\"s.");
        AgendaWhensAftersHelper.declineToQueueAWhen(game, event, player);
    }

    @ButtonHandler("declineToQueueAnAfterFowGM_")
    public static void declineToQueueAnAfterFowGM(Game game, ButtonInteractionEvent event, Player gm, String buttonID) {
        if (!gm.isGM()) return;
        Player player = game.getPlayerFromColorOrFaction(buttonID.replace("declineToQueueAnAfterFowGM_", ""));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " GM has forced you to pass on \"after\"s.");
        AgendaWhensAftersHelper.declineToQueueAnAfter(game, event, player);
    }

    @ButtonHandler("fowCreateChannelFor_")
    public static void createChannelFor(Game game, ButtonInteractionEvent event, Player gm, String buttonID) {
        if (!gm.isGM()) return;
        String userID = buttonID.replace("fowCreateChannelFor_", "");
        Player player = game.getPlayer(userID);
        if (player.getPrivateChannel() != null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), player.getUserName() + " already has a private channel.");
            return;
        }

        CreateFoWGameService.createPrivateChannelForPlayer(player.getMember(), game);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Created private channel for " + player.getUserName() + ": "
                        + player.getPrivateChannel().getAsMention());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }
}

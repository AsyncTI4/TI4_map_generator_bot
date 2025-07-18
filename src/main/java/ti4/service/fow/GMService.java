package ti4.service.fow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.ThreadGetter;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.emoji.CardEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.option.FOWOptionService.FOWOption;

public class GMService {

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
        "Gondola Maestro"
    );

    public static void showGMButtons(Game game) {
        if (!game.isFowMode()) return;

        String title = RandomHelper.isOneInX(20) ? RandomHelper.pickRandomFromList(GM) : "Game Master";
        MessageHelper.sendMessageToChannelWithButtons(getGMChannel(game), title + " Buttons", GMBUTTONS);
    }

    public static TextChannel getGMChannel(Game game) {
        List<TextChannel> channels = game.getGuild().getTextChannelsByName(game.getName() + "-gm-room", true);
        return channels.isEmpty() ? game.getMainGameChannel() : channels.getFirst();
    }

    public static void sendMessageToGMChannel(Game game, String msg) {
        sendMessageToGMChannel(game, msg, false);
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

    public static void logActivity(Game game,String eventLog, boolean ping) {
        logPlayerActivity(game, null, eventLog, null, ping);
    }

    public static void logPlayerActivity(Game game, Player player, String eventLog) {
        logPlayerActivity(game, player, eventLog, null, false);
    }

    public static void logPlayerActivity(Game game, Player player, String eventLog, String jumpUrl, boolean ping) {
        if (!game.isFowMode()) return;

        String timestamp = "`[" + LocalDateTime.now().format(formatter) + "]` ";
        final String log = timestamp + eventLog + (ping ? " - " + gmPing(game): "");
        ThreadGetter.getThreadInChannel(getGMChannel(game), game.getName() + ACTIVITY_LOG_THREAD, true, false,
            threadChannel -> {
                if (jumpUrl != null) {
                    MessageHelper.sendMessageToChannel(threadChannel, log + " - " + jumpUrl);
                } else if (player == null) {
                    MessageHelper.sendMessageToChannel(threadChannel, log);
                } else {
                    jumpToLatestMessage(player, latestJumpUrl -> {
                        MessageHelper.sendMessageToChannel(threadChannel, log + " - " + latestJumpUrl);
                    });
                }
            });
    }

    private static void jumpToLatestMessage(Player player, Consumer<String> callback) {
        MessageChannel privateChannel = player != null ? player.getPrivateChannel() : null;
        if (privateChannel != null) {
            privateChannel.getHistory().retrievePast(1).queue(messages -> {
                callback.accept( messages.get(0).getJumpUrl());
            }, throwable -> {
                callback.accept("No latest message.");
            });
        } else {
            callback.accept("No private channel.");
        }
    }

    @ButtonHandler("gmRefresh")
    public static void refreshGMButtons(ButtonInteractionEvent event, Game game) {
        showGMButtons(game);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("gmShowGameAs_")
    public static void showGameAs(ButtonInteractionEvent event, String buttonID, Game game) {
        String faction = buttonID.replace("gmShowGameAs_", "");
        if (!faction.isEmpty()) {
            Player showAs = game.getPlayerFromColorOrFaction(faction);
            ShowGameService.simpleShowGame(game, new UserOverridenGenericInteractionCreateEvent(event, showAs.getMember()));
        } else {
            List<Button> factionButtons = new ArrayList<>();
            for (Player player : game.getRealPlayers()) {
                factionButtons.add(Buttons.green("gmShowGameAs_" + player.getFaction(), StringUtils.capitalize(player.getColor())
                     + ", " + player.getUserName(), player.getFactionEmoji()));
            }
            factionButtons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), 
                "Please choose the player who to view the game as:", factionButtons);
        }
    }

    @ButtonHandler("gmCheckPlayerHands_")
    public static void checkPlayerHands(ButtonInteractionEvent event, String buttonID, Game game) {
        String option = buttonID.replace("gmCheckPlayerHands_", "");
        switch (option) {
            case "sabotage" -> {
                checkWhoHas("sabo", game, event);
            }
            case "deadly" -> {
                checkWhoHas("deadly_plot", game, event);
                checkWhoHas("bribery", game, event);
            }
            case "confusing" -> {
                checkWhoHas("confusing", game, event);
                checkWhoHas("confounding", game, event);
            }
            case "whens" -> {
                StringBuilder sbWhens = new StringBuilder("Following players have \"when\"s in hand:\n");
                StringBuilder sbAfters = new StringBuilder("Following players have \"after\"s in hand:\n");

                for (Player player : game.getRealPlayers()) {
                    List<String> whens = AgendaHelper.getPossibleWhenNames(player);
                    if (!whens.isEmpty()) {
                        sbWhens.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(": ");
                        sbWhens.append(String.join(", ", whens)).append("\n");
                    }
                    List<String> afters = AgendaHelper.getPossibleAfterNames(player);
                    if (!afters.isEmpty()) {
                        sbAfters.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(": ");
                        sbAfters.append(String.join(", ", afters)).append("\n");
                    }
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), sbWhens.toString());
                MessageHelper.sendMessageToChannel(event.getChannel(), sbAfters.toString());
            }
            case "secret" -> {
                StringBuilder sos = new StringBuilder();
                for (Player player : game.getRealPlayers()) {
                    String allSecrets = SecretObjectiveInfoService.getSecretObjectiveCardInfo(game, player);
                    String unscored = StringUtils.substringAfter(allSecrets, "Unscored");
                    sos.append("__")
                        .append(player.getRepresentationUnfoggedNoPing())
                        .append(" Unscored")
                        .append(unscored)
                        .append("\n");
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), sos.toString());
            }
            default -> {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please choose what to look for:", HAND_CHECK_BUTTONS);
            }
        }
    }

    @ButtonHandler("gmWhoCanSee~MDL")
    public static void whoCanSeePosition(ButtonInteractionEvent event) {
        TextInput position = TextInput.create(Constants.POSITION, "Position", TextInputStyle.SHORT)
            .setPlaceholder("000")
            .setRequiredRange(3, 4)
            .build();
        Modal modal = Modal.create("gmWhoCanSeeResolve", "Who Can See Position")
          .addActionRow(position)
          .build();
        event.replyModal(modal).queue();
    }

    @ModalHandler("gmWhoCanSeeResolve")
    public static void resolveWhoCanSeePosition(ModalInteractionEvent event, Game game) {
        String position = event.getValue(Constants.POSITION).getAsString();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Position '" + position + "' is invalid.");
            return;
        }

        StringBuilder sb = new StringBuilder("Following players can see system **");
        sb.append(position).append("**:\n");
        for (Player player : FoWHelper.getAdjacentPlayers(game, position, false)) {
            sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append("\n");
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    private static void checkWhoHas(String acId, Game game, ButtonInteractionEvent event) {
        StringBuilder sb = new StringBuilder("Following players have **");
        sb.append(acId).append("** in hand:\n");
        for (Player player : game.getRealPlayers()) {
            for (String ac : player.getActionCards().keySet()) {
                if (ac.startsWith(acId)) {
                    sb.append("> ").append(player.getRepresentationUnfoggedNoPing()).append("\n");
                    break;
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
    }

    public static void createFOWStatusSummary(Game game) {
        if (!game.isFowMode() || !game.getFowOption(FOWOption.STATUS_SUMMARY)) return;

        ThreadGetter.getThreadInChannel(game.getMainGameChannel(), STATUS_SUMMARY_THREAD, true, false,
            threadChannel -> {
                MessageHelper.sendMessageToChannel(threadChannel, "# Round " + game.getRound() + " Status Summary " + game.getPing());
                ExploreService.secondHalfOfExpInfo(Arrays.asList(Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER), 
                    threadChannel, null, game, true, false);
              
                RelicHelper.showRemaining(threadChannel, true, game, null);
            });
    }
}

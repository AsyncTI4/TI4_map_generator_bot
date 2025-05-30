package ti4.service.fow;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.ThreadGetter;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TileModel.TileBack;
import ti4.service.ShowGameService;
import ti4.service.emoji.CardEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.option.FOWOptionService.FOWOption;

public class GMService {

    private static final List<Button> GMBUTTONS = Arrays.asList(
        Buttons.REFRESH_MAP,
        Buttons.SHOW_DECKS,
        Buttons.green("gmShowGameAs_", "Show Game As..."),
        Buttons.green("gmCheckPlayerHands_", "Check Player Hands for..."),
        Buttons.green("gmWhoCanSee~MDL", "Who Can See Position..."),
        Buttons.EDIT_NOTEPAD,
        Buttons.POST_NOTEPAD,
        Buttons.green("gmSystemLore", "Edit System Lore"),
        Buttons.EDIT_SUMMARIES,
        Buttons.gray("gmRefresh", "Refresh"));

    private static final List<Button> HAND_CHECK_BUTTONS = Arrays.asList(
        Buttons.gray("gmCheckPlayerHands_sabotage", "Sabotages", CardEmojis.ActionCard),
        Buttons.gray("gmCheckPlayerHands_whens", "Whens/Afters", CardEmojis.ActionCard),
        Buttons.gray("gmCheckPlayerHands_deadly", "Deadly Plots/Briberies", CardEmojis.ActionCard),
        Buttons.gray("gmCheckPlayerHands_confusing", "Confusing/Confounding", CardEmojis.ActionCard),
        Buttons.DONE_DELETE_BUTTONS);

    private static final String ACTIVITY_LOG_THREAD = "-activity-log";
    private static final String STATUS_SUMMARY_THREAD = "Status Summaries";
    private static final String SYSTEM_LORE_KEY = "fowSystemLore";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss");

    public static void showGMButtons(Game game) {
        if (!game.isFowMode()) return;
        MessageHelper.sendMessageToChannelWithButtons(getGMChannel(game), "GM Buttons", GMBUTTONS);
    }

    public static TextChannel getGMChannel(Game game) {
        List<TextChannel> channels = game.getGuild().getTextChannelsByName(game.getName() + "-gm-room", true);
        return channels.isEmpty() ? null : channels.getFirst();
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
                factionButtons.add(Buttons.green("gmShowGameAs_" + player.getFaction(), player.getColor() + ", " + player.getUserName(), player.getFactionEmoji()));
            }
            factionButtons.add(Buttons.DONE_DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Select player who to view the game as:", factionButtons);
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
                  StringBuilder sbWhens = new StringBuilder("Following players have **whens** in hand:\n");
                  StringBuilder sbAfters = new StringBuilder("Following players have **afters** in hand:\n");

                  for (Player player : game.getRealPlayers()) {
                      List<String> whens = AgendaHelper.getPossibleWhenNames(player);
                      if (!whens.isEmpty()) {
                          sbWhens.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(": ").append(String.join(", ", whens)).append("\n");
                      }
                      List<String> afters = AgendaHelper.getPossibleAfterNames(player);
                      if (!afters.isEmpty()) {
                          sbAfters.append("> ").append(player.getRepresentationUnfoggedNoPing()).append(": ").append(String.join(", ", afters)).append("\n");
                      }
                  }
                  MessageHelper.sendMessageToChannel(event.getChannel(), sbWhens.toString());
                  MessageHelper.sendMessageToChannel(event.getChannel(), sbAfters.toString());
            }
            default -> {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Select what to look for:", HAND_CHECK_BUTTONS);
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
                List<String> types = new ArrayList<>();
                types.add(Constants.CULTURAL);
                types.add(Constants.INDUSTRIAL);
                types.add(Constants.HAZARDOUS);
                types.add(Constants.FRONTIER);
                ExploreService.secondHalfOfExpInfo(types, threadChannel, null, game, true, false);
              
                RelicHelper.showRemaining(threadChannel, true, game, null);
            });
    }

    @ButtonHandler("gmSystemLore")
    public static void showSystemLoreButtons(Game game) {
        showSystemLoreButtons(game, null);
    }

    private static void showSystemLoreButtons(Game game, String originalMessageId) {
        StringBuffer sb = new StringBuffer("### System Lore\n");
        sb.append("-# Shown to the first player to conclude an action with units in the system.\n");

        List<Button> systemLoreButtons = new ArrayList<>();

        for (Map.Entry<String, String> lore : getSavedLore(game).entrySet()) {
            String position = lore.getKey();
            String loreText = lore.getValue().replace("\n", " ");

            Tile tile = game.getTileByPosition(position);
            sb.append("**").append(position).append("** ");
            sb.append(tile != null ? tile.getRepresentation() : "").append(" - `");
            sb.append(StringUtils.substring(loreText, 0, 50));
            sb.append(loreText.length() > 50 ? "..." : "").append("`\n");

            systemLoreButtons.add(Buttons.green("gmSystemLoreEdit_" + position + "~MDL", position));
        }
        systemLoreButtons.add(Buttons.blue("gmSystemLoreEdit~MDL", "Add New"));
        systemLoreButtons.add(Buttons.DONE_DELETE_BUTTONS);

        if (originalMessageId == null) {
            MessageHelper.sendMessageToChannelWithButtons(getGMChannel(game), sb.toString(), systemLoreButtons);
        } else {
            List<List<ActionRow>> buttonRows = MessageHelper.getPartitionedButtonLists(systemLoreButtons);
            getGMChannel(game).editMessageById(originalMessageId, sb.toString()).setComponents(buttonRows.getFirst()).queue();
        }
    }

    private static Map<String, String> getSavedLore(Game game) {
        Map<String, String> savedLoreMap = new HashMap<>();
        String savedLoreString = game.getStoredValue(SYSTEM_LORE_KEY);
        if (StringUtils.isNotBlank(savedLoreString)) {
            for (String savedLore : savedLoreString.split("\\|")) {
                String[] splitLore = savedLore.split(";");
                if (splitLore.length == 2) {
                    savedLoreMap.put(splitLore[0], splitLore[1]);
                } else {
                    BotLogger.warning(new BotLogger.LogMessageOrigin(game), "Invalid lore string: " + savedLore);
                }
            }
        }
        return savedLoreMap;
    }

    @ButtonHandler("gmSystemLoreEdit")
    public static void editSystemLore(ButtonInteractionEvent event, String buttonID, Game game) {
        String existingPosition = buttonID.contains("_") ? StringUtils.substringBetween(buttonID, "gmSystemLoreEdit_", "~MDL") : "";

        TextInput.Builder position = TextInput.create(Constants.POSITION, "Position", TextInputStyle.SHORT)
            .setRequired(true)
            .setPlaceholder("000")
            .setMaxLength(4);
        TextInput.Builder lore = TextInput.create(Constants.MESSAGE, "Lore (empty to remove)", TextInputStyle.PARAGRAPH)
            .setRequired(false)
            .setPlaceholder("There once was Mecatol...")
            .setMaxLength(420);

        if (StringUtils.isNotBlank(existingPosition)) {
            position.setValue(existingPosition);
            lore.setValue(getSavedLore(game).get(existingPosition));
        }

        Modal editLoreModal = Modal.create("gmSystemLoreSave_" + event.getMessageId(), "Add Lore to Position")
            .addActionRow(position.build())
            .addActionRow(lore.build())
            .build();

        event.replyModal(editLoreModal).queue();
    }

    @ModalHandler("gmSystemLoreSave_")
    public static void saveSystemLore(ModalInteractionEvent event, Player player, Game game) {
        String origMessageId = event.getModalId().replace("gmSystemLoreSave_", "");
        String position = event.getValue(Constants.POSITION).getAsString();
        String loreText = event.getValue(Constants.MESSAGE).getAsString();

        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Position " + position + " is invalid to save lore `" + loreText + "`");
            return;
        }

        Map<String, String> savedLoreMap = getSavedLore(game);
        if (StringUtils.isBlank(loreText)) {
            savedLoreMap.remove(position);
        } else {
            savedLoreMap.put(position, loreText.replace(";", "").replace("|", ""));
            MessageEmbed embed = buildLoreEmbed(game, position, loreText);
            MessageHelper.sendMessageToChannelWithEmbedsAndButtons(event.getChannel(), 
                "Saved Lore Preview", Arrays.asList(embed), Arrays.asList(Buttons.DONE_DELETE_BUTTONS));
        }

        setSystemLore(game, savedLoreMap);
        showSystemLoreButtons(game, origMessageId);
    }

    private static void setSystemLore(Game game, Map<String, String> systemLore) {
        String loreString = systemLore.entrySet().stream()
            .map(entry -> entry.getKey() + ";" + entry.getValue())
            .collect(Collectors.joining("|"));
        game.setStoredValue(SYSTEM_LORE_KEY, loreString);
    }

    private static MessageEmbed buildLoreEmbed(Game game, String position, String lore) {
        Tile tile = game.getTileByPosition(position);
        String titleTile = position;
        Color embedColor = Color.black;
        if (tile != null && tile.getTileModel() != null) {
            titleTile += " - " + tile.getTileModel().getNameNullSafe() + " " + tile.getTileModel().getEmoji();
            switch (tile.getTileModel().getTileBack()) {
                case TileBack.RED -> embedColor = Color.red;
                case TileBack.BLUE -> embedColor = Color.blue;
                case TileBack.GREEN -> embedColor = Color.green;
                default -> embedColor = Color.black;
            }
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("⭐ Lore of " + titleTile);
        eb.setDescription(lore);
        eb.setColor(embedColor);
        return eb.build();
    }

    public static void showSystemLore(Player player, Game game) {
        String pos = game.getActiveSystem();
        if (!FoWHelper.playerHasUnitsInSystem(player, game.getTileByPosition(pos))) {
            return;
        }

        Map<String, String> systemLore = getSavedLore(game);
        if (systemLore.isEmpty() || !systemLore.containsKey(pos)) {
            return;
        }

        MessageEmbed embed = buildLoreEmbed(game, pos, systemLore.get(pos));
        MessageHelper.sendMessageToChannelWithEmbed(player.getPrivateChannel(), "You found a Lore Fragment", embed);
        
        logPlayerActivity(game, player, player.getRepresentationUnfoggedNoPing() + " was shown the lore of " + pos);

        systemLore.remove(pos);
        setSystemLore(game, systemLore);
    }
}

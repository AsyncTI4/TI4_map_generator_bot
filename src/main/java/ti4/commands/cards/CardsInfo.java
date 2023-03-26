package ti4.commands.cards;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.*;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.Nullable;

import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.*;

public class CardsInfo extends CardsSubcommandData {

    public static final String CARDS_INFO = Constants.CARDS_INFO_THREAD_PREFIX;
    private static HashMap<Map, TextChannel> threadTextChannels = new HashMap<>();

    public CardsInfo() {
        super(Constants.INFO, "Resent all my cards in Private Message");
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to show full promissory text").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        sentUserCardInfo(event, activeMap, player);
    }

    public static void sentUserCardInfo(SlashCommandInteractionEvent event, Map activeMap, Player player) {
        sentUserCardInfo(event, activeMap, player, null);
    }

    public static void sentUserCardInfo(@Nullable SlashCommandInteractionEvent event, Map activeMap, Player player, @Nullable ButtonInteractionEvent buttonEvent) {
        checkAndAddPNs(activeMap, player);
        OptionMapping longPNOption = event != null ? event.getOption(Constants.LONG_PN_DISPLAY) : null;
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = longPNOption.getAsString().equalsIgnoreCase("y") || longPNOption.getAsString().equalsIgnoreCase("yes");
        }
        LinkedHashMap<String, Integer> secretObjective = activeMap.getSecretObjective(player.getUserID());
        LinkedHashMap<String, Integer> scoredSecretObjective = new LinkedHashMap<>(activeMap.getScoredSecretObjective(player.getUserID()));
        String soText;
        String acText;
        String pnText;
        for (String id : activeMap.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("--------------------\n");
        sb.append("**Game: **`").append(activeMap.getName()).append("`\n");
        sb.append(Helper.getPlayerRepresentation(event, player, true));
        int index = 1;
        sb.append("\n");

        //SCORED SECRET OBJECTIVES
        sb.append("**Scored Secret Objectives:**").append("\n");
        if (scoredSecretObjective != null) {
            for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
                String[] soSplit = Mapper.getSecretObjective(so.getKey()).split(";");
                String soName = soSplit[0];
                String soPhase = soSplit[1];
                String soDescription = soSplit[2];
                sb.append("`").append(index).append(".").append(Helper.leftpad("(" + so.getValue(), 4)).append(")`");
                sb.append(Emojis.SecretObjective).append("__" + soName + "__"); //.append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
                sb.append("\n");
                index++;
            }
        }
        sb.append("\n");

        //UNSCORED SECRET OBJECTIVES
        sb.append("**Unscored Secret Objectives:**").append("\n");
        List<Button> soButtons = new ArrayList<>();
        if (secretObjective != null) {
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                String[] soSplit = Mapper.getSecretObjective(so.getKey()).split(";");
                String soName = soSplit[0];
                String soPhase = soSplit[1];
                String soDescription = soSplit[2];
                Integer idValue = so.getValue();
                sb.append("`").append(index).append(".").append(Helper.leftpad("(" + idValue, 4)).append(")`");
                sb.append(Emojis.SecretObjective).append("__**" + soName + "**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
                index++;
                if (soName != null) {
                    soButtons.add(Button.primary(Constants.SO_SCORE_FROM_HAND + idValue, "(" + idValue + ") " + soName).withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
                }
            }
        }
        soText = sb.toString();
        sb = new StringBuilder();

        sb.append("_ _\n");

        //ACTION CARDS
        sb.append("**Action Cards:**").append("\n");
        index = 1;

        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                String[] acSplit = Mapper.getActionCard(ac.getKey()).split(";");
                String acName = acSplit[0];
                String acPhase = acSplit[1];
                String acWindow = acSplit[2];
                String acDescription = acSplit[3];
                Integer value = ac.getValue();
                String key = ac.getKey();
                sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                sb.append(Emojis.ActionCard).append("__**" + acName + "**__").append(" *(").append(acPhase).append(" Phase)*: _").append(acWindow).append(":_ ").append(acDescription).append("\n");
                index++;
                String ac_name = Mapper.getActionCardName(key);
                if (ac_name != null) {
                    acButtons.add(Button.danger(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        acText = sb.toString();
        sb = new StringBuilder();
        sb.append("_ _\n");
       
        //PROMISSORY NOTES
        sb.append("**Promissory Notes:**").append("\n");
        index = 1;
        LinkedHashMap<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + pn.getValue(), 3)).append(")`");
                    sb.append(Emojis.PN).append(Mapper.getPromissoryNote(pn.getKey(), longPNDisplay));
                    sb.append("\n");
                    index++;
                }
            }
            sb.append("\n");

            //PLAY AREA PROMISSORY NOTES
            sb.append("\n").append("**PLAY AREA Promissory Notes:**").append("\n");
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    String pnData = Mapper.getPromissoryNote(pn.getKey(), longPNDisplay);
                    sb.append("`").append(index).append(".").append("(" + pn.getValue()).append(")`");
                    sb.append(Emojis.PN).append(pnData);
                    sb.append("\n");
                    index++;
                }
            }
        }
        pnText = sb.toString();

        User userById = event != null ? event.getJDA().getUserById(player.getUserID()) : (buttonEvent != null ? buttonEvent.getJDA().getUserById(player.getUserID()) : null);
        if (userById != null) {
            String cardInfo = soText + "\n" + acText + "\n" + pnText;

            try {
                MessageChannel channel = event != null ? event.getChannel() : buttonEvent.getChannel();
                MessageChannelUnion channelUnion = event != null ? event.getChannel() : buttonEvent.getChannel();
                if (activeMap.isFoWMode()) {
                    if (player.getPrivateChannel() == null) {
                        MessageHelper.sendMessageToChannel(channel, "Private channels are not set up for this game. Messages will be suppressed.");
                    } else {
                        channel = player.getPrivateChannel();
                    }
                }

                if (channel == null) {
                    MessageHelper.sendMessageToUser(cardInfo, userById);
                    BotLogger.log("Could not find channel");
                    return;
                }
                if (event != null) {
                    OptionMapping option = event.getOption(Constants.DM_CARD_INFO);
                    if (option != null && option.getAsBoolean()) {
                        MessageHelper.sendMessageToUser(cardInfo, userById);
                    }
                }
                ChannelType type = channel.getType();

                TextChannel textChannel = threadTextChannels.get(activeMap);
                if (activeMap.isFoWMode()) {
                    textChannel = (TextChannel) channel;
                }
                if (textChannel == null) {
                    String mainChannelName = activeMap.getName() + Constants.ACTIONS_CHANNEL_SUFFIX;
                    for (TextChannel textChannel_ : MapGenerator.jda.getTextChannels()) {
                        if (textChannel_.getName().equals(mainChannelName)) {
                            textChannel = textChannel_;
                            threadTextChannels.put(activeMap, textChannel);
                            break;
                        }
                    }

                    if (textChannel == null) {
                        if (ChannelType.GUILD_PUBLIC_THREAD.equals(type) || ChannelType.GUILD_PRIVATE_THREAD.equals(type)) {
                            IThreadContainer parentChannel = channelUnion.asThreadChannel().getParentChannel();
                            if (parentChannel instanceof TextChannel) {
                                textChannel = (TextChannel) parentChannel;
                            } else {
                                MessageHelper.sendMessageToUser(cardInfo, userById);
                                MessageHelper.sendMessageToUser("Please Execute info command in non thread channel", userById);
                                return;
                            }
                        } else {
                            textChannel = channelUnion.asTextChannel();
                        }
                    }
                }
                List<ThreadChannel> threadChannels = textChannel.getThreadChannels();
                boolean threadFound = false;
                String threadName = CARDS_INFO + activeMap.getName() + "-" + player.getUserName().replaceAll("/", "");
                String playerPing = threadName + " " + Helper.getPlayerPing(player);

                for (ThreadChannel threadChannel : threadChannels) {
                    if (threadChannel.getName().equals(threadName)) {
                        sendCardInfoToChannel(threadChannel, playerPing, soText, soButtons, acText, acButtons, pnText);
                        threadFound = true;
                        break;
                    }
                }
                if (!threadFound) {
                    //Make card info thread a public thread in community mode
                    ThreadChannelAction thread = textChannel.createThreadChannel(threadName, !activeMap.isCommunityMode());
                    thread.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
                    if (!activeMap.isCommunityMode()) {
                        thread.setInvitable(false);
                    }
                    ThreadChannel new_thread = thread.complete();
                    
                    sendCardInfoToChannel(new_thread, playerPing, soText, soButtons, acText, acButtons, pnText);
                }
            } catch (Exception e) {
                BotLogger.log("Could not create Private Thread");
            }
        } else {
            MessageHelper.sendMessageToUser("Player: " + player.getUserName() + " not found", event != null ? event : buttonEvent);
        }
    }

    private static void sendCardInfoToChannel(MessageChannel privateChannel, String ping, String so, List<Button> soButtons, String ac, List<Button> acButtons, String pn) {
        String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
        String acPlayMsg = "_ _\nClick a button below to play an Action Card";
        String text = ping == null ? null : ping + "\n";
        MessageHelper.sendMessageToChannel(privateChannel, text);
        MessageHelper.sendMessageToChannel(privateChannel, so);
        List<MessageCreateData> messageList = getMessageObject(secretScoreMsg, soButtons);
        for (MessageCreateData message : messageList) {
            privateChannel.sendMessage(message).queue();
        }
        MessageHelper.sendMessageToChannel(privateChannel, ac);
        messageList = getMessageObject(acPlayMsg, acButtons);
        for (MessageCreateData message : messageList) {
            privateChannel.sendMessage(message).queue();
        }
        MessageHelper.sendMessageToChannel(privateChannel, pn);
    }

    private static List<MessageCreateData> getMessageObject(String message, List<Button> buttons) {
        buttons.removeIf(Objects::isNull);
        List<List<Button>> partitions = ListUtils.partition(buttons, 5);
        List<ActionRow> actionRows = new ArrayList<>();
        for (List<Button> partition : partitions) {
            actionRows.add(ActionRow.of(partition));
        }
        List<List<ActionRow>> partitionActionRows = ListUtils.partition(actionRows, 5);
        List<MessageCreateData> buttonMessages = new ArrayList<>();

        for (List<ActionRow> partitionActionRow : partitionActionRows) {
            buttonMessages.add(new MessageCreateBuilder()
                    .addContent(message)
                    .addComponents(partitionActionRow).build());
        }
        return buttonMessages;
    }

    private static void checkAndAddPNs(Map activeMap, Player player) {
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (Mapper.isColorValid(playerColor) && Mapper.isFaction(playerFaction)) {
            List<String> promissoryNotes = new ArrayList<>(Mapper.getPromissoryNotes(playerColor, playerFaction));
            for (Player player_ : activeMap.getPlayers().values()) {
                promissoryNotes.removeAll(player_.getPromissoryNotes().keySet());
                promissoryNotes.removeAll(player_.getPromissoryNotesInPlayArea());
            }
            promissoryNotes.removeAll(player.getPromissoryNotes().keySet());
            promissoryNotes.removeAll(player.getPromissoryNotesInPlayArea());
            promissoryNotes.removeAll(activeMap.getPurgedPN());
            if (!promissoryNotes.isEmpty()) {
                for (String promissoryNote : promissoryNotes) {
                    player.setPromissoryNote(promissoryNote);
                }
            }
        }
    }
}

package ti4.commands.cards;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import org.apache.commons.collections4.ListUtils;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import javax.annotation.CheckForNull;
import java.util.*;

public class CardsInfo extends CardsSubcommandData {

    public static final String CARDS_INFO = "Cards Info-";
    private static HashMap<Map, TextChannel> threadTextChannels = new HashMap<>();

    public CardsInfo() {
        super(Constants.INFO, "Resent all my cards in Private Message");
        addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to enable").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO, "Set TRUE to get card info as direct message also").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        sentUserCardInfo(event, activeMap, player);
    }

    public static void sentUserCardInfo(GenericCommandInteractionEvent event, Map activeMap, Player player) {
        sentUserCardInfo(event, activeMap, player, null);
    }

    public static void sentUserCardInfo(@CheckForNull GenericCommandInteractionEvent event, Map activeMap, Player player, @CheckForNull ButtonInteractionEvent buttonEvent) {
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
        sb.append("**Game: **").append(activeMap.getName()).append("\n");
        String color = player.getColor();
        sb.append(Helper.getFactionIconFromDiscord(player.getFaction()));
        sb.append("(").append(player.getFaction()).append(")");
        if (color != null) {
            sb.append(" (").append(color).append(")");
        }
        sb.append("\n");
        sb.append("**Secret Objectives:**").append("\n");
        int index = 1;
        List<Button> soButtons = new ArrayList<>();
        if (secretObjective != null) {
            HashMap<String, String> secretObjectivesJustNames = Mapper.getSecretObjectivesJustNames();
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                Integer idValue = so.getValue();
                String key = so.getKey();
                sb.append(index).append(". (").append(idValue).append(") - ").append(Helper.getEmojiFromDiscord("secretobjective")).append(Mapper.getSecretObjective(key)).append("\n");
                index++;
                String soName = secretObjectivesJustNames.get(key);
                if (soName != null) {
                    soButtons.add(Button.primary(Constants.SO_SCORE_FROM_HAND + idValue, "(" + idValue + ") " + soName));
                }
            }
        }
        sb.append("\n").append("**Scored Secret Objectives:**").append("\n");
        for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
            sb.append(index).append(". (").append(so.getValue()).append(") - ").append(Helper.getEmojiFromDiscord("secretobjective")).append(Mapper.getSecretObjective(so.getKey())).append("\n");
            index++;
        }
        soText = sb.toString();
        sb = new StringBuilder();

        sb.append("\n").append("**Action Cards:**").append("\n");
        index = 1;

        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                sb.append(index).append(". (").append(value).append(") - ").append(Helper.getEmojiFromDiscord("actioncard")).append(Mapper.getActionCard(key)).append("\n");
                index++;
                String ac_name = Mapper.getActionCardName(key);
                if (ac_name != null) {
                    acButtons.add(Button.danger(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name));
                }
            }
        }
        acText = sb.toString();
        sb = new StringBuilder();
        sb.append("\n").append("**Promissory Notes:**").append("\n");
        index = 1;
        LinkedHashMap<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes != null) {
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append(index).append(". (").append(pn.getValue()).append(") - ").append(Helper.getEmojiFromDiscord("pn"))
                            .append(Mapper.getPromissoryNote(pn.getKey(), longPNDisplay));
                    sb.append("\n");
                    index++;
                }
            }
            sb.append("\n");
            sb.append("\n").append("**PLAY AREA Promissory Notes:**").append("\n");
            for (java.util.Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append(index).append(". (").append(pn.getValue()).append(") - ").append(Helper.getEmojiFromDiscord("pn")).append(Mapper.getPromissoryNote(pn.getKey(), longPNDisplay));
                    sb.append("\n");
                    index++;
                }
            }
        }
        sb.append("--------------------\n");
        pnText = sb.toString();
        User userById = event != null ? event.getJDA().getUserById(player.getUserID()) : (buttonEvent != null ? buttonEvent.getJDA().getUserById(player.getUserID()) : null);
        if (userById != null) {
            String cardInfo = soText + "\n" + acText + "\n" + pnText;
            if (activeMap.isCommunityMode() && player.getChannelForCommunity() instanceof MessageChannel) {
                MessageHelper.sendMessageToChannel((MessageChannel) player.getChannelForCommunity(), cardInfo);
            } else {
                try {
                    Channel channel = event != null ? event.getChannel() : buttonEvent.getChannel();
                    if (channel == null) {
                        MessageHelper.sentToMessageToUser(event, cardInfo, userById);
                        BotLogger.log("Could not find channel");
                        return;
                    }
                    if (event != null) {
                        OptionMapping option = event.getOption(Constants.DM_CARD_INFO);
                        if (option != null && option.getAsBoolean()) {
                            MessageHelper.sentToMessageToUser(event, cardInfo, userById);
                        }
                    }
                    ChannelType type = channel.getType();

                    TextChannel textChannel = threadTextChannels.get(activeMap);
                    if (textChannel == null) {
                        String mainChannelName = activeMap.getName() + "-actions";
                        for (TextChannel textChannel_ : MapGenerator.jda.getTextChannels()) {

                            if (textChannel_.getName().equals(mainChannelName)) {
                                textChannel = textChannel_;
                                threadTextChannels.put(activeMap, textChannel);
                                break;
                            }
                        }

                        if (textChannel == null) {
                            if (ChannelType.GUILD_PUBLIC_THREAD.equals(type) || ChannelType.GUILD_PRIVATE_THREAD.equals(type)) {
                                IThreadContainer parentChannel = event != null ? event.getThreadChannel().getParentChannel() : buttonEvent.getThreadChannel().getParentChannel();
                                if (parentChannel instanceof TextChannel) {
                                    textChannel = (TextChannel) parentChannel;
                                } else {
                                    MessageHelper.sentToMessageToUser(event, cardInfo, userById);
                                    MessageHelper.sentToMessageToUser(event, "Please Execute info command in non thread channel", userById);
                                    return;
                                }
                            } else {
                                textChannel = event != null ? event.getTextChannel() : buttonEvent.getTextChannel();
                            }
                        }
                    }
                    List<ThreadChannel> threadChannels = textChannel.getThreadChannels();
                    boolean threadFound = false;
                    String threadName = CARDS_INFO + activeMap.getName() + "-" + player.getUserName().replaceAll("/", "");
                    String playerPing = threadName + " " + Helper.getPlayerPing(player);

                    String secretScoreMsg = "Your Secrets to Score";
                    String acPlayMsg = "Your Action Cards to play";
                    for (ThreadChannel threadChannel : threadChannels) {
                        if (threadChannel.getName().equals(threadName) && !threadChannel.isArchived()) {
                            String text = playerPing + "\n";
                            MessageHelper.sendMessageToChannel(threadChannel, text);
                            MessageHelper.sendMessageToChannel(threadChannel, soText);
                            List<Message> messageList = getMessageObject(secretScoreMsg, soButtons);
                            for (Message message : messageList) {
                                threadChannel.sendMessage(message).queue();
                            }
                            MessageHelper.sendMessageToChannel(threadChannel, acText);
                            messageList = getMessageObject(acPlayMsg, acButtons);
                            for (Message message : messageList) {
                                threadChannel.sendMessage(message).queue();
                            }
                            MessageHelper.sendMessageToChannel(threadChannel, pnText);
                            threadChannel.getManager().setInvitable(false);
                            threadFound = true;
                            break;
                        }
                    }
                    if (!threadFound) {
                        ThreadChannelAction threadChannel_ = textChannel.createThreadChannel(threadName, true);
                        threadChannel_.queue(msg -> {
                            String text = playerPing + "\n";
                            sendTextToChannel(msg, soText);
                            List<Message> messageList = getMessageObject(secretScoreMsg, soButtons);
                            for (Message message : messageList) {
                                msg.sendMessage(message).queue();
                            }
                            sendTextToChannel(msg, acText);
                            messageList = getMessageObject(acPlayMsg, acButtons);
                            for (Message message : messageList) {
                                msg.sendMessage(message).queue();
                            }
                            sendTextToChannel(msg, pnText);
                            ThreadChannelAction threadChannelAction = threadChannel_.setInvitable(false);
                        });
                    }
                } catch (Exception e) {
                    BotLogger.log("Could not create Private Thread");
                }
            }
        } else {
            MessageHelper.sentToMessageToUser(event != null ? event : buttonEvent, "Player: " + player.getUserName() + " not found");
        }
    }

    private static void sendTextToChannel(ThreadChannel msg, String text) {
        if (text.length() > 1500) {
            List<String> texts = new ArrayList<>();
            int index_ = 0;
            while (index_ < text.length()) {
                texts.add(text.substring(index_, Math.min(index_ + 1500, text.length())));
                index_ += 1500;
            }
            for (String msgText : texts) {
                msg.sendMessage(msgText).queue();
            }
        } else {
            msg.sendMessage(text).queue();
        }
    }

    private static List<Message> getMessageObject(String message, List<Button> buttons) {
        buttons.removeIf(Objects::isNull);
        List<List<Button>> partitions = ListUtils.partition(buttons, 5);
        List<ActionRow> actionRows = new ArrayList<>();
        for (List<Button> partition : partitions) {
            actionRows.add(ActionRow.of(partition));
        }
        List<List<ActionRow>> partitionActionRows = ListUtils.partition(actionRows, 5);
        List<Message> buttonMessages = new ArrayList<>();

        for (List<ActionRow> partitionActionRow : partitionActionRows) {
            buttonMessages.add(new MessageBuilder()
                    .append(message)
                    .setActionRows(partitionActionRow).build());
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

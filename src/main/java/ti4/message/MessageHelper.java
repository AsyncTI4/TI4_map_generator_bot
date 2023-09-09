package ti4.message;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import ti4.MapGenerator;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.helpers.Constants;
import ti4.helpers.DiscordWebhook;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.NotNull;

public class MessageHelper {
	interface MessageFunction{
		void run(Message msg);
	}

	public static void sendMessageToChannel(MessageChannel channel, String messageText) {
		splitAndSent(messageText, channel);
	}

	public static void sendMessageToChannelWithButtons(MessageChannel channel, String messageText, List<Button> buttons) {
		splitAndSent(messageText, channel, buttons);
	}

	private static void addFactionReactToMessage(Game activeGame, Player player, Message message) {
		Emoji reactionEmoji = Helper.getPlayerEmoji(activeGame, player, message);
		if (reactionEmoji != null) {
			message.addReaction(reactionEmoji).queue();
		}
	}

	public static void sendMessageToChannelWithFactionReact(MessageChannel channel, String messageText, Game activeGame, Player player, List<Button> buttons) {
		MessageFunction addFactionReact = (msg) -> addFactionReactToMessage(activeGame, player, msg);
		splitAndSentWithAction(messageText, channel, addFactionReact, buttons);
	}
	public static void sendMessageToChannelWithPersistentReacts(MessageChannel channel, String messageText, Game activeGame, List<Button> buttons, String whenOrAfter) {
		MessageFunction addFactionReact = (msg) -> {
			StringTokenizer players  = null;
			if (whenOrAfter != null && whenOrAfter.equalsIgnoreCase("when")) {
				if (activeGame.getLatestWhenMsg() != null && activeGame.getLatestWhenMsg() != "") {
					activeGame.getMainGameChannel().deleteMessageById(activeGame.getLatestWhenMsg()).queue();
				}
				activeGame.setLatestWhenMsg(msg.getId());
				players = new StringTokenizer(activeGame.getPlayersWhoHitPersistentNoWhen(), "_");
			} else {
				if (activeGame.getLatestAfterMsg() != null && activeGame.getLatestAfterMsg() != "") {
					activeGame.getMainGameChannel().deleteMessageById(activeGame.getLatestAfterMsg()).queue();
				}
				activeGame.setLatestAfterMsg(msg.getId());
				players = new StringTokenizer(activeGame.getPlayersWhoHitPersistentNoAfter(), "_");
			}
			while (players.hasMoreTokens()) {
				String player = players.nextToken();
				Player player_ = Helper.getPlayerFromColorOrFaction(activeGame, player);
				addFactionReactToMessage(activeGame, player_, msg);
			}


		};

		splitAndSentWithAction(messageText, channel, addFactionReact, buttons);
	}


	public static void sendMessageToChannelAndPin(MessageChannel channel, String messageText) {
		MessageFunction pin = (msg) -> msg.pin().queue();
		splitAndSentWithAction(messageText, channel, pin);
	}

	public static void sendFileToChannel(MessageChannel channel, File file) {
		if(channel.getName().contains("-actions")){
			String threadName = channel.getName().replace("-actions","")  + "-bot-map-updates";
			List<ThreadChannel> threadChannels = ((TextChannel) channel).getThreadChannels();
			for (ThreadChannel threadChannel_ : threadChannels) {
				if (threadChannel_.getName().equals(threadName)) {
					channel = (MessageChannel) threadChannel_;
				}
			}
		}
		FileUpload fileUpload = FileUpload.fromData(file);
		channel.sendFiles(fileUpload).queue();
	}
	public static void sendFileToChannel(MessageChannel channel, File file, boolean SCPlay) {
		FileUpload fileUpload = FileUpload.fromData(file);
		channel.sendFiles(fileUpload).queue();
	}
	public static void sendFileToChannelWithButtonsAfter(MessageChannel channel, File file, String message, List<Button> buttons) {
		if(channel.getName().contains("-actions")){
			String threadName = channel.getName().replace("-actions","")  + "-bot-map-updates";
			List<ThreadChannel> threadChannels = ((TextChannel) channel).getThreadChannels();
			for (ThreadChannel threadChannel_ : threadChannels) {
				if (threadChannel_.getName().equals(threadName)) {
					channel = (MessageChannel) threadChannel_;
				}
			}
		}
		FileUpload fileUpload = FileUpload.fromData(file);
		channel.sendFiles(fileUpload).queue();
		splitAndSent(message, channel, buttons);
	}

	public static void replyToMessage(GenericInteractionCreateEvent event, String messageText) {
		splitAndSent(messageText, event.getMessageChannel());
	}

	public static void replyToMessage(GenericInteractionCreateEvent event, File file) {
		replyToMessage(event, file, false, null, false);
	}

	public static void replyToMessage(GenericInteractionCreateEvent event, File file, boolean forceShowMap) {
		replyToMessage(event, file, forceShowMap, null, false);
	}

	public static void replyToMessage(GenericInteractionCreateEvent event, File file, boolean forceShowMap, String messageText, boolean pinMessage) {
		try {
			if (forceShowMap && event.getChannel() instanceof MessageChannel) {
				sendMessageWithFile((MessageChannel) event.getChannel(), file, messageText, pinMessage);
				return;
			}
			String gameName = event.getChannel().getName();
			gameName = gameName.replace(Constants.CARDS_INFO_THREAD_PREFIX, "");
			gameName = gameName.substring(0, gameName.indexOf("-"));
			if (event.getChannel() instanceof MessageChannel) {
				sendMessageWithFile((MessageChannel)event.getChannel(), file, messageText, pinMessage);
			}

		} catch (Exception e) {
			replyToMessage(event, "Could not send response, use /show_game or contact Admins or Bothelper");
		}
	}

	public static void sendMessageWithFile(MessageChannel channel, File file, String messageText, boolean pinMessage) {
		
		if(channel.getName().contains("-actions")){
			String threadName = channel.getName().replace("-actions","")  + "-bot-map-updates";
			List<ThreadChannel> threadChannels = ((TextChannel) channel).getThreadChannels();
			for (ThreadChannel threadChannel_ : threadChannels) {
				if (threadChannel_.getName().equals(threadName)) {
					channel = (MessageChannel) threadChannel_;
				}
			}
		}
		
		FileUpload fileUpload = FileUpload.fromData(file);
		MessageCreateBuilder message = new MessageCreateBuilder();
		if (messageText != null) {
			message.addContent(messageText);
		}
		MessageCreateData messageObject = message.addFiles(fileUpload).build();
		channel.sendMessage(messageObject).queue(x -> {
			if (pinMessage) x.pin().queue();
		});
	}

	private static void splitAndSent(String messageText, MessageChannel channel) {
		splitAndSent(messageText, channel, null);
	}

	private static void splitAndSent(String messageText, MessageChannel channel, List<Button> buttons) {
		splitAndSentWithAction(messageText, channel, null, buttons);
	}

	private static void splitAndSentWithAction(String messageText, MessageChannel channel, MessageFunction restAction) {
		splitAndSentWithAction(messageText, channel, restAction, null);
	}

	private static void splitAndSentWithAction(String messageText, MessageChannel channel, MessageFunction restAction, List<Button> buttons) {
		if (channel == null) {
			return;
		}

		Iterator<MessageCreateData> iterator = getMessageCreateDataObjects(messageText, buttons).iterator();
		while (iterator.hasNext()) {
			MessageCreateData messageCreateData = iterator.next();
			if (iterator.hasNext()) { //not  message
				channel.sendMessage(messageCreateData).queue(null, (error) -> BotLogger.log(getRestActionFailureMessage(channel, messageText, error)));
			} else { //last message, do action
				channel.sendMessage(messageCreateData).queue(complete -> {

					if(messageText.contains("Use buttons to do your turn") || messageText.contains("Use buttons to end turn"))
					{
						String gameName = channel.getName();
						gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
						gameName = gameName.substring(0, gameName.indexOf("-"));
						Game activeGame = GameManager.getInstance().getGame(gameName);
						if(!activeGame.isFoWMode()){
							activeGame.setLatestTransactionMsg(complete.getId());
						}
						
					}
					if(messageText.toLowerCase().contains("up next") && messageText.contains("#"))
					{
						String gameName = channel.getName();
						gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
						gameName = gameName.substring(0, gameName.indexOf("-"));
						Game activeGame = GameManager.getInstance().getGame(gameName);
						if(!activeGame.isFoWMode()){
							if(activeGame.getLatestUpNextMsg()!= null && !activeGame.getLatestUpNextMsg().equalsIgnoreCase("")){
								String id = activeGame.getLatestUpNextMsg().split("_")[0];
								String message = activeGame.getLatestUpNextMsg().substring(activeGame.getLatestUpNextMsg().indexOf("_")+1, activeGame.getLatestUpNextMsg().length()).replace("#", "");
								message = message.replace("UP NEXT", "started their turn");
								
								activeGame.getActionsChannel().editMessageById(id, message).queue(null, (error) -> BotLogger.log(getRestActionFailureMessage(channel, messageText, error)));
							}
							
							activeGame.setLatestUpNextMsg(complete.getId()+"_"+messageText);
							
						}


					}
					
					if (restAction != null) restAction.run(complete);
				}, (error) -> BotLogger.log(getRestActionFailureMessage(channel, messageText, error)));
			}
		}
	}

	private static String getRestActionFailureMessage(MessageChannel channel, String messageText, Throwable error) {
		return channel.getAsMention() + "  RestAction Failure within MessageHelper.splitAndSentWithAction:\nMessageText: " + messageText + "\n```" + error.getMessage() + "```";
	}

	/**
	 * Send a private message to the player.
	 *
	 * @param player      Player to send a message to
	 * @param activeGame   Active map
	 * @param event       Event that caused the message
	 * @param messageText Message to send
	 * @param failText    Feedback if the message failed to send
	 * @param successText Feedback if the message successfully sent
	 * @return True if the message was send successfully, false otherwise
	 */
	public static boolean sendPrivateMessageToPlayer(Player player, Game activeGame, GenericInteractionCreateEvent event, String messageText, String failText, String successText) {
		return sendPrivateMessageToPlayer(player, activeGame, event.getMessageChannel(), messageText, failText, successText);
	}

	/**
	 * Send a private message to the player.
	 * <p>
	 * This implementation does not provide feedback
	 *
	 * @param player      Player to send a message to
	 * @param activeGame   Active map
	 * @param messageText Message to send
	 * @return True if the message was send successfully, false otherwise
	 */
	public static boolean sendPrivateMessageToPlayer(Player player, Game activeGame, String messageText) {
		return sendPrivateMessageToPlayer(player, activeGame, (MessageChannel) null, messageText, null, null);
	}

	/**
	 * Send a private message to the player.
	 *
	 * @param player          Player to send a message to
	 * @param activeGame       Active map
	 * @param feedbackChannel Channel to send feedback to
	 * @param messageText     Message to send
	 * @param failText        Feedback if the message failed to send
	 * @param successText     Feedback if the message successfully sent
	 * @return True if the message was send successfully, false otherwise
	 */
	public static boolean sendPrivateMessageToPlayer(Player player, Game activeGame, MessageChannel feedbackChannel, String messageText, String failText, String successText) {
        if (messageText == null || messageText.length() == 0) return true; // blank message counts as a success
		User user = MapGenerator.jda.getUserById(player.getUserID());
		if (user == null) {
			sendMessageToChannel(feedbackChannel, failText);
			return false;
		} else {
			MessageChannel privateChannel = player.getPrivateChannel();
			if (privateChannel == null) {
				sendMessageToUser(activeGame.getName() + " " + messageText, user);
			} else {
				sendMessageToChannel(privateChannel, messageText);
			}
			sendMessageToChannel(feedbackChannel, successText);
			return true;
		}
	}

	public static boolean privatelyPingPlayerList(List<Player> players, Game activeGame, String message) {
		return privatelyPingPlayerList(players, activeGame, (MessageChannel) null, message, null, null);
	}

	public static boolean privatelyPingPlayerList(List<Player> players, Game activeGame, MessageChannel feedbackChannel, String message, String failText, String successText) {
		int count = 0;
		for (Player player : players) {
			String playerRepresentation = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true);
			boolean success = sendPrivateMessageToPlayer(player, activeGame, feedbackChannel, playerRepresentation + message, failText, successText);
			if (success) count++;
		}
		return count == players.size();
	}

    public static void sendMessageToUser(String messageText, GenericInteractionCreateEvent event) {
        sendMessageToUser(messageText, event.getUser());
    }

    public static void sendMessageToUser(String messageText, User user) {
        user.openPrivateChannel().queue(channel -> splitAndSent(messageText, channel));
    }

    /**
     * @param player Player to send the messageText
     * @param activeGame Map/Game the player is in
     * @param messageText messageText - handles large text ()>1500 chars)
     */
    public static void sendMessageToPlayerCardsInfoThread(@NotNull Player player, @NotNull Game activeGame, String messageText) {
        //GET CARDS INFO THREAD
        ThreadChannel threadChannel = player.getCardsInfoThread(activeGame);
        if (threadChannel == null) {
            BotLogger.log("`MessageHelper.sendMessageToPlayerCardsInfoThread` - could not find or create Cards Info thread for player " + player.getUserName() + " in game " + activeGame.getName());
            return;
        }

        //SEND MESSAGES
		if (messageText == null || messageText.isEmpty()) return;
        for (String text : splitLargeText(messageText, 2000)) {
            threadChannel.sendMessage(text).queue();
        }
    }

	/**
	 * Given a text string and a maximum length, will return a List<String> split by
	 * either the max length or the last newline "\n"
	 *
	 * @param messageText any non-null, non-empty string
	 * @param maxLength   maximum length, any positive integer
	 * @return
	 */
	private static List<String> splitLargeText(String messageText, int maxLength) {
		List<String> texts = new ArrayList<>();
		if (messageText == null || messageText.isEmpty()) return Collections.emptyList();
		int messageLength = messageText.length();
        if (messageLength <= maxLength) return Collections.singletonList(messageText);
		int index = 0;
		while (index < messageLength) {
			String nextChars = messageText.substring(index, Math.min(index + maxLength, messageLength));
			int lastNewLineIndex = nextChars.lastIndexOf("\n") + 1; // number of chars until right after the last \n
			String textToAdd = "";
			if (lastNewLineIndex > 0) {
				textToAdd = nextChars.substring(0, lastNewLineIndex);
				index += lastNewLineIndex;
			} else {
				textToAdd = nextChars;
				index += nextChars.length();
			}
			texts.add(textToAdd);
		}
		return texts;
	}

    /**
	 * @message Message to send - can be large or null/empty
	 * @buttons List of Button - can be large or null/empty
	 * <p></p>
	 * Example of use:
	 * <pre>
	* {@code
		for (MessageCreateData messageData : getMessageObject(message, buttons)) {
			channel.sendMessage(messageData).queue();
		}
	* </pre>
     */
    public static List<MessageCreateData> getMessageCreateDataObjects(String message, List<Button> buttons) {
		List<MessageCreateData> messageCreateDataList = new ArrayList<>();

		List<List<ActionRow>> partitionedButtons = getPartitionedButtonLists(buttons);
		Iterator<List<ActionRow>> buttonIterator = partitionedButtons.iterator();

		List<String> messageList = splitLargeText(message, 2000);
		Iterator<String> messageIterator = messageList.iterator();

		while (messageIterator.hasNext()) {
			String smallMessage = messageIterator.next();

			//More messages exists, so just frontload the plain messages
			if (messageIterator.hasNext() && smallMessage != null && !smallMessage.isEmpty()) {
				messageCreateDataList.add(new MessageCreateBuilder().addContent(smallMessage).build());

			//We are at the last message, so try and add the first row of buttons
			} else if (!messageIterator.hasNext() && smallMessage != null && !smallMessage.isEmpty()) {
				MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
				messageCreateBuilder.addContent(smallMessage);

				//add first row of buttons if it exists
				if (buttonIterator.hasNext()) {
					List<ActionRow> actionRows = buttonIterator.next();
					if (actionRows != null && !actionRows.isEmpty()) {
						messageCreateBuilder.addComponents(actionRows);
					}
				}
				messageCreateDataList.add(messageCreateBuilder.build());
			}
		}

		//ADD REMAINING BUTTONS IF THEY EXIST
		while (buttonIterator.hasNext()) {
			List<ActionRow> actionRows = buttonIterator.next();
			if (actionRows != null && !actionRows.isEmpty()) {
				messageCreateDataList.add(new MessageCreateBuilder().addComponents(actionRows).build());
			}
		}
        return messageCreateDataList;
    }

	private static List<List<ActionRow>> getPartitionedButtonLists(List<Button> buttons) {
		List<List<ActionRow>> partitionedButtonRows = new ArrayList<>();
		if (buttons == null || buttons.isEmpty()) return partitionedButtonRows;
		try {
			buttons.removeIf(Objects::isNull);
		} catch (Exception e) {
			//Do nothing
		}
		List<List<Button>> partitions = ListUtils.partition(buttons, 5);
		List<ActionRow> buttonRows = new ArrayList<>();
		for (List<Button> partition : partitions) {
			buttonRows.add(ActionRow.of(partition));
		}
		partitionedButtonRows = ListUtils.partition(buttonRows, 5);
		return partitionedButtonRows;
	}

    public static void sendMessageToThread(MessageChannelUnion channel, String threadName, String messageToSend) {
		if (channel == null || threadName == null || messageToSend == null || threadName.isEmpty() || messageToSend.isEmpty()) return;
        if (channel instanceof TextChannel) {
			Helper.checkThreadLimitAndArchive(channel.asGuildMessageChannel().getGuild());
            channel.asTextChannel().createThreadChannel(threadName).setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR).queueAfter(500, TimeUnit.MILLISECONDS, t -> MessageHelper.sendMessageToChannel(t, messageToSend));
        } else if (channel instanceof ThreadChannel) {
            MessageHelper.sendMessageToChannel(channel, messageToSend);
        }
    }

	public static void sendMessageEmbedsToThread(MessageChannelUnion channel, String threadName, List<MessageEmbed> embeds) {
		if (channel == null || threadName == null || embeds == null || threadName.isEmpty() || embeds.isEmpty()) return;
		if (channel instanceof TextChannel) {
			Helper.checkThreadLimitAndArchive(channel.asGuildMessageChannel().getGuild());
            channel.asTextChannel().createThreadChannel(threadName).setAutoArchiveDuration(AutoArchiveDuration.TIME_1_HOUR)
				.queueAfter(500, TimeUnit.MILLISECONDS, t -> {
					for (List<MessageEmbed> messageEmbeds_ : ListUtils.partition(embeds, 10)) { //max 10 embeds per message
					t.sendMessageEmbeds(messageEmbeds_).queue();
					}
				});
        } else if (channel instanceof ThreadChannel) {
            for (List<MessageEmbed> messageEmbeds_ : ListUtils.partition(embeds, 10)) { //max 10 embeds per message
					channel.sendMessageEmbeds(messageEmbeds_).queue();
				};
        }
	}

	public static void sendMessageEmbedsToCardsInfoThread(Game activeGame, Player player, List<MessageEmbed> embeds) {
		ThreadChannel channel = player.getCardsInfoThread(activeGame);
		if (channel == null || embeds == null || embeds.isEmpty()) return;
        for (List<MessageEmbed> messageEmbeds_ : ListUtils.partition(embeds, 10)) { //max 10 embeds per message
			channel.sendMessageEmbeds(messageEmbeds_).queue();
		};
        
	}

    public static void sendMessageToBotLogWebhook(String message) {
        if (!MapGenerator.guildPrimary.getId().equals("943410040369479690")) return; //Only run in Prod
        DiscordWebhook webhook = new DiscordWebhook("https://discord.com/api/webhooks/1106562763708432444/AK5E_Nx3Jg_JaTvy7ZSY7MRAJBoIyJG8UKZ5SpQKizYsXr57h_VIF3YJlmeNAtuKFe5v");
		webhook.setContent(message);
		try {
			webhook.execute();
		} catch (Exception e) {
			// Do nothing
		}
    }
}

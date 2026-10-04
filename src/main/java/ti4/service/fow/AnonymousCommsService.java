package ti4.service.fow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.cron.AnonymousCommsArchiveCron;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.discord.utility.DiscordChannelUtility;
import ti4.discord.utility.DiscordErrorUtility;
import ti4.discord.utility.DiscordThreadUtility;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.AliasHandler;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class AnonymousCommsService {

    public static final String HIDE_HOURS_KEY = "anonCommsHideHours";
    public static final String SETTINGS_BUTTON = "anonCommsSettings~MDL";
    static final int DEFAULT_HIDE_HOURS = 3;
    static final int MAX_HIDE_HOURS = 12;
    static final String REPLY_PREFIX = "anonCommsReply_";
    static final String OPEN_PREFIX = "anonCommsOpen_";
    static final String INBOX = "inbox";
    static final String INBOX_KEY_PREFIX = "anonCommsInbox_";
    static final String THREAD_KEY_PREFIX = "anonCommsThread_";
    static final String PARTNERS_KEY_PREFIX = "anonCommsPartners_";
    private static final String MODAL_SUFFIX = "~MDL";
    private static final String REPLY_RESOLVE_PREFIX = "anonCommsReplyResolve_";
    private static final String SETTINGS_RESOLVE = "anonCommsSettingsResolve";
    private static final String DELETE_MANAGED_BUTTON = "anonCommsDeleteManaged";
    private static final String MESSAGE_INPUT = "message";
    private static final String HOURS_INPUT = "hours";
    private static final String PARTNER_SEPARATOR = "-";
    private static final Pattern THREAD_NAME = Pattern.compile("^\\w+-comms-(\\w+)-private$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TO_PREFIX =
            Pattern.compile("^to(\\w+)[^\\w\\s]*\\s+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern USER_MENTION = Pattern.compile("<@!?\\d+>");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&\\d+>");
    private static final Pattern MASS_MENTION = Pattern.compile("@(everyone|here)");
    private static final int PREVIEW_LENGTH = 150;
    private static final int MESSAGE_LIMIT = 2000;
    private static final int MODAL_MESSAGE_LIMIT = 1900;
    private static final int BUTTONS_PER_ROW = 5;
    private static final int MAX_ROWS = 5;
    private static final Map<String, ThreadActivity> LAST_ACTIVITY = new ConcurrentHashMap<>();
    private static final AtomicBoolean CRON_REGISTERED = new AtomicBoolean();

    record ThreadActivity(String gameName, Instant lastMessage) {}

    public static boolean isActive(Game game) {
        return game.isFowMode() && game.getFowOption(FOWOption.ANONYMOUS_COMMS);
    }

    public static boolean isCommsThread(Channel channel) {
        return channel instanceof ThreadChannel && isCommsThreadName(channel.getName());
    }

    static boolean isCommsThreadName(String name) {
        return THREAD_NAME.matcher(name).matches();
    }

    @Nullable
    static String threadTarget(String threadName) {
        Matcher matcher = THREAD_NAME.matcher(threadName);
        return matcher.matches() ? matcher.group(1).toLowerCase() : null;
    }

    public static boolean handleMessage(Game game, @Nullable Player sender, Message message) {
        if (!isActive(game) || sender == null || !(message.getChannel() instanceof ThreadChannel thread)) {
            return false;
        }
        if (!sender.equals(ownerOf(game, thread))) return false;
        List<String> attachments = message.getAttachments().stream()
                .map(Message.Attachment::getUrl)
                .toList();
        handleTyped(game, sender, thread, message.getContentRaw(), attachments, message.getReferencedMessage());
        return true;
    }

    public static boolean handleTyped(
            Game game,
            Player sender,
            ThreadChannel thread,
            String text,
            List<String> attachments,
            @Nullable Message replyTo) {
        String target = threadTarget(thread.getName());
        if (target == null) return false;
        if (INBOX.equals(target)) return handleInboxMessage(game, sender, thread, text, attachments, replyTo);
        Player partner = playerWithColor(game, target);
        if (partner == null) {
            post(thread, "No player plays " + target + " in this game anymore.");
            return false;
        }
        return relay(game, sender, partner, text, attachments, thread);
    }

    private static boolean handleInboxMessage(
            Game game,
            Player sender,
            ThreadChannel inbox,
            String text,
            List<String> attachments,
            @Nullable Message replyTo) {
        Player repliedTo = replyTo == null ? null : playerWithColor(game, replyTargetColor(replyTo));
        if (repliedTo != null) return relay(game, sender, repliedTo, text, attachments, inbox);

        Matcher to = TO_PREFIX.matcher(text.trim());
        if (!to.matches()) {
            post(
                    inbox,
                    "Start your message with `to<color>`, e.g. `tored want to trade?`, or reply to a 📨 message to"
                            + " answer its sender.");
            return false;
        }
        Player receiver = playerWithColor(game, to.group(1));
        if (receiver == null) {
            post(inbox, "No player plays `" + to.group(1) + "`. Address comms by color, e.g. `tored hello`.");
            return false;
        }
        return relay(game, sender, receiver, to.group(2), attachments, inbox);
    }

    @Nullable
    static String replyTargetColor(Message message) {
        if (!message.getAuthor().isBot()) return null;
        return message.getComponentTree().findAll(Button.class).stream()
                .map(Button::getCustomId)
                .filter(id -> id != null && id.startsWith(REPLY_PREFIX))
                .map(AnonymousCommsService::colorFromReplyButton)
                .findFirst()
                .orElse(null);
    }

    static String colorFromReplyButton(String buttonId) {
        return StringUtils.removeEnd(StringUtils.removeStart(buttonId, REPLY_PREFIX), MODAL_SUFFIX);
    }

    @Nullable
    static Player playerWithColor(Game game, @Nullable String colorName) {
        if (StringUtils.isBlank(colorName)) return null;
        String color = AliasHandler.resolveColor(colorName.toLowerCase());
        return game.getRealPlayers().stream()
                .filter(player -> color.equalsIgnoreCase(player.getColor()))
                .findFirst()
                .orElse(null);
    }

    public static boolean canTalk(Game game, Player sender, Player receiver) {
        return FowCommunicationThreadService.getCommPartners(game, sender).contains(receiver);
    }

    static boolean relay(
            Game game, Player sender, Player receiver, String text, List<String> attachments, MessageChannel source) {
        if (receiver.equals(sender)) {
            post(source, "You cannot send comms to yourself.");
            return false;
        }
        if (!canTalk(game, sender, receiver)) {
            post(source, noCommsNotice(game, sender, receiver));
            return false;
        }
        String body = sanitize(withAttachments(text, attachments));
        if (body.isBlank()) return false;

        ThreadChannel senderThread = partnerThread(game, sender, receiver);
        ThreadChannel receiverThread = partnerThread(game, receiver, sender);
        ThreadChannel receiverInbox = inbox(game, receiver);
        if (senderThread == null || receiverThread == null || receiverInbox == null) {
            post(source, "Your message could not be delivered to " + colorName(receiver) + ". Please ping the GM.");
            return true;
        }

        boolean typedInConversation = senderThread.getId().equals(source.getId());
        if (!typedInConversation) post(senderThread, "📤 **You:** " + body);
        Message delivered = post(receiverThread, ColorEmojis.getColorEmoji(sender.getColor()) + " " + body);
        notifyInbox(receiver, receiverInbox, sender, body, delivered);
        if (!typedInConversation) {
            post(source, "📤 Sent to " + colorName(receiver) + " · " + senderThread.getAsMention());
        }
        touch(game, senderThread);
        touch(game, receiverThread);
        return true;
    }

    private static String noCommsNotice(Game game, Player sender, Player receiver) {
        List<String> partners = partnerColors(game, sender);
        String reachable = partners.isEmpty()
                ? "You have no comms with anyone right now."
                : "You can reach: "
                        + String.join(
                                ", ",
                                partners.stream()
                                        .map(AnonymousCommsService::colorName)
                                        .toList())
                        + ".";
        return "You do not have comms with " + colorName(receiver) + " right now. " + reachable;
    }

    private static void notifyInbox(
            Player receiver, ThreadChannel inbox, Player sender, String body, @Nullable Message delivered) {
        String preview = StringUtils.abbreviate(body.replace('\n', ' '), PREVIEW_LENGTH);
        String link = delivered == null ? "" : "\n-# [Open conversation](" + delivered.getJumpUrl() + ")";
        String content = receiver.getPing() + " 📨 " + colorName(sender) + ": " + preview + link;
        sendWithButtons(inbox, content, List.of(replyButton(sender), openButton(sender)));
    }

    private static Button replyButton(Player partner) {
        return Buttons.blue(REPLY_PREFIX + partner.getColor() + MODAL_SUFFIX, "Reply to " + colorTitle(partner));
    }

    private static Button openButton(Player partner) {
        return Buttons.gray(OPEN_PREFIX + partner.getColor(), "Open " + colorTitle(partner));
    }

    static String sanitize(String text) {
        String withoutUsers = USER_MENTION.matcher(text).replaceAll("@someone");
        String withoutRoles = ROLE_MENTION.matcher(withoutUsers).replaceAll("@role");
        return MASS_MENTION.matcher(withoutRoles).replaceAll("@\u200b$1");
    }

    static String withAttachments(String text, List<String> attachments) {
        if (attachments.isEmpty()) return text;
        return (text + "\n" + String.join("\n", attachments)).trim();
    }

    private static String colorName(Player player) {
        return colorName(player.getColor());
    }

    private static String colorName(String color) {
        return ColorEmojis.getColorEmojiWithName(color);
    }

    private static String colorTitle(Player player) {
        return StringUtils.capitalize(player.getColor());
    }

    @Nullable
    static Player ownerOf(Game game, ThreadChannel thread) {
        String parentId = thread.getParentChannel().getId();
        return game.getRealPlayers().stream()
                .filter(player -> parentId.equals(player.getPrivateChannelID()))
                .findFirst()
                .orElse(null);
    }

    static String inboxKey(Player owner) {
        return INBOX_KEY_PREFIX + owner.getColor();
    }

    static String threadKey(Player owner, Player partner) {
        return THREAD_KEY_PREFIX + owner.getColor() + "_" + partner.getColor();
    }

    @Nullable
    public static ThreadChannel inbox(Game game, Player owner) {
        String name = game.getName() + "-comms-" + INBOX + "-private";
        return findOrCreateThread(
                game, owner, inboxKey(owner), name, AutoArchiveDuration.TIME_1_WEEK, inboxIntro(game));
    }

    @Nullable
    public static ThreadChannel partnerThread(Game game, Player owner, Player partner) {
        String name = game.getName() + "-comms-" + partner.getColor() + "-private";
        String intro = "## Comms with " + colorName(partner) + "\nType here to talk to " + colorName(partner)
                + ". Your messages are relayed by the bot, so neither of you sees who is behind the other color.";
        return findOrCreateThread(
                game, owner, threadKey(owner, partner), name, AutoArchiveDuration.TIME_24_HOURS, intro);
    }

    private static String inboxIntro(Game game) {
        return "## Comms inbox\n"
                + "Messages from the colors you have comms with arrive here. The bot relays every message, so nobody"
                + " can see which Discord user plays which color.\n"
                + "- **Send:** `to<color> message`, e.g. `tored want to trade?`\n"
                + "- **Reply:** use Discord's reply on a 📨 message, or its **Reply** button\n"
                + "- **Conversations:** each color gets its own thread in this channel, where you type without a"
                + " prefix. Quiet threads hide after " + hideHours(game) + " hours and come back with the next"
                + " message.";
    }

    @Nullable
    private static ThreadChannel findOrCreateThread(
            Game game, Player owner, String key, String name, AutoArchiveDuration duration, String intro) {
        ThreadChannel existing = findThread(owner, game.getStoredValue(key));
        if (existing != null) return existing;
        TextChannel privateChannel = owner.getPrivateChannel();
        if (privateChannel == null) return null;
        try {
            ThreadChannel created = privateChannel
                    .createThreadChannel(DiscordThreadUtility.fitThreadName(name))
                    .setAutoArchiveDuration(duration)
                    .complete();
            game.setStoredValue(key, created.getId());
            post(created, withMemberTags(game, owner, intro));
            return created;
        } catch (Exception e) {
            BotLogger.error("Could not create anonymous comms thread " + name, e);
            return null;
        }
    }

    private static String withMemberTags(Game game, Player owner, String intro) {
        String tags = (owner.getPing() + " " + GMService.gmPing(game)).trim();
        return tags.isEmpty() ? intro : tags + "\n" + intro;
    }

    @Nullable
    static ThreadChannel findThread(Player owner, @Nullable String threadId) {
        if (StringUtils.isBlank(threadId)) return null;
        TextChannel privateChannel = owner.getPrivateChannel();
        if (privateChannel == null) return null;
        try {
            return DiscordChannelUtility.retrieveThreadChannelById(privateChannel.getGuild(), threadId)
                    .complete();
        } catch (Exception e) {
            if (!DiscordErrorUtility.isUnknownChannelError(e)) {
                BotLogger.error("Could not retrieve anonymous comms thread " + threadId, e);
            }
            return null;
        }
    }

    @Nullable
    private static Message post(MessageChannel channel, String text) {
        try {
            unarchive(channel);
            Message first = null;
            for (String part : MessageHelper.splitLargeText(text, MESSAGE_LIMIT)) {
                Message sent = channel.sendMessage(part).complete();
                if (first == null) first = sent;
            }
            return first;
        } catch (Exception e) {
            BotLogger.error("Could not post to anonymous comms channel " + channel.getName(), e);
            return null;
        }
    }

    private static void sendWithButtons(MessageChannel channel, String text, List<Button> buttons) {
        try {
            unarchive(channel);
            channel.sendMessage(text).addComponents(rowsOf(buttons)).complete();
        } catch (Exception e) {
            BotLogger.error("Could not post to anonymous comms channel " + channel.getName(), e);
        }
    }

    private static List<ActionRow> rowsOf(List<Button> buttons) {
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size() && rows.size() < MAX_ROWS; i += BUTTONS_PER_ROW) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + BUTTONS_PER_ROW, buttons.size()))));
        }
        return rows;
    }

    private static void unarchive(MessageChannel channel) {
        if (channel instanceof ThreadChannel thread && thread.isArchived()) {
            thread.getManager().setArchived(false).complete();
        }
    }

    public static void refreshPartners(Game game) {
        if (!isActive(game)) return;
        for (Player player : game.getRealPlayers()) {
            if (player.getPrivateChannel() == null) continue;
            List<String> partners = partnerColors(game, player);
            List<String> previous = storedPartners(game, player);
            boolean hasInbox = !game.getStoredValue(inboxKey(player)).isEmpty();
            if (hasInbox && partners.equals(previous)) continue;
            ThreadChannel inbox = inbox(game, player);
            if (inbox == null) continue;
            announcePartnerChanges(inbox, previous, partners);
            game.setStoredValue(PARTNERS_KEY_PREFIX + player.getColor(), String.join(PARTNER_SEPARATOR, partners));
        }
    }

    public static List<String> partnerColors(Game game, Player player) {
        return FowCommunicationThreadService.getCommPartners(game, player).stream()
                .filter(Player::isRealPlayer)
                .map(Player::getColor)
                .sorted()
                .toList();
    }

    private static List<String> storedPartners(Game game, Player player) {
        String stored = game.getStoredValue(PARTNERS_KEY_PREFIX + player.getColor());
        return Arrays.stream(stored.split(PARTNER_SEPARATOR))
                .filter(StringUtils::isNotBlank)
                .sorted()
                .toList();
    }

    private static void announcePartnerChanges(ThreadChannel inbox, List<String> previous, List<String> partners) {
        List<String> gained =
                partners.stream().filter(color -> !previous.contains(color)).toList();
        List<String> lost =
                previous.stream().filter(color -> !partners.contains(color)).toList();
        StringBuilder message = new StringBuilder();
        if (!gained.isEmpty())
            message.append("📡 Comms opened with ").append(colorList(gained)).append(".\n");
        if (!lost.isEmpty())
            message.append("📴 Comms lost with ").append(colorList(lost)).append(".\n");
        if (partners.isEmpty()) {
            post(
                    inbox,
                    message.append("You have no comms with anyone right now.").toString());
            return;
        }
        message.append("You can reach ").append(colorList(partners)).append('.');
        List<Button> buttons = partners.stream()
                .map(color -> Buttons.gray(OPEN_PREFIX + color, "Open " + StringUtils.capitalize(color)))
                .toList();
        sendWithButtons(inbox, message.toString(), buttons);
    }

    private static String colorList(List<String> colors) {
        return String.join(
                ", ", colors.stream().map(AnonymousCommsService::colorName).toList());
    }

    public static boolean postToConversation(Game game, Player owner, Player partner, String text) {
        ThreadChannel thread = partnerThread(game, owner, partner);
        if (thread == null || post(thread, text) == null) return false;
        touch(game, thread);
        return true;
    }

    public static void postToConversationLater(Game game, Player owner, Player partner, String text) {
        String gameName = game.getName();
        String ownerFaction = owner.getFaction();
        String partnerFaction = partner.getFaction();
        ExecutorServiceManager.runAsync(
                "anonymous comms post",
                ExecutionLockManager.wrapWithLockAndRelease(gameName, ExecutionLockType.WRITE, () -> {
                    ManagedGame managedGame = GameManager.getManagedGame(gameName);
                    if (managedGame == null) return;
                    Game current = managedGame.getGame();
                    Player currentOwner = current.getPlayerFromColorOrFaction(ownerFaction);
                    Player currentPartner = current.getPlayerFromColorOrFaction(partnerFaction);
                    if (currentOwner == null || currentPartner == null) return;
                    if (!postToConversation(current, currentOwner, currentPartner, text)) {
                        MessageHelper.sendMessageToChannel(currentOwner.getCorrectChannel(), text);
                    }
                    GameManager.save(current, "Anonymous comms");
                }));
    }

    public static void offerManagedThreadCleanup(Game game) {
        MessageHelper.sendMessageToChannelWithButton(
                GMService.getGMChannel(game),
                "Anonymous comms is on. Any managed comm threads from before still show which Discord user plays"
                        + " which color to the players in them.",
                Buttons.red(DELETE_MANAGED_BUTTON, "Delete Managed Comm Threads"));
    }

    @ButtonHandler(value = DELETE_MANAGED_BUTTON, save = false)
    public static void deleteManagedThreads(ButtonInteractionEvent event, Game game) {
        FowCommunicationThreadService.deleteManagedThreads(game, event.getChannel());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    public static String threadState(Game game, Player owner, Player partner) {
        ThreadChannel thread = findThread(owner, game.getStoredValue(threadKey(owner, partner)));
        if (thread == null) return "missing";
        if (!canTalk(game, owner, partner)) return "lost";
        return thread.isArchived() ? "archived" : "active";
    }

    public static List<String> deleteAllThreads(Game game) {
        List<String> deleted = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            List<String> keys = game.getStoredValueMap().keySet().stream()
                    .filter(key ->
                            key.equals(inboxKey(player)) || key.startsWith(THREAD_KEY_PREFIX + player.getColor() + "_"))
                    .toList();
            for (String key : keys) {
                ThreadChannel thread = findThread(player, game.getStoredValue(key));
                if (thread != null) {
                    deleted.add(thread.getName());
                    thread.delete().queue(Consumers.nop(), BotLogger::catchRestError);
                }
                game.removeStoredValue(key);
            }
            game.removeStoredValue(PARTNERS_KEY_PREFIX + player.getColor());
        }
        return deleted;
    }

    private static void touch(Game game, ThreadChannel thread) {
        LAST_ACTIVITY.put(thread.getId(), new ThreadActivity(game.getName(), Instant.now()));
        if (CRON_REGISTERED.compareAndSet(false, true)) AnonymousCommsArchiveCron.register();
    }

    public static int hideHours(Game game) {
        return clampHours(parseHours(game.getStoredValue(HIDE_HOURS_KEY)), 0);
    }

    static int clampHours(@Nullable Integer hours, int minimum) {
        if (hours == null) return DEFAULT_HIDE_HOURS;
        return Math.clamp(hours, minimum, MAX_HIDE_HOURS);
    }

    @Nullable
    static Integer parseHours(@Nullable String value) {
        if (StringUtils.isBlank(value)) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isQuiet(Instant lastMessage, int hideHours, Instant now) {
        return !now.isBefore(lastMessage.plus(Duration.ofHours(hideHours)));
    }

    public static void archiveQuietThreads() {
        Instant now = Instant.now();
        for (Map.Entry<String, ThreadActivity> entry : LAST_ACTIVITY.entrySet()) {
            ManagedGame managedGame =
                    GameManager.getManagedGame(entry.getValue().gameName());
            if (managedGame == null) {
                LAST_ACTIVITY.remove(entry.getKey());
                continue;
            }
            if (!isQuiet(entry.getValue().lastMessage(), hideHours(managedGame.getGame()), now)) continue;
            LAST_ACTIVITY.remove(entry.getKey(), entry.getValue());
            archive(entry.getKey());
        }
    }

    private static void archive(String threadId) {
        ThreadChannel thread = JdaService.jda.getThreadChannelById(threadId);
        if (thread == null || thread.isArchived()) return;
        thread.getManager().setArchived(true).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler(OPEN_PREFIX)
    public static void openConversation(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player partner = playerWithColor(game, buttonID.replace(OPEN_PREFIX, ""));
        if (partner == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That color is no longer in this game.");
            return;
        }
        ThreadChannel thread = partnerThread(game, player, partner);
        if (thread == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not open that conversation.");
            return;
        }
        unarchive(thread);
        touch(game, thread);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Conversation with " + colorName(partner) + ": " + thread.getAsMention());
    }

    @ButtonHandler(value = REPLY_PREFIX, save = false)
    public static void openReplyModal(ButtonInteractionEvent event, String buttonID) {
        String color = colorFromReplyButton(buttonID);
        TextInput input = TextInput.create(MESSAGE_INPUT, TextInputStyle.PARAGRAPH)
                .setRequiredRange(1, MODAL_MESSAGE_LIMIT)
                .build();
        Modal modal = Modal.create(REPLY_RESOLVE_PREFIX + color, "Reply to " + StringUtils.capitalize(color))
                .addComponents(Label.of("Message", input))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(REPLY_RESOLVE_PREFIX)
    public static void resolveReply(ModalInteractionEvent event, Game game, Player player) {
        Player receiver = playerWithColor(game, event.getModalId().replace(REPLY_RESOLVE_PREFIX, ""));
        if (receiver == null) {
            event.getHook()
                    .setEphemeral(true)
                    .sendMessage("That color is no longer in this game.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }
        relay(game, player, receiver, event.getValue(MESSAGE_INPUT).getAsString(), List.of(), event.getChannel());
    }

    @ButtonHandler(value = SETTINGS_BUTTON, save = false)
    public static void openSettings(ButtonInteractionEvent event, Game game) {
        TextInput hours = TextInput.create(HOURS_INPUT, TextInputStyle.SHORT)
                .setValue(String.valueOf(hideHours(game)))
                .setRequiredRange(1, 2)
                .build();
        Modal modal = Modal.create(SETTINGS_RESOLVE, "Anonymous comms settings")
                .addComponents(Label.of("Hide quiet threads after (hours, 1-12)", hours))
                .build();
        event.replyModal(modal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler(SETTINGS_RESOLVE)
    public static void resolveSettings(ModalInteractionEvent event, Game game) {
        int hours = clampHours(parseHours(event.getValue(HOURS_INPUT).getAsString()), 1);
        game.setStoredValue(HIDE_HOURS_KEY, String.valueOf(hours));
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Anonymous comms conversation threads now hide after " + hours + " quiet hours.");
    }
}

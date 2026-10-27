package ti4.service.fow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
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
    static final String PING_KEY_PREFIX = "anonCommsPingAt_";
    static final String HUB_KEY_PREFIX = "anonCommsHub_";
    static final String REPLY_GRANT_KEY_PREFIX = "anonCommsGuildReply_";
    static final int REPING_HOURS = 12;
    private static final String INBOX_MARKER = "📨 ";
    private static final String HUB_HEADER = "📇 **Comms** · 🟢 open · 🔴 closed, read back only";
    private static final String MODAL_SUFFIX = "~MDL";
    private static final String REPLY_RESOLVE_PREFIX = "anonCommsReplyResolve_";
    private static final String SETTINGS_RESOLVE = "anonCommsSettingsResolve";
    private static final String DELETE_MANAGED_BUTTON = "anonCommsDeleteManaged";
    private static final String MESSAGE_INPUT = "message";
    private static final String HOURS_INPUT = "hours";
    private static final String PARTNER_SEPARATOR = "-";
    private static final String COMMS_SUFFIX = "-comms-private";
    private static final Pattern THREAD_NAME =
            Pattern.compile("^(?:\\w+-comms-(\\w+)|(?:\\S+\\s+)?(\\w+)-comms)-private$", Pattern.CASE_INSENSITIVE);
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
    // TODO: in-memory only and the cron registers on first touch, so after a restart quiet threads fall back to
    // Discord's 24h auto-archive until the next relay; persisting activity needs startup registration in JdaService.
    private static final Map<String, ThreadActivity> LAST_ACTIVITY = new ConcurrentHashMap<>();
    private static final AtomicBoolean CRON_REGISTERED = new AtomicBoolean();
    private static final Set<String> RENAME_ATTEMPTED = ConcurrentHashMap.newKeySet();

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
        if (!matcher.matches()) return null;
        String target = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return target.toLowerCase();
    }

    static String partnerThreadName(String color) {
        return CommsColorMarker.of(color) + " " + StringUtils.capitalize(color) + COMMS_SUFFIX;
    }

    static String inboxThreadName() {
        return INBOX_MARKER + StringUtils.capitalize(INBOX) + COMMS_SUFFIX;
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
        acknowledge(game, sender);
        if (INBOX.equals(target)) {
            boolean sent = handleInboxMessage(game, sender, thread, text, attachments, replyTo);
            refreshHub(game, sender, thread);
            return sent;
        }
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
        Player repliedTo = replyTo == null ? null : replyTarget(game, replyTo);
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
    static Player replyTarget(Game game, Message message) {
        if (!message.getAuthor().isBot()) return null;
        Player fromButton = playerWithColor(game, replyButtonColor(message));
        if (fromButton != null) return fromButton;
        Map<String, String> colorsByMarker = new HashMap<>();
        for (Player player : game.getRealPlayers()) {
            colorsByMarker.put(notificationMarker(player.getColor()), player.getColor());
        }
        return playerWithColor(game, earliestMarkerColor(message.getContentRaw(), colorsByMarker));
    }

    @Nullable
    static String earliestMarkerColor(String content, Map<String, String> colorsByMarker) {
        String earliest = null;
        int earliestIndex = Integer.MAX_VALUE;
        for (Map.Entry<String, String> entry : colorsByMarker.entrySet()) {
            int index = content.indexOf(entry.getKey());
            if (index >= 0 && index < earliestIndex) {
                earliestIndex = index;
                earliest = entry.getValue();
            }
        }
        return earliest;
    }

    private static String notificationMarker(String color) {
        return INBOX_MARKER + colorName(color) + ":";
    }

    @Nullable
    private static String replyButtonColor(Message message) {
        return message.getComponentTree().findAll(Button.class).stream()
                .map(Button::getCustomId)
                .filter(id -> id != null && id.startsWith(REPLY_PREFIX))
                .map(AnonymousCommsService::colorFromReplyButton)
                .findFirst()
                .orElse(null);
    }

    static String colorFromReplyButton(String buttonId) {
        String withoutPrefix = buttonId.startsWith(REPLY_PREFIX) ? buttonId.substring(REPLY_PREFIX.length()) : buttonId;
        return withoutPrefix.endsWith(MODAL_SUFFIX)
                ? withoutPrefix.substring(0, withoutPrefix.length() - MODAL_SUFFIX.length())
                : withoutPrefix;
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

    public static boolean sendFromInbox(Game game, Player sender, Player receiver, String text) {
        ThreadChannel senderInbox = inbox(game, sender);
        if (senderInbox == null) return false;
        boolean sent = relay(game, sender, receiver, text, List.of(), senderInbox);
        refreshHub(game, sender, senderInbox);
        return sent;
    }

    @Nullable
    public static ThreadChannel existingInbox(Game game, Player owner) {
        return findThread(owner, game.getStoredValue(inboxKey(owner)));
    }

    public static boolean canTalk(Game game, Player sender, Player receiver) {
        if (FowCommunicationThreadService.getCommPartners(game, sender).contains(receiver)) return true;
        return hasReplyGrant(game, sender, receiver)
                && FowCommunicationThreadService.getCommPartners(game, receiver).contains(sender);
    }

    private static boolean hasReplyGrant(Game game, Player owner, Player guildShipsPlayer) {
        return FowCommunicationThreadService.hasGuildShips(guildShipsPlayer)
                && !game.getStoredValue(replyGrantKey(owner, guildShipsPlayer)).isEmpty();
    }

    static void grantReplyIfOneWay(Game game, Player sender, Player receiver) {
        if (!FowCommunicationThreadService.hasGuildShips(sender)) return;
        if (!game.getStoredValue(replyGrantKey(receiver, sender)).isEmpty()) return;
        if (FowCommunicationThreadService.getCommPartners(game, receiver).contains(sender)) return;
        game.setStoredValue(replyGrantKey(receiver, sender), "true");
    }

    static String replyGrantKey(Player owner, Player guildShipsPlayer) {
        return REPLY_GRANT_KEY_PREFIX + owner.getColor() + "_" + guildShipsPlayer.getColor();
    }

    private static List<String> replyGrantColors(Game game, Player owner) {
        String prefix = REPLY_GRANT_KEY_PREFIX + owner.getColor() + "_";
        return game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(prefix.length()))
                .filter(color -> {
                    Player guildShipsPlayer = playerWithColor(game, color);
                    return guildShipsPlayer != null && FowCommunicationThreadService.hasGuildShips(guildShipsPlayer);
                })
                .toList();
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

        Message delivered = post(receiverThread, ColorEmojis.getColorEmoji(sender.getColor()) + " " + body);
        if (delivered == null) {
            post(source, "Your message could not be delivered to " + colorName(receiver) + ". Please try again.");
            return false;
        }
        boolean typedInConversation = senderThread.getId().equals(source.getId());
        if (!typedInConversation) post(senderThread, "📤 **You:** " + body);
        notifyInbox(game, receiver, receiverInbox, sender, body, delivered);
        if (!typedInConversation) {
            post(source, "📤 Sent to " + colorName(receiver) + " · " + senderThread.getAsMention());
        }
        grantReplyIfOneWay(game, sender, receiver);
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
            Game game, Player receiver, ThreadChannel inbox, Player sender, String body, @Nullable Message delivered) {
        String preview = StringUtils.abbreviate(body.replace('\n', ' '), PREVIEW_LENGTH);
        String link = delivered == null ? "" : "\n-# [Open conversation](" + delivered.getJumpUrl() + ")";
        String content = notificationMarker(sender.getColor()) + " " + preview + link;
        Instant now = Instant.now();
        boolean ping = shouldPing(lastPing(game, receiver), now);
        if (ping) {
            content = (receiver.getPing() + " " + content).trim();
            game.setStoredValue(pingKey(receiver), String.valueOf(now.toEpochMilli()));
        }
        send(inbox, content, List.of(), !ping);
        refreshHub(game, receiver, inbox);
    }

    static boolean shouldPing(@Nullable Long lastPingMillis, Instant now) {
        return lastPingMillis == null || isQuiet(Instant.ofEpochMilli(lastPingMillis), REPING_HOURS, now);
    }

    @Nullable
    private static Long lastPing(Game game, Player player) {
        String stored = game.getStoredValue(pingKey(player));
        if (StringUtils.isBlank(stored)) return null;
        try {
            return Long.parseLong(stored.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void acknowledge(Game game, Player player) {
        if (!game.getStoredValue(pingKey(player)).isEmpty()) game.removeStoredValue(pingKey(player));
    }

    static String pingKey(Player owner) {
        return PING_KEY_PREFIX + owner.getColor();
    }

    static String hubKey(Player owner) {
        return HUB_KEY_PREFIX + owner.getColor();
    }

    private static void refreshHub(Game game, Player owner, ThreadChannel inbox) {
        refreshHub(game, owner, inbox, partnerColors(game, owner));
    }

    private static void refreshHub(Game game, Player owner, ThreadChannel inbox, List<String> partners) {
        try {
            unarchive(inbox);
        } catch (Exception e) {
            BotLogger.error("Could not unarchive anonymous comms inbox " + inbox.getName(), e);
        }
        deleteHub(game, owner, inbox);
        Set<String> realColors =
                game.getRealPlayers().stream().map(Player::getColor).collect(Collectors.toSet());
        List<String> open = new ArrayList<>(partners);
        open.addAll(replyGrantColors(game, owner));
        Map<String, Boolean> styles = hubButtonStyles(knownColors(game, owner), open, realColors, owner.getColor());
        List<Button> buttons = styles.entrySet().stream()
                .map(entry -> hubButton(entry.getKey(), entry.getValue()))
                .toList();
        String text = buttons.isEmpty() ? HUB_HEADER + "\nNo colors encountered yet." : HUB_HEADER;
        Message hub = send(inbox, text, buttons, true);
        if (hub != null) game.setStoredValue(hubKey(owner), hub.getId());
    }

    private static void deleteHub(Game game, Player owner, ThreadChannel inbox) {
        String hubId = game.getStoredValue(hubKey(owner));
        if (StringUtils.isBlank(hubId)) return;
        game.removeStoredValue(hubKey(owner));
        inbox.deleteMessageById(hubId).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static Set<String> knownColors(Game game, Player owner) {
        Set<String> known = new HashSet<>(FowCommunicationThreadService.encounteredColors(game, owner));
        String threadPrefix = THREAD_KEY_PREFIX + owner.getColor() + "_";
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(threadPrefix))
                .map(key -> key.substring(threadPrefix.length()))
                .forEach(known::add);
        return known;
    }

    static Map<String, Boolean> hubButtonStyles(
            Collection<String> known, Collection<String> partners, Collection<String> realColors, String ownColor) {
        Map<String, Boolean> styles = new TreeMap<>();
        Stream.concat(known.stream(), partners.stream())
                .filter(realColors::contains)
                .filter(color -> !color.equals(ownColor))
                .forEach(color -> styles.put(color, partners.contains(color)));
        return styles;
    }

    private static Button hubButton(String color, boolean open) {
        String label = StringUtils.capitalize(color);
        return open ? Buttons.green(OPEN_PREFIX + color, label) : Buttons.red(OPEN_PREFIX + color, label);
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
        return findOrCreateThread(
                game, owner, inboxKey(owner), inboxThreadName(), AutoArchiveDuration.TIME_1_WEEK, inboxIntro(game));
    }

    @Nullable
    public static ThreadChannel partnerThread(Game game, Player owner, Player partner) {
        String name = partnerThreadName(partner.getColor());
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
                + "- **Reply:** use Discord's reply on a 📨 message\n"
                + "- **Conversations:** each color gets its own thread in this channel, where you type without a"
                + " prefix. Quiet threads hide after " + hideHours(game) + " hours and come back with the next"
                + " message. The 📇 buttons at the bottom open them: green while you have comms, red when you"
                + " can only read back.\n"
                + "- **Pings:** only the first new message pings you. The next ones arrive silently until you"
                + " send a message or press a comms button, or " + REPING_HOURS + " hours have passed.";
    }

    @Nullable
    private static ThreadChannel findOrCreateThread(
            Game game, Player owner, String key, String name, AutoArchiveDuration duration, String intro) {
        String fittedName = DiscordThreadUtility.fitThreadName(name);
        ThreadChannel existing = findThread(owner, game.getStoredValue(key));
        if (existing != null) return renamedIfOutdated(existing, fittedName);
        TextChannel privateChannel = owner.getPrivateChannel();
        if (privateChannel == null) return null;
        try {
            ThreadChannel created = privateChannel
                    .createThreadChannel(fittedName)
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

    private static ThreadChannel renamedIfOutdated(ThreadChannel thread, String name) {
        if (name.equals(thread.getName()) || !RENAME_ATTEMPTED.add(thread.getId())) return thread;
        thread.getManager().setArchived(false).setName(name).queue(Consumers.nop(), BotLogger::catchRestError);
        return thread;
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

    @Nullable
    private static Message send(MessageChannel channel, String text, List<Button> buttons, boolean silent) {
        try {
            unarchive(channel);
            MessageCreateAction action = channel.sendMessage(text).setSuppressedNotifications(silent);
            if (!buttons.isEmpty()) action = action.addComponents(rowsOf(buttons));
            return action.complete();
        } catch (Exception e) {
            BotLogger.error("Could not post to anonymous comms channel " + channel.getName(), e);
            return null;
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

    static void refreshPartners(Game game, Map<Player, Set<Player>> basePartners) {
        if (!isActive(game)) return;
        for (Player player : game.getRealPlayers()) {
            if (player.getPrivateChannel() == null) continue;
            List<String> partners = partnerColors(game, player, basePartners.get(player));
            List<String> previous = storedPartners(game, player);
            boolean hasInbox = !game.getStoredValue(inboxKey(player)).isEmpty();
            if (hasInbox && partners.equals(previous)) continue;
            ThreadChannel inbox = inbox(game, player);
            if (inbox == null) continue;
            announcePartnerChanges(inbox, previous, partners);
            refreshHub(game, player, inbox, partners);
            game.setStoredValue(PARTNERS_KEY_PREFIX + player.getColor(), String.join(PARTNER_SEPARATOR, partners));
        }
    }

    public static List<String> partnerColors(Game game, Player player) {
        return partnerColors(game, player, null);
    }

    private static List<String> partnerColors(Game game, Player player, @Nullable Set<Player> basePartners) {
        return FowCommunicationThreadService.getCommPartners(game, player, basePartners).stream()
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
            message.append("You have no comms with anyone right now.");
        } else {
            message.append("You can reach ").append(colorList(partners)).append('.');
        }
        post(inbox, message.toString());
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
            game.removeStoredValue(pingKey(player));
            game.removeStoredValue(hubKey(player));
            String grantPrefix = REPLY_GRANT_KEY_PREFIX + player.getColor() + "_";
            game.getStoredValueMap().keySet().removeIf(key -> key.startsWith(grantPrefix));
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
        acknowledge(game, player);
        unarchive(thread);
        touch(game, thread);
        String closedNotice = canTalk(game, player, partner)
                ? ""
                : "\nComms are closed right now: you can read back, but your messages will not be relayed.";
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Conversation with " + colorName(partner) + ": " + thread.getAsMention() + closedNotice);
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
        acknowledge(game, player);
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

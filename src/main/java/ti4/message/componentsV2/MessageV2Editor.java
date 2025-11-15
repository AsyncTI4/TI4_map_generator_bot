package ti4.message.componentsV2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.message.logging.BotLogger;

public class MessageV2Editor {
    private final List<ReplaceMessagePart> replaceByCustomId = new ArrayList<>();
    private final List<ReplaceMessagePart> replaceByPattern = new ArrayList<>();

    public enum MessagePartType {
        TEXT_DISPLAY,
        BUTTON,
        STRING_SELECT,
        ENTITY_SELECT,
        MEDIA_GALLERY
    }

    public static class ReplaceMessagePart {
        private final Object part;

        @Getter
        private final MessagePartType type;

        /**
         * The key to identify what's being replaced. Use
         * depends on the type:
         * - BUTTONS, STRING_SELECT, ENTITY_SELECT: the custom ID of the component
         * - TEXT_DISPLAY: any text in the component content
         * - MEDIA_GALLERY: a part of the file name
         */
        @Getter
        private final String replaceKey;

        public ReplaceMessagePart(String oldCustomId, Button part) {
            this.part = part;
            type = MessagePartType.BUTTON;
            replaceKey = oldCustomId;
        }

        public ReplaceMessagePart(String oldCustomId, StringSelectMenu part) {
            this.part = part;
            type = MessagePartType.STRING_SELECT;
            replaceKey = oldCustomId;
        }

        public ReplaceMessagePart(String oldCustomId, EntitySelectMenu part) {
            this.part = part;
            type = MessagePartType.ENTITY_SELECT;
            replaceKey = oldCustomId;
        }

        public ReplaceMessagePart(String oldLineStartsWith, TextDisplay part) {
            this.part = part;
            type = MessagePartType.TEXT_DISPLAY;
            replaceKey = oldLineStartsWith;
        }

        public ReplaceMessagePart(String oldItemUrlPart, MediaGallery part) {
            this.part = part;
            type = MessagePartType.MEDIA_GALLERY;
            replaceKey = oldItemUrlPart;
        }

        public Component asComponent() {
            return (Component) part;
        }
    }

    public MessageV2Editor replace(String oldId, Button button) {
        replaceByCustomId.add(new ReplaceMessagePart(oldId, button));
        return this;
    }

    public MessageV2Editor replace(String oldId, StringSelectMenu stringSelectMenu) {
        replaceByCustomId.add(new ReplaceMessagePart(oldId, stringSelectMenu));
        return this;
    }

    public MessageV2Editor replace(String oldId, EntitySelectMenu entitySelectMenu) {
        replaceByCustomId.add(new ReplaceMessagePart(oldId, entitySelectMenu));
        return this;
    }

    /**
     * For media gallery replacement, replace MediaGallery components with an item whose filename contains the provided pattern
     * @param filenamePattern A string to test against each MediaGallery item's filenames.
     * @param mediaGallery A replacement MediaGallery.
     */
    public MessageV2Editor replace(String filenamePattern, MediaGallery mediaGallery) {
        replaceByPattern.add(new ReplaceMessagePart(filenamePattern, mediaGallery));
        return this;
    }

    /**
     * For media gallery replacement, replace MediaGallery components with an item whose filename contains the provided pattern
     * @param filenamePattern A string to test against each MediaGallery item's filenames.
     * @param imageFile A file upload to convert to a media gallery.
     */
    public MessageV2Editor replace(String filenamePattern, FileUpload imageFile) {
        if (imageFile == null) {
            return this;
        }
        MediaGallery mediaGallery = MessageV2Builder.makeDisplayableV2Image(imageFile);
        return replace(filenamePattern, mediaGallery);
    }

    /**
     * For text replacement, replace ALL TextDisplay components whose content matches with the provided regex string
     * @param contentPattern A string to test against each text component.
     * @param newContent A replacement TextDisplay.
     */
    public MessageV2Editor replace(String contentPattern, TextDisplay newContent) {
        replaceByPattern.add(new ReplaceMessagePart(contentPattern, newContent));
        return this;
    }

    /**
     * Replace text content in a message. It's recommended to send text which may be replaced using MessageV2Builder::appendReplaceableText,
     * otherwise this may unintentionally replace additional text near the intended target.
     * @param contentPattern A string to test against each text component.
     * @param newContent The content to replace with.
     */
    public MessageV2Editor replace(String contentPattern, String newContent) {
        return replace(contentPattern, TextDisplay.of(newContent));
    }

    /**
     * Apply changes to recent messages in a channel. This is useful for editing
     * messages that are very likely to be near the bottom of a channel. Especially
     * useful for custom channels, such as draft channels.
     *
     * If you're doing any text updates, be sure that the pattern is highly specific.
     * This method will probably check messages you didn't intend.
     * @param channel The channel to edit messages in.
     * @param messageLookback How many recent messages to load and check for edits.
     * @param onApplied A callback that accepts a boolean indicating if any changes were made.
     */
    public void applyToRecentMessages(MessageChannel channel, int messageLookback, Consumer<Boolean> onApplied) {
        channel.getHistoryAround(channel.getLatestMessageIdLong(), messageLookback)
                .queue(
                        messageHistory -> {
                            List<Message> recentMessages = messageHistory.getRetrievedHistory();
                            if (recentMessages.isEmpty()) {
                                onApplied.accept(false);
                                return;
                            }
                            boolean madeChanges = false;
                            for (Message message : recentMessages) {
                                if (!message.getAuthor().isBot()) {
                                    continue;
                                }
                                madeChanges = applyToMessage(message) || madeChanges;
                            }
                            onApplied.accept(madeChanges);
                        },
                        BotLogger::catchRestError);
    }

    /**
     * Apply changes to a target message and the ones around it. This is useful
     * when a complex message is split into multiple messages. You can respond to
     * an interaction in one message, while also affecting related components that
     * were split into a different message.
     *
     * If you're doing any text updates, be sure that the pattern is highly specific.
     * This method will probably check messages you didn't intend.
     * @param targetMessage The message to edit, and use as a center point for surrounding messages.
     * @param limit The total number of messages around this one to check. 4 is a good number (2 above, 2 below).
     * @param onApplied A callback that accepts a boolean indicating if any changes were made.
     */
    public void applyAroundMessage(Message targetMessage, int limit, Consumer<Boolean> onApplied) {
        MessageChannel channel = targetMessage.getChannel();
        channel.getHistoryAround(targetMessage.getIdLong(), limit)
                .queue(
                        messageHistory -> {
                            boolean madeChanges = applyToMessage(targetMessage);
                            for (Message message : messageHistory.getRetrievedHistory()) {

                                // Skip the target message; this is a sanity check since it shouldn't be included in the
                                // history.
                                if (message.getIdLong() == targetMessage.getIdLong()) {
                                    continue;
                                }

                                if (!message.getAuthor().isBot()) {
                                    continue;
                                }

                                madeChanges = applyToMessage(message) || madeChanges;
                            }
                            onApplied.accept(madeChanges);
                        },
                        BotLogger::catchRestError);
    }

    /**
     * Apply changes to a single message.
     * @param message The message to edit.
     * @return True if any changes were made, false otherwise.
     */
    public Boolean applyToMessage(Message message) {
        MessagePartComponentReplacer replacer = new MessagePartComponentReplacer(replaceByCustomId, replaceByPattern);
        MessageComponentTree messageComponents = message.getComponentTree();
        replacer.startingChanges();
        MessageComponentTree newComponents = messageComponents.replace(replacer);
        Boolean madeChanges = replacer.finishedChanges();
        if (!madeChanges) {
            return false;
        }
        MessageEditAction editAction = message.editMessageComponents(newComponents);
        if (message.isUsingComponentsV2()) {
            // This should be implicit, but it's not.
            editAction = editAction.useComponentsV2();
        }
        editAction.queue(null, BotLogger::catchRestError);
        return true;
    }
}

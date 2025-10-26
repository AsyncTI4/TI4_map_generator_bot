package ti4.message.componentsV2;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.filedisplay.FileDisplay;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Constants;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

public class MessageV2Builder {
    private final MessageChannel channel;
    private final List<MessagePart> parts = new ArrayList<>();
    private final Integer maxSplits;

    public MessageV2Builder(MessageChannel channel) {
        Objects.requireNonNull(channel, "Channel cannot be null");
        this.channel = channel;
        this.maxSplits = null;
    }

    /**
     * Constructor for MessageV2Builder with a maximum number of splits.
     * @param channel The channel to send the message to.
     * @param maxSplits The maximum number of message splits allowed. If the built message
     *                  exceeds this number of splits, an error is logged and the message is
     *                  truncated. This is useful for messages that will be edited, so you can
     *                  ensure queries for messages find all expected splits.
     */
    public MessageV2Builder(MessageChannel channel, int maxSplits) {
        Objects.requireNonNull(channel, "Channel cannot be null");
        this.channel = channel;
        this.maxSplits = maxSplits;
    }

    public enum MessagePartType {
        TEXT,
        BUTTONS,
        TOP_LEVEL_COMPONENT
    }

    public static class MessagePart {
        private final Object part;
        private final MessagePartType type;

        public MessagePart(String part) {
            this.part = part;
            this.type = MessagePartType.TEXT;
        }

        public MessagePart(Button part) {
            this.part = List.of(part);
            this.type = MessagePartType.BUTTONS;
        }

        public MessagePart(List<Button> part) {
            this.part = part;
            this.type = MessagePartType.BUTTONS;
        }

        public MessagePart(MessageTopLevelComponent part) {
            this.part = part;
            this.type = MessagePartType.TOP_LEVEL_COMPONENT;
        }

        public MessagePartType getType() {
            return type;
        }

        public String asString() {
            return (String) part;
        }

        @SuppressWarnings("unchecked")
        public List<Button> asButtons() {
            List<?> raw = (List<?>) part;
            if (raw.stream().allMatch(item -> item instanceof Button)) {
                return (List<Button>) raw;
            } else {
                throw new ClassCastException("The list does not contain only Button objects.");
            }
        }

        public MessageTopLevelComponent asTopLevelComponent() {
            return (MessageTopLevelComponent) part;
        }
    }

    public MessageV2Builder append(String message) {
        if (StringUtils.isEmpty(message)) {
            return this;
        }
        parts.add(new MessagePart(message));
        return this;
    }

    public MessageV2Builder appendLine(String message) {
        if (!parts.isEmpty() && parts.getLast().getType() == MessagePartType.TEXT) {
            parts.add(new MessagePart(System.lineSeparator()));
        }
        return append(message);
    }

    /**
     * Append text that can be replaced. This means that the text component
     * won't be combined with any other text components, so that replacements
     * and removals are isolated to just this content.
     * @param message The text to append
     */
    public MessageV2Builder appendReplaceableText(String message) {
        return append(TextDisplay.of(message));
    }

    public MessageV2Builder append(Button button) {
        parts.add(new MessagePart(button));
        return this;
    }

    public MessageV2Builder append(List<Button> buttons) {
        parts.add(new MessagePart(buttons));
        return this;
    }

    public MessageV2Builder append(MessageTopLevelComponent component) {
        parts.add(new MessagePart(component));
        return this;
    }

    public MessageV2Builder appendInlineImage(FileUpload imageFile) {
        if (imageFile == null) {
            return this;
        }
        MediaGallery mediaGallery = makeDisplayableV2Image(imageFile);
        append(mediaGallery);
        return this;
    }

    public void send() {
        List<MessageCreateData> combinedComponents = build();
        if (combinedComponents.isEmpty()) {
            return;
        }
        if (maxSplits != null && combinedComponents.size() > maxSplits) {
            List<String> componentTrees = combinedComponents.stream()
                    .map(msg -> MessageV2Builder.ComponentTypeTree(msg.getComponentTree()))
                    .collect(Collectors.toList());
            BotLogger.warning(
                    Constants.jabberwockyPing() + "Attempted to send a v2 message that exceeds the component limit, "
                            + "but splitting is disabled. Message not sent.\n"
                            + String.join("\n---\n", componentTrees));
            return;
        }
        MessageHelper.sendMessagesWithRetry(channel, combinedComponents, null, "Failed to send v2 message", 1);
    }

    private List<MessageCreateData> build() {
        List<MessageTopLevelComponent> topLevelComponents = new LinkedList<>();
        List<Button> currentButtons = null;
        StringBuilder currentText = null;

        for (MessageV2Builder.MessagePart part : parts) {
            // Flush buttons if not another button
            MessagePartType type = part.getType();
            if (type != MessagePartType.BUTTONS && currentButtons != null) {
                currentButtons = MessageHelper.sanitizeButtons(currentButtons, channel);
                if (!currentButtons.isEmpty()) {
                    topLevelComponents.addAll(ActionRow.partitionOf(currentButtons));
                }
                currentButtons = null;
            }

            // Flush text if not another text
            if (type != MessagePartType.TEXT && currentText != null) {
                List<String> chunkedText =
                        StringHelper.chunkMessage(currentText.toString(), Message.MAX_CONTENT_LENGTH);
                for (String chunk : chunkedText) {
                    topLevelComponents.add(TextDisplay.of(chunk));
                }
                currentText = null;
            }

            switch (type) {
                case TEXT -> {
                    if (currentText == null) {
                        currentText = new StringBuilder();
                    }
                    currentText.append(part.asString());
                }
                case BUTTONS -> {
                    if (currentButtons == null) {
                        currentButtons = new ArrayList<>();
                    }
                    currentButtons.addAll(part.asButtons());
                }
                case TOP_LEVEL_COMPONENT -> topLevelComponents.add(part.asTopLevelComponent());
                default -> throw new IllegalArgumentException("Unknown MessagePartType: " + type);
            }
        }

        // Flush any remaining buttons
        if (currentButtons != null) {
            currentButtons = MessageHelper.sanitizeButtons(currentButtons, channel);
            if (!currentButtons.isEmpty()) {
                topLevelComponents.addAll(ActionRow.partitionOf(currentButtons));
            }
        }
        // Flush any remaining text
        if (currentText != null) {
            List<String> chunkedText = StringHelper.chunkMessage(currentText.toString(), Message.MAX_CONTENT_LENGTH);
            for (String chunk : chunkedText) {
                topLevelComponents.add(TextDisplay.of(chunk));
            }
        }

        // Partition the top level components so that no message exceeds the component
        // limit.
        List<MessageCreateData> messages = new ArrayList<>();
        List<MessageTopLevelComponent> currentPartition = new ArrayList<>();
        int currentCount = 0;
        while (!topLevelComponents.isEmpty()) {
            MessageTopLevelComponent component = topLevelComponents.removeFirst();
            int componentCount = MessageV2Builder.CountComponents(component);
            if (componentCount > Message.MAX_COMPONENT_COUNT_IN_COMPONENT_TREE) {
                BotLogger.warning("Cannot send a message with a top-level component that exceeds the component limit.\n"
                        + MessageV2Builder.ComponentTypeTree(component));
                continue;
            }
            if (currentCount + componentCount > Message.MAX_COMPONENT_COUNT_IN_COMPONENT_TREE
                    && !currentPartition.isEmpty()) {
                messages.add(buildMessage(currentPartition));
                currentPartition.clear();
                currentCount = 0;
            }
            currentPartition.add(component);
            currentCount += componentCount;
        }
        if (!currentPartition.isEmpty()) {
            messages.add(buildMessage(currentPartition));
        }

        return messages;
    }

    private static MessageCreateData buildMessage(List<MessageTopLevelComponent> components) {
        MessageComponentTree componentTree = MessageComponentTree.of(components);
        return new MessageCreateBuilder()
                .useComponentsV2()
                .setComponents(componentTree)
                .build();
    }

    public static MediaGallery makeDisplayableV2Image(FileUpload imageFile) {
        // Filenames are more sensitive with v2 messages.
        imageFile.setName(imageFile.getName().replaceAll(Pattern.quote(" "), "_"));
        return MediaGallery.of(MediaGalleryItem.fromFile(imageFile));
    }

    public static int CountComponents(Component component) {
        return switch (component) {
            case ActionRow actionRow -> actionRow.getComponents().size() + 1;
            case Button button -> 1;
            case StringSelectMenu stringSelectMenu -> 1;
            case TextInput textInput -> 1;
            case EntitySelectMenu entitySelectMenu -> 1;
            case Section section ->
                section.getContentComponents().stream()
                                .mapToInt(MessageV2Builder::CountComponents)
                                .sum()
                        + CountComponents(section.getAccessory())
                        + 1;
            case TextDisplay textDisplay -> 1;
            case Thumbnail thumbnail -> 1;
            case MediaGallery mediaGallery -> 1; // items are not components
            case FileDisplay fileDisplay -> 1;
            case Separator separator -> 1;
            case Container container ->
                container.getComponents().stream()
                                .mapToInt(MessageV2Builder::CountComponents)
                                .sum()
                        + 1;
            case Label label -> 1 + CountComponents(label.getChild());
            case null -> 0;
            default ->
                throw new IllegalArgumentException(
                        "Unknown component type: " + component.getClass().getName());
        };
    }

    public static String ComponentTypeTree(MessageComponentTree componentTree) {
        if (componentTree == null) {
            return "null";
        }
        return componentTree.getComponents().stream()
                .map(MessageV2Builder::ComponentTypeTree)
                .collect(Collectors.joining(", ", "ComponentTree(", ")"));
    }

    public static String ComponentTypeTree(Component component) {
        return switch (component) {
            case ActionRow actionRow ->
                "ActionRow("
                        + actionRow.getComponents().stream()
                                .map(MessageV2Builder::ComponentTypeTree)
                                .collect(Collectors.joining(", "))
                        + ")";
            case Button button -> "Button(" + button.getLabel() + "[" + button.getCustomId() + "])";
            case StringSelectMenu stringSelectMenu -> "StringSelectMenu(" + stringSelectMenu.getCustomId() + ")";
            case TextInput textInput -> "TextInput";
            case EntitySelectMenu entitySelectMenu -> "EntitySelectMenu(" + entitySelectMenu.getCustomId() + ")";
            case Section section ->
                "Section("
                        + section.getContentComponents().stream()
                                .map(MessageV2Builder::ComponentTypeTree)
                                .collect(Collectors.joining(", "))
                        + ")";
            case TextDisplay textDisplay -> "TextDisplay";
            case Thumbnail thumbnail -> "Thumbnail";
            case MediaGallery mediaGallery -> "MediaGallery";
            case FileDisplay fileDisplay -> "FileDisplay";
            case Separator separator -> "Separator";
            case Container container ->
                "Container("
                        + container.getComponents().stream()
                                .map(MessageV2Builder::ComponentTypeTree)
                                .collect(Collectors.joining(", "))
                        + ")";
            case Label label ->
                "Label(" + (label.getChild() != null ? ComponentTypeTree(label.getChild()) : "no child") + ")";
            case null -> "null";
            default ->
                throw new IllegalArgumentException(
                        "Unknown component type: " + component.getClass().getName());
        };
    }
}

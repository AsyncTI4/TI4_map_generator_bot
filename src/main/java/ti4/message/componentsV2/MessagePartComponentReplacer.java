package ti4.message.componentsV2;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attribute.ICustomId;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.replacer.IReplaceable;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.message.componentsV2.MessageV2Editor.MessagePartType;
import ti4.message.componentsV2.MessageV2Editor.ReplaceMessagePart;
import ti4.message.logging.BotLogger;

public class MessagePartComponentReplacer implements TrackingComponentReplacer {
    private final Map<String, ReplaceMessagePart> replaceByCustomId;
    private final List<ReplaceMessagePart> replaceByPattern;
    private boolean madeChanges = false;

    public MessagePartComponentReplacer(
            List<ReplaceMessagePart> replaceByCustomId, List<ReplaceMessagePart> replaceByPattern) {
        this.replaceByCustomId = replaceByCustomId.stream()
                .collect(Collectors.toMap(ReplaceMessagePart::getReplaceKey, Function.identity()));
        this.replaceByPattern = replaceByPattern;
    }

    public void startingChanges() {
        madeChanges = false;
    }

    /**
     * @return true if any changes were made, false otherwise.
     */
    public Boolean finishedChanges() {
        return madeChanges;
    }

    /**
     * The apply method of the ComponentReplacer interface is called as part
     * of the MessageComponentTree.replace method. It is called for each component
     * in the message's component tree. If the input component is returned, nothing happens.
     * If null is returned, the component is removed. If a different component is returned,
     * the component is replaced.
     *
     * This also populates the changedMessages set when any action is performed; this
     * allows the caller to know if any changes were made at all. (to help save on PATCH
     * requests)
     */
    @Override
    public Component apply(@Nonnull Component curComponent) {
        ReplaceMessagePart replacement = tryGetReplacementByCustomId(curComponent);
        if (replacement == null) {
            replacement = tryGetReplacementByPattern(curComponent);
        }
        if (replacement == null && curComponent instanceof IReplaceable) {
            Component containerReplacement = removeIfEmptyContainer(curComponent);
            if (containerReplacement != curComponent) {
                madeChanges = true;
            }
            return containerReplacement;
        }
        if (replacement == null) {
            return curComponent;
        }
        if (!canReplace(curComponent, replacement.asComponent())) {
            BotLogger.warning("Cannot replace component of type "
                    + curComponent.getClass().getName() + " with type "
                    + replacement.asComponent().getClass().getName());
            return curComponent;
        }
        madeChanges = true;
        return replacement.asComponent();
    }

    /**
     * Because this "replacer" also removes components, we need to
     * pre-emptively replace containers that would be made empty by child removal.
     * The replacement logic doesn't handle this natively.
     * @param curComponent A component that is IReplaceable
     * @return the input component if it would still be valid, otherwise null
     */
    private Component removeIfEmptyContainer(Component curComponent) {
        if (curComponent == null) {
            return null;
        }

        List<? extends Component> children =
                switch (curComponent) {
                    case ActionRow actionRow -> actionRow.getComponents();
                    case Container container -> container.getComponents();
                    case Section section -> section.getContentComponents();
                    case Label label -> label.getChild() == null ? List.of() : List.of(label.getChild());
                    default ->
                        throw new IllegalArgumentException("Unknown IReplaceable component type: "
                                + curComponent.getClass().getName());
                };

        if (children.isEmpty()) {
            return null;
        }

        // In all cases, we just need to ensure that at least one
        // child is still present.
        for (Component child : children) {
            Component replacement = apply(child);
            if (replacement != null) {
                return curComponent;
            }
        }

        return null;
    }

    private ReplaceMessagePart tryGetReplacementByPattern(Component curComponent) {
        if (curComponent == null) {
            return null;
        }
        for (ReplaceMessagePart replacement : replaceByPattern) {
            if (replacement.getType() == MessagePartType.TEXT_DISPLAY
                    && curComponent instanceof TextDisplay textDisplay) {
                if (matchText(textDisplay, replacement.getReplaceKey())) {
                    return replacement;
                }
            } else if (replacement.getType() == MessagePartType.MEDIA_GALLERY
                    && curComponent instanceof MediaGallery mediaGallery) {
                if (matchText(mediaGallery, replacement.getReplaceKey())) {
                    return replacement;
                }
            }
        }

        return null;
    }

    private ReplaceMessagePart tryGetReplacementByCustomId(Component curComponent) {
        String curId = getCustomId(curComponent);
        if (curId == null) {
            return null;
        }
        ReplaceMessagePart replacement = replaceByCustomId.getOrDefault(curId, null);
        if (replacement == null) {
            return null;
        }
        return replacement;
    }

    private static String getCustomId(Component component) {
        if (component instanceof ICustomId customIdComponent) {
            return customIdComponent.getCustomId();
        }
        return null;
    }

    private static boolean matchText(TextDisplay textDisplay, String pattern) {
        if (textDisplay == null || pattern == null) {
            return false;
        }
        String content = textDisplay.getContent();
        return content.matches(pattern);
    }

    private static boolean matchText(MediaGallery mediaGallery, String contains) {
        if (mediaGallery == null || contains == null) {
            return false;
        }
        Predicate<MediaGalleryItem> matchFunc = (item) -> {
            return item.getUrl() != null && item.getUrl().contains(contains);
        };
        return mediaGallery.getItems().stream().anyMatch(matchFunc);
    }

    private static boolean canReplace(Component current, Component replacement) {
        if (current == null || replacement == null) {
            return true;
        }
        if (!current.getClass().equals(replacement.getClass())) {
            return false;
        }
        return true;
    }
}

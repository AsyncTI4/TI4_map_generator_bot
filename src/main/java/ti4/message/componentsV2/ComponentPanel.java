package ti4.message.componentsV2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.logging.BotLogger;

@UtilityClass
public class ComponentPanel {

    public static final String ON = "✅";
    public static final String OFF = "🚫";
    public static final int ACCENT = 0x5865F2;
    public static final int WARNING_ACCENT = 0xF0B232;
    public static final int MAX_NOTICE_LENGTH = 900;

    public static List<MessageTopLevelComponent> compose(
            String header, String notice, PanelView view, List<ActionRow> navigation) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of(header));
        if (StringUtils.isNotBlank(notice)) {
            children.add(TextDisplay.of(quote(StringUtils.abbreviate(notice.trim(), MAX_NOTICE_LENGTH))));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.addAll(view.body());

        List<MessageTopLevelComponent> components = new ArrayList<>();
        components.add(Container.of(children).withAccentColor(view.warning() ? WARNING_ACCENT : ACCENT));
        components.addAll(view.controls());
        if (!view.hideNavigation()) {
            components.addAll(navigation);
        }
        if (!fits(components) && StringUtils.isNotBlank(notice)) {
            return compose(header, null, view, navigation);
        }
        return components;
    }

    public static String quote(String text) {
        return "> " + text.replace("\n", "\n> ");
    }

    public static void tell(GenericInteractionCreateEvent event, String text) {
        if (!(event instanceof IReplyCallback callback)) return;
        String message = StringUtils.abbreviate(text, Message.MAX_CONTENT_LENGTH);
        if (callback.isAcknowledged()) {
            callback.getHook()
                    .sendMessage(message)
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            callback.reply(message).setEphemeral(true).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static Section settingRow(String name, String value, String hint, Button action) {
        String text = "**" + name + ":** " + value + (StringUtils.isBlank(hint) ? "" : "\n-# " + hint);
        return Section.of(action, TextDisplay.of(text));
    }

    public static ActionRow confirmRow(Button confirm, String cancelId) {
        return ActionRow.of(confirm, Buttons.gray(cancelId, "Cancel"));
    }

    public static List<Button> pageButtons(
            IntFunction<String> idOfPage, int page, int pageCount, String previousLabel, String nextLabel) {
        List<Button> buttons = new ArrayList<>();
        if (page > 0) buttons.add(Buttons.gray(idOfPage.apply(page - 1), previousLabel));
        if (page < pageCount - 1) buttons.add(Buttons.gray(idOfPage.apply(page + 1), nextLabel));
        return buttons;
    }

    public static boolean isPanelMessage(GenericInteractionCreateEvent event, String panelMessageId) {
        Message message = messageOf(event);
        return message != null && panelMessageId != null && message.getId().equals(panelMessageId);
    }

    public static void showOrRepost(
            GenericInteractionCreateEvent event,
            String panelMessageId,
            Supplier<List<MessageTopLevelComponent>> build,
            Runnable repost) {
        if (!isPanelMessage(event, panelMessageId)) {
            repost.run();
            return;
        }
        show(event, build.get());
    }

    public static void repost(
            MessageChannel target,
            MessageChannel oldChannel,
            String oldMessageId,
            List<MessageTopLevelComponent> components,
            Consumer<Message> onPosted) {
        if (oldChannel != null && oldMessageId != null) {
            oldChannel.deleteMessageById(oldMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
        }
        post(target, components, onPosted);
    }

    public static Section toggleSection(String title, String description, boolean on, Button accessory) {
        String text = "**" + title + "** " + (on ? ON : OFF) + "\n-# " + description;
        return Section.of(accessory, TextDisplay.of(text));
    }

    public static Button toggleButton(String id, boolean on) {
        return on ? Buttons.red(id, "Turn off") : Buttons.green(id, "Turn on");
    }

    public static <T> List<Button> tabButtons(
            List<T> tabs, T selected, Function<T, String> idOf, Function<T, String> labelOf) {
        return tabs.stream()
                .map(tab -> tab.equals(selected)
                        ? Buttons.gray(idOf.apply(tab), labelOf.apply(tab))
                        : Buttons.blue(idOf.apply(tab), labelOf.apply(tab)))
                .toList();
    }

    public static List<ActionRow> selectPages(
            String idPrefix, List<SelectOption> options, int page, int menusPerPage, String placeholder) {
        List<List<SelectOption>> menus = ListUtils.partition(options, SelectMenu.OPTIONS_MAX_AMOUNT);
        List<ActionRow> rows = new ArrayList<>();
        int first = page * menusPerPage;
        for (int index = first; index < menus.size() && index < first + menusPerPage; index++) {
            List<SelectOption> menu = menus.get(index);
            String range = menus.size() > 1 ? rangeLabel(menu) : "";
            rows.add(ActionRow.of(StringSelectMenu.create(idPrefix + index)
                    .setPlaceholder(StringUtils.left(placeholder + range, SelectMenu.PLACEHOLDER_MAX_LENGTH))
                    .addOptions(menu)
                    .setRequiredRange(1, 1)
                    .build()));
        }
        return rows;
    }

    public static int pageCount(int optionCount, int menusPerPage) {
        int menus = (optionCount + SelectMenu.OPTIONS_MAX_AMOUNT - 1) / SelectMenu.OPTIONS_MAX_AMOUNT;
        return Math.max(1, (menus + menusPerPage - 1) / menusPerPage);
    }

    private static String rangeLabel(List<SelectOption> menu) {
        String first = menu.getFirst().getLabel();
        String last = menu.getLast().getLabel();
        return first.equals(last) ? " (" + first + ")" : " (" + first + " - " + last + ")";
    }

    public static void post(
            MessageChannel channel, List<MessageTopLevelComponent> components, Consumer<Message> onSent) {
        channel.sendMessageComponents(components).useComponentsV2().queue(onSent, BotLogger::catchRestError);
    }

    public static void post(MessageChannel channel, List<MessageTopLevelComponent> components) {
        post(channel, components, Consumers.nop());
    }

    public static Message messageOf(GenericInteractionCreateEvent event) {
        if (event instanceof GenericComponentInteractionCreateEvent componentEvent) {
            return componentEvent.getMessage();
        }
        if (event instanceof ModalInteractionEvent modalEvent) {
            return modalEvent.getMessage();
        }
        return null;
    }

    public static void show(GenericInteractionCreateEvent event, List<MessageTopLevelComponent> components) {
        Message message = messageOf(event);
        InteractionHook hook = hookOf(event);
        if (message == null || hook == null || !message.isUsingComponentsV2()) {
            if (message != null) {
                message.delete().queue(Consumers.nop(), BotLogger::catchRestError);
            }
            post(event.getMessageChannel(), components);
            return;
        }
        hook.editOriginalComponents(components).useComponentsV2().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static InteractionHook hookOf(GenericInteractionCreateEvent event) {
        if (event instanceof GenericComponentInteractionCreateEvent componentEvent) {
            return componentEvent.getHook();
        }
        if (event instanceof ModalInteractionEvent modalEvent) {
            return modalEvent.getHook();
        }
        return null;
    }

    public static int componentCount(List<MessageTopLevelComponent> components) {
        return components.stream().mapToInt(MessageV2Builder::CountComponents).sum();
    }

    public static int characterCount(List<MessageTopLevelComponent> components) {
        return components.stream().mapToInt(MessageV2Builder::CountCharacters).sum();
    }

    public static boolean fits(List<MessageTopLevelComponent> components) {
        return componentCount(components) <= Message.MAX_COMPONENT_COUNT_IN_COMPONENT_TREE
                && characterCount(components) <= Message.MAX_CONTENT_LENGTH_COMPONENT_V2;
    }
}

package ti4.message.componentsV2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.logging.BotLogger;

@UtilityClass
public class ComponentPanel {

    public static final String ON = "✅";
    public static final String OFF = "🚫";

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

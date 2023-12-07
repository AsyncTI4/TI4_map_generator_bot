package ti4.selections.selectmenus;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import ti4.selections.Selection;

import java.util.ArrayList;
import java.util.List;

public class BigSelectDemo implements Selection {

    public static final String selectionID = "demo";

    private static List<String> options;

    public BigSelectDemo() {
        options = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            options.add(String.valueOf(i));
        }
    }

    @Override
    public String getSelectionID() {
        return selectionID;
    }

    @Override
    public void execute(StringSelectInteractionEvent event) {
        List<String> values = event.getValues();

        List<String> keepValues = new ArrayList<>();
        boolean hasPageOption = false;
        int nextPageStart = 0;
        for (String option : values) {
            if (option.contains("page_")) {
                hasPageOption = true;
                nextPageStart = Integer.parseInt(option.substring(5));
            }
            else {
                keepValues.add(option);
            }
        }

        if (!hasPageOption) {
            event.getChannel().sendMessage("You selected: " + String.join(", ", keepValues)).queue();
            return;
        }

        serveDemoSelectMenu(event, keepValues, nextPageStart);
    }

    public static void serveDemoSelectMenu(GenericInteractionCreateEvent event) {
        serveDemoSelectMenu(event, null, 0);
    }

    public static void serveDemoSelectMenu(GenericInteractionCreateEvent event, List<String> preselected, int startIdx) {

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(selectionID);
        int padding = 0;
        if (preselected != null) {
            for (String option: preselected) {
                if (!option.contains("page_")) {
                    menuBuilder.addOption(option, option);
                }
            }
            menuBuilder.setDefaultValues(preselected);
            padding = preselected.size();
        }

        boolean needsPageOption = false;
        int itemCount = 25 - padding;
        if (itemCount < options.size()) {
            itemCount--;
            needsPageOption = true;
        }
        for (int i = startIdx; i < startIdx + itemCount && i < options.size(); i++) {
            menuBuilder.addOption(options.get(i), options.get(i));
        }
        if (needsPageOption) {
            int nextPage = startIdx+itemCount;
            if (nextPage > options.size()) {
                nextPage = 0;
            }
            menuBuilder.addOption("More...", "page_" + nextPage);
        }
        menuBuilder.setMinValues(1);
        menuBuilder.setMaxValues(4);

        if (event instanceof StringSelectInteractionEvent) {
            ((StringSelectInteractionEvent) event).getMessage().delete().queue();
        }

        event.getMessageChannel().sendMessage("").addComponents(ActionRow.of(menuBuilder.build())).queue();
    }
}

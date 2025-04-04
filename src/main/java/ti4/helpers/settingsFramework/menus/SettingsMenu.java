package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.json.ObjectMapperFactory;
import ti4.listeners.context.ListenerContext;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

/**
 * <h1>Jazzxhands Menu Framework</h1>
 * <p>
 * - Handles
 * <p>
 * <b>Menu Button Layout:</b>
 * <p>
 * - [[navigation buttons]] [[special buttons]] [reset settings button*] [prev page*] [[all settings buttons*]] [next page*]
 * <p>
 * <i>- *no buttons added if there are no settings in this menu</i>
 */
@Getter
public abstract class SettingsMenu {
    // Prefix "Jazz Menu Framework"
    protected static final @JsonIgnore String menuNav = "jmfN";
    protected static final @JsonIgnore String menuAction = "jmfA";

    protected final @JsonIgnore String menuName;
    protected final @JsonIgnore List<String> description = new ArrayList<>();
    protected final @JsonIgnore SettingsMenu parent;

    protected final String menuId;
    protected String messageId = null;

    protected SettingsMenu(String menuId, String menuName, String description, SettingsMenu parent) {
        this.menuId = menuId;
        this.menuName = menuName;
        this.description.add(description);
        this.parent = parent;
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridable Methods:
    //  - Override these as needed
    // ---------------------------------------------------------------------------------------------------------------------------------
    protected List<SettingInterface> settings() {
        return Collections.emptyList();
    }

    protected List<SettingsMenu> categories() {
        return Collections.emptyList();
    }

    protected List<Button> specialButtons() {
        return Collections.emptyList();
    }

    /** Action Handler. Returns "success" on a success, returns null if the action was not found */
    protected String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        return null;
    }

    /** Action Handler. Returns null on a success */
    protected String resetSettings() {
        if (enabledSettings().isEmpty()) return "No settings to reset.";
        for (SettingInterface setting : enabledSettings())
            setting.reset();
        return null;
    }

    protected void updateTransientSettings() {
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // "Static" methods:
    //  - These methods should only rarely need to be overridden
    // ---------------------------------------------------------------------------------------------------------------------------------
    public List<SettingInterface> enabledSettings() {
        updateTransientSettings();
        return settings().stream().filter(x -> !x.isDisabled()).toList();
    }

    public String menuSummaryString(String lastSettingTouched) {
        StringBuilder sb = new StringBuilder("# **__").append(menuName).append(":__**");
        for (String line : description)
            sb.append("\n- *").append(line).append("*");
        sb.append("\n");

        int pad = enabledSettings().stream().map(x -> x.getName().length()).max(Comparator.comparingInt(x -> x)).orElse(15);
        for (SettingInterface setting : enabledSettings()) {
            sb.append("> ");
            sb.append(setting.longSummary(pad, lastSettingTouched));
            sb.append("\n");
        }
        if (!enabledSettings().isEmpty()) sb.append("\n"); // extra line for formatting

        if (!categories().isEmpty()) {
            List<String> catStrings = new ArrayList<>();
            for (SettingsMenu cat : categories()) {
                catStrings.add(cat.shortSummaryString(false));
            }
            String catStr = String.join("\n\n", catStrings);
            if (sb.length() + catStr.length() > 1999) {
                List<String> shorterCatStrings = new ArrayList<>();
                for (SettingsMenu cat : categories()) {
                    shorterCatStrings.add(cat.shortSummaryString(true));
                }
                catStr = String.join("\n\n", shorterCatStrings);
                if (sb.length() + catStr.length() > 1999) catStr = ""; //give up
            }
            sb.append(catStr);
        }
        return sb.toString();
    }

    public String shortSummaryString(boolean shortDescrOnly) {
        StringBuilder sb = new StringBuilder("**__" + menuName + ":__**");
        for (String line : description) {
            sb.append("\n- *").append(line).append("*");
            if (shortDescrOnly)
                break;
        }
        if (shortDescrOnly) return sb.toString();

        int maxlength = enabledSettings().stream()
            .filter(s -> s.getId() != null)
            .map(s -> s.getId().length()).max(Comparator.comparingInt(x -> x)).orElse(5);
        for (SettingInterface setting : enabledSettings()) {
            sb.append("\n> ").append(setting.shortSummary(maxlength));
        }
        return sb.toString();
    }

    public void postMessageAndButtons(GenericInteractionCreateEvent event) {
        String newSummary = menuSummaryString(null);
        List<Button> buttons = getPaginatedButtons(0);
        MessageHelper.splitAndSentWithAction(newSummary, event.getMessageChannel(), buttons, this::setMessageId);
    }

    public void parseButtonInput(ButtonInteractionEvent event) {
        parseInput(event, event.getButton().getId());
    }

    public void parseSelectionInput(StringSelectInteractionEvent event) {
        parseInput(event, event.getComponentId());
    }

    public void parseInput(ListenerContext context) {
        parseInput(context.getEvent(), context.getOrigComponentID());
    }

    private void parseInput(GenericInteractionCreateEvent event, String originalId) {
        // This should only ever be run on the most top-level settings menu
        if (getParent() != null) {
            parent.parseInput(event, originalId);
            return;
        }

        List<String> parts = Arrays.asList(originalId.split("_"));
        if (parts.size() < 3) {
            buttonFailed(event, "This button is not formatted properly.", true);
        } else {
            String buttonType = parts.get(0);
            String pathString = parts.get(1);
            String action = String.join("_", parts.subList(2, parts.size()));
            List<String> path = new ArrayList<>(Arrays.asList(pathString.split("\\.")));
            handleButtonPress(event, buttonType, action, path);
        }
    }

    protected String navId() {
        String base = getParent() != null ? getParent().navId() + "." : "";
        return base + menuId;
    }

    protected void buttonFailed(GenericInteractionCreateEvent event, String userMsg) {
        buttonFailed(event, userMsg, true);
    }

    protected void buttonFailed(GenericInteractionCreateEvent event, String userMsg, boolean pingJazz) {
        if (pingJazz) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), userMsg + "\n" + Constants.jazzPing() + " Menu Framework button has failed.");
            userMsg += "\n> *Jazz has been pinged to take a look.*";
        }
        if (event instanceof ButtonInteractionEvent buttonEvent)
            buttonEvent.getHook().sendMessage(userMsg).setEphemeral(true).queue();
        else if (event instanceof ModalInteractionEvent modalEvent)
            modalEvent.getHook().sendMessage(userMsg).setEphemeral(true).queue();
        else if (event instanceof StringSelectInteractionEvent stringEvent)
            stringEvent.getHook().sendMessage(userMsg).setEphemeral(true).queue();
    }

    public String getMessageId() {
        if (this.parent != null) {
            return this.parent.getMessageId();
        }
        return this.messageId;
    }

    public void setMessageId(String messageId) {
        if (Objects.equals(this.messageId, messageId)) return;
        this.messageId = messageId;
        for (SettingsMenu cat : categories()) {
            if (cat != null) {
                cat.setMessageId(messageId);
            }
        }
        if (parent != null) {
            parent.setMessageId(messageId);
        }
    }

    private void setMessageId(Message msg) {
        setMessageId(msg.getId());
    }

    private boolean handleButtonPress(GenericInteractionCreateEvent event, String buttonType, String action, List<String> path) {
        List<String> remainingPath = new ArrayList<>(path); // copy to prevent pointer shenanigans
        if (!remainingPath.isEmpty()) {
            String menu = remainingPath.getFirst();
            if (menu.equals(menuId)) {
                remainingPath.removeFirst();
                if (!remainingPath.isEmpty()) {
                    for (SettingsMenu child : categories()) {
                        if (remainingPath.getFirst().equals(child.getMenuId())) {
                            // found the path
                            return child.handleButtonPress(event, buttonType, action, remainingPath);
                        }
                    }
                } else { // We have found our button
                    switch (buttonType) {
                        case menuNav -> handleNavigation(event, action);
                        case menuAction -> handleAction(event, action);
                    }
                    return true;
                }
            }
        }
        buttonFailed(event, "Could not complete this button press. [Likely not yet implemented]", true);
        return false;
    }

    private void handleNavigation(GenericInteractionCreateEvent event, String action) {
        int page = 0;
        try {
            page = Integer.parseInt(action);
        } catch (Exception e) {
            buttonFailed(event, "Could not parse page number for nav button. Navigating to page 0 instead.", true);
        }
        refreshMessageAndButtons(event, null, page);
    }

    private void handleAction(GenericInteractionCreateEvent event, String action) {
        String err = null;
        boolean found = false;
        String settingTouched = null;

        // Check the settings buttons
        for (SettingInterface setting : enabledSettings()) {
            if (action.endsWith(setting.getId())) {
                err = setting.modify(event, action);
                settingTouched = setting.getId();
                found = true;
                break;
            } else if (action.contains("_")) {
                String actionID = action.split("_")[0];
                if (!actionID.isEmpty() && actionID.endsWith(setting.getId())) {
                    err = setting.modify(event, action);
                    settingTouched = setting.getId();
                    found = true;
                    break;
                }
            }
        }

        if (action.equals("reset")) {
            err = resetSettings();
            found = true;
        }

        // Check the special buttons
        String special = handleSpecialButtonAction(event, action);
        if (special != null) {
            if (special.equals("success")) {
                found = true;
            } else {
                err = special;
            }
        }

        if (!found) {
            buttonFailed(event, err + " - This action is not supported [yet].", true);
        } else if (err != null) {
            buttonFailed(event, err);
        } else {
            refreshMessageAndButtons(event, settingTouched, 0);
        }
    }

    private void refreshMessageAndButtons(GenericInteractionCreateEvent event, String settingTouched, int page) {
        String newSummary = menuSummaryString(settingTouched);
        List<LayoutComponent> actionRows = new ArrayList<>();
        for (List<Button> row : ListUtils.partition(getPaginatedButtons(page), 5)) {
            actionRows.add(ActionRow.of(row));
        }

        // Edit the existing message, if able
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            setMessageId(buttonEvent.getMessage());
            buttonEvent.getHook().editOriginal(newSummary).setComponents(actionRows).queue();
        } else if (event instanceof ModalInteractionEvent modalEvent) {
            if (modalEvent.getMessage() != null) {
                modalEvent.getMessage().editMessage(newSummary).setComponents(actionRows).queue();
            }
        } else if (event instanceof StringSelectInteractionEvent selectEvent) {
            selectEvent.getGuildChannel()
                .editMessageById(getMessageId(), newSummary)
                .setComponents(actionRows)
                .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    @JsonIgnore
    private List<Button> getPaginatedButtons(int settingsButtonPage) {
        List<Button> buttons = new ArrayList<>();
        buttons.addAll(navButtons());
        buttons.addAll(specialButtons());
        int settingsButtonSize = 25 - buttons.size();
        buttons.addAll(settingsButtonsPage(settingsButtonSize, settingsButtonPage));
        return buttons;
    }

    private List<Button> navButtons() {
        List<Button> buttons = new ArrayList<>();
        if (getParent() != null) {
            buttons.add(getParent().getNavButton(true));
        }
        for (SettingsMenu child : categories()) {
            if (child != null) buttons.add(child.getNavButton(false));
        }
        return buttons;
    }

    @JsonIgnore
    private Button getNavButton(boolean fromChild) {
        ButtonStyle style = fromChild ? ButtonStyle.PRIMARY : ButtonStyle.SECONDARY;
        Emoji emoji = Emoji.fromUnicode(fromChild ? "💾" : "✏️");
        String id = menuNav + "_" + navId() + "_0";
        String label = (fromChild ? "Go back to " : "Edit ") + menuName;
        return Button.of(style, id, label, emoji);
    }

    /**
     * @param allottedSpace Number of navigation buttons already included in this menu. If there are too many, settings buttons will be split into pages
     * @param pageNum
     * @return
     */
    private List<Button> settingsButtonsPage(int allottedSpace, int pageNum) {
        List<Button> allButtons = allSettingsButtons();
        if (allottedSpace < allButtons.size()) {
            if (allottedSpace < 3) {
                // This shouldn't ever happen as I don't really expect to ever see more than 7 other buttons,
                // which means allotted space should always be >= 18
                BotLogger.error("NOT ENOUGH SPACE FOR BUTTONS IN MENU: " + navId());
                return Collections.emptyList();
            }
            List<List<Button>> paginated = ListUtils.partition(allButtons, allottedSpace - 2);
            int maxPage = paginated.size() - 1;
            pageNum = Math.max(0, Math.min(pageNum, maxPage));

            String navString = menuNav + "_" + navId() + "_";
            List<Button> buttonsToUse = new ArrayList<>();
            if (pageNum > 0) {
                Button prevPage = Button.of(ButtonStyle.PRIMARY, navString + (pageNum - 1), "Previous Page", Emoji.fromUnicode("⏪"));
                buttonsToUse.add(prevPage);
            }
            buttonsToUse.addAll(paginated.get(pageNum));
            if (pageNum < maxPage) {
                Button nextPage = Button.of(ButtonStyle.PRIMARY, navString + (pageNum + 1), "Next Page", Emoji.fromUnicode("⏩"));
                buttonsToUse.add(nextPage);
            }
            return buttonsToUse;
        }
        return allButtons;
    }

    private List<Button> allSettingsButtons() {
        String prefixID = menuAction + "_" + navId() + "_";
        List<SettingInterface> interfaces = new ArrayList<>(enabledSettings());
        List<Button> settingButtons = interfaces.stream()
            .flatMap(setting -> new ArrayList<>(setting.getButtons(prefixID)).stream())
            .toList();
        List<Button> output = new ArrayList<>();
        if (!settingButtons.isEmpty())
            output.add(Button.of(ButtonStyle.DANGER, prefixID + "reset", "Reset to default settings"));
        output.addAll(settingButtons);
        return output;
    }

    @JsonIgnore
    public String json() {
        ObjectMapper mapper = ObjectMapperFactory.build();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            BotLogger.error("Error mapping to json:", e);
        }
        return null;
    }
}

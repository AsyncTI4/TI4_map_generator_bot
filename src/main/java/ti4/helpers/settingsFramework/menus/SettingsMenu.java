package ti4.helpers.settingsFramework.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.function.Consumers;

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
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.json.ObjectMapperFactory;
import ti4.map.Game;
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
public abstract class SettingsMenu {
    // Prefix "Jazz Menu Framework"
    public static final String menuNav = "jmfN";
    public static final String menuAction = "jmfA";

    @Getter
    private String messageID = null;
    @Getter
    @JsonIgnore
    protected String menuId = null;
    @Getter
    @JsonIgnore
    protected String menuName = null;
    @Getter
    @JsonIgnore
    protected String description = null;
    @Getter
    @JsonIgnore
    protected SettingsMenu parent = null;

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridable Methods:
    //  - Override these as needed
    // ---------------------------------------------------------------------------------------------------------------------------------
    public List<SettingInterface> settings() {
        return Collections.emptyList();
    }

    public List<SettingsMenu> categories() {
        return Collections.emptyList();
    }

    public List<Button> specialButtons() {
        return Collections.emptyList();
    }

    /** Action Handler. Returns "success" on a success, returns null if the action was not found */
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        return null;
    }

    /** Action Handler. Returns null on a success */
    protected String resetSettings() {
        if (settings().size() == 0) return "No settings to reset.";
        for (SettingInterface setting : settings())
            setting.reset();
        return null;
    }

    @JsonIgnore
    public void finishInitialization(Game game, SettingsMenu parent) {
        this.parent = parent;
        for (SettingsMenu cat : categories())
            cat.finishInitialization(game, this);
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // "Static" methods:
    //  - These methods should only rarely need to be overridden
    // ---------------------------------------------------------------------------------------------------------------------------------
    public String menuSummaryString(String lastSettingTouched) {
        StringBuilder sb = new StringBuilder("# **__").append(menuName).append(":__**");
        sb.append("\n- *").append(description).append("*\n");

        int pad = settings().stream().map(x -> x.name.length()).max(Comparator.comparingInt(x -> x)).orElse(15);
        for (SettingInterface setting : settings()) {
            sb.append("> ");
            sb.append(setting.longSummary(pad, lastSettingTouched));
            sb.append("\n");
        }
        if (settings().size() > 0) sb.append("\n"); // extra line for formatting

        if (categories().size() > 0) {
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

    public String shortSummaryString(boolean descrOnly) {
        StringBuilder sb = new StringBuilder("**__" + menuName + ":__**");
        if (description != null) sb.append("\n*").append(description).append("*");
        if (descrOnly) return sb.toString();

        int maxlength = settings().stream()
            .filter(s -> s.id != null)
            .map(s -> s.id.length()).max(Comparator.comparingInt(x -> x)).orElse(5);
        for (SettingInterface setting : settings()) {
            sb.append("\n> ").append(setting.shortSummary(maxlength));
        }
        return sb.toString();
    }

    public void postMessageAndButtons(GenericInteractionCreateEvent event) {
        String newSummary = menuSummaryString(null);
        List<Button> buttons = getPaginatedButtons(0);
        MessageHelper.splitAndSentWithAction(newSummary, event.getMessageChannel(), buttons, this::setMessageID);
    }

    public void parseModalInput(ModalInteractionEvent event) {
        parseInput(event, event.getModalId());
    }

    public void parseButtonInput(ButtonInteractionEvent event) {
        parseInput(event, event.getButton().getId());
    }

    public void parseSelectionInput(StringSelectInteractionEvent event) {
        parseInput(event, event.getComponentId());
    }

    protected void parseInput(GenericInteractionCreateEvent event, String originalId) {
        // This should only ever be run on the most top-level settings menu
        if (getParent() != null) {
            parent.parseInput(event, originalId);
            return;
        }

        List<String> parts = Arrays.asList(originalId.split("_"));
        if (parts.size() < 3) {
            buttonFailed(event, "This button is not formatted properly.", true);
            return;
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
            BotLogger.log(event, userMsg + "\n" + Constants.jazzPing() + " Menu Framework button has failed.");
            userMsg += "\n> *Jazz has been pinged to take a look.*";
        }
        if (event instanceof ButtonInteractionEvent buttonEvent)
            buttonEvent.getHook().sendMessage(userMsg).setEphemeral(true).queue();
        else if (event instanceof ModalInteractionEvent modalEvent)
            modalEvent.getHook().sendMessage(userMsg).setEphemeral(true).queue();
        else if (event instanceof StringSelectInteractionEvent stringEvent)
            stringEvent.getHook().sendMessage(userMsg).setEphemeral(true).queue();
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    @JsonIgnore
    private void setMessageID(Message msg) {
        if (Objects.equals(this.messageID, msg.getId())) return;
        this.messageID = msg.getId();
        for (SettingsMenu cat : categories())
            cat.setMessageID(msg);
        if (this.parent != null)
            this.parent.setMessageID(msg);
    }

    private boolean handleButtonPress(GenericInteractionCreateEvent event, String buttonType, String action, List<String> path) {
        List<String> remainingPath = new ArrayList<>(path); // copy to prevent pointer shenanigans
        if (remainingPath.size() > 0) {
            String menu = remainingPath.get(0);
            if (menu.equals(menuId)) {
                remainingPath.remove(0);
                if (remainingPath.size() > 0) {
                    for (SettingsMenu child : categories()) {
                        if (remainingPath.get(0).equals(child.getMenuId())) {
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
        for (SettingInterface setting : settings()) {
            if (action.endsWith(setting.id)) {
                err = setting.modify(event, action);
                settingTouched = setting.id;
                found = true;
                break;
            } else if (action.contains("_")) {
                String actionID = action.split("_")[0];
                if (actionID.length() > 0 && actionID.endsWith(setting.id)) {
                    err = setting.modify(event, action);
                    settingTouched = setting.id;
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
            if (this.messageID == null) {
                this.setMessageID(buttonEvent.getMessage());
            }
            buttonEvent.getHook().editOriginal(newSummary).setComponents(actionRows).queue();
        } else if (event instanceof ModalInteractionEvent modalEvent) {
            if (modalEvent.getMessage() != null) {
                modalEvent.getMessage().editMessage(newSummary).setComponents(actionRows).queue();
            }
        } else if (event instanceof StringSelectInteractionEvent selectEvent) {
            selectEvent.getGuildChannel()
                .editMessageById(this.messageID, newSummary)
                .setComponents(actionRows)
                .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

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
                BotLogger.log("NOT ENOUGH SPACE FOR BUTTONS IN MENU: " + navId());
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
        List<SettingInterface> interfaces = new ArrayList<>(settings());
        List<Button> settingButtons = interfaces.stream()
            .flatMap(setting -> new ArrayList<>(setting.getButtons(prefixID)).stream())
            .toList();
        List<Button> output = new ArrayList<>();
        if (settingButtons.size() > 0)
            output.add(Button.of(ButtonStyle.DANGER, prefixID + "reset", "Reset to default settings"));
        output.addAll(settingButtons);
        return output;
    }

    @JsonIgnore
    public String json() {
        ObjectMapper mapper = ObjectMapperFactory.build();
        try {
            String val = mapper.writeValueAsString(this);
            return val;
        } catch (Exception e) {
            BotLogger.log("Error mapping to json:", e);
        }
        return null;
    }

    @JsonIgnore
    public static <T extends SettingsMenu> T readJson(String json, Class<T> typeClass) {
        ObjectMapper mapper = ObjectMapperFactory.build();
        try {
            T newMenu = mapper.readValue(json, typeClass);
            return newMenu;
        } catch (Exception e) {
            BotLogger.log("Error reading settings menu from json:", e);
        }
        return null;
    }
}

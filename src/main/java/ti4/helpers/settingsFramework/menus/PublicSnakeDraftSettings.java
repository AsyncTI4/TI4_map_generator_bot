package ti4.helpers.settingsFramework.menus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.collections4.ListUtils;
import ti4.buttons.Buttons;
import ti4.helpers.StringHelper;
import ti4.helpers.settingsFramework.settings.BooleanSetting;
import ti4.helpers.settingsFramework.settings.ReadOnlyTextSetting;
import ti4.helpers.settingsFramework.settings.SettingInterface;
import ti4.map.Game;
import ti4.map.Player;

// This is a sub-menu
@Getter
@JsonIgnoreProperties("messageId")
public class PublicSnakeDraftSettings extends SettingsMenu {
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Settings & Submenus
    // ---------------------------------------------------------------------------------------------------------------------------------
    private final BooleanSetting presetDraftOrder;
    private final ReadOnlyTextSetting orderedPlayerInfo;
    // List settings don't seem to handle order well, so manage it separately
    private List<String> orderedPlayerIds;
    // ---------------------------------------------------------------------------------------------------------------------------------
    // Constructor & Initialization
    // ---------------------------------------------------------------------------------------------------------------------------------

    private static final String MENU_ID = "dsPublicSnake";

    public PublicSnakeDraftSettings(Game game, JsonNode json, SettingsMenu parent) {
        super(MENU_ID, "Public Snake Draft", "Control how the public snake draft works", parent);

        // Initialize Settings to default values
        presetDraftOrder = new BooleanSetting("Static Order?", "use static order", false);
        orderedPlayerInfo = new ReadOnlyTextSetting("The Order", "static order", "");
        orderedPlayerIds = null;

        // Load JSON if applicable
        if (!(json == null
                || !json.has("menuId")
                || !MENU_ID.equals(json.get("menuId").asText("")))) {
            presetDraftOrder.initialize(json.get("presetDraftOrder"));

            if (json.has("orderedPlayerIds")) {
                orderedPlayerIds = new ArrayList<>();
                for (JsonNode node : json.get("orderedPlayerIds")) {
                    orderedPlayerIds.add(node.asText());
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // Overridden Implementation
    // ---------------------------------------------------------------------------------------------------------------------------------
    @Override
    public List<SettingInterface> settings() {
        List<SettingInterface> ls = new ArrayList<>();
        ls.add(presetDraftOrder);
        if (presetDraftOrder.isVal()) {
            ls.add(orderedPlayerInfo);
        }
        return ls;
    }

    @Override
    public List<Button> specialButtons() {
        String idPrefix = menuAction + "_" + navId() + "_";
        List<Button> ls = new ArrayList<>(super.specialButtons());

        if (presetDraftOrder.isVal()) {
            ls.add(Buttons.gray(idPrefix + "setOrder", "🔀 Change draft order"));
        }

        return ls;
    }

    @Override
    public String handleSpecialButtonAction(GenericInteractionCreateEvent event, String action) {
        String error =
                switch (action) {
                    case "staticOrder~MDL" -> getPresetSlicesFromUser(event);
                    case "staticOrder" -> setPresetSlices(event);
                    case "setOrder" -> initialSetOrderButtons(event);
                    default -> null;
                };

        if (action.startsWith("orderFor_")) {
            proceedSettingOrder(event, action);
        }

        return (error == null ? "success" : error);
    }

    @Override
    protected void updateTransientSettings() {
        super.updateTransientSettings();

        if (presetDraftOrder.isVal()) {
            if (orderedPlayerIds != null && !orderedPlayerIds.isEmpty()) {
                if (parent instanceof DraftSystemSettings dss) {
                    Set<String> playerIdsInDraft = dss.getPlayerUserIds();
                    for (String playerId : orderedPlayerIds) {
                        if (!playerIdsInDraft.contains(playerId)) {
                            orderedPlayerIds = null;
                            orderedPlayerInfo.setDisplay("(not set)");
                            return;
                        }
                    }
                }

                StringBuilder display = new StringBuilder();
                for (int i = 0; i < orderedPlayerIds.size(); ++i) {
                    if (i > 0) display.append(", ");
                    String playerId = orderedPlayerIds.get(i);
                    Player player = null;
                    if (parent instanceof DraftSystemSettings dss) {
                        Game game = dss.getGame();
                        player = game.getPlayer(playerId);
                    }
                    if (player != null) {
                        display.append(player.getUserName());
                    } else {
                        display.append("Unknown(").append(playerId).append(")");
                    }
                }
                orderedPlayerInfo.setDisplay(display.toString());
            } else {
                orderedPlayerInfo.setDisplay("(not set)");
            }
        } else {
            orderedPlayerIds = null;
            orderedPlayerInfo.setDisplay("");
        }
    }

    private String initialSetOrderButtons(GenericInteractionCreateEvent event) {
        if (!(parent instanceof DraftSystemSettings)) {
            return "Unknown Event (or unknown parent menu)";
        }
        DraftSystemSettings dss = (DraftSystemSettings) parent;
        Set<String> playerUserIds = dss.getPlayerUserIds();
        Game game = dss.getGame();

        List<Button> buttons = new ArrayList<>();
        String currentPrefix = menuAction + "_" + navId() + "_orderFor_";
        for (String userId : playerUserIds) {
            Player player = game.getPlayer(userId);
            if (player == null) {
                return "Player in draft is not found in game";
            }
            String playerName = player.getUserName();
            buttons.add(Buttons.blue(currentPrefix + userId, playerName));
        }

        orderedPlayerIds = null;
        String content = "Select the first player in the draft order";

        // MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), content, buttons);
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            List<MessageTopLevelComponent> components = new ArrayList<>();
            for (List<Button> row : ListUtils.partition(buttons, 5)) {
                components.add(ActionRow.of(row));
            }
            buttonEvent
                    .getHook()
                    .sendMessage(content)
                    .addComponents(components)
                    .setEphemeral(true)
                    .queue();
            return null;
        }
        return null;
    }

    private String proceedSettingOrder(GenericInteractionCreateEvent event, String action) {

        // Delete the extra message
        if (event instanceof ButtonInteractionEvent buttonEvent
                && buttonEvent.getMessage().isEphemeral()) {
            buttonEvent.getMessage().delete().queue();
        }

        if (!(parent instanceof DraftSystemSettings)) {
            return "Unknown Event (or unknown parent menu)";
        }
        DraftSystemSettings dss = (DraftSystemSettings) parent;
        Set<String> playerUserIds = dss.getPlayerUserIds();
        Game game = dss.getGame();

        String playerUserId = action.substring("orderFor_".length());

        Player player = game.getPlayer(playerUserId);
        if (player == null) {
            return "Player in draft is not found in game";
        }

        if (orderedPlayerIds == null) {
            orderedPlayerIds = new ArrayList<>();
        }
        if (orderedPlayerIds.contains(playerUserId)) {
            String playerName = player.getUserName();
            return "Player " + playerName + " is already in the order";
        }
        orderedPlayerIds.add(playerUserId);

        if (orderedPlayerIds.size() == playerUserIds.size() - 1) {
            // Last one, just add them and finish
            for (String nextUserId : playerUserIds) {
                if (orderedPlayerIds.contains(nextUserId)) continue;
                Player nextPlayer = game.getPlayer(nextUserId);
                if (nextPlayer == null) {
                    return "Player in draft is not found in game";
                }
                orderedPlayerIds.add(nextUserId);
            }
        } else if (orderedPlayerIds.size() < playerUserIds.size()) {
            List<Button> buttons = new ArrayList<>();
            String currentPrefix = menuAction + "_" + navId() + "_orderFor_";
            for (String nextUserId : playerUserIds) {
                if (orderedPlayerIds.contains(nextUserId)) continue;
                Player nextPlayer = game.getPlayer(nextUserId);
                if (nextPlayer == null) {
                    return "Player in draft is not found in game";
                }
                String nextPlayerName = nextPlayer.getUserName();
                buttons.add(Buttons.blue(currentPrefix + nextUserId, nextPlayerName));
            }

            int draftSize = orderedPlayerIds.size();
            String content = "Select the " + StringHelper.ordinal(draftSize + 1) + " player in the draft order.";
            // MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(event.getMessageChannel(), content, buttons);
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                List<MessageTopLevelComponent> components = new ArrayList<>();
                for (List<Button> row : ListUtils.partition(buttons, 5)) {
                    components.add(ActionRow.of(row));
                }
                buttonEvent
                        .getHook()
                        .sendMessage(content)
                        .addComponents(components)
                        .setEphemeral(true)
                        .queue();
                return null;
            }
        }
        return null;
    }

    private String getPresetSlicesFromUser(GenericInteractionCreateEvent event) {
        String modalId = menuAction + "_" + navId() + "_staticOrder";
        // TextInput ttsString = TextInput.create("sliceStrings", TextInputStyle.PARAGRAPH)
        //         .setPlaceholder("25,69,34,49,45;24,28,46,47,67;...")
        //         .setMinLength(1)
        //         .setRequired(true)
        //         .build();

        if (!(parent instanceof DraftSystemSettings)) {
            return "Unknown Event (or unknown parent menu)";
        }
        DraftSystemSettings dss = (DraftSystemSettings) parent;
        Set<String> playerUserIds = dss.getPlayerUserIds();
        Game game = dss.getGame();
        List<ModalTopLevelComponent> components = new ArrayList<>();
        int defaultIndex = 1;
        int count = playerUserIds.size();
        for (String userId : playerUserIds) {
            Player player = game.getPlayer(userId);
            if (player == null) {
                return "Player in draft is not found in game";
            }
            String playerName = player.getUserName();

            StringSelectMenu.Builder builder = StringSelectMenu.create("orderFor_" + userId);
            for (int j = 1; j <= count; ++j) {
                builder.addOption(
                        "Pick " + j,
                        "" + j,
                        "Pick position " + j + " in the first round, and " + (count - j + 1)
                                + " in the second round, etc");
            }
            builder.setDefaultValues("" + defaultIndex);
            builder.setRequired(true);
            builder.setRequiredRange(1, 1);
            components.add(Label.of("Pick position for " + playerName, builder.build()));
        }

        Modal modal = Modal.create(modalId, "Position picker")
                .addComponents(components)
                // .addComponents(Label.of("TTS String", ttsString))
                .build();
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            buttonEvent.replyModal(modal).queue();
            return null;
        }
        return "Unknown Event";
    }

    private String setPresetSlices(GenericInteractionCreateEvent event) {
        if (event instanceof ModalInteractionEvent modalEvent
                && modalEvent.getModalId().endsWith("_staticOrder")) {
            Map<String, String> selections = modalEvent.getValues().stream()
                    .collect(Collectors.toMap(
                            v -> v.getCustomId().substring("orderFor_".length()), v -> v.getAsString()));

            orderedPlayerIds = new ArrayList<>(List.of(new String[selections.size()]));

            for (Map.Entry<String, String> playerToPos : selections.entrySet()) {
                String playerId = playerToPos.getKey();
                String posStr = playerToPos.getValue();
                int pos;
                try {
                    pos = Integer.parseInt(posStr);
                } catch (NumberFormatException nfe) {
                    return "Invalid position: " + posStr;
                }
                if (pos < 1 || pos > selections.size()) {
                    return "Position out of range: " + posStr;
                }
                if (orderedPlayerIds.get(pos - 1) != null) {
                    return "Two players cannot have the same position: " + posStr;
                }
                orderedPlayerIds.set(pos - 1, playerId);
            }
        }
        return "Unknown Event";
    }
}

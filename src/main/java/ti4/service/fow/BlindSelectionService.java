package ti4.service.fow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import ti4.buttons.Buttons;
import ti4.helpers.FoWHelper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

/*
 * Flow of Blind Selection:
 * 1) filterForBlindPositionSelection()
 *   - List of all possible buttons is filtered to only those that are valid and visible to the player
 *   - List of valid targets is stored for later validation
 *   - Blind Selection button is added and will contain the original button prefix
 * 2) offerBlindSelection()
 *   - Player clicks Blind Selection button, which spawns a Modal to enter the target
 * 3) doBlindSelection()
 *   - Target value is validated to be a valid tile position
 *   - Button with the selected target is created, along with a Change Selection button
 *  4) doBlindValidation()
 *   - When player clicks the target button, the target is validated against the stored valid targets
 *   - If valid, the real action button is created with the original button prefix and target
 */
@UtilityClass
public class BlindSelectionService {
    private static final String TARGET = "target";
    private static final String VALIDATION_KEY = "blindSelectionValidTargets";
    private static final String VALID_SEPARATOR = ";";

    public void filterForBlindPositionSelection(Game game, Player player, List<Button> buttons, String buttonPrefix) {
        if (!game.isFowMode()) return;

        StringBuilder validTargets = new StringBuilder(VALID_SEPARATOR);
        Set<String> visibleTilePositions = FoWHelper.getTilePositionsToShow(game, player);
        for (Button button : new ArrayList<>(buttons)) {
            String[] parts = button.getCustomId().split("_");
            if (parts.length < 2) continue;

            validTargets.append(parts[1]).append(VALID_SEPARATOR);
            if (!visibleTilePositions.contains(parts[1])) {
                buttons.remove(button);
            }
        }

        game.setStoredValue(VALIDATION_KEY, validTargets.toString());
        buttons.add(Buttons.red("blindSelection~MDL_" + buttonPrefix, "Blind Selection"));
    }

    @ButtonHandler("blindSelection~MDL")
    public static void offerBlindSelection(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String buttonIdPrefix = buttonID.replace("blindSelection~MDL_", "");
        TextInput target =
                TextInput.create(TARGET, TextInputStyle.SHORT).setRequired(true).build();

        Modal blindSelectionModal = Modal.create(
                        "blindSelection_" + event.getMessageId() + "_" + buttonIdPrefix, "Blind Selection")
                .addComponents(Label.of("Target", target))
                .build();

        event.replyModal(blindSelectionModal).queue();
    }

    @ModalHandler("blindSelection_")
    public static void doBlindSelection(ModalInteractionEvent event, Player player, Game game) {
        String[] buttonData = event.getModalId().split("_");
        String origMessageId = buttonData[1];
        String buttonIdPrefix = buttonData[2];
        String target = event.getValue(TARGET).getAsString().trim();

        if (!PositionMapper.isTilePositionValid(target)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Target " + target + " is invalid.");
            return;
        }

        List<Button> chooseTargetButtons = new ArrayList<>();
        chooseTargetButtons.add(Buttons.blue("blindValidation_" + buttonIdPrefix + "_" + target, target));
        chooseTargetButtons.add(Buttons.red("blindSelection~MDL_" + buttonIdPrefix, "Change Selection"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please select the target:", chooseTargetButtons);

        event.getMessageChannel().deleteMessageById(origMessageId).queue();
    }

    @ButtonHandler("blindValidation_")
    public static void doBlindValidation(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] parts = buttonID.split("_");
        String buttonIdPrefix = parts[1];
        String target = parts[2];

        Button actionButton = null;
        String msg = "### 👎 " + target + " was not valid for this action.";
        String validTargets = game.getStoredValue(VALIDATION_KEY);
        System.out.println("Valid targets: " + validTargets);
        System.out.println("Chosen target: " + target);
        if (validTargets != null && validTargets.contains(VALID_SEPARATOR + target + VALID_SEPARATOR)) {
            msg = "### 👍 " + target + " is valid for this action.";
            actionButton = Buttons.green(
                    buttonIdPrefix + "_" + target, event.getButton().getLabel());
        }
        game.removeStoredValue(VALIDATION_KEY);

        MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, actionButton);
        event.getMessage().delete().queue();
    }
}

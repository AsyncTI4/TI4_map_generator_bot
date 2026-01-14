package ti4.service.fow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.FoWHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

/*
 * Flow of Blind Selection:
 * 1) filterForBlindPositionSelection()
 *   - filter the list of all valid buttons to only those that are visible to the player
 *   - store list of valid targets for later validation
 *   - Blind Selection button will carry the original button prefix
 * 2) offerBlindSelection()
 *   - spawn a Modal to enter the target
 * 3) doBlindSelection()
 *   - validate the modal input
 *   - show Button with the selected target and a Change Selection button
 *  4) doBlindValidation()
 *   - do the actual validation against stored valid targets
 *   - valid -> real action button is created with the original button prefix
 */
@UtilityClass
public class BlindSelectionService {
    public static final String TBD_FACTION = "TBDF";
    private static final String TARGET = "target";
    private static final String VALIDATION_KEY = "blindSelectionValidTargets";
    private static final String VALID_SEPARATOR = ";";

    private static final String PLANET = "P";
    private static final String POSITION = "T";

    public void filterForBlindPositionSelection(Game game, Player player, List<Button> buttons, String buttonPrefix) {
        if (!game.isFowMode()) return;
        filterForBlindSelection(game, player, buttons, buttonPrefix, POSITION);
    }

    public void filterForBlindPlanetSelection(Game game, Player player, List<Button> buttons, String buttonPrefix) {
        if (!game.isFowMode()) return;
        filterForBlindSelection(game, player, buttons, buttonPrefix, PLANET);
    }

    private static void filterForBlindSelection(
            Game game, Player player, List<Button> buttons, String buttonPrefix, String TYPE) {

        Set<String> visibleTilePositions = FoWHelper.getTilePositionsToShow(game, player);
        StringBuilder validTargets = new StringBuilder(VALID_SEPARATOR);

        for (Button button : new ArrayList<>(buttons)) {
            String target = StringUtils.substringAfterLast(button.getCustomId(), "_");
            validTargets.append(target).append(VALID_SEPARATOR);

            boolean keep;
            if (TYPE.equals(POSITION)) {
                // position selection: visible tile only
                keep = visibleTilePositions.contains(target);
            } else {
                // planet selection: planet must be on visible tile
                // or its owner must be visible to the requesting player
                Tile planetTile = game.getTileFromPlanet(target);
                if (planetTile == null) {
                    keep = false;
                } else {
                    Player owner = game.getPlayerThatControlsPlanet(target);
                    if (owner == null) {
                        keep = visibleTilePositions.contains(planetTile.getPosition());
                    } else {
                        keep = FoWHelper.canSeeStatsOfPlayer(game, owner, player);
                    }
                }
            }

            if (!keep) {
                buttons.remove(button);
            }
        }

        String encodedButtonPrefix =
                Base64.getUrlEncoder().withoutPadding().encodeToString(buttonPrefix.getBytes(StandardCharsets.UTF_8));
        game.setStoredValue(VALIDATION_KEY, validTargets.toString());
        buttons.add(Buttons.red("blindSelection~MDL_" + encodedButtonPrefix + "_" + TYPE, "Blind Target"));
    }

    @ButtonHandler("blindSelection~MDL")
    public static void offerBlindPositionSelection(ButtonInteractionEvent event, String buttonID) {
        String[] splitButton = buttonID.replace("blindSelection~MDL_", "").split("_");
        String encodedButtonPrefix = splitButton[0];
        String type = splitButton[1];

        TextInput target =
                TextInput.create(TARGET, TextInputStyle.SHORT).setRequired(true).build();

        Modal blindSelectionModal = Modal.create(
                        "blindSelection_" + event.getMessageId() + "_" + encodedButtonPrefix + "_" + type,
                        "Blind Target")
                .addComponents(Label.of("Target", target))
                .build();

        event.replyModal(blindSelectionModal).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ModalHandler("blindSelection_")
    public static void doBlindSelection(ModalInteractionEvent event, Player player) {
        String[] buttonData = event.getModalId().split("_");
        String origMessageId = buttonData[1];
        String encodedButtonPrefix = buttonData[2];
        String type = buttonData[3];
        String target = event.getValue(TARGET).getAsString().replace(" ", "").trim();

        boolean invalidTarget = false;
        // Check for position
        if (type.equals(POSITION)) {
            if (!PositionMapper.isTilePositionValid(target)) {
                invalidTarget = true;
            }
        } else {
            // Check for planet
            String planetID = AliasHandler.resolvePlanet(target);
            if (!Mapper.isValidPlanet(planetID)) {
                invalidTarget = true;
            } else {
                target = planetID;
            }
        }

        if (invalidTarget) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not parse target `" + target + "`");
            return;
        }

        String buttonLabel =
                Mapper.isValidPlanet(target) ? Mapper.getPlanetRepresentations().get(target) : target;
        List<Button> chooseTargetButtons = new ArrayList<>();
        chooseTargetButtons.add(
                Buttons.blue("blindValidation_" + encodedButtonPrefix + "_" + type + "_" + target, buttonLabel));
        chooseTargetButtons.add(
                Buttons.red("blindSelection~MDL_" + encodedButtonPrefix + "_" + type, "Change Selection"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the target.",
                chooseTargetButtons);

        event.getMessageChannel().deleteMessageById(origMessageId).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("blindValidation_")
    public static void doBlindValidation(ButtonInteractionEvent event, String buttonID, Game game) {
        String[] parts = buttonID.split("_");
        String originalButtonPrefix = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String type = parts[2];
        String target = parts[3];

        Button actionButton = null;
        String msg = "⛔ **" + target + "** was not valid for this action";
        String validTargets = game.getStoredValue(VALIDATION_KEY);
        if (validTargets != null && validTargets.contains(VALID_SEPARATOR + target + VALID_SEPARATOR)) {
            originalButtonPrefix = insertFactionToButtonId(target, type, originalButtonPrefix, game);
            msg = "✅ **" + target + "** is valid for this action";
            actionButton = Buttons.green(
                    originalButtonPrefix + "_" + target, event.getButton().getLabel());
        }
        game.removeStoredValue(VALIDATION_KEY);

        MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, actionButton);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    // if the original button id contains TBDF, replace it with the faction of the owner of the target
    private static String insertFactionToButtonId(String target, String type, String originalButtonPrefix, Game game) {
        if (!originalButtonPrefix.contains(TBD_FACTION)) return originalButtonPrefix;

        Player owner = null;
        if (type.equals(POSITION)) {
            owner = game.getPlayerThatControlsTile(game.getTileByPosition(target));
        } else {
            owner = game.getPlayerThatControlsPlanet(target);
        }

        if (owner != null) {
            return originalButtonPrefix.replace(TBD_FACTION, owner.getFaction());
        }
        return originalButtonPrefix;
    }
}

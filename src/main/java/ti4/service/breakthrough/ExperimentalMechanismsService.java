package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;

@UtilityClass
public class ExperimentalMechanismsService {

    private static final String STORED_KEY = "arvaxiMobilizationEngine";
    private static final String BOON_TEXT = "The printed values of this unit have been adjusted: Cost -1, Combat -1, Move +1, Capacity +1.";
    private static final String CURSE_TEXT = "The printed values of this unit have been adjusted: Cost +1, Combat +1, Move -1, Capacity -1.";

    private String btRep() {
        return Mapper.getBreakthrough("arvaxibt").getNameRepresentation();
    }

    public void postInitialButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            buttons.add(Buttons.gray(
                    player.finChecker() + "arvaxiEnginePlayer_" + target.getFaction(),
                    StringUtils.capitalize(target.getFaction()),
                    target.getFactionEmojiOrColor()));
        }
        buttons.add(Buttons.red(player.finChecker() + "deleteButtons", "Delete these buttons"));

        String msg = player.getRepresentation() + " use " + btRep()
                + " to choose which player's unit upgrade to attach the Mobilization Engine to.";
        String existing = game.getStoredValue(STORED_KEY);
        if (!existing.isEmpty()) {
            String[] parts = existing.split("_", 3);
            if (parts.length == 3) {
                Player owner = game.getPlayerFromColorOrFaction(parts[0]);
                TechnologyModel tech = Mapper.getTech(parts[1]);
                String ownerName = owner != null ? owner.getRepresentationNoPing() : parts[0];
                String techName = tech != null ? tech.getName() : parts[1];
                msg += "\n-# Currently attached to " + ownerName + " " + techName + " (" + parts[2] + ").";
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("arvaxiEnginePlayer_")
    private void chooseTargetTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String targetFaction = buttonID.replace("arvaxiEnginePlayer_", "");
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) return;
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = new ArrayList<>();
        for (String techID : target.getTechs()) {
            TechnologyModel tech = Mapper.getTech(techID);
            if (tech == null || !tech.isUnitUpgrade()) continue;
            UnitModel unit = Mapper.getUnitModelByTechUpgrade(techID);
            if (unit == null || !unit.getIsShip() || "fighter".equalsIgnoreCase(unit.getBaseType())) continue;
            buttons.add(Buttons.gray(
                    player.finChecker() + "arvaxiEngineTech_" + targetFaction + "_" + techID,
                    tech.getName()));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no valid non-fighter ship unit upgrade technologies.");
            return;
        }
        buttons.add(Buttons.red(player.finChecker() + "deleteButtons", "Delete these buttons"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                "Choose which of " + target.getRepresentationNoPing()
                        + "'s unit upgrades to attach the Mobilization Engine to.",
                buttons);
    }

    @ButtonHandler("arvaxiEngineTech_")
    private void chooseSide(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String remainder = buttonID.replace("arvaxiEngineTech_", "");
        int sep = remainder.indexOf('_');
        String targetFaction = remainder.substring(0, sep);
        String techID = remainder.substring(sep + 1);
        ButtonHelper.deleteMessage(event);

        TechnologyModel tech = Mapper.getTech(techID);
        String techName = tech != null ? tech.getName() : techID;

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.finChecker() + "arvaxiEngineAttach_" + targetFaction + "_" + techID + "_boon",
                "Boon side"));
        buttons.add(Buttons.blue(
                player.finChecker() + "arvaxiEngineAttach_" + targetFaction + "_" + techID + "_curse",
                "Curse side"));
        buttons.add(Buttons.red(player.finChecker() + "deleteButtons", "Cancel"));

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                "Choose which side of the Mobilization Engine to attach to " + techName + ".", buttons);
    }

    @ButtonHandler("arvaxiEngineAttach_")
    private void attachEngine(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String remainder = buttonID.replace("arvaxiEngineAttach_", "");
        // format: <faction>_<techID>_<side> — side has no underscores, faction has no underscores
        int lastSep = remainder.lastIndexOf('_');
        String side = remainder.substring(lastSep + 1);
        String factionAndTech = remainder.substring(0, lastSep);
        int firstSep = factionAndTech.indexOf('_');
        String targetFaction = factionAndTech.substring(0, firstSep);
        String techID = factionAndTech.substring(firstSep + 1);

        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        TechnologyModel tech = Mapper.getTech(techID);
        String techName = tech != null ? tech.getName() : techID;
        String targetName = target != null ? target.getRepresentationNoPing() : targetFaction;

        game.setStoredValue(STORED_KEY, targetFaction + "_" + techID + "_" + side);
        ButtonHelper.deleteMessage(event);

        String targetRep = target != null ? target.getRepresentation() : targetName;
        String effectText = "boon".equals(side) ? BOON_TEXT : CURSE_TEXT;
        String msg = player.getRepresentationNoPing() + " attached the Arvaxi Mobilization Engine ("
                + side + " side) to " + targetRep + " " + techName + " using " + btRep() + "."
                + "\n> " + effectText;
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }
}

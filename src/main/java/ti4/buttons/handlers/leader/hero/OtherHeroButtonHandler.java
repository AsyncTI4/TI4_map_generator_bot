package ti4.buttons.handlers.leader.hero;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class OtherHeroButtonHandler {
    
    @ButtonHandler("purgeHacanHero")
    public static void purgeHacanHero(ButtonInteractionEvent event, Player player) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("hacanhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Harrugh Gefhara, the Hacan hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Harrugh Gefhara, the Hacan hero, was not purged - something went wrong.");
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeSardakkHero")
    public static void purgeSardakkHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("sardakkhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Sh'val, Harbinger, the N'orr hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Sh'val, Harbinger, the N'orr hero, was not purged - something went wrong.");
        }
        ButtonHelperHeroes.killShipsSardakkHero(player, game, event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " All ships have been removed, continue to land troops.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeRohdhnaHero")
    public static void purgeRohdhnaHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("rohdhnahero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Roh’Vhin Dhna mk4, the Roh'Dhna hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Roh’Vhin Dhna mk4, the Roh'Dhna hero, was not purged - something went wrong.");
        }
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game,
            game.getTileByPosition(game.getActiveSystem()), "rohdhnaBuild", "place");
        String message2 = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeVaylerianHero")
    public static void purgeVaylerianHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("vaylerianhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - Dyln Harthuul, the Vaylerian hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Dyln Harthuul, the Vaylerian hero, was not purged - something went wrong.");
        }
        if (!game.isNaaluAgent()) {
            player.setTacticalCC(player.getTacticalCC() - 1);
            CommandCounterHelper.addCC(event, player.getColor(),
                game.getTileByPosition(game.getActiveSystem()));
            game.setStoredValue("vaylerianHeroActive", "true");
        }
        for (Tile tile : ButtonHelperAgents.getGloryTokenTiles(game)) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event,
                "vaylerianhero");
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    "Use buttons to remove a token from the board", buttons);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " may gain 1 CC");
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 = player.getRepresentationUnfogged() + "! Your current CCs are " + player.getCCRepresentation()
            + ". Use buttons to gain CCs";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
        ButtonHelper.deleteTheOneButton(event);
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
    }

    @ButtonHandler("purgeKeleresAHero")
    public static void purgeKeleresAHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("keleresherokuuasi");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + " - Kuuasi Aun Jalatai, the Keleres (Argent) hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Kuuasi Aun Jalatai, the Keleres (Argent) hero, was not purged - something went wrong.");
        }
        AddUnitService.addUnits(event, game.getTileByPosition(game.getActiveSystem()), game, player.getColor(), "2 cruiser, 1 flagship");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationUnfogged() + " 2 cruisers and 1 flagship added.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeDihmohnHero")
    public static void purgeDihmohnHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("dihmohnhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + " - Verrisus Ypru, the Dih-Mohn hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Verrisus Ypru, the Dih-Mohn hero, was not purged - something went wrong.");
        }
        ButtonHelperHeroes.resolvDihmohnHero(game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationUnfogged()
            + " sustained everything. Reminder you do not take hits this round.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("purgeKortaliHero_")
    public static void purgeKortaliHero(ButtonInteractionEvent event, Player player, String buttonID, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("kortalihero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + " - Queen Nadalia, the Kortali hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Queen Nadalia, the Kortali hero, was not purged - something went wrong.");
        }
        ButtonHelperHeroes.offerStealRelicButtons(game, player, buttonID, event);
    }

    @ButtonHandler("glimmersHeroOn_")
    public static void glimmerHeroOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(), unit);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getFactionEmojiOrColor() + " chose to duplicate a " + unit + " in " + game.getTileByPosition(pos).getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("glimmersHeroIn_")
    public static void glimmersHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperHeroes.getUnitsToGlimmersHero(player, game, event, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentationUnfogged() + " select which unit you'd like to duplicate", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("ghotiHeroIn_")
    public static void ghotiHeroIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), player.getRepresentationUnfogged() + " select which unit you'd like to replace", buttons);
        ButtonHelper.deleteTheOneButton(event);
    }


}

package ti4.buttons.handlers.leader.hero;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

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

    @ButtonHandler("purgeAtokeraHero")
    public static void purgeAtokeraHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("atokerahero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - the Atokera hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                " the Atokera hero, was not purged - something went wrong.");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " Unfortunately at this time the addition of ships to the ground is not automated. /move units can place them on the planet however, and they will roll dice as normal once there.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("utilizePharadnHero_")
    public static void utilizePharadnHero(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "pharadnHeroDestroy_" + planet, "Destroy All Infantry"));
        for (int x = 1; x < player.getNomboxTile().getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player) + 1; x++) {
            buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "pharadnHeroCommit_" + planet + "_" + x, "Commit " + x + " Infantry"));
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            player.getFactionEmoji() + " you chose to use the Pharadn Hero on " + Mapper.getPlanet(planet).getAutoCompleteName() + ". Decide whether to destroy all infantry or commit infantry.", buttons);

        Leader playerLeader = player.unsafeGetLeader("pharadnhero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                message + " - the Pharadn hero has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "the Pharadn hero was not purged - something went wrong.");
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("pharadnHeroDestroy_")
    public static void pharadnHeroDestroy(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        Planet unitHolder = game.getUnitHolderFromPlanet(planet);
        Tile tile = game.getTileFromPlanet(planet);
        if (unitHolder == null || tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not locate planet");
            return;
        }

        for (Player p2 : game.getRealPlayers()) {
            if (tile.containsPlayersUnits(p2)) {
                int amountInf = unitHolder.getUnitCount(UnitType.Infantry, p2.getColor());
                UnitKey infKey = Units.getUnitKey(UnitType.Infantry, p2.getColorID());
                DestroyUnitService.destroyUnit(event, tile, game, infKey, amountInf, unitHolder, false);
            }
        }

        String msg = player.getFactionEmoji() + " you chose to use the Pharadn Hero on " + unitHolder.getRepresentation(game) + " to destroy all infantry.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pharadnHeroCommit_")
    public static void pharadnHeroCommit(Player player, Game game, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String amount = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " you chose to use the Pharadn Hero on " + Mapper.getPlanet(planet).getAutoCompleteName() + " to commit " + amount + " infantry.");
        ButtonHelper.deleteMessage(event);
        Tile tile = game.getTileFromPlanet(planet);
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " inf " + planet);
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), amount + " inf");
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
            CommandCounterHelper.addCC(event, player, game.getTileByPosition(game.getActiveSystem()));
            game.setStoredValue("vaylerianHeroActive", "true");
        }
        List<Tile> gloryTiles = ButtonHelperAgents.getGloryTokenTiles(game);
        for (int i = 0; i < gloryTiles.size(); i++) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "vaylerianhero");
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    "Use buttons to remove a command token from the game board.", buttons);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " may gain 1 command token.");
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 = player.getRepresentationUnfogged() + ", your current command tokens are " + player.getCCRepresentation()
            + ". Use buttons to gain 1 command token.";
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

    @ButtonHandler("purgeUydaiHero")
    public static void purgeUydaiHero(ButtonInteractionEvent event, Player player, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("uydaihero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + " - Uydai hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Uydai hero, was not purged - something went wrong.");
        }
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
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        String message2 = player.getRepresentationUnfogged() + ", your current command tokens are " + player.getCCRepresentation()
            + ". Use buttons to gain command tokens equal to your tech skip and legendart planets.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2, buttons);
    }

    @ButtonHandler("purgeOrlandoHero_")
    public static void purgeOrlandoHero(ButtonInteractionEvent event, Player player, String buttonID, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("orlandohero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + "  the Orlando hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                " the Orlando was not purged - something went wrong.");
        }
        String msg = player.getRepresentationNoPing() + " please select the unit that recently died with which you wish to resolve the orlando hero on.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, ButtonHelperActionCards.getCourageousOptions(player, game, true, "orlando"));
        ButtonHelper.deleteTheOneButton(event);

    }

    @ButtonHandler("purgeRedCreussHero_")
    public static void purgeRedCreussHero(ButtonInteractionEvent event, Player player, String buttonID, Game game) { // TODO: add service
        Leader playerLeader = player.unsafeGetLeader("redcreusshero");
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ")
            .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = player.removeLeader(playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                message + "  the Red Creuss hero, has been purged.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                " the Red Creuss was not purged - something went wrong.");
        }
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder captureUnitHolder = player.getNombox();
        UnitHolder spaceUnitHolder = tile.getSpaceUnitHolder();
        if (captureUnitHolder != null && spaceUnitHolder != null) {
            for (UnitKey key : captureUnitHolder.getUnitKeys()) {
                Player player_ = game.getPlayerFromColorOrFaction(key.getColor());
                if (player_ != player) {
                    continue;
                }
                int amt = captureUnitHolder.getUnitCount(key);
                var removed = captureUnitHolder.removeUnit(key, amt);
                spaceUnitHolder.addUnitsWithStates(key, removed);
            }
        }
        ButtonHelper.deleteTheOneButton(event);
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

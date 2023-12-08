package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.ShowAllPN;
import ti4.commands.cardsso.ShowAllSO;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ButtonHelperCommanders {

    public static void yinCommanderStep1(Player player, Game activeGame, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(activeGame, player, UnitType.Infantry)) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
                    buttons
                        .add(
                            Button.success("yinCommanderRemoval_" + tile.getPosition() + "_" + unitHolder.getName(), "Remove Inf from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, activeGame)));
                }
            }
        }
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true, true) + " use buttons to remove an infantry", buttons);
    }

    public static void resolveYinCommanderRemoval(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        String unitHName = buttonID.split("_")[2];
        Tile tile = activeGame.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get(unitHName);
        if ("space".equalsIgnoreCase(unitHName)) {
            unitHName = "";
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdent(player) + " removed 1 infantry from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, activeGame) + " using Yin Commander");
        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 infantry " + unitHName, activeGame);
        event.getMessage().delete().queue();
    }

    public static void mykoCommanderUsage(Player player, Game activeGame, ButtonInteractionEvent event) {
        String msg = ButtonHelper.getIdent(player) + " spent 1 ";
        if (player.getCommodities() > 0) {
            msg = msg + "commoditity (" + player.getCommodities() + "->" + (player.getCommodities() - 1) + ") ";
            player.setCommodities(player.getCommodities() - 1);
        } else {
            msg = msg + "tg (" + player.getTg() + "->" + (player.getTg() - 1) + ") ";
            player.setTg(player.getTg() - 1);
        }
        msg = msg + " to cancel one hit";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    public static void titansCommanderUsage(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        int cTG = player.getTg();
        int fTG = cTG + 1;
        player.setTg(fTG);
        ButtonHelperAbilities.pillageCheck(player, activeGame);
        ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        String msg = "Used Titans commander to gain a tg (" + cTG + "->" + fTG + "). ";
        player.addSpentThing(msg);
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, activeGame, "res");
        ButtonHelper.deleteTheOneButton(event);
        event.getMessage().editMessage(exhaustedMessage).queue();
    }

    public static void resolveLetnevCommanderCheck(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "letnevcommander")) {
            int old = player.getTg();
            int newTg = player.getTg() + 1;
            player.setTg(player.getTg() + 1);
            String mMessage = player.getRepresentation(true, true) + " Since you have Barony commander unlocked, 1tg has been added automatically (" + old
                + "->" + newTg + ")";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        }
    }

    public static void resolveGhostCommanderPlacement(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String pos = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(pos);
        new AddUnits().unitParsing(event, player.getColor(), tile, "fighter", activeGame);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdent(player) + " placed 1 fighter in " + tile.getRepresentation() + " using Ghost Commander");
    }

    public static List<Button> resolveFlorzenCommander(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            Planet planetReal = (Planet) ButtonHelper.getUnitHolderFromPlanetName(planet, activeGame);
            if (planetReal != null && planetReal.getOriginalPlanetType() != null && player.getPlanetsAllianceMode().contains(planet)) {
                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(activeGame, planetReal, player);
                buttons.addAll(planetButtons);
            }
        }
        return buttons;
    }

    public static void resolveMuaatCommanderCheck(Player player, Game activeGame, GenericInteractionCreateEvent event) {
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "muaatcommander")) {
            int old = player.getTg();
            int newTg = player.getTg() + 1;
            player.setTg(player.getTg() + 1);
            String mMessage = player.getRepresentation(true, true) + " Since you have Muaat commander unlocked, 1tg has been added automatically (" + old
                + "->" + newTg + ")";
            if (activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), mMessage);
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), mMessage);
            }
            ButtonHelperAbilities.pillageCheck(player, activeGame);
            ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
        }
    }

    public static void resolveNekroCommanderCheck(Player player, String tech, Game activeGame) {
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "nekrocommander")) {
            if ("".equals(Mapper.getTech(AliasHandler.resolveTech(tech)).getFaction().orElse("")) || !player.hasAbility("technological_singularity")) {
                List<Button> buttons = new ArrayList<>();
                if (player.hasAbility("scheming")) {
                    buttons.add(Button.success("draw_2_ACDelete", "Draw 2 AC (With Scheming)"));
                } else {
                    buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
                }
                buttons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true) + " You gained tech while having Nekro commander, use buttons to resolve. ", buttons);
            } else {
                if (player.hasAbility("technological_singularity")) {
                    int count = 0;
                    for (String nekroTech : player.getTechs()) {
                        if ("vax".equalsIgnoreCase(nekroTech) || "vay".equalsIgnoreCase(nekroTech)) {
                            continue;
                        }
                        if (!"".equals(Mapper.getTech(AliasHandler.resolveTech(nekroTech)).getFaction().orElse(""))) {
                            count = count + 1;
                        }

                    }
                    if (count > 2) {
                        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                            "# " + player.getRepresentation(true, true) + " heads up, that was your 3rd faction tech, you may wanna lose one with /tech remove");
                    }
                }
            }
        }
    }

    public static void resolveSolCommander(Player player, Game activeGame, String buttonID, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        new AddUnits().unitParsing(event, player.getColor(), tile, "1 inf " + planet, activeGame);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " placed 1 infantry on " + Helper.getPlanetRepresentation(planet, activeGame) + " using Sol Commander");
    }

    public static void yssarilCommander(String buttonID, ButtonInteractionEvent event, Game activeGame, Player player, String ident) {
        buttonID = buttonID.replace("yssarilcommander_", "");
        String enemyFaction = buttonID.split("_")[1];
        Player enemy = activeGame.getPlayerFromColorOrFaction(enemyFaction);
        if (enemy == null) return;
        String message = "";
        String type = buttonID.split("_")[0];
        if ("ac".equalsIgnoreCase(type)) {
            ShowAllAC.showAll(enemy, player, activeGame);
            message = "Yssaril commander used to look at ACs";
        }
        if ("so".equalsIgnoreCase(type)) {
            new ShowAllSO().showAll(enemy, player, activeGame);
            message = "Yssaril commander used to look at SOs";
        }
        if ("pn".equalsIgnoreCase(type)) {
            new ShowAllPN().showAll(enemy, player, activeGame, false);
            message = "Yssaril commander used to look at PNs";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(enemy.getPrivateChannel(), message);
        }
        event.getMessage().delete().queue();
    }

    public static void pay1tgToUnlockKeleres(Player player, Game activeGame, ButtonInteractionEvent event) {
        int oldTg = player.getTg();
        if (player.getTg() > 0) {
            player.setTg(oldTg - 1);
        }
        ButtonHelper.commanderUnlockCheck(player, activeGame, "keleres", event);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
            ButtonHelper.getIdentOrColor(player, activeGame) + " paid 1tg to unlock commander " + "(" + oldTg + "->" + player.getTg() + ")");
        event.getMessage().delete().queue();
    }

    public static void resolveSardakkCommander(Game activeGame, Player p1, String buttonID, ButtonInteractionEvent event, String ident) {
        String mechorInf = buttonID.split("_")[1];
        String planet1 = buttonID.split("_")[2];
        String planet2 = buttonID.split("_")[3];
        String planetRepresentation2 = Helper.getPlanetRepresentation(planet2, activeGame);
        String planetRepresentation = Helper.getPlanetRepresentation(planet1, activeGame);

        String message = ident + " moved 1 " + mechorInf + " from " + planetRepresentation2 + " to " + planetRepresentation + " using Sardakk Commander";
        new RemoveUnits().unitParsing(event, p1.getColor(),
            activeGame.getTileFromPlanet(planet2), "1 " + mechorInf + " " + planet2,
            activeGame);
        new AddUnits().unitParsing(event, p1.getColor(),
            activeGame.getTileFromPlanet(planet1), "1 " + mechorInf + " " + planet1,
            activeGame);

        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p1, activeGame), message);
        String exhaustedMessage = event.getMessage().getContentRaw();
        if ("".equalsIgnoreCase(exhaustedMessage)) {
            exhaustedMessage = "Updated";
        }
        List<ActionRow> actionRow2 = new ArrayList<>();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (buttonRow.size() > 0) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        if (actionRow2.size() > 0 && !exhaustedMessage.contains("select the user of the agent")) {
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
        } else {
            event.getMessage().delete().queue();
        }
    }

    public static List<Button> getSardakkCommanderButtons(Game activeGame, Player player, GenericInteractionCreateEvent event) {

        Tile tile = activeGame.getTileByPosition(activeGame.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder planetUnit : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                continue;
            }
            Planet planetReal = (Planet) planetUnit;
            String planetId = planetReal.getName();
            String planetRepresentation = Helper.getPlanetRepresentation(planetId, activeGame);
            for (String pos2 : FoWHelper.getAdjacentTiles(activeGame, tile.getPosition(), player, false)) {
                Tile tile2 = activeGame.getTileByPosition(pos2);
                if (AddCC.hasCC(event, player.getColor(), tile2)) {
                    continue;
                }
                for (UnitHolder planetUnit2 : tile2.getUnitHolders().values()) {
                    if ("space".equalsIgnoreCase(planetUnit2.getName())) {
                        continue;
                    }
                    Planet planetReal2 = (Planet) planetUnit2;
                    int numMechs = 0;
                    int numInf = 0;
                    String colorID = Mapper.getColorID(player.getColor());
                    if (planetUnit2.getUnits() != null) {
                        numMechs = planetUnit2.getUnitCount(UnitType.Mech, colorID);
                        numInf = planetUnit2.getUnitCount(UnitType.Infantry, colorID);
                    }
                    String planetId2 = planetReal2.getName();
                    String planetRepresentation2 = Helper.getPlanetRepresentation(planetId2, activeGame);
                    if (numInf > 0) {
                        buttons.add(Button.success("sardakkcommander_infantry_" + planetId + "_" + planetId2, "Commit 1 infantry from " + planetRepresentation2 + " to " + planetRepresentation)
                            .withEmoji(Emoji.fromFormatted(Emojis.Sardakk)));
                    }
                    if (numMechs > 0) {
                        buttons.add(Button.primary("sardakkcommander_mech_" + planetId + "_" + planetId2, "Commit 1 mech from " + planetRepresentation2 + " to " + planetRepresentation)
                            .withEmoji(Emoji.fromFormatted(Emojis.Sardakk)));
                    }
                }
            }
        }
        return buttons;
    }

}
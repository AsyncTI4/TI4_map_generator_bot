package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;

public class DSHelperBreakthroughs {
    // @ButtonHandler("componentActionRes_")
    public static void edynBTStep1(Game game, Player p1) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(p1)) {
            buttons.add(Buttons.blue(
                    p1.getFinsFactionCheckerPrefix() + "edynbtSelect_" + p2.getFaction(),
                    p2.getFactionNameOrColor(),
                    p2.getFactionEmojiOrColor()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCorrectChannel(), "Select a player to target with your breakthrough:", buttons);
    }

    public static void kolumeBTStep1(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("gain_CC_deleteThisMessage", "Gain 1 CC"));
        buttons.add(Buttons.gray("acquireATech_deleteThisMessage", "Research a Tech for 3i"));

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is resolving the kolume breakthrough to either spend 3i to research 1 tech as the same color of one of their exhausted techs or gain 1 command token");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " choose whether you want to either spend 3i to research 1 tech as the same color of one of their exhausted techs or gain 1 command token",
                buttons);
    }

    public static void bentorBTStep1(Game game, Player p1) {
        for (Player p2 : game.getRealPlayersExcludingThis(p1)) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("acceptBentorBT_" + p1.getFaction(), "Explore 1 Planet"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    p2.getCorrectChannel(),
                    p2.getRepresentationUnfogged() + ", " + p1.getFactionNameOrColor()
                            + " has activated their Bentor breakthrough. Do you wish to explore one of your planets?",
                    buttons);
        }
        MessageHelper.sendMessageToChannel(p1.getCorrectChannel(), "Sent buttons to every player to resolve.");
    }

    @ButtonHandler("acceptBentorBT")
    public static void acceptBentorBT(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                p2.getRepresentation() + " " + p1.getFactionNameOrColor()
                        + " has accepted your breakthrough offer to explore one of their planets and you have been given 1 commodity (if possible).");
        p2.gainCommodities(1);
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(p1, game);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose which planet you wish to explore.", buttons);
    }

    @ButtonHandler("useAxisBT")
    public static void useAxisBT(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        p1.setBreakthroughExhausted("axisbt", true);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation()
                        + " has used their Axis breakthrough to move any number of ships between two systems with their spacedocks.");
        ButtonHelper.deleteTheOneButton(event);
        String message = ", please choose the first system that you wish to swap a ship between (and transport).";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, p1, UnitType.Spacedock);
        for (Tile tile : tiles) {
            if (FoWHelper.otherPlayersHaveShipsInSystem(p1, tile, game)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    p1.getFinsFactionCheckerPrefix() + "axisBTStep2_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCorrectChannel(), p1.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("useFlorzenBT")
    public static void useFlorzenBT(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        p1.setBreakthroughExhausted("florzenbt", true);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation()
                        + " has used their Florzen breakthrough to choose another player and each simultaenously spend 0/1/2tgs. If they spend the same, Florzen gets a random PN.");
        ButtonHelper.deleteTheOneButton(event);
        String message = ", please choose the target player.";
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayersExcludingThis(p1)) {
            buttons.add(Buttons.gray(
                    p1.getFinsFactionCheckerPrefix() + "florzenBTStep2_" + p2.getFaction(),
                    p2.getFactionNameOrColor(),
                    p2.getFactionEmojiOrColor()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCorrectChannel(), p1.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("florzenBTStep2")
    public static void florzenBTStep2(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(), p1.getRepresentation() + " has targeted " + p2.getRepresentation());
        ButtonHelper.deleteMessage(event);
        String message = ", please choose the amount of tg you want to spend.";
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 3 && x <= p1.getTg(); x++) {
            buttons.add(Buttons.gray(
                    p1.getFinsFactionCheckerPrefix() + "florzenBTStep3_" + p2.getFaction() + "_" + x, x + " tg"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p1.getCardsInfoThread(), p1.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("florzenBTStep3")
    public static void florzenBTStep3(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String originalBid = buttonID.split("_")[2];
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation() + " has chosen and buttons have been sent to " + p2.getRepresentation()
                        + " for them to choose an amount.");
        ButtonHelper.deleteMessage(event);
        String message = ", please choose the amount of tg you want to spend.";
        List<Button> buttons = new ArrayList<>();
        for (int x = 0; x < 3 && x <= p2.getTg(); x++) {
            buttons.add(Buttons.gray(
                    p1.getFinsFactionCheckerPrefix() + "florzenBTStep4_" + p1.getFaction() + "_" + originalBid + "_"
                            + x,
                    x + " tg"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                p2.getCardsInfoThread(), p2.getRepresentationUnfogged() + message, buttons);
    }

    @ButtonHandler("florzenBTStep4")
    public static void florzenBTStep4(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String originalBid = buttonID.split("_")[2];
        String originalBid2 = buttonID.split("_")[3];
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation() + " has chosen to spend " + originalBid2 + " tg and " + p2.getRepresentation()
                        + " has chosen to spend " + originalBid + ". The tg have been subtracted");
        ButtonHelper.deleteMessage(event);
        if (StringUtils.isNumeric(originalBid2) && Integer.parseInt(originalBid2) > 0) {
            p1.setTg(p1.getTg() - Integer.parseInt(originalBid2));
        }
        if (StringUtils.isNumeric(originalBid) && Integer.parseInt(originalBid) > 0) {
            p2.setTg(p2.getTg() - Integer.parseInt(originalBid));
        }
        if (originalBid.equalsIgnoreCase(originalBid2)) {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(),
                    p1.getRepresentation() + " has sent a random PN to " + p2.getRepresentation());
            PromissoryNoteHelper.sendRandom(event, game, p1, p2);
        }
    }

    @ButtonHandler("useLanefirBt")
    public static void useLanefirBt(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        p1.setBreakthroughExhausted("lanefirbt", true);
        MessageHelper.sendMessageToChannel(
                p1.getCorrectChannel(),
                p1.getRepresentation() + " has used their Lanefir breakthrough to explore 1 planet they control.");
        ButtonHelper.deleteTheOneButton(event);
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(p1, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose which planet you wish to explore.", buttons);
    }

    public static void doLanefirBtCheck(Game game, Player player) {
        for (Player p2 : game.getRealPlayersExcludingThis(player)) {
            if (p2.hasUnlockedBreakthrough("lanefirbt")) {
                List<Button> buttons = new ArrayList<>();
                if (p2.isBreakthroughExhausted("lanefirbt")) {
                    buttons.add(Buttons.green("readyLanefirBt", "Ready Lanefir Breakthrough"));
                }
                buttons.add(Buttons.green("gain_CC_deleteThisMessage", "Gain 1 CC"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentation()
                                + " is resolving the lanefir breakthrough to either ready the breakthrough or gain 1 command token after another player has purged a non-action card component.");
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentation()
                                + " choose whether you want to either ready the breakthrough or gain 1 command token",
                        buttons);
            }
        }
    }

    @ButtonHandler("axisBTStep2_")
    public static void axisBTStep2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        String message =
                ", please choose the second system that you wish to swap a ship between (and transport). The first system is position "
                        + pos + ".";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock);
        for (Tile tile : tiles) {
            if (tile.getPosition().equalsIgnoreCase(pos)) {
                continue;
            }
            if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.getFinsFactionCheckerPrefix() + "redcreussAgentPart2_" + pos + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("edynbtSelect_")
    public static void edynbtSelect(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p1.getSecretsUnscored().size() > 0) {
            for (String soID : p1.getSecretsUnscored().keySet()) {
                buttons.add(Buttons.blue(
                        p1.getFinsFactionCheckerPrefix() + "edynbtTarget_" + p2.getFaction() + "_" + soID,
                        Mapper.getSecretObjective(soID).getName()));
            }
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, "Choose a secret to show.", buttons);
            ButtonHelper.deleteMessage(event);
        } else {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(), "Sent buttons to " + p2.getFactionNameOrColor() + "'s channel to resolve.");
            edynbtTarget(game, p1, event, "edynbtTarget_" + p2.getFaction() + "_none");
        }
    }

    @ButtonHandler("edynbtTarget_")
    public static void edynbtTarget(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        List<Button> buttons = new ArrayList<>();
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        SecretObjectiveModel so = Mapper.getSecretObjective(buttonID.split("_")[2]);
        if (so != null) {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(),
                    "You have shown " + so.getName() + " to " + p2.getFactionNameOrColor() + ".");
            MessageHelper.sendMessageToChannelWithEmbed(
                    p2.getCardsInfoThread(),
                    p1.getFactionNameOrColor() + " has shown you the secret objective: " + so.getName() + ".",
                    so.getRepresentationEmbed());
        }
        if (p2.getSecretsUnscored().size() > 0) {
            buttons.add(Buttons.green(
                    "edynbtFinal_showSecret_" + p1.getFaction(),
                    "Show Random Secret Objective to " + p2.getFactionNameOrColor()));
        }
        buttons.add(Buttons.blue(
                "edynbtFinal_noShowSecret_" + p1.getFaction(), "Allow Coexistence to " + p2.getFactionNameOrColor()));
        MessageHelper.sendMessageToChannel(
                p2.getCorrectChannel(),
                "Choose whether to show a secret objective to " + p2.getFactionNameOrColor() + " or allow coexistence.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("edynbtFinal_")
    public static void edynbtFinal(Game game, Player p1, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        String action = buttonID.split("_")[1];
        if (action.equals("showSecret")) {
            List<String> unscoredSOs = new ArrayList<>(p1.getSecretsUnscored().keySet());
            Collections.shuffle(unscoredSOs);
            String randomSOID = unscoredSOs.get(0);
            SecretObjectiveModel so = Mapper.getSecretObjective(randomSOID);
            if (so != null) {
                MessageHelper.sendMessageToChannelWithEmbed(
                        p2.getCardsInfoThread(),
                        p2.getRepresentation() + " " + p1.getFactionNameOrColor()
                                + " has shown you the secret objective: " + so.getName() + ".",
                        so.getRepresentationEmbed());
                MessageHelper.sendMessageToChannel(
                        p1.getCorrectChannel(), p2.getFactionNameOrColor() + " was shown a random secret objective..");
            } else {
                MessageHelper.sendMessageToChannel(
                        p1.getCorrectChannel(), p1.getFactionNameOrColor() + " has no unscored secret objectives.");
            }
        } else {
            MessageHelper.sendMessageToChannel(
                    p1.getCorrectChannel(),
                    p2.getRepresentation() + " " + p1.getFactionNameOrColor()
                            + " has chosen to allow you to coexist on one of their planets.");
            List<Button> buttons = new ArrayList<>();
            Player target = p1;
            Player player = p2;
            for (String planet : target.getPlanetsAllianceMode()) {
                if (game.getUnitHolderFromPlanet(planet) != null
                        && game.getUnitHolderFromPlanet(planet).hasGroundForces(target)
                        && !ButtonHelper.getPlanetExplorationButtons(
                                        game, (Planet) game.getUnitHolderFromPlanet(planet), player, false, true)
                                .isEmpty()) {
                    buttons.add(Buttons.gray(
                            player.getFinsFactionCheckerPrefix() + "exchangeProgramPart3_" + planet,
                            Helper.getPlanetRepresentation(planet, game)));
                }
            }
            buttons.add(Buttons.red("deleteButtons", "Cancel"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", please choose a planet to coexist on with "
                            + target.getFactionNameOrColor() + ".",
                    buttons);
        }
        ButtonHelper.deleteMessage(event);
    }
}

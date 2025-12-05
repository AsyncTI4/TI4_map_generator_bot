package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
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

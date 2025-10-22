package ti4.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.RelicModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TechEmojis;

@UtilityClass
public class EdictPhaseHandler {

    @ButtonHandler("edictPhase")
    public static void edictPhase(ButtonInteractionEvent event, Game game) {
        game.setPhaseOfGame("agenda");
        List<String> edicts = Mapper.getShuffledDeck("agendas_twilights_fall");
        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();
        Player tyrant = game.getTyrant();
        for (int x = 0; x < 3; x++) {
            AgendaModel edict = Mapper.getAgenda(edicts.get(x));
            buttons.add(Buttons.green(
                    tyrant.getFinsFactionCheckerPrefix() + "resolveEdict_" + edicts.get(x), edict.getName()));
            embeds.add(edict.getRepresentationEmbed());
        }
        String msg = tyrant.getRepresentation()
                + " as Tyrant, you should now choose which of the 3 edicts you wish to resolve.";
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(tyrant.getCorrectChannel(), msg, embeds, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("blessBoonTg")
    public static void blessBoonTg(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        MessageHelper.sendMessageToChannel(event.getChannel(), player.getRepresentation() + " gained 3 tg.");
        player.setTg(player.getTg() + 3);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, 3);
    }

    @ButtonHandler("artificeStep2")
    public static void artificeStep2(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        int relics = Integer.parseInt(buttonID.split("_")[1]);
        int paradigms = Integer.parseInt(buttonID.split("_")[2]);
        if (relics > 0) {
            RelicHelper.drawWithAdvantage(player, game, 1 + relics);
        } else {
            RelicHelper.drawRelicAndNotify(player, event, game);
        }
        if (paradigms > 0) {
            List<Button> buttons = new ArrayList<>();
            for (int x = 0; x < paradigms; x++) {
                ButtonHelperTwilightsFall.drawParadigm(game, player, event, false);
            }
            for (String paradigm : game.getStoredValue("artificeParadigms").split("_")) {
                buttons.add(Buttons.green(
                        "keepArtificeParadigm_" + paradigm,
                        "Keep " + Mapper.getLeader(paradigm).getName()));
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " choose the paradigm you would like to keep.",
                    buttons);
        } else {
            game.removeStoredValue("artificeParadigms");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("keepArtificeParadigm")
    public static void keepArtificeParadigm(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String paradigm = buttonID.split("_")[1];
        for (String paradigmToLose : game.getStoredValue("artificeParadigms").split("_")) {
            if (!paradigmToLose.equalsIgnoreCase(paradigm)) {
                player.removeLeader(paradigmToLose);
                game.setStoredValue(
                        "savedParadigms",
                        game.getStoredValue("savedParadigms")
                                .replace(paradigmToLose, "")
                                .replace("__", "_"));
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " kept the "
                        + Mapper.getLeader(paradigm).getName() + " paradigm.");
        game.removeStoredValue("artificeParadigms");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("conveneStep1_")
    public static void conveneStep1_(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String cardID = buttonID.split("_")[1];
        if (player == game.getSpeaker()) {
            Player tyrant = game.getTyrant();
            tyrant.addTech(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    tyrant.getRepresentation() + " has acquired the ability: "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
        } else {
            List<Button> buttons = new ArrayList<>();
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player || game.getStoredValue("convenePlayers").contains(p2.getFaction())) {
                    continue;
                }
                buttons.add(
                        Buttons.green("conveneStep2_" + cardID + "_" + p2.getFaction(), p2.getFactionNameOrColor()));
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " choose the player you would like to give "
                            + Mapper.getTech(cardID).getName() + " to",
                    buttons);
        }

        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("conveneStep2_")
    public static void conveneStep2(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String cardID = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        game.setStoredValue("convenePlayers", game.getStoredValue("convenePlayers") + p2.getFaction());
        p2.addTech(cardID);
        MessageHelper.sendMessageToChannelWithEmbed(
                game.getActionsChannel(),
                p2.getRepresentation() + " has acquired the ability: "
                        + Mapper.getTech(cardID).getName(),
                Mapper.getTech(cardID).getRepresentationEmbed());

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("electCensure")
    public static void electCensure(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        game.discardSpecificAgenda("tf-censure");
        game.addLaw("tf-censure", p2.getFaction());
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), p2.getRepresentation() + " has been censured.");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveEdict_")
    public static void resolveEdict(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String edict = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();
        game.removeStoredValue("convenePlayers");
        AgendaModel edictE = Mapper.getAgenda(edict);
        embeds.add(edictE.getRepresentationEmbed());
        String msg = player.getRepresentation() + " use buttons to resolve the edict.";
        switch (edict) {
            case "tf-bless" -> {
                buttons.add(Buttons.green("blessBoon_tg", "Gain 3 TG"));
                buttons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
                buttons.add(Buttons.blue("redistributeCCButtons", "Gain 1 Command Token"));
                msg += " " + game.getPing() + " other players get to resolve 1 of the 3 boons.";
            }
            case "tf-splice" -> {
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + "startSplice_7_all", "Initiate Ability Splice"));
                buttons.add(Buttons.gray(
                        player.getFinsFactionCheckerPrefix() + "startSplice_2_all", "Initiate Genome Splice"));
                buttons.add(Buttons.blue(
                        player.getFinsFactionCheckerPrefix() + "startSplice_6_all", "Initiate Unit Upgrade Splice"));
            }
            case "tf-arise" -> {
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + "riseOfAMessiah", "1 infantry on every planet"));
                buttons.add(Buttons.green(
                        player.getFinsFactionCheckerPrefix() + "fighterConscription", "1 fighter with every ship"));
            }
            case "tf-arbitrate" -> {
                buttons.add(Buttons.red("discardSpliceCard_ability", "Discard 1 Ability"));
                buttons.add(Buttons.red("discardSpliceCard_units", "Discard 1 Unit Upgrade"));
                buttons.add(Buttons.red("discardSpliceCard_genome", "Discard 1 Genome"));
                buttons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
                buttons.add(Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade"));
                buttons.add(Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome"));
            }
            case "tf-legacy_of_ixth" -> {
                Die d1 = new Die(6);
                msg += "\n\n# Rolled a " + d1.getResult() + " for legacy of ixth!";
                if (d1.isSuccess()) {
                    msg += TechEmojis.Propulsion3 + " " + TechEmojis.Biotic3 + " " + TechEmojis.Cybernetic3 + " "
                            + TechEmojis.Warfare3;
                    buttons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
                    buttons.add(Buttons.green("drawSingularNewSpliceCard_units", "Draw 1 Unit Upgrade"));
                    buttons.add(Buttons.green("drawSingularNewSpliceCard_genome", "Draw 1 Genome"));
                } else {
                    msg += "💥 💥 💥 💥";
                    buttons.add(Buttons.gray("getModifyTiles", "Modify Units"));
                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
            }
            case "tf-artifice" -> {
                int vpDifference = Math.max(game.getHighestScore() - player.getTotalVictoryPoints(), 0);
                if (vpDifference < 1) {
                    RelicHelper.drawRelicAndNotify(player, event, game);
                    ButtonHelperTwilightsFall.drawParadigm(game, player, event, false);
                    game.removeStoredValue("artificeParadigms");
                } else {
                    ButtonHelperTwilightsFall.drawParadigm(game, player, event, false);
                    String relic = game.getAllRelics().get(0);
                    RelicModel mod = Mapper.getRelic(relic);
                    MessageHelper.sendMessageToChannelWithEmbed(
                            player.getCorrectChannel(),
                            "You would draw the following relic:",
                            mod.getRepresentationEmbed());
                    for (int x = 0; x < vpDifference + 1; x++) {
                        buttons.add(Buttons.green(
                                player.getFinsFactionCheckerPrefix() + "artificeStep2_" + (x) + "_"
                                        + (vpDifference - x),
                                "Draw " + x + " Extra Relics and " + (vpDifference - x) + "Extra Paradigms"));
                    }
                }
            }
            case "tf-execute" -> {
                buttons.add(
                        Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePlagueStep12", "Resolve Execute"));
                buttons.add(
                        Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePlagueStep11", "Resolve Execute"));
                buttons.add(
                        Buttons.green(player.getFinsFactionCheckerPrefix() + "resolvePlagueStep13", "Resolve Execute"));
            }
            case "tf-convene" -> {
                List<String> techs = ButtonHelperTwilightsFall.getDeckForSplicing(
                        game, "ability", game.getRealPlayers().size());

                for (String tech : techs) {
                    buttons.add(Buttons.green(
                            "conveneStep1_" + tech,
                            "Assign " + Mapper.getTech(tech).getName()));
                    embeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                }
                msg += "\n\n" + game.getSpeaker().getRepresentation()
                        + " needs to assign the first ability to the tyrant, then the tyrant does the rest";
            }
            case "tf-foretell" -> {
                int loc = 1;
                for (String objective1 : game.getPublicObjectives1Peakable()) {
                    buttons.add(Buttons.green("foretellPeak_1_" + loc, "Stage 1, Position " + loc));
                    loc++;
                }
                loc = 1;
                for (String objective1 : game.getPublicObjectives2Peakable()) {
                    buttons.add(Buttons.blue("foretellPeak_2_" + loc, "Stage 2, Position " + loc));
                    loc++;
                }

                buttons.add(Buttons.red("deleteButtons", "Done Peeking"));
            }
            case "tf-censure" -> {
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    buttons.add(Buttons.green("electCensure_" + p2.getFaction(), p2.getFactionNameOrColor()));
                }
                msg += "\n\nChoose the player to censure.";
            }
        }

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCorrectChannel(), msg, embeds, buttons);

        Player yellowFSPlayer = game.getPlayerFromColorOrFaction("orangetf");
        if (yellowFSPlayer != null
                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, yellowFSPlayer, "flagship", false) < 1) {
            yellowFSPlayer = null;
        }
        if (!buttonID.contains("orangetf") && yellowFSPlayer != null) {
            String msg2 = yellowFSPlayer.getRepresentation()
                    + " after resolving the edict, use this button to resolve an additional edict from your flagship.";
            List<String> edicts = Mapper.getShuffledDeck("agendas_twilights_fall");
            if (ButtonHelper.isLawInPlay(game, "tf-censure")) {
                edicts.removeIf(edict2 -> edict2.equalsIgnoreCase("tf-censure"));
            }
            Button proceedToStrategyPhase = Buttons.green(
                    yellowFSPlayer.getFinsFactionCheckerPrefix() + "resolveEdict_" + edicts.get(0) + "_orangetf",
                    "Resolve 1 Edict");
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg2, proceedToStrategyPhase);
        } else {
            String msg2 = player.getRepresentation()
                    + " after resolving the edict, use this button to proceed to the strategy phase.";
            Button proceedToStrategyPhase = Buttons.green(
                    "proceed_to_strategy",
                    "Proceed to Strategy Phase (will refresh all cards and ping the priority player)");
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg2, proceedToStrategyPhase);
        }
        ButtonHelper.deleteMessage(event);
    }
}

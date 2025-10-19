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
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.service.emoji.CardEmojis;

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

    @ButtonHandler("resolveEdict_")
    public static void resolveEdict(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String edict = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        List<MessageEmbed> embeds = new ArrayList<>();
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
                // offer buttons to discard any type and draw singular cards from decks
            }
            case "tf-legacy_of_ixth" -> {
                // roll die, offer buttons to either remove or draw singular cards from decks
            }
            case "tf-artifice" -> {
                // bentor heroesq for relics, do similar for paradigms
            }
            case "tf-execute" -> {
                // 3 plague esqe
            }
            case "tf-convene" -> {
                // have 2 step buttons -- click ability, assign ability
            }
            case "tf-foretell" -> {
                // buttons to peek at objectives
            }
            case "tf-censure" -> {
                // button to elect a player
                // code to remove this from deck if law is in play
                // code to not allow transactions
                // code to remove law in status phase
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

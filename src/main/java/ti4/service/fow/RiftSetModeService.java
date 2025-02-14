package ti4.service.fow;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.CardEmojis;

/*
 * For Eronous to run fow300
 * 
 * Specifications:
 * 
 * Eronous is Cabal with color Riftset
 * When any unit fails rift throw, Eronous eats it
 * One additional Custom Strategy Card, Sacrifice
 * One additional agenda, Crucible Reallocation, removed from normal agenda draw but can be flipped with a button in every agenda phase
 * 
 * TODO
 * /special swap_systems to support RANDOM options
 * A way to see what _own_ units Cabal has captured
 
 * These are in effect only after Custodians is taken:
 * Exploring a planet has 1/100 chance of Stellar Converting it
 * Custom frontier token explore which pings Eronous to do Cabal attack and recycles itself back to the deck
 * After you activate a tile it has a 1/10 chance of having a gravity rift placed in it if it doesn’t already have one.
 * After you activate a tile it has a 1/25 chance of placing Vortex token. These are adjacent to each other and you can go through them like wormholes
 * Change frontier token image to a special one
  */
public class RiftSetModeService {
    private static final String CRUCIBLE_PN = "crucible";
    private static final String CRUCIBLE_AGENDA = "riftset_crucible";

    public static boolean activate(GenericInteractionCreateEvent event, Game game) {
        if (game.getPlayer(Constants.eronousId) == null && AsyncTI4DiscordBot.guildFogOfWar != null) {
            MessageHelper.replyToMessage(event, "Can only use RiftSetMode if Eronous is in the game.");
            return false;
        }

        if (!game.isFowMode()) {
            MessageHelper.replyToMessage(event, "Can only use RiftSetMode in FoW");
            return false;
        }

        if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_riftset"))) return false;
        if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_riftset"))) return false;
        game.discardSpecificAgenda(CRUCIBLE_AGENDA);
        game.setScSetID("riftset");
        game.addSC(9);
        game.addTag("RiftSet");
        game.setFowOption(Constants.RIFTSET_MODE, "true");
        return true;
    }

    public static boolean isActive(Game game) {
        return Boolean.valueOf(game.getFowOption(Constants.RIFTSET_MODE));
    }

    public static Player getCabalPlayer(Game game) {
        return isActive(game) ? game.getPlayerFromColorOrFaction("cabal") : null;
    }

    public static void includeCrucibleAgendaButton(List<Button> buttons, Game game) {
        if (!isActive(game)) return;

        buttons.add(Buttons.blue("flip_" + CRUCIBLE_AGENDA, "Flip Crucible Reallocation"));
    }

    @ButtonHandler("flip_" + CRUCIBLE_AGENDA)
    public static void flipRiftSetCrucible(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(CRUCIBLE_AGENDA);
        if (uniqueID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda `" + CRUCIBLE_AGENDA + "` not found.");
            return;
        }
        game.putAgendaBackIntoDeckOnTop(uniqueID);
        AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
    }

    public static void resolveRiftSetCrucible(String agendaID, Player winner, Game game) {
        if (!isActive(game) || !CRUCIBLE_AGENDA.equalsIgnoreCase(agendaID)) return;

        for (Player p : game.getRealPlayers()) {
            if (p.hasPlayablePromissoryInHand(CRUCIBLE_PN)) {
                p.removePromissoryNote(CRUCIBLE_PN);
                PromissoryNoteHelper.sendPromissoryNoteInfo(game, p, false);
                break;
            }
        }

        Player cabal = getCabalPlayer(game);
        cabal.removePromissoryNote(CRUCIBLE_PN);

        winner.setPromissoryNote(CRUCIBLE_PN);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, winner, false);
        
        PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(CRUCIBLE_PN);
        MessageHelper.sendMessageToChannel(winner.getPrivateChannel(), winner.getRepresentation(true, true) + ", you recieved " + CardEmojis.PN + pnModel.getName());
    }
}

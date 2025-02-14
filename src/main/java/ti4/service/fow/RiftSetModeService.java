package ti4.service.fow;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.StellarConverterService;
import ti4.service.emoji.CardEmojis;

/*
 * For Eronous to run fow300
 * 
 * HOW TO RUN
 * 1. Have Eronous in a FoW game
 * 2. Run /game weird_game_setup riftset_mode: true
 * 3. Setup any player as Cabal
 * 
 * SPECS:
 * - When any unit fails rift throw, Cabal eats it
 * - One additional Custom Strategy Card, 9. Sacrifice
 * - One additional agenda, Crucible Reallocation
 *   - Removed from the deck at setup. Can be flipped with a button in every agenda phase.
 * - Custom frontier explore Unstable Rifts (tells player to ping GM to resolve)
 *   - Recycles itself back to the deck instantly
 * - /special swap_systems to support RANDOM options
 * AFTER CUSTODIANS IS SCORED:
 * - When concluding tactical action, tile has a 1/10 chance of having a gravity rift placed in it
 * - Exploring a planet has 1/100 chance of Stellar Converting it
 * 
 * TODO
  * A way to see what _own_ units Cabal has captured
  * After you activate a tile it has a 1/25 chance of placing Vortex token. These are adjacent to each other and you can go through them like wormholes
  * Change frontier token image to a special one after custodians is taken
  */
public class RiftSetModeService {
    private static final String CRUCIBLE_PN = "crucible";
    private static final String CRUCIBLE_AGENDA = "riftset_crucible";
    private static final String RIFTSET_INVASION_EXPLORE = "riftset_invasion";

    private static final int CHANCE_TO_SPAWN_RIFT = 10;
    //private static final int CHANCE_TO_SPAWN_VORTEX = 25;
    private static final int CHANCE_TO_STELLAR_CONVERT = 101; //- Math.pow(roundNmbr, 2);

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
        MessageHelper.sendMessageToChannel(winner.getCorrectChannel(), winner.getRepresentation(true, true) + ", you recieved " + CardEmojis.PN + pnModel.getName());
    }

    public static void resolveExplore(String exploreCardId, Player player, Game game) {
        if (!isActive(game)) return;

        if (RIFTSET_INVASION_EXPLORE.equals(exploreCardId)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), 
                "**GM ping:** " + game.getPlayersWithGMRole().stream().map(p -> p.getPing()).collect(Collectors.joining(", ")) + " Unstable Rifts Event waiting for resolving!");
            game.addExplore(RIFTSET_INVASION_EXPLORE);
        }
    }

    public static void concludeTacticalAction(Player player, Game game, GenericInteractionCreateEvent event) {
        if (!isActive(game) || !game.isCustodiansScored()) return;

        Tile tile = game.getTileByPosition(game.getActiveSystem());

        if (new Random().nextInt(CHANCE_TO_SPAWN_RIFT) == 0
            && !tile.getTileModel().isGravityRift()
            && !tile.hasCabalSpaceDockOrGravRiftToken()) {
            AddTokenCommand.addToken(event, tile, "gravityrift", game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## A new Gravity Rift has formed in " + tile.getPosition());
        }
    }

    public static boolean willPlanetGetStellarConverted(String planetName, Player player, Game game, GenericInteractionCreateEvent event) {
        if (!isActive(game) || !game.isCustodiansScored()) return false;

        if (new Random().nextInt(CHANCE_TO_STELLAR_CONVERT - (int)Math.pow(game.getRound(), 2)) == 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## While trying to explore the planet, you find something dark and dangerous...");
            StellarConverterService.secondHalfOfStellar(game, planetName, event);
            return true;
        }

        return false;
    }
}
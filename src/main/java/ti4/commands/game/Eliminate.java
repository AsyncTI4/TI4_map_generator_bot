package ti4.commands.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.RemoveCC;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.GameCreationHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class Eliminate extends AddRemovePlayer {

    private StringBuilder sb = new StringBuilder();

    public Eliminate() {
        super(Constants.ELIMINATE, "Eliminate player from game");
    }

    protected String getResponseMessage(Game game, User user) {
        return sb.toString();
    }

    @Override
    protected void action(SlashCommandInteractionEvent event, Game game, User user) {
        sb = new StringBuilder();
        removeUser(event, game, Constants.PLAYER1);
        removeUser(event, game, Constants.PLAYER2);
        removeUser(event, game, Constants.PLAYER3);
        removeUser(event, game, Constants.PLAYER4);
        removeUser(event, game, Constants.PLAYER5);
        removeUser(event, game, Constants.PLAYER6);
        removeUser(event, game, Constants.PLAYER7);
        removeUser(event, game, Constants.PLAYER8);
    }

    private void removeUser(SlashCommandInteractionEvent event, Game game, String playerID) {
        OptionMapping option;
        option = event.getOption(playerID);

        // OptionMapping option2 = event.getOption(Constants.CONFIRM);
        // if(!option2.getAsString().equalsIgnoreCase("yes"))
        // {
        //     MessageHelper.sendMessageToChannel(event.getChannel(), "Please confirm with yes");
        //     return;
        // }
        if (option != null) {

            // OptionMapping removeOption = event.getOption(Constants.FACTION_COLOR);

            // if (removeOption == null) {
            //     MessageHelper.replyToMessage(event, "Specify player to remove and replacement");
            //     return;
            // }

            // Player player = CommandHelper.getPlayerFromEvent(game, event);
            User extraUser = option.getAsUser();
            Player player = game.getPlayer(extraUser.getId());
            Map<String, PromissoryNoteModel> promissoryNotes = Mapper.getPromissoryNotes();
            if (player != null && player.getColor() != null && player.getFaction() != null && !"null".equalsIgnoreCase(player.getFaction()) && player.isRealPlayer() && !"".equalsIgnoreCase(player.getFaction())) {
                if (!player.getPlanetsAllianceMode().isEmpty()) {
                    Role bothelperRole = GameCreationHelper.getRole("Bothelper", event.getGuild());
                    String msg = "This person doesn't meet the elimination conditions. If you want to replace a player, run /game replace.";
                    if (bothelperRole != null) {
                        msg = msg + " Pinging bothelper for assistance: " + bothelperRole.getAsMention();
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    return;
                }
                //send back all the PNs of others that the player was holding
                Set<String> pns = new HashSet<>(player.getPromissoryNotes().keySet());
                for (String pnID : pns) {

                    PromissoryNoteModel pn = promissoryNotes.get(pnID);
                    if (pn != null && !pn.getOwner().equalsIgnoreCase(player.getColor()) && !pn.getOwner().equalsIgnoreCase(player.getFaction())) {
                        Player p2 = game.getPlayerFromColorOrFaction(pn.getOwner());
                        player.removePromissoryNote(pnID);
                        p2.setPromissoryNote(pnID);
                        PNInfo.sendPromissoryNoteInfo(game, p2, false);
                    }
                }

                //Purge all the PNs of the eliminated player that other players were holding
                for (Player p2 : game.getPlayers().values()) {
                    pns = new HashSet<>(p2.getPromissoryNotes().keySet());
                    for (String pnID : pns) {
                        PromissoryNoteModel pn = promissoryNotes.get(pnID);
                        if (pn != null && (pn.getOwner().equalsIgnoreCase(player.getColor()) || pn.getOwner().equalsIgnoreCase(player.getFaction()))) {
                            p2.removePromissoryNote(pnID);
                            PNInfo.sendPromissoryNoteInfo(game, p2, false);
                        }
                    }
                }
                //Remove all of the players units and ccs from the board
                for (Tile tile : game.getTileMap().values()) {
                    tile.removeAllUnits(player.getColor());
                    if (!"null".equalsIgnoreCase(player.getColor()) && AddCC.hasCC(event, player.getColor(), tile)) {
                        RemoveCC.removeCC(event, player.getColor(), tile, game);
                    }
                }
                //discard all of a players ACs
                Map<String, Integer> acs = new LinkedHashMap<>(player.getActionCards());
                for (Map.Entry<String, Integer> ac : acs.entrySet()) {
                    game.discardActionCard(player.getUserID(), ac.getValue());
                    String sb = "Player: " + player.getUserName() + " - " + "Discarded Action Card:" + "\n" + Mapper.getActionCard(ac.getKey()).getRepresentation() + "\n";
                    MessageHelper.sendMessageToChannel(event.getChannel(), sb);
                }
                PlayAC.serveReverseEngineerButtons(game, player, new ArrayList<>(acs.keySet()));

                //unscore all of a players SOs
                acs = new LinkedHashMap<>(player.getSecretsScored());
                for (int so : acs.values()) {
                    game.unscoreSecretObjective(player.getUserID(), so);
                }
                //discard all of a players SOs

                acs = new LinkedHashMap<>(player.getSecrets());
                for (int so : acs.values()) {
                    game.discardSecretObjective(player.getUserID(), so);
                }
                //return SCs
                Set<Integer> scs = new HashSet<>(player.getSCs());
                for (int sc : scs) {
                    player.removeSC(sc);
                }
                player.setEliminated(true);
                player.setDummy(true);
                if (!game.isFowMode()) {
                    Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), game);
                }
            } else {
                game.removePlayer(player.getUserID());
            }

            Guild guild = event.getGuild();
            Member removedMember = guild.getMemberById(player.getUserID());
            List<Role> roles = guild.getRolesByName(game.getName(), true);
            if (removedMember != null && roles.size() == 1) {
                guild.removeRoleFromMember(removedMember, roles.getFirst()).queue();
            }
            sb.append("Eliminated player: ").append(player.getUserName()).append(" from game: ").append(game.getName()).append("\n");
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        }
    }
}

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
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.commands.commandcounter.RemoveCommandCounterService;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

class EliminatePlayer extends GameStateSubcommand {

    public EliminatePlayer() {
        super(Constants.ELIMINATE, "Eliminate player from game", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player @playerName"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        StringBuilder stringBuilder = new StringBuilder();
        removeUser(event, game, Constants.PLAYER1, stringBuilder);
        removeUser(event, game, Constants.PLAYER2, stringBuilder);
        removeUser(event, game, Constants.PLAYER3, stringBuilder);
        removeUser(event, game, Constants.PLAYER4, stringBuilder);
        removeUser(event, game, Constants.PLAYER5, stringBuilder);
        removeUser(event, game, Constants.PLAYER6, stringBuilder);
        removeUser(event, game, Constants.PLAYER7, stringBuilder);
        removeUser(event, game, Constants.PLAYER8, stringBuilder);

        Helper.fixGameChannelPermissions(event.getGuild(), game);

        MessageHelper.replyToMessage(event, stringBuilder.toString());
    }

    private void removeUser(SlashCommandInteractionEvent event, Game game, String playerId, StringBuilder stringBuilder) {
        OptionMapping option = event.getOption(playerId);
        if (option == null) {
            return;
        }
        User extraUser = option.getAsUser();
        Player player = game.getPlayer(extraUser.getId());
        Map<String, PromissoryNoteModel> promissoryNotes = Mapper.getPromissoryNotes();
        if (player == null) return;
        if (player.getColor() == null || player.getFaction() == null || "null".equalsIgnoreCase(player.getFaction()) ||
            !player.isRealPlayer() || "".equalsIgnoreCase(player.getFaction())) {
            game.removePlayer(player.getUserID());
        } else {
            if (!player.getPlanetsAllianceMode().isEmpty()) {
                String msg = "This player doesn't meet the elimination conditions. If you wish to replace a player, run `/game replace`. Ping a bothelper for assistance if you need it.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                return;
            }
            if (game.getSpeakerUserID().equalsIgnoreCase(player.getUserID())) {
                boolean foundSpeaker = false;
                for (Player p4 : Helper.getSpeakerOrderFromThisPlayer(player,game)) {
                    if (foundSpeaker) {
                        game.setSpeakerUserID(p4.getUserID());
                        break;
                    }
                    if (p4 == player) {
                        foundSpeaker = true;
                    }
                }
            }
            // send back all the PNs of others that the player was holding
            Set<String> pns = new HashSet<>(player.getPromissoryNotes().keySet());
            for (String pnID : pns) {
                PromissoryNoteModel pn = promissoryNotes.get(pnID);
                if (pn != null && !pn.getOwner().equalsIgnoreCase(player.getColor()) && !pn.getOwner().equalsIgnoreCase(player.getFaction())) {
                    Player p2 = game.getPlayerFromColorOrFaction(pn.getOwner());
                    player.removePromissoryNote(pnID);
                    if (p2 == null) {
                        BotLogger.warning(new BotLogger.LogMessageOrigin(event), "Could not find player when removing eliminated player's PN: " + pn.getOwner());
                    } else {
                        p2.setPromissoryNote(pnID);
                        PromissoryNoteHelper.sendPromissoryNoteInfo(game, p2, false);
                    }
                }
            }

            //Purge all the PNs of the eliminated player that other players were holding
            for (Player p2 : game.getPlayers().values()) {
                pns = new HashSet<>(p2.getPromissoryNotes().keySet());
                for (String pnID : pns) {
                    PromissoryNoteModel pn = promissoryNotes.get(pnID);
                    if (pn != null && (pn.getOwner().equalsIgnoreCase(player.getColor()) || pn.getOwner().equalsIgnoreCase(player.getFaction()))) {
                        p2.removePromissoryNote(pnID);
                        PromissoryNoteHelper.sendPromissoryNoteInfo(game, p2, false);
                    }
                }
            }
            //Remove all the players units and ccs from the board
            for (Tile tile : game.getTileMap().values()) {
                tile.removeAllUnits(player.getColor());
                if (!"null".equalsIgnoreCase(player.getColor()) && CommandCounterHelper.hasCC(event, player.getColor(), tile)) {
                    RemoveCommandCounterService.fromTile(event, player.getColor(), tile, game);
                }
            }
            //discard all of a players ACs
            Map<String, Integer> acs = new LinkedHashMap<>(player.getActionCards());
            for (Map.Entry<String, Integer> ac : acs.entrySet()) {
                game.discardActionCard(player.getUserID(), ac.getValue());
                String sb = "Player: " + player.getUserName() + " - " + "Discarded action card:" + "\n" + Mapper.getActionCard(ac.getKey()).getRepresentation() + "\n";
                MessageHelper.sendMessageToChannel(event.getChannel(), sb);
            }
            ActionCardHelper.serveReverseEngineerButtons(game, player, new ArrayList<>(acs.keySet()));

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
                Helper.addMapPlayerPermissionsToGameChannels(event.getGuild(), game.getName());
            }
        }

        Guild guild = event.getGuild();
        Member removedMember = guild.getMemberById(player.getUserID());
        List<Role> roles = guild.getRolesByName(game.getName(), true);
        if (removedMember != null && roles.size() == 1) {
            guild.removeRoleFromMember(removedMember, roles.getFirst()).queue();
        }
        stringBuilder.append("Eliminated player: ").append(player.getUserName()).append(" from game: ").append(game.getName()).append("\n");
    }
}

package ti4.commands.player;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.game.StartPhaseService;
import ti4.service.player.PlayerStatsService;
import ti4.service.strategycard.PickStrategyCardService;

class SCPick extends GameStateSubcommand {

    public SCPick() {
        super(Constants.SC_PICK, "Pick a strategy card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy card initiative number")
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Collection<Player> activePlayers = game.getRealPlayers();
        if (activePlayers.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No active players found.");
            return;
        }

        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer <= 0) maxSCsPerPlayer = 1;

        Player player = getPlayer();
        int playerSCCount = player.getSCs().size();
        if (playerSCCount >= maxSCsPerPlayer) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Player may not pick another strategy card. Max strategy cards per player for this game is "
                            + maxSCsPerPlayer + ".");
            return;
        }

        OptionMapping option = event.getOption(Constants.STRATEGY_CARD);
        int scPicked = option.getAsInt();

        boolean pickSuccessful = PlayerStatsService.pickSC(event, game, player, option);
        Set<Integer> playerSCs = player.getSCs();
        if (!pickSuccessful) {
            MessageHelper.sendMessageToEventChannel(event, "No strategy card picked.");
            return;
        }
        // ONLY DEAL WITH EXTRA PICKS IF IN FoW
        if (playerSCs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No strategy card picked.");
            return;
        }
        secondHalfOfSCPick(event, player, game, scPicked);
    }

    public static void secondHalfOfSCPick(GenericInteractionCreateEvent event, Player player, Game game, int scPicked) {
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);

        String msgExtra = "";
        boolean allPicked = true;
        Player privatePlayer = null;
        List<Player> activePlayers = PickStrategyCardService.getSCPickOrder(game);
        int maxSCsPerPlayer = game.getStrategyCardsPerPlayer();
        if (maxSCsPerPlayer < 1) {
            maxSCsPerPlayer = 1;
        }
        if (!game.getStoredValue("exhaustedSC" + scPicked).isEmpty()) {
            game.setSCPlayed(scPicked, true);
            for (Player p2 : game.getRealPlayers()) {
                p2.addFollowedSC(scPicked);
            }
        }

        boolean nextCorrectPing = false;
        Queue<Player> players = new ArrayDeque<>(activePlayers);
        while (players.iterator().hasNext()) {
            Player player_ = players.poll();
            if (player_ == player) {
                nextCorrectPing = true;
            }
            if (player_ == null || !player_.isRealPlayer()) {
                continue;
            }
            int player_SCCount = player_.getSCs().size();
            if (nextCorrectPing && player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                msgExtra += player_.getRepresentationUnfogged() + " is up to pick their strategy card.";
                game.setPhaseOfGame("strategy");
                privatePlayer = player_;
                allPicked = false;
                break;
            }

            if (player_SCCount < maxSCsPerPlayer && player_.getFaction() != null) {
                players.add(player_);
            }
        }

        // SEND EXTRA MESSAGE
        if (isFowPrivateGame) {
            String fail = "User for next faction not found. Report to ADMIN";
            String success = "The next player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(privatePlayer, game, event, msgExtra, fail, success);
            game.updateActivePlayer(privatePlayer);
            if (!allPicked) {
                game.setPhaseOfGame("strategy");
                MessageHelper.sendMessageToChannelWithButtons(
                        privatePlayer.getPrivateChannel(),
                        "Use buttons to pick your strategy card.",
                        Helper.getRemainingSCButtons(game, privatePlayer));
            }
        } else {
            if (!allPicked) {
                game.updateActivePlayer(privatePlayer);
                game.setPhaseOfGame("strategy");
                PickStrategyCardService.checkForForcePickLastStratCard(event, privatePlayer, game, msgExtra);
            }
        }
        if (allPicked) {
            StartPhaseService.startActionPhase(event, game);
        }
    }
}

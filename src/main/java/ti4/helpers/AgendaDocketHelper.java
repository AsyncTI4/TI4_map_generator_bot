package ti4.helpers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;

public final class AgendaDocketHelper {

    private AgendaDocketHelper() {}

    private static void offerDocketBidding(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        int spacer = 1;
        if (Helper.getPlayerInfluenceAvailable(player, game) > 22) {
            spacer = 2;
        }
        for (int x = 0; x < Helper.getPlayerInfluenceAvailable(player, game) + 1; x += spacer) {
            buttons.add(Buttons.gray("bidInfluence_" + x, "" + x));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please "
                        + "choose how much influence you wish to spend bidding on being one of the two people who propose agendas. You will be"
                        + " prompted to exhaust planets later. Please ensure the value you submit is one that your planets can add up to.",
                buttons);
    }

    @ButtonHandler("reviseBid")
    public static void reviseBid(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue("influenceBidFor" + player.getFaction(), "");
        offerDocketBidding(game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("bidInfluence_")
    public static void bidInfluence(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String influence = buttonID.split("_")[1];
        game.setStoredValue("influenceBidFor" + player.getFaction(), influence);
        ButtonHelper.deleteMessage(event);
        int amount = 0;
        for (Player player2 : game.getRealPlayers()) {
            if (game.getStoredValue("docketSpace1").equalsIgnoreCase(player2.getFaction())
                    || player2.hasAbility("galactic_threat")
                    || player2.getFaction().equalsIgnoreCase(game.getStoredValue("docketspace2"))) {
                amount++;
                continue;
            }
            if (!game.getStoredValue("influenceBidFor" + player2.getFaction()).isEmpty()) {
                amount++;
            }
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray("reviseBid", "Revise Bid"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you successfully " + "bid " + influence
                        + " influence. Use this button to revise your bid if necessary.",
                buttons);

        if (amount == game.getRealPlayers().size()) {
            Map<String, List<Player>> top2 = findTopTwoBidders(game);
            buttons = new ArrayList<>();
            List<Player> first = top2.get("first");
            List<Player> second = top2.get("second");
            if (first.size() == 1) {
                game.setStoredValue("docketSpace1", first.getFirst().getFaction());
                if (second.size() == 1) {
                    game.setStoredValue("docketSpace2", second.getFirst().getFaction());
                    // offer agendas to 1st
                } else {
                    game.removeStoredValue("docketSpace2");
                    for (Player player2 : second) {
                        buttons.add(Buttons.gray(
                                game.getSpeaker().factionButtonChecker() + "breakDocketTie_2_" + player2.getFaction(),
                                player2.getDisplayName()));
                    }
                    String msg = game.getSpeaker() + ", please break the tie for second place using these buttons.";
                    MessageHelper.sendMessageToChannelWithButtons(
                            game.getSpeaker().getCorrectChannel(), msg, buttons);
                }
            } else {
                game.removeStoredValue("docketSpace1");
                game.removeStoredValue("docketSpace2");

                for (Player player2 : second) {
                    buttons.add(Buttons.gray(
                            game.getSpeaker().factionButtonChecker() + "breakDocketTie_1_" + player2.getFaction(),
                            player2.getDisplayName()));
                }
                String msg = game.getSpeaker() + ", please break the tie for first place using these buttons.";
                MessageHelper.sendMessageToChannelWithButtons(game.getSpeaker().getCorrectChannel(), msg, buttons);
                buttons = new ArrayList<>();
                for (Player player2 : second) {
                    buttons.add(Buttons.gray(
                            game.getSpeaker().factionButtonChecker() + "breakDocketTie_2_" + player2.getFaction(),
                            player2.getDisplayName()));
                }
                msg = game.getSpeaker() + ", please break the tie for second place using these buttons.";
                MessageHelper.sendMessageToChannelWithButtons(game.getSpeaker().getCorrectChannel(), msg, buttons);
            }

            // Offer buttons for exhausting and display bid results
            // Offer agendas to 1st
        }
    }

    @ButtonHandler("breakDocketTie_")
    public static void breakDocketTie(ButtonInteractionEvent event, Game game, String buttonID) {
        String pos = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        game.setStoredValue("docketSpace" + pos, faction);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "The Speaker broke the tie for place #" + pos + " in favor of "
                        + game.getPlayerFromColorOrFaction(faction).getRepresentation() + ".");

        if (!game.getStoredValue("docketSpace1").isEmpty()
                && !game.getStoredValue("docketSpace2").isEmpty()) {
            // offer agendas to 1st
            Player first = game.getPlayerFromColorOrFaction(game.getStoredValue("docketSpace1"));
        }
    }

    private static Map<String, List<Player>> findTopTwoBidders(Game game) {
        List<Player> players = game.getRealPlayers();
        List<PlayerBid> playerBids = new ArrayList<>(players.size());

        for (Player p : players) {
            if (!game.getStoredValue("influenceBidFor" + p.getFaction()).isEmpty()) {
                int bid = Integer.parseInt(game.getStoredValue("influenceBidFor" + p.getFaction()));
                playerBids.add(new PlayerBid(p, bid));
            }
        }

        playerBids.sort(Comparator.comparingInt(PlayerBid::bid).reversed());

        List<Player> first = new ArrayList<>();
        List<Player> second = new ArrayList<>();

        if (playerBids.isEmpty()) {
            Map<String, List<Player>> result = new HashMap<>();
            result.put("first", first);
            result.put("second", second);
            return result;
        }

        int highestBid = playerBids.getFirst().bid();
        for (PlayerBid pb : playerBids) {
            if (pb.bid() == highestBid) {
                first.add(pb.player());
            }
        }

        if (first.size() < 2) {
            Integer secondHighestBid = null;
            for (PlayerBid pb : playerBids) {
                int b = pb.bid();
                if (b < highestBid) {
                    secondHighestBid = b;
                    break;
                }
            }

            if (secondHighestBid != null) {
                for (PlayerBid pb : playerBids) {
                    if (pb.bid() == secondHighestBid) {
                        second.add(pb.player());
                    } else if (pb.bid() < secondHighestBid) {
                        break;
                    }
                }
            }
        }

        Map<String, List<Player>> result = new HashMap<>();
        result.put("first", first);
        result.put("second", second);
        return result;
    }

    private record PlayerBid(Player player, int bid) {}
}

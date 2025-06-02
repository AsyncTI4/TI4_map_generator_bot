package ti4.helpers;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.UnlockLeaderService;

public class CryypterHelper {

    public static List<Button> getCryypterSC3Buttons(int sc) 
    {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawCards = Buttons.gray("cryypterSC3Draw", "Draw Action Cards", CardEmojis.ActionCard);
        return List.of(drawCards, followButton, noFollowButton);
    }

    @ButtonHandler("cryypterSC3Draw")
    public static void resolveCryypterSC3Draw(ButtonInteractionEvent event, Game game, Player player) 
    {
        drawXPickYActionCards(game, player, 3, true);
    }

    private static void drawXPickYActionCards(Game game, Player player, int draw, boolean addScheming) 
    {
        if (draw > 10) 
        {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " drew " + draw + " action card" + (draw == 1 ? "" : "s") + ".";
        if (addScheming && player.hasAbility("scheming")) 
        {
            draw++;
            message = player.getRepresentation() + " drew " + draw + " action card" + (draw == 1 ? "" : "s") 
                + " (**Scheming** increases this from the normal " + (draw-1) + " action card" + (draw == 2 ? "" : "s") + ").";
        }

        for (int i = 0; i < draw; i++) 
        {
            game.drawActionCard(player.getUserID());
        }
        ActionCardHelper.sendActionCardInfo(game, player);

        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " use buttons to discard 1 of the " + draw + " cards just drawn.",
            ActionCardHelper.getDiscardActionCardButtons(player, false));

        ButtonHelper.checkACLimit(game, player);
        if (addScheming && player.hasAbility("scheming")) ActionCardHelper.sendDiscardActionCardButtons(player, false);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) 
        {
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }

    public static void checkEnvoyUnlocks(Game game) 
    {
        if (!game.isVotcMode()) 
        {
            return;
        }
        for (Player player : game.getRealPlayers()) 
        {
            Leader envoy = player.getLeaderByType("envoy").orElse(null);
            if (envoy != null && envoy.isLocked()) 
            {
                UnlockLeaderService.unlockLeader(envoy.getId(), game, player);
            }
        }
    }

    public static void votcSetup(Game game)
    {
        game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_cryypter"));
        game.setTechnologyDeckID("techs_cryypter");
        game.swapInVariantTechs();
        game.setStrategyCardSet("votc");
        //TODO: Implement swap function to only replace specific ACs
        game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_deck_2"));
        //TODO: swap Xxcha and Keleres!Xxcha heroes
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to Voices of the Council mode.");
    }

    public static String argentEnvoyReminder(Player player, Game game)
    {
        Player argent = Helper.getPlayerFromUnlockedLeader(game, "argentenvoy");
        if (argent != null && argent != player) 
        {
            return " Reminder that Argent's Envoy is in play, and you may not wish to abstain.";
        }
        else
        {
            return "";
        }
    }
}

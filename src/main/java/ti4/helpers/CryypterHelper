package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.commands.agenda.ListVoteCount;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.DiscardACRandom;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.explore.DrawRelic;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.player.SCPlay;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.special.SwordsToPlowsharesTGGain;
import ti4.commands.special.WormholeResearchFor;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.PlanetModel;
import ti4.model.TechnologyModel;

public class CryypterHelper {

      public static List<Button> getCryypterSC3Buttons(int sc, Game game) {
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);

         String assignSpeakerMessage = player.getRepresentation()
                + ", please, before you draw your action cards or look at agendas, click a faction below to assign Speaker "
                + Emojis.SpeakerToken;

        List<Button> assignSpeakerActionRow = getPoliticsAssignSpeakerButtons(game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            assignSpeakerMessage, assignSpeakerActionRow);

        String assignSpeakerMessage2 = player.getRepresentation() + " after assigning speaker, Use this button to draw agendas into your cards info thread.";

        List<Button> drawAgendaButton = new ArrayList<>();
        Button draw2Agenda = Button.success("FFCC_" + player.getFaction() + "_" + "drawAgenda_2", "Draw 2 agendas");
        drawAgendaButton.add(draw2Agenda);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), assignSpeakerMessage2, drawAgendaButton);

        Button followButton = Buttons.success("sc_follow_" + sc, "Spend A Strategy CC");
        Button noFollowButton = Buttons.primary("sc_no_follow_" + sc, "Not Following");
        Button drawCards = Buttons.secondary("cryypterSC3Draw", "Draw Action Cards").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));

        return List.of(drawCards, followButton, noFollowButton);
    }

    @ButtonHandler("cryypterSC3Draw")
    public static void resolveCryypterSC3Draw(ButtonInteractionEvent event, Game game, Player player) {
        event.editButton(event.getButton().asDisabled()).queue();
        drawXPickYActionCards(game, player, 3, 1, true)
        RevealEvent.revealEvent(event, game, game.getMainGameChannel());
    }

    public static void drawXPickYActionCards(Game game, Player player, int draw, int discard, boolean addScheming) {
        if (draw > 10) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " Drew " + draw + " AC";
        if (addScheming && player.hasAbility("scheming")) {
            message = "Drew [" + draw + "+1=" + ++draw + "] AC (Scheming)";
        }

        for (int i = 0; i < draw; i++) {
            game.drawActionCard(player.getUserID());
        }
        ACInfo.sendActionCardInfo(game, player);
        
        String ident = player.getRepresentation(true, true);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                ident + " use buttons to discard 1 of the " + draw + " cards just drawn.", ACInfo.getDiscardActionCardButtons(game, player, false));

        ButtonHelper.checkACLimit(game, null, player);
        if (addScheming && player.hasAbility("scheming")) ACInfo.sendDiscardActionCardButtons(game, player, false);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "yssaril", null);
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }
    
}

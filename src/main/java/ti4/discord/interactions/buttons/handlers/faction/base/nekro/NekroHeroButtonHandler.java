package ti4.discord.interactions.buttons.handlers.faction.base.nekro;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.tech.ListTechService;

@UtilityClass
public class NekroHeroButtonHandler {

    @ButtonHandler("nekroHeroStep2_")
    public static void resolveNekroHeroStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.split("_")[1];
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        List<String> techTypes = unitHolder.getTechSpecialities();
        if (techTypes.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "No technology specialties found.");
            return;
        }
        StringBuilder message = new StringBuilder();
        String planetRep = Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(unitHolder.getName(), game);
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }

            StringBuilder emoji = new StringBuilder();
            for (Units.UnitKey key : unitHolder.getUnitKeys()) {
                if (!p2.getColor().equals(key.getColor())) continue;
                int amt = unitHolder.getUnitCount(key);
                unitHolder.removeUnit(key, amt);
                emoji.append(key.unitEmoji().emojiString().repeat(amt));
            }
            if (!emoji.isEmpty()) {
                message.append(p2.getRepresentation())
                        .append(", your ")
                        .append(emoji)
                        .append(" on ")
                        .append(planetRep)
                        .append(" have become the latest casualties in the Nekro Virus War Upon Life.\n");
            }
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(p2.getCardsInfoThread(), message.toString());
                message = new StringBuilder();
            }

            String color = p2.getColor();
            unitHolder.removeAllUnits(color);
            unitHolder.removeAllUnitDamage(color);
        }
        int oldTg = player.getTg();
        int count = unitHolder.getResources() + unitHolder.getInfluence();
        player.setTg(oldTg + count);
        message.append(player.getFactionEmoji())
                .append(" gained ")
                .append(count)
                .append(" trade good")
                .append(count == 1 ? "" : "s")
                .append(" (")
                .append(oldTg)
                .append("->")
                .append(player.getTg())
                .append(") from scouring ")
                .append(planetRep)
                .append(".");
        MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, count);

        if (!game.isTwilightsFallMode()) {
            game.setComponentAction(true);
            List<TechnologyModel> techs = new ArrayList<>();
            for (String type : techTypes) techs.addAll(ListTechService.getAllTechOfAType(game, type, player));
            List<Button> buttons = ListTechService.getTechButtons(techs, player, "nekro");
            message =
                    new StringBuilder(player.getRepresentation() + ", please choose which technology you wish to get.");
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message.toString(), buttons);
        } else {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("drawSingularNewSpliceCard_ability", "Draw 1 Ability"));
            buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), "Please use these buttons to draw 1 ability.", buttons);
        }
        ButtonHelper.deleteMessage(event);
    }
}

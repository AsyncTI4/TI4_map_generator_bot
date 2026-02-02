package ti4.service.objectives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StringHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.game.RoundOneService;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
public class DrawSecretService {

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player) {
        drawSO(event, game, player, 1, true);
    }

    public static void drawSO(
            GenericInteractionCreateEvent event, Game game, Player player, int count, boolean useTnelis) {
        String output = " drew " + count + " secret objective" + (count > 1 ? "s" : "") + ".";
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            output += "Drew a " + (count == 1 ? "second" : StringHelper.ordinal(count + 1))
                    + " secret objective due to **Plausible Deniability**.";
            count++;
        }
        List<String> idsDrawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            idsDrawn.add(game.drawSecretObjective(player.getUserID()));
        }
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + output);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            SecretObjectiveHelper.sendSODiscardButtons(player);
        }
        if (event instanceof ButtonInteractionEvent bevent
                && bevent.getUser().getId().equals(player.getUserID())) {
            List<MessageEmbed> soEmbeds = idsDrawn.stream()
                    .map(Mapper::getSecretObjective)
                    .filter(Objects::nonNull)
                    .map(SecretObjectiveModel::getRepresentationEmbed)
                    .toList();
            bevent.getHook()
                    .setEphemeral(true)
                    .sendMessage("Drew the following secret objective(s):")
                    .addEmbeds(soEmbeds)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void dealSOToAll(GenericInteractionCreateEvent event, int count, Game game) {
        if (count > 0) {
            for (Player player : game.getRealPlayers()) {
                for (int i = 0; i < count; i++) {
                    game.drawSecretObjective(player.getUserID());
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + " due to **Plausible Deniability**, you were dealt an extra secret objective. Thus, you must also discard an extra secret objective.");
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event, game.getRound() == 1, false);
            }
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                count + " " + CardEmojis.SecretObjective + " dealt to all players. Check your `#cards-info` threads.");
        if (game.getRound() == 1) {
            RoundOneService.RoundOne(event, game);
            for (Player p : game.getRealPlayers()) {
                if (p.hasAbility("questing_prince")) {
                    int shrineCount = game.getRealPlayers().size() - 1;
                    List<Player> others = new ArrayList<>(game.getRealPlayersExcludingThis(p));
                    Collections.shuffle(others);
                    for (int i = 0; i < shrineCount; i++) {
                        Player target = others.get(i);
                        String shrine = "normal";
                        if (i == 0) {
                            shrine = "special";
                        }
                        List<Button> buttons = new ArrayList<>();
                        Tile rex = game.getMecatolTile();
                        if (rex != null) {
                            for (String pos :
                                    FoWHelper.getAdjacentTiles(game, rex.getPosition(), target, false, false)) {
                                Tile tile = game.getTileByPosition(pos);
                                if (tile != null) {
                                    for (Planet planet : tile.getPlanetUnitHolders()) {
                                        buttons.add(Buttons.green(
                                                "addShrine_" + planet.getName() + "_" + shrine,
                                                Helper.getPlanetRepresentation(planet.getName(), game) + " in tile "
                                                        + tile.getRepresentationForButtons()));
                                    }
                                }
                            }
                        }
                        Tile hs = target.getHomeSystemTile();
                        if (hs != null) {
                            for (String pos :
                                    FoWHelper.getAdjacentTiles(game, hs.getPosition(), target, false, false)) {
                                Tile tile = game.getTileByPosition(pos);
                                if (tile != null) {
                                    for (Planet planet : tile.getPlanetUnitHolders()) {
                                        buttons.add(Buttons.green(
                                                "addShrine_" + planet.getName() + "_" + shrine,
                                                Helper.getPlanetRepresentation(planet.getName(), game) + " in tile "
                                                        + tile.getRepresentationForButtons()));
                                    }
                                }
                            }
                        }
                        if (buttons.size() > 0) {
                            MessageHelper.sendMessageToChannelWithButtons(
                                    target.getCorrectChannel(),
                                    target.getRepresentation() + " Select a planet to place a shrine.",
                                    buttons);
                        }
                    }
                }
            }
        }
    }
}

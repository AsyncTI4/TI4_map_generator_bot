package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class PsychosporeService {

    private String psychosporeRep() {
        return Mapper.getBreakthrough("arborecbt").getNameRepresentation();
    }

    public List<Button> getPsychosporeButtons(Game game, Player player) {
        String prefix = "resolvePsychospore";
        Predicate<Tile> canUsePsychospore = Tile.tileHasPlayersInfAndCC(player);
        return ButtonHelper.getTilesWithPredicateForAction(player, game, prefix, canUsePsychospore, false);
    }

    public void postInitialButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = getPsychosporeButtons(game, player);
        String message = "Choose a tile to use " + psychosporeRep() + " and remove your Command Token.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("resolvePsychospore_")
    private void resolvePsychospore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rx = "resolvePsychospore_" + RegexHelper.posRegex(game);
        ButtonHelper.deleteMessage(event);
        RegexService.runMatcher(rx, buttonID, matcher -> {
            String pos = matcher.group("pos");

            // Remove Token
            Tile tile = game.getTileByPosition(pos);
            String msg = player.getRepresentationNoPing() + " removed their Command Token from ";
            msg += tile.getRepresentationForButtons(game, player) + " using " + psychosporeRep() + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            RemoveCommandCounterService.fromTile(event, player, tile);

            // Place an infantry
            List<Button> bonusInfantry = new ArrayList<>();
            for (String planetOrSpace : tile.getUnitHolders().keySet()) {
                String place = planetOrSpace + ("space".equals(planetOrSpace) ? tile.getPosition() : "");
                String id = player.finChecker() + "placeOneNDone_skipbuild_gf_" + place;
                String label = Helper.getUnitHolderRepresentation(tile, planetOrSpace, game, player);
                bonusInfantry.add(Buttons.green(id, label, PlanetEmojis.getPlanetEmoji(planetOrSpace)));
            }
            bonusInfantry.add(Buttons.DONE_DELETE_BUTTONS.withLabel("Skip placing an infantry"));
            String postPsychosporeMsg =
                    player.getRepresentationUnfogged() + " use the buttons to place an infantry in the system:";
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                    player.getCorrectChannel(), postPsychosporeMsg, bonusInfantry);
        });
    }
}

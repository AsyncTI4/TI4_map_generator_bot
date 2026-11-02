package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.Mapper;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class NetrunnersTechHandler {

    public static void addMimeticOverrideCombatButton(List<Button> buttons, Player player) {
        if (player.hasTechReady("benetrunnersmo")) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "useMimeticOverride",
                    "Use Mimetic Override",
                    FactionEmojis.netrunners));
        }
    }

    @ButtonHandler("useMimeticOverride")
    public static void useMimeticOverride(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTechReady("benetrunnersmo")) {
            return;
        }
        PlayerTechService.exhaustTechAndResolve(event, game, player, "benetrunnersmo");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void resolveMimeticOverride(GenericInteractionCreateEvent event, Game game, Player player) {
        CombatModifierModel modifier = Mapper.getCombatModifiers().get("mimetic_override");
        if (modifier == null) {
            return;
        }
        player.addNewTempCombatMod(new TemporaryCombatModifierModel(
                Constants.TECH, "benetrunnersmo", modifier, player.getNumberOfTurns()));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " will apply _Mimetic Override_ to this combat round.");
    }

    public static void resolveDataMining(GenericInteractionCreateEvent event, Game game, Player player, String tech) {
        if (!player.hasTech("benetrunnersdm")) {
            return;
        }

        int dmCount = 0;

        for (Player player2 : game.getRealPlayersExcludingThis(player)) {
            if (!player2.hasTech(tech)) {
                continue;
            }

            dmCount++;
        }
        if (dmCount == 0) {
            return;
        }

        player.gainTG(dmCount, true);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " gained " + dmCount + " trade goods due to their _Data Mining_ technology."
                        + "\nThey now have " + player.getTg() + " trade goods.");
    }
}

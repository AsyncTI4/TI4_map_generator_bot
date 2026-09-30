package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Vanguard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.ListTechService;

@UtilityClass
public class VanguardAbilitiesHandler {
    private static final String GET_FUNDAMENTAL_TECH = "researchTechWithFundamentals";

    public static void sendFundamentalsButton(Player player) {
        if (player == null || !player.hasAbility("enhanced_fundamentals") || player.getTg() < 2) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + GET_FUNDAMENTAL_TECH,
                "Research Tech (On Win)",
                FactionEmojis.vanguard));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", this is a reminder that if you win this ground combat, you may spend 2 trade goods to research a technology with no prerequisites.",
                buttons);
    }

    @ButtonHandler(GET_FUNDAMENTAL_TECH)
    public static void researchTechWithFundamentals(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasAbility("enhanced_fundamentals")) {
            return;
        }

        if (player.getTg() < 2) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "You do not have at least 2 trade goods to use _Enhanced Fundamentals_ right now.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<TechnologyModel> techs = new ArrayList<>(game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> tech.getFaction().isEmpty()
                        || player.getNotResearchedFactionTechs().contains(tech.getAlias()))
                .filter(tech -> tech.getRequirements().isEmpty())
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .toList());

        if (techs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "You do not have any technologies with zero prerequisites that you can research.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setTg(player.getTg() - 2);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " choose which technology you would like to research due to _Enhanced Fundamentals_.",
                ListTechService.getTechButtons(techs, player, "nopay"));

        ButtonHelper.deleteMessage(event);
    }

    public static void addInterlockingShieldsButton(
            List<Button> buttons, Game game, Player player, Tile tile, String cancelType, int remainingHits) {
        if (remainingHits < 1 || !player.hasAbility("interlocking_shields")) {
            return;
        }

        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || !combat.factions().contains(player.getFaction())
                || !tile.getPosition().equals(combat.tilePosition())) {
            return;
        }

        int round = combat.factions().stream()
                .mapToInt(faction -> NumberUtils.toInt(game.getStoredValue(
                        "combatRoundTracker" + faction + combat.tilePosition() + combat.unitHolderName())))
                .max()
                .orElse(0);

        String key =
                "interlockingShields" + player.getFaction() + combat.tilePosition() + combat.unitHolderName() + round;
        if (!game.getStoredValue(key).isEmpty()) {
            return;
        }

        buttons.add(Buttons.gray(
                player.factionButtonChecker()
                        + cancelType
                        + "_"
                        + tile.getPosition()
                        + "_"
                        + remainingHits
                        + "_interlocking",
                "Use Interlocking Shields"));
    }

    public static boolean useInterlockingShields(Game game, Player player, Tile tile) {
        if (!player.hasAbility("interlocking_shields")) {
            return false;
        }

        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || !combat.factions().contains(player.getFaction())
                || !tile.getPosition().equals(combat.tilePosition())) {
            return false;
        }

        int round = combat.factions().stream()
                .mapToInt(faction -> NumberUtils.toInt(game.getStoredValue(
                        "combatRoundTracker" + faction + combat.tilePosition() + combat.unitHolderName())))
                .max()
                .orElse(0);

        String key =
                "interlockingShields" + player.getFaction() + combat.tilePosition() + combat.unitHolderName() + round;
        if (!game.getStoredValue(key).isEmpty()) {
            return false;
        }

        game.setStoredValue(key, "used");
        return true;
    }
}

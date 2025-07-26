package ti4.buttons.handlers.tech;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.TechEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.tech.PlayerTechService;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
class TechButtonHandler {

    @ButtonHandler("useTech_")
    public static void useTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("useTech_", "");
        TechnologyModel techModel = Mapper.getTech(tech);
        if (!tech.equalsIgnoreCase("st")) {
            if (techModel.isUnitUpgrade() || tech.equalsIgnoreCase("dsgledr")) {
                String message = player.getRepresentation() + " used tech: " + techModel.getRepresentation(false);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                ButtonHelper.deleteMessage(event);
                return;
            }
        }

        String message = player.getRepresentation() + " exhausted tech: " + techModel.getRepresentation(false);
        player.exhaustTech(tech);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
        CommanderUnlockCheckService.checkPlayer(player, "jolnar");
    }

    @ButtonHandler("acquireAFreeTech") // Buttons.GET_A_FREE_TECH
    public static void acquireAFreeTech(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String finsFactionCheckerPrefix = player.getFinsFactionCheckerPrefix();
        game.setComponentAction(true);
        buttons.add(Buttons.blue(finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay",
            "Get a Propulsion Technology", TechEmojis.PropulsionTech));
        buttons.add(Buttons.green(finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay", "Get a Biotic Technology",
            TechEmojis.BioticTech));
        buttons.add(Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay",
            "Get a Cybernetic Technology", TechEmojis.CyberneticTech));
        buttons.add(Buttons.red(finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay", "Get a Warfare Technology",
            TechEmojis.WarfareTech));
        buttons.add(Buttons.gray(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay",
            "Get A Unit Upgrade Technology", TechEmojis.UnitUpgradeTech));
        String message = player.getRepresentation() + ", please choose what type of technology you wish to get?";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("nekroTechExhaust")
    public static void nekroTechExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        Button doneExhausting = Buttons.red("deleteButtons_technology", "Done Exhausting Planets");
        buttons.add(doneExhausting);
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("exhauste6g0network")
    public static void exhaustE6G0Network(ButtonInteractionEvent event, Player player, Game game) {
        player.addExhaustedRelic("e6-g0_network");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " chose to exhaust _E6-G0 Network_.");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message = player.getFactionEmoji()
                + " drew 2 action cards with **Scheming**. Please discard 1 action card.";
            ActionCardHelper.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " use buttons to discard",
                ActionCardHelper.getDiscardActionCardButtons(player, false));
        } else if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = player.getFactionEmoji() + " drew 1 action card.";
        }
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.checkACLimit(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("autoneticMemoryStep3")
    public static void autoneticMemoryStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("autoneticMemoryStep3_", "");
        player.refreshTech(tech);
        String message = player.getFactionEmoji() + " readied " + Mapper.getTech(tech).getRepresentation(false) + " with **Autonetic Memory**.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cymiaeHeroAutonetic")
    public static void cymiaeHeroAutonetic(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            if (player.getTechExhausted().contains(tech)) {
                buttons.add(Buttons.green("autoneticMemoryStep3_" + tech, Mapper.getTech(tech).getName()));
            }
        }

        String message = player.getRepresentationUnfogged() + ", choose which technology to ready with **Autonetic Memory**.";
        if (buttons.isEmpty()) {
            message = "You have no exhausted technologies to ready.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
        ButtonHelper.deleteMessage(event);
    }
}
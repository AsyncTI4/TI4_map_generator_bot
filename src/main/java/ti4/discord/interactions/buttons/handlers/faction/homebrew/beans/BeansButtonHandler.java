package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class BeansButtonHandler {

    @ButtonHandler("beans_dream_remove_nexus_")
    public static void removeNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.removeNexusToken(event, game, player, buttonID);
    }

    @ButtonHandler("beans_dream_add_nexus_")
    public static void addNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.addNexusToken(event, game, player, buttonID);
    }

    @ButtonHandler("beans_dream_move_nexus_from_")
    public static void moveNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.moveNexusToken(event, game, player, buttonID);
    }

    @ButtonHandler("beans_dream_offer_add_nexus")
    public static void offerAddNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        DreamButtonHandler.offerAddNexusButtons(event, game, player);
    }

    @ButtonHandler("beans_dream_offer_move_nexus")
    public static void offerMoveNexusButtons(ButtonInteractionEvent event, Game game, Player player) {
        DreamButtonHandler.offerMoveNexusButtons(event, game, player);
    }

    @ButtonHandler("beans_dream_liturgy_menu_back")
    public static void showLiturgyMenu(ButtonInteractionEvent event, Game game, Player player) {
        DreamButtonHandler.showLiturgyMenu(event, game, player);
    }

    @ButtonHandler("beans_incomprehensible_form_")
    public static void incomprehensibleForm(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.presentIncomprehensibleChoices(event, game, player, buttonID);
    }

    @ButtonHandler("beans_incomprehensible_form_use_token_")
    public static void incomprehensibleFormUseToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.useIncomprehensibleForm(event, game, player, buttonID);
    }

    @ButtonHandler("beans_incomprehensible_form_use_flagship_")
    public static void incomprehensibleFormUseFlagship(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.useIncomprehensibleForm(event, game, player, buttonID);
    }

    @ButtonHandler("beans_promissory_bepndream_return_")
    public static void visionsReturnPromissory(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.resolveVisionsPromissory(event, game, player, buttonID);
    }

    public static void offerVisionsPromissoryAtTacticalStart(Game game, Player player) {
        DreamButtonHandler.offerVisionsPromissoryAtTacticalStart(game, player);
    }

    @ButtonHandler("beans_not_implemented")
    public static void beansNotImplemented(ButtonInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, "This Beans automation button is not implemented yet.");
    }
}

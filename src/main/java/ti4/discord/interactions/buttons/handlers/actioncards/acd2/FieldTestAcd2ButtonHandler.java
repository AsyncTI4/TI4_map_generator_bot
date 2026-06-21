package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
class FieldTestAcd2ButtonHandler {

    static final String STORED_KEY_PREFIX = "fieldTestTech";

    private static final TechnologyType[] TYPES = {
        TechnologyType.BIOTIC,
        TechnologyType.CYBERNETIC,
        TechnologyType.PROPULSION,
        TechnologyType.WARFARE,
        TechnologyType.UNITUPGRADE
    };

    @ButtonHandler("resolveFieldTest")
    public static void resolveFieldTest(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (TechnologyType type : TYPES) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "fieldTestType_" + type.name(), type.toString() + " Technology"));
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a technology type for _Field Test_. You will treat the chosen technology as if you"
                        + " own it until the end of your turn.",
                buttons);
    }

    @ButtonHandler("fieldTestType_")
    public static void resolveFieldTestType(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        TechnologyType type;
        try {
            type = TechnologyType.valueOf(buttonID.replace("fieldTestType_", ""));
        } catch (IllegalArgumentException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<TechnologyModel> techs = ListTechService.getAllTechOfAType(game, type.toString(), player, false, false);
        ButtonHelper.deleteMessage(event);
        if (techs.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there are no unowned " + type
                            + " technologies for _Field Test_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : techs) {
            buttons.add(
                    Buttons.green(player.factionButtonChecker() + "fieldTestTech_" + tech.getAlias(), tech.getName()));
        }
        buttons.add(Buttons.red("deleteButtons", "Back / Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which technology to gain temporarily for _Field Test_.",
                buttons);
    }

    @ButtonHandler("fieldTestTech_")
    public static void resolveFieldTestTech(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String techID = buttonID.replace("fieldTestTech_", "");
        TechnologyModel tech = Mapper.getTech(techID);
        ButtonHelper.deleteMessage(event);
        if (tech == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Field Test_.");
            return;
        }
        if (player.hasTech(techID)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " already owns _" + tech.getName() + "_.");
            return;
        }

        player.addTech(techID);
        game.setStoredValue(STORED_KEY_PREFIX + player.getFaction(), techID);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gained _" + tech.getName()
                        + "_ via _Field Test_; they treat it as owned until the end of their turn.");
    }
}

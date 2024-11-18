package ti4.commands.player;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class UnitInfo extends GameStateSubcommand {

    public UnitInfo() {
        super(Constants.UNIT_INFO, "Send special unit information to your Cards Info channel", false, true);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_ALL_UNITS, "'True' also show basic (non-faction) units (Default: False)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        boolean showAllUnits = event.getOption(Constants.SHOW_ALL_UNITS, false, OptionMapping::getAsBoolean);
        sendUnitInfo(game, player, event, showAllUnits);
    }

    @ButtonHandler(Constants.REFRESH_UNIT_INFO)
    public static void sendUnitInfoSpecial(Game game, Player player, GenericInteractionCreateEvent event) {
        sendUnitInfo(game, player, event, false);
    }

    @ButtonHandler(Constants.REFRESH_ALL_UNIT_INFO)
    public static void sendUnitInfoAll(Game game, Player player, GenericInteractionCreateEvent event) {
        sendUnitInfo(game, player, event, true);
    }

    public static void sendUnitInfo(Game game, Player player, GenericInteractionCreateEvent event, boolean showAllUnits) {
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendUnitInfo(player, showAllUnits);
    }

    public static void sendUnitInfo(Player player, boolean showAllUnits) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
            player.getCardsInfoThread(),
            "__**Unit Info:**__",
            getUnitMessageEmbeds(player, showAllUnits),
            getUnitInfoButtons());
    }

    private static List<Button> getUnitInfoButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_UNIT_INFO);
        buttons.add(Buttons.REFRESH_ALL_UNIT_INFO);
        return buttons;
    }

    public static List<MessageEmbed> getUnitMessageEmbeds(Player player, boolean includeAllUnits) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();

        List<String> unitList = new ArrayList<>();
        if (includeAllUnits) {
            unitList.addAll(player.getUnitsOwned());
        } else {
            unitList.addAll(player.getSpecialUnitsOwned());
        }
        for (UnitModel unitModel : unitList.stream().sorted().map(Mapper::getUnit).toList()) {
            MessageEmbed unitRepresentationEmbed = unitModel.getRepresentationEmbed(false);
            messageEmbeds.add(unitRepresentationEmbed);
        }
        return messageEmbeds;
    }

}

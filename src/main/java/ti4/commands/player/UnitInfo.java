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
import ti4.commands.uncategorized.CardsInfoHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class UnitInfo extends PlayerSubcommandData {
    public UnitInfo() {
        super(Constants.UNIT_INFO, "Send special unit information to your Cards Info channel");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_ALL_UNITS, "'True' also show basic (non-faction) units (Default: False)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        boolean showAllUnits = event.getOption(Constants.SHOW_ALL_UNITS, false, OptionMapping::getAsBoolean);
        sendUnitInfo(game, player, event, showAllUnits);
    }

    public static void sendUnitInfo(Game game, Player player, GenericInteractionCreateEvent event, boolean showAllUnits) {
        String headerText = player.getRepresentation() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendUnitInfo(game, player, showAllUnits);
    }

    public static void sendUnitInfo(Game game, Player player, boolean showAllUnits) {
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

    private static List<MessageEmbed> getUnitMessageEmbeds(Player player, boolean includeAllUnits) {
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

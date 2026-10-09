package ti4.ai.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.AiSettings;
import ti4.ai.seat.AiSeatService;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class AiAdd extends GameStateSubcommand {

    AiAdd() {
        super("add", "Add an AI player to this game", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.HS_TILE_POSITION, "Home system tile position")
                .setRequired(true)
                .setAutoComplete(true));
        OptionData faction = new OptionData(
                OptionType.STRING, Constants.FACTION, "Faction the AI plays (default: the first free one)");
        AiSettings.FACTION_NAMES.forEach((alias, name) -> faction.addChoice(name, alias));
        addOptions(faction);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of the AI's units").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SPEAKER, "True to make the AI the speaker"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!AiCommandSupport.mayManage(event, getGame())) {
            MessageHelper.replyToMessage(event, AiCommandSupport.notAllowed());
            return;
        }
        String position = StringUtils.substringBefore(
                event.getOption(Constants.HS_TILE_POSITION, "", OptionMapping::getAsString), " ");
        String requestedColor = event.getOption(Constants.COLOR, null, OptionMapping::getAsString);
        String color = requestedColor == null ? null : AliasHandler.resolveColor(requestedColor.toLowerCase());
        if (color != null && !Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "Color `" + requestedColor + "` is not valid.");
            return;
        }
        String faction = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        boolean speaker = event.getOption(Constants.SPEAKER, false, OptionMapping::getAsBoolean);
        AiSeatService.AddResult result = AiSeatService.addSeat(getGame(), faction, color, position, speaker, event);
        MessageHelper.replyToMessage(event, result.message());
    }
}

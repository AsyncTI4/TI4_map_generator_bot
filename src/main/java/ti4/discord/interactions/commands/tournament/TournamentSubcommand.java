package ti4.discord.interactions.commands.tournament;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.service.game.GameNameService;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentService;

abstract class TournamentSubcommand extends Subcommand {

    static final String TOURNAMENT = "tournament";
    static final String CHANNEL = "channel";

    TournamentSubcommand(String name, String description) {
        super(name, description);
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    static OptionData tournamentOption() {
        return new OptionData(OptionType.STRING, TOURNAMENT, "Which tournament", true).setAutoComplete(true);
    }

    static OptionData channelOption(String name, String description) {
        return new OptionData(OptionType.CHANNEL, name, description)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD);
    }

    static void reply(SlashCommandInteractionEvent event, String message) {
        event.getHook().editOriginal(message).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    static Tournament tournament(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(TOURNAMENT);
        if (option == null) return null;
        try {
            return TournamentService.getBean()
                    .find(Long.parseLong(option.getAsString().trim()))
                    .filter(tournament -> tournament.getStatus().isActive())
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static GuildMessageChannel channel(SlashCommandInteractionEvent event, String optionName) {
        OptionMapping option = event.getOption(optionName);
        if (option == null) {
            return event.getChannel() instanceof GuildMessageChannel channel ? channel : null;
        }
        GuildChannelUnion channel = option.getAsChannel();
        return channel instanceof GuildMessageChannel messageChannel ? messageChannel : null;
    }

    static boolean refusePanelInGameChannel(SlashCommandInteractionEvent event) {
        if (!(event.getChannel() instanceof GuildMessageChannel channel) || !isGameChannel(channel)) return false;
        reply(event, "Run this outside game channels. Only seated players can press buttons in a game channel.");
        return true;
    }

    static boolean isGameChannel(GuildMessageChannel channel) {
        return GameManager.isValid(GameNameService.getGameNameFromChannel(channel));
    }
}

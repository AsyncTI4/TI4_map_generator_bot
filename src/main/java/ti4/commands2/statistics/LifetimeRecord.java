package ti4.commands2.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class LifetimeRecord extends Subcommand {

    public LifetimeRecord() {
        super(Constants.LIFETIME_RECORD, "Dice luck and average turn time for all games of specific players");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<User> members = new ArrayList<>();

        for (int i = 1; i <= 8; i++) {
            if (!Objects.nonNull(event.getOption("player" + i))) {
                break;
            }
            User member = event.getOption("player" + i).getAsUser();
            members.add(member);
        }
        DiceLuck luck = new DiceLuck();
        AverageTurnTime time = new AverageTurnTime();
        String sb = luck.getSelectUsersDiceLuck(members, luck.getAllPlayersDiceLuck(false)) +
                time.getSelectUsersTurnTimes(members, time.getAllPlayersTurnTimes(false));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
    }

}

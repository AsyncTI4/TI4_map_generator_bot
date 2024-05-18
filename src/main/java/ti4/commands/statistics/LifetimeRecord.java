package ti4.commands.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class LifetimeRecord extends StatisticsSubcommandData {

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
            if (Objects.nonNull(event.getOption("player" + i))) {
                User member = event.getOption("player" + i).getAsUser();
                if (member != null)
                    members.add(member);
            } else {
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        DiceLuck luck = new DiceLuck();
        AverageTurnTime time = new AverageTurnTime();
        sb.append(luck.getSelectUsersDiceLuck(members, luck.getAllPlayersDiceLuck(false)));
        sb.append(time.getSelectUsersTurnTimes(members, time.getAllPlayersTurnTimes(false)));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

}

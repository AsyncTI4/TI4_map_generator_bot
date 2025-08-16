package ti4.commands.uncategorized;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.ParentCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ServerPromoteCommand implements ParentCommand {

    public static final String DEV_CHANNEL = "947520255826198549";
    public static final Map<String, String> Servers = new HashMap<>() {
        {
            put(Constants.ASYNCTI4_HUB_SERVER_ID, "Async Hub");
            put("1176104225932058694", "War Sun Tzu");
            put("1145823841227112598", "Dread Not!");
            put("1250131684393881610", "Tommer Hawk");
            put("1090910555327434774", "Stroter's Paradise");
            put("1209956332380229672", "Fighter Club");
            put("1155639926675746886", "Emoji Farm 1");
            put("1156671516784730314", "Emoji Farm 2");
            put("1156686770436591637", "Emoji Farm 3");
            put("1158956227829706762", "Emoji Farm 4");
            put("1158956387376828507", "Emoji Farm 5");
            put("1158956545019760750", "Emoji Farm 6");
            put("1158956865875615836", "Emoji Farm 7");
            put("1158956969290383360", "Emoji Farm 8");
            put("1164297443379249302", "Emoji Farm 9");
            put("1164298025603190864", "Emoji Farm 10");
            put("1171620536833560676", "Emoji Farm 11");
            put("1180152020582289478", "Emoji Farm 12");
            put("1180160763353124864", "Emoji Farm 13");
            put("1197344983531913267", "Emoji Farm 14");
            put("1220415501608681512", "Emoji Farm 15");
            put("1220415609725124660", "Emoji Farm 16");
            put("1220415693837832212", "Emoji Farm 17");
        }
    };
    public static final Map<String, String> Ranks = new HashMap<>() {
        {
            put("943596173896323072", "Admin");
            put("947648366056185897", "Developer");
            put("1166011604488425482", "Bothelper");
            put("", ""); // allow blank for emoji farms since there's only the admin rank
        }
    };

    @Override
    public String getName() {
        return Constants.SERVERPROMOTE;
    }

    @Override
    public String getDescription() {
        return "Server promotion";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!DEV_CHANNEL.equals(event.getChannelId())) {
            MessageHelper.replyToMessage(event, "This command can only be run in the `#development` channel.");
            return false;
        }

        OptionMapping targetOpt = event.getOption(Constants.PROMOTE_TARGET);
        if (targetOpt == null || !Servers.containsKey(targetOpt.getAsString())) {
            MessageHelper.replyToMessage(event, "Server does not exist.");
            return false;
        }

        OptionMapping rankOpt = event.getOption(Constants.PROMOTE_RANK);
        if (rankOpt != null && !Ranks.containsKey(rankOpt.getAsString())) {
            MessageHelper.replyToMessage(event, "Rank does not exist.");
            return false;
        }

        if (!Servers.get(targetOpt.getAsString()).startsWith("Emoji Farm")) {
            if (rankOpt == null || rankOpt.getAsString().isEmpty()) {
                MessageHelper.replyToMessage(event, "Rank required (for non Emoji Farm servers).");
                return false;
            }
            Member member = event.getMember();
            boolean allowed = false;
            if ("admin".equalsIgnoreCase(Ranks.get(rankOpt.getAsString()))) {
                for (Role r : member.getRoles()) {
                    if ("943596173896323072".equals(r.getId())) {
                        allowed = true;
                    }
                }
            } else if ("developer".equalsIgnoreCase(Ranks.get(rankOpt.getAsString()))) {
                for (Role r : member.getRoles()) {
                    if ("943596173896323072".equals(r.getId()) || "947648366056185897".equals(r.getId())) {
                        allowed = true;
                    }
                }
            } else if ("bothelper".equalsIgnoreCase(Ranks.get(rankOpt.getAsString()))) {
                for (Role r : member.getRoles()) {
                    if ("943596173896323072".equals(r.getId())
                            || "947648366056185897".equals(r.getId())
                            || "1166011604488425482".equals(r.getId())) {
                        allowed = true;
                    }
                }
            }

            if (!allowed) {
                MessageHelper.replyToMessage(event, "You cannot promote yourself above a rank you already have.");
                return false;
            }
        }

        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping targetOpt = event.getOption(Constants.PROMOTE_TARGET);
        OptionMapping rankOpt = event.getOption(Constants.PROMOTE_RANK);
        OptionMapping demoteOpt = event.getOption(Constants.PROMOTE_DEMOTE);

        String target = targetOpt.getAsString();
        String rank = rankOpt == null ? "" : rankOpt.getAsString();
        boolean demote = demoteOpt != null && demoteOpt.getAsBoolean();
        User user = event.getUser();

        MessageHelper.replyToMessage(
                event,
                (demote ? "Demoting" : "Promoting") + " " + user.getEffectiveName() + "; rank " + Ranks.get(rank));

        if (target.startsWith("Emoji Farm")) {
            Guild guild = event.getJDA().getGuildById(Long.parseLong(target));
            guild.getRoles().forEach(r -> {
                if ("admin".equalsIgnoreCase(r.getName())) {
                    if (demote) {
                        guild.removeRoleFromMember(user, r);
                    } else {
                        guild.addRoleToMember(user, r);
                    }
                }
            });
        } else {
            Guild guild = event.getJDA().getGuildById(Long.parseLong(target));
            guild.getRoles().forEach(r -> {
                if (r.getName().equalsIgnoreCase(Ranks.get(rank))) {
                    if (demote) {
                        guild.removeRoleFromMember(user, r);
                    } else {
                        guild.addRoleToMember(user, r);
                    }
                }
            });
        }
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.PROMOTE_TARGET, "Target Server")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.PROMOTE_RANK, "Rank").setAutoComplete(true),
                new OptionData(OptionType.BOOLEAN, Constants.PROMOTE_DEMOTE, "Demote").setAutoComplete(true));
    }
}

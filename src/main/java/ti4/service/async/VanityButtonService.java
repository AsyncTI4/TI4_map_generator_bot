package ti4.service.async;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.collections4.ListUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class VanityButtonService {

    @ButtonHandler("chooseVanityRole")
    private void chooseVanityRoleCategory(ButtonInteractionEvent event, String buttonID) {
        Guild guild = event.getGuild();
        Member member = guild.getMember(event.getUser());

        String menu = buttonID.split("_")[1];
        List<ComponentSource> factionSources =
                switch (menu) {
                    case "Base" -> List.of(ComponentSource.base);
                    case "PoK" -> List.of(ComponentSource.pok);
                    case "TE" -> List.of(ComponentSource.thunders_edge, ComponentSource.codex3);
                    case "TF" -> List.of(ComponentSource.twilights_fall);
                    case "DS" -> List.of(ComponentSource.ds);
                    default -> List.of();
                };

        List<Button> allButtons = new ArrayList<>();
        for (FactionModel faction : getFactionsForSources(factionSources)) {
            Role vanityRole = getVanityRoleForFaction(faction, guild);
            if (vanityRole == null) continue;

            String name = vanityRole.getName();
            String emoji = faction.getFactionEmoji();
            if (memberHasRole(member, vanityRole)) {
                allButtons.add(Buttons.red("removeVanityRole_" + name, name, emoji));
            } else {
                allButtons.add(Buttons.green("addVanityRole_" + name, name, emoji));
            }
        }

        String msg = "Use the buttons to add or remove a vanity role.";
        List<List<Button>> partitionedButts = ListUtils.partition(allButtons, 24);
        for (var butts : partitionedButts) {
            List<Button> buttons = new ArrayList<>(butts);
            buttons.add(Buttons.CANCEL);
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, msg, buttons);
        }
    }

    @ButtonHandler("addVanityRole")
    @ButtonHandler("removeVanityRole")
    private static void addVanityRole(ButtonInteractionEvent event, String buttonID) {
        Guild guild = event.getGuild();
        String roleName = buttonID.split("_")[1];
        try {
            Role vanityRole = guild.getRolesByName(roleName, true).getFirst();
            if (buttonID.startsWith("remove")) {
                guild.removeRoleFromMember(event.getUser(), vanityRole).queue();
            } else {
                guild.addRoleToMember(event.getUser(), vanityRole).queue();
            }
        } catch (Exception e) {
            String error = "Unable to add the role `" + roleName + "`.";
            error += " The role probably doesn't exist. Ask `@Bothelper` for assistance.";
            MessageHelper.sendEphemeralMessageToEventChannel(event, error);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean memberHasRole(Member m, Role role) {
        return m.getRoles().stream().anyMatch(r -> r.getId().equals(role.getId()));
    }

    private static Role getVanityRoleForFaction(FactionModel faction, Guild guild) {
        String name = faction.getShortName();
        for (Role role : guild.getRolesByName(name, true)) {
            if (role.getName().equalsIgnoreCase(name)) {
                return role;
            }
        }
        return null;
    }

    private static List<FactionModel> getFactionsForSources(List<ComponentSource> sources) {
        return Mapper.getFactionsValues().stream()
                .filter(f -> sources.contains(f.getSource()))
                .filter(f -> !f.getAlias().equals("keleresa") && !f.getAlias().equals("keleresx"))
                .sorted(Comparator.comparing(FactionModel::getShortName))
                .toList();
    }
}

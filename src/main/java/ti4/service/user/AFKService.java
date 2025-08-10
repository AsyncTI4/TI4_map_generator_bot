package ti4.service.user;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Helper;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class AFKService {

    public boolean userIsAFK(User user) {
        return userIsAFK(user.getId());
    }

    public boolean userIsAFK(String userID) {
        String afkHours = UserSettingsManager.get(userID).getAfkHours();
        if (StringUtils.isBlank(afkHours)) {
            return false;
        }
        String[] hoursAFK = afkHours.split(";");
        int currentHour = Helper.getCurrentHour();
        for (String hour : hoursAFK) {
            int h = Integer.parseInt(hour);
            if (h == currentHour) {
                return true;
            }
        }
        return false;
    }
}

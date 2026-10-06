package ti4.spring.service.title;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.EarnedTitle;
import ti4.spring.service.persistence.StandaloneTitleEntityRepository;
import ti4.spring.service.persistence.TitleEntityRepository;

@Service
@RequiredArgsConstructor
public class PlayerTitleService {

    private final TitleEntityRepository titleEntityRepository;
    private final StandaloneTitleEntityRepository standaloneTitleEntityRepository;

    public List<EarnedTitle> getEndedGameTitles(String userId) {
        return titleEntityRepository.findEndedGameTitlesByUserId(userId);
    }

    public List<EarnedTitle> getStandaloneTitles(String userId) {
        return standaloneTitleEntityRepository.findEarnedTitlesByUserId(userId);
    }

    public static PlayerTitleService getBean() {
        return SpringContext.getBean(PlayerTitleService.class);
    }
}

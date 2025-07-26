package ti4.spring.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GetPlayerActionCards {

    private final List<String> actionCards;
}

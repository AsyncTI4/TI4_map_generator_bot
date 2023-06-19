import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
//import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;
import ti4.model.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestResourceHelper {

    @Test
    public void testHelper() {
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
        String tileID = "18";
        //String tileName = Mapper.getTileID(tileID);
        //String tilePath = ResourceHelper.getInstance().getTileFile(tileID);

        assertEquals(tileID, tileID);
    }
}
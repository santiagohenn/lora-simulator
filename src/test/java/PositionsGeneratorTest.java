import org.junit.Test;
import satellite.tools.assets.entities.Position;

import java.util.ArrayList;

public class PositionsGeneratorTest {

    @Test
    public void RandomPositionsGenerationTest() {

        PositionsGenerator pg = new PositionsGenerator(40, -31.6, -65.9, 7.1946);
        ArrayList<Position> coordinates = pg.generatePositions();
        coordinates.forEach(p -> System.out.println(p.getLat() + "," + p.getLon() + "," + p.getHeight()));

    }



}

import generators.PositionsFactory;
import org.junit.Test;
import satellite.tools.assets.entities.Position;

import java.util.ArrayList;

public class PositionsGeneratorTest {

    @Test
    public void RandomPositionsGenerationTest() {

        PositionsFactory pg = new PositionsFactory(40, -31.6, -65.9, 7.1946);
        ArrayList<Position> coordinates = pg.generateUniformRandomPositions();
        coordinates.forEach(p -> System.out.println(p.getLat() + "," + p.getLon() + "," + p.getHeight()));

    }

    @Test
    public void RandomTest() {
        System.out.println(Double.parseDouble("517.8016038646876"));
    }


}

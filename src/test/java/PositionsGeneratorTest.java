import generators.PositionsFactory;
import org.junit.Assume;
import org.junit.Test;
import org.orekit.time.AbsoluteDate;
import satellite.tools.Simulation;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.structures.Ephemeris;
import satellite.tools.utils.Utils;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    public void SSPTest() {
        
        // Paths
        // Exploratory test: prints the sub-satellite point of the first satellite of a local file.
        String satellitePath = "inputs/satellites.csv";
        Assume.assumeTrue("Skipped: " + satellitePath + " not found", new java.io.File(satellitePath).exists());

        // Scenario parameters
        String startTime = "2025-01-01T15:00:00.000";
        String endTime = "2025-01-01T17:00:00.000";
        double propagatorTimeStepSeconds = 60.0;

        List<Satellite> satellites = Utils.satellitesFromFile(satellitePath);
        Simulation sim = new Simulation(startTime, endTime, new Position(0,0,0), satellites.get(0), propagatorTimeStepSeconds, 5);
        AbsoluteDate date = Utils.stamp2AD(startTime);
        AbsoluteDate stopDate = Utils.stamp2AD(endTime);
        while (date.compareTo(stopDate) <= 0) {
            Ephemeris e = sim.computeFixedEphemeris(date);
            date = date.shiftedBy(propagatorTimeStepSeconds);
            System.out.println(Math.toDegrees(e.getLatitude()) + "," + Math.toDegrees(e.getLongitude()));
        }

    }


}

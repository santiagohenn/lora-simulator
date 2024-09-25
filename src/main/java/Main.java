import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.geometry.GeographicTools;
import constellation.tools.math.TimedMetricsRecord;
import constellation.tools.utilities.TimeUtils;
import org.apache.commons.cli.*;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static final double EARTH_RADIUS_M = 6371000.0;

    public static void main(String[] args) {

        String[] options = {"--simulate-pure-aloha", "--save-analysis-datasets",
                "-o", "C:/test/",
                "-s", "C:/mg_data_test_550/satellites.csv",
                "-cov-cfg", "C:/Users/Santi/Desktop/Doctorado/d3co_analysis/config.d3co.properties",
                "-nodes", "10,10,130",
                "-roi", "-21.0,-58.0,7.1946,150",
                "-vt", "20.0"};

        LoraConstellationSimulator.main(options);

    }


}

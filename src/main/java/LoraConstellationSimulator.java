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

public class LoraConstellationSimulator {

    private static final Options options = configureOptions();

    public static void main(String[] args) {

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("LoraConstellationSimulator", options);
            System.exit(1);
        }

        String outputPath = cmd.hasOption("o") ? cmd.getOptionValue("o") : "/";
        String startDate = cmd.hasOption("start") ? cmd.getOptionValue("start") : "2025-01-01T16:00:00.000";
        String endDate = cmd.hasOption("end") ? cmd.getOptionValue("end") : "2025-01-01T16:30:00.000";
        String satellitePath = cmd.hasOption("satellites") ? cmd.getOptionValue("satellites") : (outputPath + "satellites.csv");
        String configurationsPath = cmd.hasOption("cov-cfg") ? cmd.getOptionValue("cov-cfg") : (outputPath + "config.d3co.properties");

        /* Default values */
        double timeOnAir;
        double averageInterArrivalTime = 0.1;
        double frameGenerationRate;
        double dutyCyclePercentage = 1.0;
        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;
        int nOfRuns = 150;
        double propagatorTimeStepSeconds = 30.0;
        double visibilityThresholdDegrees = 20.0;
        double sampleStepSeconds = 15.0;
        double centerLatDegrees = 0.0;
        double centerLonDegrees = 0.0;
        double roiSphericalRadius = 1.0;
        int roiSegments = 50;

        boolean saveAccessIntervals = cmd.hasOption("save-access-intervals");
        boolean saveAnalysisDatasets = cmd.hasOption("save-analysis-datasets");
        boolean saveDestinations = cmd.hasOption("save-destinations");
        boolean saveTransmissions = cmd.hasOption("save-transmissions");

        if (cmd.hasOption("start"))
            startDate = cmd.getOptionValue("start");

        if (cmd.hasOption("end"))
            endDate = cmd.getOptionValue("end");

        if (cmd.hasOption("time-on-air")) {
            timeOnAir = parseDoubleFromCLI(cmd.getOptionValue("toa"));
            frameGenerationRate = timeOnAir / averageInterArrivalTime;
        } else {
            timeOnAir = 0.5;
            frameGenerationRate = timeOnAir / averageInterArrivalTime;
        }

        if (cmd.hasOption("average-inter-arrival-time"))
            averageInterArrivalTime = parseDoubleFromCLI(cmd.getOptionValue("iat"));

        if (cmd.hasOption("frame-generation-rate"))
            frameGenerationRate = parseDoubleFromCLI(cmd.getOptionValue("fgr"));

        if (cmd.hasOption("duty-cycle-percentage"))
            dutyCyclePercentage = parseDoubleFromCLI(cmd.getOptionValue("duty-cycle-percentage"));

        if (cmd.hasOption("nodes")) {
            String[] nodes = cmd.getOptionValue("nodes").split(",");
            initialNodes = Integer.parseInt(nodes[0]);
            stepNodes = Integer.parseInt(nodes[1]);
            maxNodes = Integer.parseInt(nodes[2]);
        }

        if (cmd.hasOption("roi")) {
            String[] roiParams = cmd.getOptionValue("roi").split(",");
            centerLatDegrees = parseDoubleFromCLI(roiParams[0]);
            centerLonDegrees = parseDoubleFromCLI(roiParams[1]);
            roiSphericalRadius = parseDoubleFromCLI(roiParams[2]);
            roiSegments = parseIntFromCLI(roiParams[3]);
        }

        if (cmd.hasOption("runs"))
            nOfRuns = parseIntFromCLI(cmd.getOptionValue("runs"));

        if (cmd.hasOption("sample-step"))
            sampleStepSeconds = parseDoubleFromCLI(cmd.getOptionValue("sample-step"));

        if (cmd.hasOption("time-step"))
            propagatorTimeStepSeconds = parseDoubleFromCLI(cmd.getOptionValue("time-step"));

        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitePath);

        // LoRa Simulation
        NetworkSimulator networkSimulator = new NetworkSimulator(outputPath);
        networkSimulator.setSaveActions(saveAccessIntervals, saveAnalysisDatasets, saveDestinations, saveTransmissions);

        // Scenario parameters
        networkSimulator.setScenarioParameters(
                startDate,
                endDate,
                propagatorTimeStepSeconds,
                visibilityThresholdDegrees
        );

        networkSimulator.setSatellites(satelliteList);
        networkSimulator.setDeploymentArea(centerLatDegrees, centerLonDegrees, roiSphericalRadius);
        networkSimulator.setNetworkParameters(
                timeOnAir * 1000.0,
                dutyCyclePercentage,
                averageInterArrivalTime,
                sampleStepSeconds * 1000.0);
        networkSimulator.setNumberOfRuns(nOfRuns);

        Log.info(networkSimulator.toString());

        Log.info("Running constellation coverage ... ");
        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer(configurationsPath);
        List<double[]> ROI = GeographicTools.computeSphericalCap(centerLatDegrees, centerLonDegrees, roiSphericalRadius, roiSegments);

        GeographicTools geographicTools = new GeographicTools();
        double roiSurface = geographicTools.computeNonEuclideanSurface(ROI);

        constellationCoverageComputer.setOutputPath(outputPath);
        constellationCoverageComputer.setSatelliteList(satelliteList);
        constellationCoverageComputer.setROI(ROI);
        constellationCoverageComputer.run();
        Log.info("... done!");

        List<TimedMetricsRecord> coverageData = constellationCoverageComputer.getConstellationCoverageTimeSeries();
        List<TimedMetricsRecord> backOffTimeSeries = null;

        // Run!
        //simulator.setTimeOnAirAndSampleStep(timeOnAirMS, sampleStepMS);

        Log.info("Minimum DC silent time " + ((100.0 - dutyCyclePercentage) * timeOnAir) + " S.");

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {

            networkSimulator.setNumberOfNodes(nodes);

            double deviceDensity = nodes / roiSurface;
            String finalStartDate = startDate;
            double finalFrameGenerationRate = frameGenerationRate;

            if (cmd.hasOption("simulate-with-ca") || cmd.hasOption("simulate-with-ca-nr")) {
                backOffTimeSeries = coverageData.stream().map(coverageMetric -> {
                    TimedMetricsRecord backOffTimeMetric = new TimedMetricsRecord(coverageMetric.getTime() + TimeUtils.stamp2unix(finalStartDate), coverageMetric.getMetrics().length);
                    for (int i = 0; i < coverageMetric.getMetrics().length; i++) {
                        double backOffTime = (2 * deviceDensity * roiSurface * coverageMetric.getMetrics()[i] - 1 / finalFrameGenerationRate - 1) * timeOnAir;
                        backOffTimeMetric.addMetric(i, Math.max(backOffTime, 0.0));
                    }
                    return backOffTimeMetric;
                }).toList();
            }

            Log.info("Running with " + nodes + " nodes | avg IAT (s): "
                    + networkSimulator.getAverageInterArrivalTime());

            if (cmd.hasOption("simulate-pure-aloha")) {
                networkSimulator.run();
            } else if (cmd.hasOption("simulate-with-ca")) {
                networkSimulator.run(backOffTimeSeries, true);
            } else if (cmd.hasOption("simulate-with-ca-nr")) {
                networkSimulator.run(backOffTimeSeries, false);
            }

        }


    }

    private static Options configureOptions() {

        List<Option> optionList = new ArrayList<>();

        optionList.add(new Option("o", "output", true, "output path"));
        optionList.add(new Option("simulate-pure-aloha", "simulate-pure-aloha", false, "simulate pure aloha scenario"));
        optionList.add(new Option("simulate-with-ca", "simulate-with-coverage-awareness", false, "simulate aloha with coverage awareness"));
        optionList.add(new Option("simulate-with-ca-nr", "simulate-with-coverage-awareness-nr", false, "simulate aloha with coverage awareness with no duty cycle restrictions"));
        optionList.add(new Option("cov-cfg", "coverage-configs", true, "configurations file path for constellation coverage"));
        optionList.add(new Option("s", "satellites", true, "satellites file"));
        optionList.add(new Option("n", "nodes", true, "nodes steps in the format start,step,end"));
        optionList.add(new Option("toa", "time-on-air", true, "time on air in seconds"));
        optionList.add(new Option("iat", "inter-arrival-time", true, "inter arrival time in seconds"));
        optionList.add(new Option("fgr", "frame-generation-rate", true, "frame generation rate in packets per second"));
        optionList.add(new Option("dc", "duty-cycle", true, "duty cycle in percentage [0 - 100]"));
        optionList.add(new Option("runs", "n-of-runs", true, "number of runs to average"));
        optionList.add(new Option("ts", "time-step", true, "propagator time step in seconds"));
        optionList.add(new Option("ss", "sample-step", true, "sample step in seconds"));
        optionList.add(new Option("vt", "visibility-th", true, "visibility threshold in degrees [0 - 89]"));
        optionList.add(new Option("start", "start-date", true, "start date in ISO 8601 format"));
        optionList.add(new Option("end", "end-date", true, "end date in ISO 8601 format"));
        optionList.add(new Option("roi", "deployment-area", true, "deployment area " +
                "in [center-lat,center-lon,radius,segments] format in degrees, or a file path with coordinates"));
        optionList.add(new Option("save-intervals", "save-intervals", false, "save file with access intervals"));
        optionList.add(new Option("save-datasets", "save-analysis-datasets", false, "save file with analysis datasets"));
        optionList.add(new Option("save-destinations", "save-destinations", false, "save file with destinations"));
        optionList.add(new Option("save-transmissions", "save-transmissions", false, "save file with transmissions"));

        optionList.get(0).setRequired(true);

        Options options = new Options();

        // Add options to options
        optionList.forEach(options::addOption);

        return options;

    }

    private static int parseIntFromCLI(String numberStr) {
        int number = 1;
        if (numberStr != null) {
            try {
                number = Integer.parseInt(numberStr);
            } catch (NumberFormatException e) {
                System.out.println("Error: argument must be a valid integer.");
                System.exit(1);
            }
        }
        return number;
    }

    private static double parseDoubleFromCLI(String numberStr) {
        double number = 1.0;  // Default value if not provided
        if (numberStr != null) {
            try {
                number = Double.parseDouble(numberStr);
            } catch (NumberFormatException e) {
                System.out.println("Error: argument must be a valid double.");
                System.exit(1);
            }
        }
        return number;
    }

}

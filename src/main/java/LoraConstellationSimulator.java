import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.geometry.GeographicTools;
import constellation.tools.math.TimedMetricsRecord;
import constellation.tools.utilities.AppConfig;
import constellation.tools.utilities.FileUtils;
import constellation.tools.utilities.TimeUtils;
import org.apache.commons.cli.*;
import org.apache.commons.configuration2.ex.ConfigurationException;

import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line entry point of the LoRa direct-to-satellite network simulator.
 *
 * <p>For every number of end devices (EDs) in the requested sweep the program
 * <ol>
 *   <li>computes the constellation coverage time series over a spherical-cap region of interest (ROI)
 *       with {@code constellation-toolkit} (written to {@code <output>/coverage_<hash>.csv});</li>
 *   <li>deploys the EDs uniformly at random inside the ROI, computes ED-to-satellite access intervals
 *       with Orekit, generates uplink traffic under one of three channel-access policies and detects
 *       collisions at each satellite gateway;</li>
 *   <li>repeats step 2 {@code -runs} times and stores the per-sample statistics of every run in
 *       {@code <output>/<N>ED_<sampleStep>ms_<ToA>ms_<a>km.json}.</li>
 * </ol>
 *
 * <p>Policies: {@code -simulate-pure-aloha} (duty-cycle-limited pure ALOHA),
 * {@code -simulate-with-ca} (coverage-aware back-off, never shorter than the duty-cycle silent time) and
 * {@code -simulate-with-ca-nr} (coverage-aware back-off without the duty-cycle lower bound).
 *
 * <p>Important: the coverage computation takes its start/end dates, time step and visibility threshold
 * from the {@code -cov-cfg} properties file, while the traffic simulation takes them from the command
 * line. Both must describe the same scenario. See README.md.
 */
public class LoraConstellationSimulator {

    private static final Options options = configureOptions();
    static String simulationHash = null;

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
            return;
        }

        String outputPath = cmd.getOptionValue("o");
        if (!outputPath.endsWith("/") && !outputPath.endsWith("\\")) {
            outputPath = outputPath + "/";
        }
        try {
            Files.createDirectories(Paths.get(outputPath));
        } catch (IOException e) {
            System.err.println("Could not create output directory " + outputPath + ": " + e.getMessage());
            System.exit(1);
        }
        String startDate = cmd.hasOption("start") ? cmd.getOptionValue("start") : "2025-01-01T16:00:00.000";
        String endDate = cmd.hasOption("end") ? cmd.getOptionValue("end") : "2025-01-01T16:30:00.000";
        String satellitePath = cmd.hasOption("satellites") ? cmd.getOptionValue("satellites") : (outputPath + "satellites.csv");
        String configurationsPath = cmd.hasOption("cov-cfg") ? cmd.getOptionValue("cov-cfg") : (outputPath + "config.d3co.properties");

        // Orekit configuration file
        AppConfig appConfig;
        String orekitDataPath = "orekit-data";
        try {
            appConfig = new AppConfig(configurationsPath);
            orekitDataPath = appConfig.orekitPath();
        } catch (ConfigurationException e) {
            System.err.println("Error loading configuration file: " + configurationsPath
                    + " (pass it with -cov-cfg; see config/coverage.template.properties)");
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error initializing application configuration.");
            e.printStackTrace();
            System.exit(1);
        }

        /* Default values (used when the corresponding CLI option is absent) */
        double timeOnAir = 0.1;                 // [s]
        double averageInterArrivalTime = 0.1;   // [s]
        double frameGenerationRate;
        double dutyCyclePercentage = 1.0;
        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;
        int nOfRuns = 10;
        double propagatorTimeStepSeconds = 15.0;
        double visibilityThresholdDegrees = 20.0;
        double sampleStepSeconds = 15.0;
        double centerLatDegrees = 0.0;
        double centerLonDegrees = 0.0;
        double roiSphericalRadius = 1.0;
        int roiSegments = 50;

        boolean saveAccessIntervals = cmd.hasOption("save-intervals");
        boolean saveAnalysisDatasets = cmd.hasOption("save-analysis-datasets");
        boolean saveDestinations = cmd.hasOption("save-destinations");
        boolean saveTransmissions = cmd.hasOption("save-transmissions");

        if (cmd.hasOption("start"))
            startDate = cmd.getOptionValue("start");

        if (cmd.hasOption("end"))
            endDate = cmd.getOptionValue("end");

        if (cmd.hasOption("time-on-air")) {
            timeOnAir = parseDoubleFromCLI(cmd.getOptionValue("toa"));
        }

        if (cmd.hasOption("iat"))
            averageInterArrivalTime = parseDoubleFromCLI(cmd.getOptionValue("iat"));

        // Normalised offered load per device, G = ToA / IAT (dimensionless). Only used for the
        // coverage-aware back-off below and recorded in simulation-parameters.json; the traffic
        // generator derives its own value in NetworkSimulator#setTimeOnAirAndSampleStep.
        frameGenerationRate = timeOnAir / averageInterArrivalTime;

        if (cmd.hasOption("frame-generation-rate"))
            frameGenerationRate = parseDoubleFromCLI(cmd.getOptionValue("fgr"));

        if (cmd.hasOption("dc"))
            dutyCyclePercentage = parseDoubleFromCLI(cmd.getOptionValue("dc"));

        if (cmd.hasOption("nodes")) {
            String[] nodes = cmd.getOptionValue("nodes").split(",");
            stepNodes = 1;
            initialNodes = Integer.parseInt(nodes[0]);
            maxNodes = Integer.parseInt(nodes[1]);
            if (nodes.length > 2) {
                stepNodes = Integer.parseInt(nodes[2]);
            }
        }

        if (cmd.hasOption("roi")) {
            String[] roiParams = cmd.getOptionValue("roi").split(",");
            centerLatDegrees = parseDoubleFromCLI(roiParams[0]);
            centerLonDegrees = parseDoubleFromCLI(roiParams[1]);
            roiSphericalRadius = parseDoubleFromCLI(roiParams[2]);
            roiSegments = parseIntFromCLI(roiParams[3]);
        }

        if (cmd.hasOption("vt"))
            visibilityThresholdDegrees = parseDoubleFromCLI(cmd.getOptionValue("vt"));

        if (cmd.hasOption("runs"))
            nOfRuns = parseIntFromCLI(cmd.getOptionValue("runs"));

        if (cmd.hasOption("sample-step"))
            sampleStepSeconds = parseDoubleFromCLI(cmd.getOptionValue("sample-step"));

        if (cmd.hasOption("time-step"))
            propagatorTimeStepSeconds = parseDoubleFromCLI(cmd.getOptionValue("time-step"));

        if (!cmd.hasOption("simulate-pure-aloha") && !cmd.hasOption("simulate-with-ca")
                && !cmd.hasOption("simulate-with-ca-nr")) {
            System.err.println("No policy selected: pass one of -simulate-pure-aloha, "
                    + "-simulate-with-ca or -simulate-with-ca-nr.");
            formatter.printHelp("LoraConstellationSimulator", options);
            System.exit(1);
        }

        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitePath);

        // LoRa Simulation
        NetworkSimulator networkSimulator = new NetworkSimulator(outputPath, orekitDataPath);
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

        Log.info("Network Simulator Configuration: ");
        Log.info(networkSimulator.toString());

        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer(configurationsPath);
        List<double[]> ROI = GeographicTools.computeSphericalCap(centerLatDegrees, centerLonDegrees, roiSphericalRadius, roiSegments);

        GeographicTools geographicTools = new GeographicTools();
        double roiSurface = geographicTools.computeNonEuclideanSurface(ROI);

        constellationCoverageComputer.setOutputPath(outputPath);
        constellationCoverageComputer.setSatelliteList(satelliteList);
        constellationCoverageComputer.setROI(ROI);

        // Save parameters used in this simulation
        saveSimulationParameters(outputPath,"simulation-parameters.json",
                startDate,
                endDate,
                propagatorTimeStepSeconds,
                visibilityThresholdDegrees,
                satellitePath,
                roiSurface,
                configurationsPath,
                initialNodes,
                stepNodes,
                maxNodes,
                timeOnAir,
                averageInterArrivalTime,
                frameGenerationRate,
                dutyCyclePercentage,
                nOfRuns,
                sampleStepSeconds,
                centerLatDegrees,
                centerLonDegrees,
                roiSphericalRadius,
                roiSegments,
                saveAccessIntervals,
                saveAnalysisDatasets,
                saveDestinations,
                saveTransmissions,
                satelliteList);

        Log.info("Running constellation coverage ... ");
        constellationCoverageComputer.run();
        simulationHash = constellationCoverageComputer.getSimulationHash();
        Log.info("... done!");

        List<TimedMetricsRecord> coverageData = constellationCoverageComputer.getConstellationCoverageTimeSeries();
        List<TimedMetricsRecord> backOffTimeSeries = null;

        Log.info("Minimum DC silent time " + ((100.0 - dutyCyclePercentage) * timeOnAir) + " s.");

        final double finalTimeOnAir = timeOnAir;

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {

            networkSimulator.setNumberOfNodes(nodes);

            double deviceDensity = nodes / roiSurface;
            String finalStartDate = startDate;
            double finalFrameGenerationRate = frameGenerationRate;

            if (cmd.hasOption("simulate-with-ca") || cmd.hasOption("simulate-with-ca-nr")) {
                backOffTimeSeries = coverageData.stream().map(coverageMetric -> {
                    TimedMetricsRecord backOffTimeMetric = new TimedMetricsRecord(coverageMetric.getTime() + TimeUtils.stamp2unix(finalStartDate), coverageMetric.getMetrics().length);
                    for (int i = 0; i < coverageMetric.getMetrics().length; i++) {
                        // Coverage-aware back-off [s] for devices seen by (i + 1) satellites:
                        // T_bo = (2 * N_ED * c_i - 1/G - 1) * ToA, clipped at 0, where c_i is metric i of
                        // ConstellationCoverageComputer#getConstellationCoverageTimeSeries(). The analytical
                        // model (scripts/lora_model.py) uses the covered FRACTION of the ROI here.
                        double backOffTime = (2 * deviceDensity * roiSurface * coverageMetric.getMetrics()[i] - 1 / finalFrameGenerationRate - 1) * finalTimeOnAir;
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
            } else if (cmd.hasOption("simulate-with-ca-nr")) { // nr : no restrictions
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
        optionList.add(new Option("n", "nodes", true, "number of end devices as start,end[,step] (inclusive end, default step 1)"));
        optionList.add(new Option("toa", "time-on-air", true, "time on air in seconds"));
        optionList.add(new Option("iat", "inter-arrival-time", true, "inter arrival time in seconds"));
        optionList.add(new Option("fgr", "frame-generation-rate", true, "normalised offered load G = ToA/IAT used by the coverage-aware back-off (default: derived from -toa and -iat)"));
        optionList.add(new Option("dc", "duty-cycle-percentage", true, "duty cycle in percentage [0 - 100]"));
        optionList.add(new Option("runs", "n-of-runs", true, "number of runs to average"));
        optionList.add(new Option("ts", "time-step", true, "propagator time step in seconds"));
        optionList.add(new Option("ss", "sample-step", true, "statistics sampling step in seconds"));
        optionList.add(new Option("vt", "visibility-th", true, "visibility threshold in degrees [0 - 89]"));
        optionList.add(new Option("start", "start-date", true, "start date in ISO 8601 format (UTC), e.g. 2025-01-01T16:00:00.000"));
        optionList.add(new Option("end", "end-date", true, "end date in ISO 8601 format"));
        optionList.add(new Option("roi", "deployment-area", true, "deployment area (spherical cap) " +
                "as center-lat,center-lon,radius,segments (angles in degrees)"));
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

    private static void saveSimulationParameters(String filePath, String fileName,
                                         String startDate,
                                         String endDate,
                                         double propagatorTimeStepSeconds,
                                         double visibilityThresholdDegrees,
                                         String satellitePath,
                                         double roiSurface,
                                         String configurationsPath,
                                         int initialNodes,
                                         int stepNodes,
                                         int maxNodes,
                                         double timeOnAir,
                                         double averageInterArrivalTime,
                                         double frameGenerationRate,
                                         double dutyCyclePercentage,
                                         int nOfRuns,
                                         double sampleStepSeconds,
                                         double centerLatDegrees,
                                         double centerLonDegrees,
                                         double roiSphericalRadius,
                                         int roiSegments,
                                         boolean saveAccessIntervals,
                                         boolean saveAnalysisDatasets,
                                         boolean saveDestinations,
                                         boolean saveTransmissions,
                                         List<Satellite> satelliteList) {
        List<String> lines = new ArrayList<>();
        lines.add("{");
        lines.add("  \"start_date\": \"" + startDate + "\",");
        lines.add("  \"end_date\": \"" + endDate + "\",");
        lines.add("  \"propagator_time_step_seconds\": " + propagatorTimeStepSeconds + ",");
        lines.add("  \"visibility_threshold_degrees\": " + visibilityThresholdDegrees + ",");
        lines.add("  \"satellites_file\": \"" + satellitePath + "\",");
        lines.add("  \"roi_surface\": \"" + roiSurface + "\",");
        lines.add("  \"coverage_configurations_file\": \"" + configurationsPath + "\",");
        lines.add("  \"initial_nodes\": " + initialNodes + ",");
        lines.add("  \"step_nodes\": " + stepNodes + ",");
        lines.add("  \"max_nodes\": " + maxNodes + ",");
        lines.add("  \"time_on_air_seconds\": " + timeOnAir + ",");
        lines.add("  \"average_inter_arrival_time_seconds\": " + averageInterArrivalTime + ",");
        lines.add("  \"frame_generation_rate_fps\": " + frameGenerationRate + ",");
        lines.add("  \"duty_cycle_percentage\": " + dutyCyclePercentage + ",");
        lines.add("  \"number_of_runs\": " + nOfRuns + ",");
        lines.add("  \"sample_step_seconds\": " + sampleStepSeconds + ",");
        lines.add("  \"deployment_area_center_lat_degrees\": " + centerLatDegrees + ",");
        lines.add("  \"deployment_area_center_lon_degrees\": " + centerLonDegrees + ",");
        lines.add("  \"deployment_area_radius_degrees\": " + roiSphericalRadius + ",");
        lines.add("  \"deployment_area_segments\": " + roiSegments + ",");
        lines.add("  \"save_access_intervals\": " + saveAccessIntervals + ",");
        lines.add("  \"save_analysis_datasets\": " + saveAnalysisDatasets + ",");
        lines.add("  \"save_destinations\": " + saveDestinations + ",");
        lines.add("  \"save_transmissions\": " + saveTransmissions + ",");
        lines.add("  \"satellites\": [");
        for (int i = 0; i < satelliteList.size(); i++) {
            String comma = (i < satelliteList.size() - 1) ? "," : "";
            lines.add(" \t \"" + satelliteList.get(i).getElements().toString() + "\"" + comma);
        }
        lines.add("\t ]");
        lines.add("}");

        FileUtils fUtils = new FileUtils(filePath);

        fUtils.saveAs(lines, fileName);

    }

}

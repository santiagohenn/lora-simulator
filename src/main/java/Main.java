import constellation.tools.ConstellationCoverageComputer;
import constellation.tools.geometry.GeographicTools;
import constellation.tools.math.TimedMetricsRecord;
import constellation.tools.utilities.TimeUtils;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;
import satellite.tools.utils.Utils;

import java.util.List;

public class Main {

    public static final double EARTH_RADIUS_M = 6371000.0;

    public static void main(String[] args) {

        String outputPath = "C:\\mg_data_test_550\\";
//        simulatePureAloha(outputPath);
        outputPath = "C:\\mg_data_test_550_wca\\";
        simulateCoverageAwareness(outputPath);
        outputPath = "C:\\mg_data_test_550_wca_wr\\";
        simulateCoverageAwarenessWithoutRestrictions(outputPath);

    }

    public static void simulateCoverageAwarenessWithoutRestrictions(String outputPath) {

        String satellitePath = outputPath + "satellites.csv";
        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitePath);

        String startDate = "2025-01-01T16:00:00.000";
        String endDate = "2025-01-01T16:30:00.000";
        double timeOnAir = 0.5;
        double averageInterArrivalTime = 0.1;
        double frameGenerationRate = timeOnAir / averageInterArrivalTime;
        double dutyCyclePercentage = 1.0;
        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;
        int nOfRuns = 150;
        // double timeOnAirMS = 500;
        // double sampleStepMS = 15000;

        // LoRa Simulation
        NetworkSimulator networkSimulator = new NetworkSimulator(outputPath);
        networkSimulator.setSaveActions(false, true, false, false);

        // Scenario parameters
        networkSimulator.setScenarioParameters(
                startDate,
                endDate,
                5.0,
                20
        );

        networkSimulator.setSatellites(satelliteList);
        networkSimulator.setDeploymentArea(-21.0, -58.0, 7.1946);
        networkSimulator.setNetworkParameters(
                timeOnAir * 1000,
                dutyCyclePercentage,
                averageInterArrivalTime,
                15000);
        networkSimulator.setNumberOfRuns(nOfRuns);

        Log.info(networkSimulator.toString());

        Log.info("Running constellation coverage ... ");
        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer("C:\\Users\\Santi\\Desktop\\Doctorado\\d3co_analysis\\config.d3co.properties");
        List<double[]> ROI = GeographicTools.computeSphericalCap(-21.0, -58.0, 7.1946,150);

        GeographicTools geographicTools = new GeographicTools();
        double roiSurface = geographicTools.computeNonEuclideanSurface(ROI);
        Log.info("ROI surface: " + roiSurface);

        constellationCoverageComputer.setOutputPath(outputPath);
        constellationCoverageComputer.setSatelliteList(satelliteList);
        constellationCoverageComputer.setROI(ROI);
        constellationCoverageComputer.run();
        Log.info("... done!");

        List<TimedMetricsRecord> coverageData = constellationCoverageComputer.getConstellationCoverageTimeSeries();
        List<TimedMetricsRecord> backOffTimeSeries;

        // coverageData.forEach(System.out::println);

        // Run!
        //simulator.setTimeOnAirAndSampleStep(timeOnAirMS, sampleStepMS);

        Log.info("Minimum DC silent time " + ((100 - dutyCyclePercentage) * timeOnAir) + " S.");

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {

            networkSimulator.setNumberOfNodes(nodes);

            double deviceDensity = nodes / roiSurface;

            backOffTimeSeries = coverageData.stream().map(coverageMetric -> {
                TimedMetricsRecord backOffTimeMetric = new TimedMetricsRecord(coverageMetric.getTime() + TimeUtils.stamp2unix(startDate), coverageMetric.getMetrics().length);
                for (int i = 0; i < coverageMetric.getMetrics().length; i++) {
                    double backOffTime = (2 * deviceDensity * roiSurface * coverageMetric.getMetrics()[i] - 1 / frameGenerationRate - 1) * timeOnAir;
                    backOffTimeMetric.addMetric(i, Math.max(backOffTime, 0.0));
                }
                return backOffTimeMetric;
            }).toList();

            Log.info("Running with " + nodes + " nodes | avg IAT (s): "
                    + networkSimulator.getAverageInterArrivalTime());
            networkSimulator.run(backOffTimeSeries, false);
        }

    }


    public static void simulateCoverageAwareness(String outputPath) {

        String satellitePath = outputPath + "satellites.csv";
        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitePath);

        String startDate = "2025-01-01T16:00:00.000";
        String endDate = "2025-01-01T16:30:00.000";
        double timeOnAir = 0.5;
        double averageInterArrivalTime = 0.1;
        double frameGenerationRate = timeOnAir / averageInterArrivalTime;
        double dutyCyclePercentage = 1.0;
        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;
        int nOfRuns = 150;
        // double timeOnAirMS = 500;
        // double sampleStepMS = 15000;

        // LoRa Simulation
        NetworkSimulator networkSimulator = new NetworkSimulator(outputPath);
        networkSimulator.setSaveActions(false, true, false, false);

        // Scenario parameters
        networkSimulator.setScenarioParameters(
                startDate,
                endDate,
                5.0,
                20
        );

        networkSimulator.setSatellites(satelliteList);
        networkSimulator.setDeploymentArea(-21.0, -58.0, 7.1946);
        networkSimulator.setNetworkParameters(
                timeOnAir * 1000,
                dutyCyclePercentage,
                averageInterArrivalTime,
                15000);
        networkSimulator.setNumberOfRuns(nOfRuns);

        Log.info(networkSimulator.toString());

        Log.info("Running constellation coverage ... ");
        ConstellationCoverageComputer constellationCoverageComputer = new ConstellationCoverageComputer("C:\\Users\\Santi\\Desktop\\Doctorado\\d3co_analysis\\config.d3co.properties");
        List<double[]> ROI = GeographicTools.computeSphericalCap(-21.0, -58.0, 7.1946,150);

        GeographicTools geographicTools = new GeographicTools();
        double roiSurface = geographicTools.computeNonEuclideanSurface(ROI);
        Log.info("ROI surface: " + roiSurface);

        constellationCoverageComputer.setOutputPath(outputPath);
        constellationCoverageComputer.setSatelliteList(satelliteList);
        constellationCoverageComputer.setROI(ROI);
        constellationCoverageComputer.run();
        Log.info("... done!");

        List<TimedMetricsRecord> coverageData = constellationCoverageComputer.getConstellationCoverageTimeSeries();
        List<TimedMetricsRecord> backOffTimeSeries;

        // coverageData.forEach(System.out::println);

        // Run!
        //simulator.setTimeOnAirAndSampleStep(timeOnAirMS, sampleStepMS);

        Log.info("Minimum DC silent time " + ((100 - dutyCyclePercentage) * timeOnAir) + " S.");

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {

            networkSimulator.setNumberOfNodes(nodes);

            double deviceDensity = nodes / roiSurface;

            backOffTimeSeries = coverageData.stream().map(coverageMetric -> {
                TimedMetricsRecord backOffTimeMetric = new TimedMetricsRecord(coverageMetric.getTime() + TimeUtils.stamp2unix(startDate), coverageMetric.getMetrics().length);
                for (int i = 0; i < coverageMetric.getMetrics().length; i++) {
                    double backOffTime = (2 * deviceDensity * roiSurface * coverageMetric.getMetrics()[i] - 1 / frameGenerationRate - 1) * timeOnAir;
                    backOffTimeMetric.addMetric(i, Math.max(backOffTime, 0.0));
                }
                return backOffTimeMetric;
            }).toList();

            Log.info("Running with " + nodes + " nodes | avg IAT (s): "
                    + networkSimulator.getAverageInterArrivalTime());
            networkSimulator.run(backOffTimeSeries, true);
        }

    }

    public static void simulatePureAloha(String outputPath) {

        NetworkSimulator networkSimulator = new NetworkSimulator(outputPath);

        networkSimulator.setSaveActions(false, true, false, false);

//        String satellitePath = "C:/Projects/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";
        String satellitePath = "C:/Projects/matlab-snippets/throughput_pipeline/case_study_south_america/scenario_data/satellites.csv";
        List<Satellite> satelliteList = Utils.satellitesFromFile(satellitePath);

        // Scenario parameters
        networkSimulator.setScenarioParameters(
                "2025-01-01T16:00:00.000",
                "2025-01-01T16:30:00.000",
                5.0,
                20
        );

        networkSimulator.setSatellites(satelliteList);
        networkSimulator.setDeploymentArea(-21.0, -58.0, 7.1946);
//        simulator.setDeploymentArea(50, 5, 3.1442);

        // Network parameters
        networkSimulator.setNetworkParameters(
                500,
                1,
                0.1,
                15000);

        networkSimulator.setNumberOfRuns(150);
        Log.info(networkSimulator.toString());

        int initialNodes = 10;
        int stepNodes = 10;
        int maxNodes = 130;

        double[] toaSteps = new double[]{500};
        double[] sampleSteps = new double[]{15000};
        double[] heightStepsKm = new double[]{1000};

        for (int nodes = initialNodes; nodes <= maxNodes; nodes += stepNodes) {
            networkSimulator.setNumberOfNodes(nodes);
            Log.info("Running with " + nodes + " nodes | avg IAT (s): "
                    + networkSimulator.getAverageInterArrivalTime());
            for (double height : heightStepsKm) {
                for (double sampleStepMS : sampleSteps) {
                    for (double timeOnAirMS : toaSteps) {
                        satelliteList.get(0).getElements().setSemiMajorAxis(EARTH_RADIUS_M + (1000.0 * height));
                        networkSimulator.setSatellites(satelliteList);
                        networkSimulator.setTimeOnAirAndSampleStep(timeOnAirMS, sampleStepMS);
                        networkSimulator.run();
                    }
                }
            }
        }

    }

}

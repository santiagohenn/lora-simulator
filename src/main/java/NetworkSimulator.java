import generators.PositionsFactory;
import generators.ReportFactory;
import network.Network;
import network.NetworkAnalyzer;
import satellite.tools.assets.entities.Position;
import satellite.tools.assets.entities.Satellite;
import satellite.tools.utils.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NetworkSimulator {

    Network network;
    NetworkAnalyzer networkAnalyzer;
    ReportFactory rf;
    PositionsFactory positionsFactory;

    // Simulator parameters
    private long timer;
    private boolean saveTransmissions = false;
    private boolean saveAccessIntervals = false;
    private boolean saveAnalysisDatasets = true;
    private boolean saveDestinations = false;
    private int numberOfRuns;

    // Scenario parameters
    private List<Satellite> satellites;
    private String startTime;
    private String endTime;
    private double propagatorTimeStepSeconds;
    private double visibilityThresholdDegrees;
    private int numberOfNodes;
    private double centerLatDegrees;
    private double centerLongitudeDegrees;
    private double radiusDegrees;

    // Network parameters
    private double timeOnAirMS;
    private double dutyCyclePercentage;
    private double averageInterArrivalTime;
    private double sampleStepMS;

    // Debug parameters
    private boolean fakeContinuousCoverage = false;
    private boolean usePoissonProcessForNodes = false;

    public NetworkSimulator(String outputPath) {
        network = new Network();
        networkAnalyzer = new NetworkAnalyzer();
        rf = new ReportFactory(outputPath);
    }

    public void setNetworkParameters(double timeOnAirMS, double dutyCyclePercentage, double averageInterArrivalTime, double sampleStepMS) {
        this.dutyCyclePercentage = dutyCyclePercentage;
        this.averageInterArrivalTime = averageInterArrivalTime;
        setTimeOnAirAndSampleStep(timeOnAirMS, sampleStepMS);
    }

    public void setScenarioParameters(String startTime, String endTime, double propagatorTimeStepSeconds, double visibilityThresholdDegrees) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.network.setSimulationInterval(startTime, endTime);
        this.propagatorTimeStepSeconds = propagatorTimeStepSeconds;
        this.visibilityThresholdDegrees = visibilityThresholdDegrees;
        this.network.setVisibilityThresholdDegrees(visibilityThresholdDegrees);
        this.network.setTimeStepSeconds(propagatorTimeStepSeconds);
    }

    public void setTimeOnAirAndSampleStep(double timeOnAirMS, double sampleStepMS) {
        this.timeOnAirMS = timeOnAirMS;
        this.sampleStepMS = sampleStepMS;
        this.networkAnalyzer.setSampleStepMS(sampleStepMS);
        double avgFrameGenerationRate = (timeOnAirMS / 1000.0) / averageInterArrivalTime;
        this.network.setParams(timeOnAirMS, avgFrameGenerationRate);
    }

    public void setDeploymentArea(double centerLatDegrees, double centerLongitudeDegrees, double radiusDegrees) {
        this.centerLatDegrees = centerLatDegrees;
        this.centerLongitudeDegrees = centerLongitudeDegrees;
        this.radiusDegrees = radiusDegrees;
        positionsFactory = new PositionsFactory(numberOfNodes, centerLatDegrees, centerLongitudeDegrees, radiusDegrees);
    }

    public void setSatellites(List<Satellite> satellites) {
        this.satellites = satellites;
        this.network.setSatellites(satellites);
    }

    private void deployNodes() {
        positionsFactory.setNOfNodes(numberOfNodes);
        ArrayList<Position> positions;
        if (usePoissonProcessForNodes) {
            positions = positionsFactory.generatePoissonPointProcessPositions(numberOfNodes);
        } else {
            positions = positionsFactory.generateUniformRandomPositions();
        }
        network.setEarthPositions(positions);
        Log.debug(numberOfNodes + " nodes deployed.");
    }

    private void simulateTraffic() {
        Log.debug("Simulating traffic ! " + tic());
        if (fakeContinuousCoverage) {
            network.simulateContinuousCoverage();
            Log.debug("Traffic simulation took " + toc() + " [ms.]");
            return;
        }
        network.simulateTraffic();
        Log.debug("Traffic simulation took " + toc() + " [ms.]");
    }

    private void analyzeTraffic() {
        Log.debug("Analyzing traffic ! " + tic());
        networkAnalyzer.analyzeTraffic(network);
        Log.debug("Traffic Analysis took " + toc() + " [ms.]");
    }

    public void run() {

        List<List<double[]>> analysisDataSets = new ArrayList<>();

        printProgress(999);
        for (int run = 0; run < numberOfRuns; run++) {
            deployNodes();
            simulateTraffic();
            analyzeTraffic();
            analysisDataSets.add(networkAnalyzer.getDataPoints());
            printProgress(((double) run) * 100.0 / (double) numberOfRuns);
        }
        printProgress(100);

        if (saveTransmissions) {
            Log.debug("Saving transmissions dataset for " + numberOfNodes + " nodes");
            rf.saveAsJSON(network.getTransmissions(), "TX_" + numberOfNodes + "N");
        }

        if (saveAccessIntervals) {
            Log.debug("Saving access intervals dataset for " + numberOfNodes + " nodes");
            rf.saveAsJSON(network.getAccessIntervals()
                    .stream().map(interval -> new long[]{interval.getFirstFrom(),
                            interval.getStart(), interval.getEnd(), interval.getToAssets().size()})
                    .collect(Collectors.toList()), "intervals_" + numberOfNodes + "N");
        }

        if (saveAnalysisDatasets) {
            Log.debug("Saving analysis datasets for " + numberOfNodes + " nodes");
            rf.saveAsJSON(analysisDataSets, numberOfNodes + "ED_" + ((int) sampleStepMS) + "ms_"
                    + ((int) timeOnAirMS) + "ms_"
                    + ((int) (satellites.get(0).getElements().getSemiMajorAxis() / 1000)) + "km");
        }

        if (saveDestinations) {
            Log.debug("Saving destinations datasets for " + numberOfNodes + " nodes");
            rf.saveAsJSON(networkAnalyzer.getDestinations(), "destinations_" + numberOfNodes + "N");
        }

    }

    public Network getNetwork() {
        return network;
    }

    public NetworkAnalyzer getNetworkAnalyzer() {
        return networkAnalyzer;
    }

    public void setSaveActions(boolean saveAccessIntervals, boolean saveAnalysisDatasets, boolean saveDestinations, boolean saveTransmissions) {
        this.saveAccessIntervals = saveAccessIntervals;
        this.saveAnalysisDatasets = saveAnalysisDatasets;
        this.saveDestinations = saveDestinations;
        this.saveTransmissions = saveTransmissions;
    }

    public void setNumberOfRuns(int numberOfRuns) {
        this.numberOfRuns = numberOfRuns;
    }

    public void setNumberOfNodes(int numberOfNodes) {
        this.numberOfNodes = numberOfNodes;
    }

    public double getAverageInterArrivalTime() {
        return averageInterArrivalTime;
    }

    public long tic() {
        timer = System.currentTimeMillis();
        return timer;
    }

    public long toc() {
        return System.currentTimeMillis() - timer;
    }

    public void printProgress(double percentage) {

        int progress = (int) percentage;

        if (percentage == 999) {
            System.out.print("Simulating | ToA = " + timeOnAirMS + " [ms] | sample step = "
                    + sampleStepMS + " [ms] | " + numberOfRuns + " runs >       ");
            return;
        }
        System.out.print("\b\b\b\b\b\b");
        if (progress % 2 == 0) {
            System.out.print("█");
        }
        System.out.printf(" %03d", progress);
        System.out.print(" %");
        if (percentage == 100) {
            System.out.println();
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Simulator Parameters:\n");
        sb.append("Timer: ").append(timer).append("\n");
        sb.append("Save Transmissions: ").append(saveTransmissions).append("\n");
        sb.append("Save Access Intervals: ").append(saveAccessIntervals).append("\n");
        sb.append("Save Analysis Datasets: ").append(saveAnalysisDatasets).append("\n");
        sb.append("Save Destinations: ").append(saveDestinations).append("\n");
        sb.append("Number of Runs: ").append(numberOfRuns).append("\n\n");

        sb.append("Scenario Parameters:\n");
        sb.append("Satellites: ").append("\n");
        for (Satellite satellite : satellites) {
            sb.append('\t').append(satellite.getElements().toString()).append("\n");
        }
        sb.append("Start Time: ").append(startTime).append("\n");
        sb.append("End Time: ").append(endTime).append("\n");
        sb.append("Propagator Time Step Seconds: ").append(propagatorTimeStepSeconds).append("\n");
        sb.append("Visibility Threshold Degrees: ").append(visibilityThresholdDegrees).append("\n");
        sb.append("Number of Nodes: ").append(numberOfNodes).append("\n");
        sb.append("Center Latitude Degrees: ").append(centerLatDegrees).append("\n");
        sb.append("Center Longitude Degrees: ").append(centerLongitudeDegrees).append("\n");
        sb.append("Radius Degrees: ").append(radiusDegrees).append("\n\n");

        sb.append("Network Parameters:\n");
        sb.append("Time On Air (ms): ").append(timeOnAirMS).append("\n");
        sb.append("Duty Cycle Percentage: ").append(dutyCyclePercentage).append("\n");
        sb.append("Average Inter-Arrival Time: ").append(averageInterArrivalTime).append("\n");
        sb.append("Sample Step (ms): ").append(sampleStepMS).append("\n\n");

        sb.append("Debug Parameters:\n");
        sb.append("Fake Continuous Coverage: ").append(fakeContinuousCoverage).append("\n");
        sb.append("Use Poisson Process for Nodes: ").append(usePoissonProcessForNodes).append("\n");

        return sb.toString();
    }
}

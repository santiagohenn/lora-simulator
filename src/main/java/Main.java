public class Main {

    public static void main(String[] args) {

        // String[] options = {"--simulate-pure-aloha", "--save-analysis-datasets",
        //         "-o", "C:/projects/results/pure-aloha/",
        //         "-s", "./inputs/satellites.csv",
        //         "-cov-cfg", "./inputs/config.d3co.properties",
        //         "-time-step", "15.0",
        //         "-time-on-air", "0.5",
        //         "-duty-cycle-percentage", "1.0",
        //         "-nodes", "50,100,1000",
        //         "-roi", "-21.0,-58.0,7.1946,150",
        //         "-vt", "20.0",
        //         "-runs", "5"}; 

        // TODO: change nodes to initial,end,step python-like
        String[] options = {"--simulate-pure-aloha", "--save-analysis-datasets",
                "-o", "C:/projects/results/pure-aloha/",
                "-s", "./inputs/satellites.csv",
                "-cov-cfg", "./inputs/config.d3co.properties",
                "-time-step", "15.0",
                "-time-on-air", "0.5",
                "-duty-cycle-percentage", "1.0",
                "-nodes", "50,10,80",
                "-roi", "-21.0,-58.0,7.1946,150",
                "-vt", "20.0",
                "-runs", "20"}; 

        // String[] options = {"--simulate-pure-aloha", "--save-analysis-datasets",
        //         "-o", "D:/results/",
        //         "-s", "./inputs/satellites.csv",
        //         "-cov-cfg", "./inputs/config.d3co.properties",
        //         "-nodes", "10,10,10",
        //         "-roi", "50.0,5,3.1442,150",
        //         "-vt", "20.0",
        //         "-runs", "1"};

        LoraConstellationSimulator.main(options);

        // options = new String[]{"-simulate-with-ca", "--save-analysis-datasets",
        //         "-o", "C:/projects/results/coverage-aware/",
        //         "-s", "./inputs/satellites.csv",
        //         "-cov-cfg", "./inputs/config.d3co.properties",
        //         "-time-step", "15.0",
        //         "-time-on-air", "0.5",
        //         "-duty-cycle-percentage", "1.0",
        //         "-nodes", "10,100,1000",
        //         "-roi", "-21.0,-58.0,7.1946,150",
        //         "-vt", "20.0",
        //         "-runs", "5"}; 

        // LoraConstellationSimulator.main(options);

        // options = new String[]{"-simulate-with-ca-nr", "--save-analysis-datasets",
        //         "-o", "C:/projects/results/coverage-aware-nr/",
        //         "-s", "./inputs/satellites.csv",
        //         "-cov-cfg", "./inputs/config.d3co.properties",
        //         "-time-step", "15.0",
        //         "-time-on-air", "0.5",
        //         "-duty-cycle-percentage", "1.0",
        //         "-nodes", "10,100,1000",
        //         "-roi", "-21.0,-58.0,7.1946,150",
        //         "-vt", "20.0",
        //         "-runs", "5"}; 

        // LoraConstellationSimulator.main(options);


    }

}

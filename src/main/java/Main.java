public class Main {

    public static void main(String[] args) {

        String[] options = {"--simulate-pure-aloha", "--save-analysis-datasets",
                "-o", "D:/results/",
                "-s", "./inputs/satellites.csv",
                "-cov-cfg", "./inputs/config.d3co.properties",
                "-nodes", "10,10,130",
                "-roi", "-21.0,-58.0,7.1946,150",
                "-vt", "20.0",
                "-runs", "500"};

        LoraConstellationSimulator.main(options);
/* 
        options = new String[]{"-simulate-with-ca", "--save-analysis-datasets",
                "-o", "C:/Users/Santi/Desktop/Doctorado/coverage-aware-analysis/coverage-aware/",
                "-s", "C:/mg_data_test_550/satellites.csv",
                "-cov-cfg", "C:/Users/Santi/Desktop/Doctorado/coverage-aware-analysis/config.d3co.properties",
                "-nodes", "10,10,130",
                "-roi", "-21.0,-58.0,7.1946,150",
                "-vt", "20.0"};

        LoraConstellationSimulator.main(options);

        options = new String[]{"-simulate-with-ca-nr", "--save-analysis-datasets",
                "-o", "C:/Users/Santi/Desktop/Doctorado/coverage-aware-analysis/coverage-aware-nr/",
                "-s", "C:/mg_data_test_550/satellites.csv",
                "-cov-cfg", "C:/Users/Santi/Desktop/Doctorado/coverage-aware-analysis/config.d3co.properties",
                "-nodes", "10,10,130",
                "-roi", "-21.0,-58.0,7.1946,150",
                "-vt", "20.0"};

        LoraConstellationSimulator.main(options);

        */

    }

}

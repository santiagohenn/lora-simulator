import java.util.Random;

public class PositionsGenerator {

    int nOfNodes;
    double centerLatDegrees;
    double centerLongitudeDegrees;
    double radiusDegrees;
    double centerLatitudeDegrees = 90.0 - centerLatDegrees;
    double centerLatitudeRadians = Math.toRadians(centerLatitudeDegrees);

    public PositionsGenerator() {

    }

    public void generatePositions() {



        // Generate at radius
        double step = (2 * Math.PI) / (nOfNodes - 1);
        double theta = 0;

        for (int i = 1; i < nOfNodes; i++) {

//            theta += step;
//            double lambdaDegrees = radiusDegrees;

            Random r = new Random();

            theta = (1.0 - r.nextDouble()) * 2 * Math.PI;

            double lambdaDegrees = radiusDegrees * Math.sqrt(1.0 - r.nextDouble());
            double lambdaRadians = Math.toRadians(lambdaDegrees);

            // Compute latitude
            double pointerLatitudeRadians = Math.acos(Math.cos(lambdaRadians) * Math.cos(centerLatitudeRadians)
                    + Math.sin(lambdaRadians) * Math.sin(centerLatitudeRadians) * Math.cos(theta));

            double latitudeDegrees = Math.toDegrees(Math.PI/2 - pointerLatitudeRadians);

            int H = 1;
            double phiMod360 = centerLatitudeDegrees % 360.0;

            if (180.0 <= phiMod360 && phiMod360 < 360.0) {
                H = -1;
            }

            double deltaLongitudeRadians = Math.acos(( Math.cos(lambdaRadians) - Math.cos(pointerLatitudeRadians) * Math.cos(centerLatitudeRadians)) /
            (H * Math.sin(centerLatitudeRadians) * Math.sin(pointerLatitudeRadians))) - (Math.PI/2) * (H - 1);

            double deltaLongitudeDegrees = Math.toDegrees(deltaLongitudeRadians);

            double longitudeDegrees;

            if (theta <= Math.PI) {
                longitudeDegrees = centerLongitudeDegrees - deltaLongitudeDegrees;
            } else {
                longitudeDegrees = centerLongitudeDegrees + deltaLongitudeDegrees;
            }

            if (centerLatitudeDegrees == -90.0) {
                longitudeDegrees = Math.toDegrees(theta);
                latitudeDegrees = -90.0 + lambdaDegrees;
            } else if (centerLatitudeDegrees == 90.0) {
                longitudeDegrees = Math.toDegrees(theta);
                latitudeDegrees = 90.0 - lambdaDegrees;
            }

            if ( theta == 2 * Math.PI && (centerLatitudeDegrees + lambdaDegrees) == 90) {
                latitudeDegrees = 90;
                longitudeDegrees = 0;
            } else if (theta == 2 * Math.PI && (centerLatitudeDegrees + lambdaDegrees) == -90) {
                latitudeDegrees = -90;
                longitudeDegrees = 0;
            }

            while (longitudeDegrees < -180) {
                longitudeDegrees += 360;
            }

            while (longitudeDegrees > 180) {
                longitudeDegrees -= 360;
            }



        }

    }

}

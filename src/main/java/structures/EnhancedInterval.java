package structures;

import satellite.tools.structures.Ephemeris;
import satellite.tools.structures.Interval;

import java.util.ArrayList;
import java.util.List;

public class EnhancedInterval extends Interval {

    List<Ephemeris> ephemeris = new ArrayList<>();

    public EnhancedInterval(Interval interval) {
        super(interval);
    }

    public EnhancedInterval(Interval interval, List<Ephemeris> ephemeris) {
        super(interval);
        this.ephemeris = ephemeris;
    }

    public List<Ephemeris> getEphemeris() {
        return ephemeris;
    }

    public void setEphemeris(List<Ephemeris> ephemeris) {
        this.ephemeris = ephemeris;
    }

}

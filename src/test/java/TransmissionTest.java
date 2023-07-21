import assets.Transmission;
import org.junit.Test;
import satellite.tools.utils.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TransmissionTest {

    @Test
    public void HashCodeTest1() {

        Transmission t1 = new Transmission(123456, 123456, 123456, 0, 0, 1);
        Transmission t2 = new Transmission(123456, 123456, 123456, 0, 1, 1);

        Log.info("tid: " + t1.getTransmissionHash());
        Log.info("tid: " + t2.getTransmissionHash());

        assertEquals(t1, t2);

    }

    @Test
    public void HashCodeTest2() {

        Transmission t1 = new Transmission(123456, 123456, 123456, 0, 0, 1);
        Transmission t2 = new Transmission(123456, 123456, 123456, 1, 0, 1);

        Log.debug("tid: " + t1.getTransmissionHash());
        Log.debug("tid: " + t2.getTransmissionHash());

        assertNotEquals(t1, t2);

    }

    @Test
    public void HashCodeTest3() {

        Transmission t1 = new Transmission(123456, 123456, 123456, 0, 0, 1);
        Transmission t2 = new Transmission(123456, 123456, 123456, 0, 1, 1);
        Transmission t3 = new Transmission(123456, 123456, 123456, 1, 0, 1);

        List<Transmission> transmissionsOnAir = new ArrayList<>();
        transmissionsOnAir.add(t1);
        transmissionsOnAir.add(t2);
        transmissionsOnAir.add(t3);

        System.out.println("t1id: " + t1.getTransmissionHash());
        System.out.println("t2id: " + t2.getTransmissionHash());
        System.out.println("t3id: " + t3.getTransmissionHash());

        assertEquals(2,transmissionsOnAir.stream().distinct().count());
        assertEquals(2,transmissionsOnAir.stream().filter(t -> t.getToId() == 0).count());
        assertEquals(1,transmissionsOnAir.stream().filter(t -> t.getToId() == 1).count());

        List<Transmission> transmissionsOnServer = new ArrayList<>();

        for (int gw = 0; gw < 2; gw++) {

            int finalNgw = gw;
            Iterator<Transmission> transmissionIterator = transmissionsOnAir.stream()
                    .filter(t -> t.getToId() == finalNgw)
                    .sorted(Comparator.comparingDouble(Transmission::getStartTransmissionTimestamp))
                    .iterator();

            // While there are other transmissions
            while (transmissionIterator.hasNext()) {

                // Take the next transmission
                Transmission nextTransmission = transmissionIterator.next();
                transmissionsOnServer.add(nextTransmission);

            }

        }

        assertEquals(1,transmissionsOnAir.stream().filter(t -> t.getToId() == 0).distinct().count());
        assertEquals(1,transmissionsOnAir.stream().filter(t -> t.getToId() == 1).distinct().count());

    }

    @Test
    public void CollisionTest0() {

        Transmission t1 = new Transmission(123456, 123456, 123456, 0, 0, 1);
        Transmission t2 = new Transmission(123456, 123456, 123456, 0, 1, 1);
        Transmission t3 = new Transmission(123456, 123456, 123456, 1, 0, 1);

        List<Transmission> transmissionsOnAir = new ArrayList<>();
        transmissionsOnAir.add(t1);
        transmissionsOnAir.add(t2);
        transmissionsOnAir.add(t3);

        List<Transmission> transmissionsOnServer = new ArrayList<>();

        for (int gw = 0; gw < 2; gw++) {

            int finalNgw = gw;
            Iterator<Transmission> transmissionIterator = transmissionsOnAir.stream()
                    .filter(t -> t.getToId() == finalNgw)
                    .sorted(Comparator.comparingDouble(Transmission::getStartTransmissionTimestamp))
                    .iterator();

            // While there are other transmissions
            while (transmissionIterator.hasNext()) {

                // Take the next transmission
                Transmission nextTransmission = transmissionIterator.next();

                // Assume no collisions
                transmissionsOnServer.add(nextTransmission);

            }

        }

        assertEquals(2,transmissionsOnServer.stream().distinct().count());

    }

}

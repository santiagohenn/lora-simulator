import assets.Transmission;
import org.junit.Test;
import satellite.tools.utils.Log;

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

}

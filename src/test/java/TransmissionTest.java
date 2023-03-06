import assets.Transmission;
import org.junit.Test;
import satellite.tools.utils.Log;

import static org.junit.Assert.assertEquals;

public class TransmissionTest {

    @Test
    public void HashCodeTest() {

        Transmission t1 = new Transmission(123456, 0,1,1);
        Transmission t2 = new Transmission(123456, 0,1,1);

        Log.info("tid: " + t1.getTransmissionID());
        Log.info("tid: " + t2.getTransmissionID());

        assertEquals(t1, t2);

    }

}

package org.linphone;

/**
 * Created by ecroze on 04/05/17.
 */

public class Utils {
    public static void waitUi(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

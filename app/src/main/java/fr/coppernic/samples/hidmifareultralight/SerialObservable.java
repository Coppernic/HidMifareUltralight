package fr.coppernic.samples.hidmifareultralight;

import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import fr.coppernic.sdk.serial.SerialCom;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

/**
 * Created by benoist on 22/11/17.
 */

public class SerialObservable implements ObservableOnSubscribe<byte[]> {

    private Observable<byte[]> observable;
    private SerialCom serialCom;

    SerialObservable(SerialCom serialCom) {
        observable = Observable.create(this);
        this.serialCom = serialCom;
    }

    @Override
    public void subscribe(ObservableEmitter<byte[]> e) throws Exception {
        Log.d(MainActivity.TAG, "Serial Observable Running");
        serialCom.flush();
        while (serialCom.isOpened()) {
            int availableBytes;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((availableBytes = serialCom.getQueueStatus()) > 0) {
                byte[] bytesRead = new byte[availableBytes];
                serialCom.receive(100, availableBytes, bytesRead);
                try {
                    baos.write(bytesRead);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                SystemClock.sleep(5);
            }

            if (baos.size() > 0) {
                Log.d(MainActivity.TAG, "Data available");
                e.onNext(baos.toByteArray());
            }
        }

        e.onComplete();
    }

    Observable<byte[]> getObservable() {
        return observable;
    }
}

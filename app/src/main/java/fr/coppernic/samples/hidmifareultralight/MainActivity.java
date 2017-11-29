package fr.coppernic.samples.hidmifareultralight;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import fr.coppernic.sdk.power.PowerManager;
import fr.coppernic.sdk.power.api.PowerListener;
import fr.coppernic.sdk.power.api.peripheral.Peripheral;
import fr.coppernic.sdk.power.impl.cone.ConePeripheral;
import fr.coppernic.sdk.serial.SerialCom;
import fr.coppernic.sdk.serial.SerialFactory;
import fr.coppernic.sdk.utils.core.CpcBytes;
import fr.coppernic.sdk.utils.core.CpcResult;
import fr.coppernic.sdk.utils.io.InstanceListener;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements InstanceListener<SerialCom>, Observer<byte[]> {
    public static final String TAG = "MainActivity";

    private static final byte[] SELECT_COMMAND = new byte[]{'s'};
    private static final byte[] CONTINUOUS_MODE_COMMAND = new byte[]{'c'};
    private static final byte[] ABORT_CONTINUOUS_READ_COMMAND = new byte[]{'.'};

    private SerialCom serialCom;
    private boolean isReaderInitialized = false;
    private boolean isCardDetected = false;
    private boolean isContinuousReadAborted = false;
    private String cardSerialNumber;
    private ToneGenerator tg;
    @BindView(R.id.tvTagValue)
    public TextView tvTagValue;
    @BindView(R.id.ivPowerStateValue)
    public ImageView ivPowerStateValue;
    @BindView(R.id.ivStateValue)
    public ImageView ivStateValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ButterKnife.bind(this);

        PowerManager.get().registerListener(new PowerListener() {
            @Override
            public void onPowerUp(CpcResult.RESULT result, Peripheral peripheral) {
                ivPowerStateValue.setImageResource(R.drawable.ic_check_24dp);
            }

            @Override
            public void onPowerDown(CpcResult.RESULT result, Peripheral peripheral) {
                ivPowerStateValue.setImageResource(R.drawable.ic_clear_24dp);
                showState(false);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Be sure that the reader is off before starting
        ConePeripheral.RFID_HID_MULTIISO_GPIO.off(this);
        // Gets serial port instance
        // Peripheral will be power on as soon as we get the serial port instance
        SerialFactory.getDirectInstance(MainActivity.this, MainActivity.this);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "Closes serial com");
        serialCom.close();

        super.onStop();
    }

    @Override
    public void onCreated(SerialCom serialCom) {
        Log.d(TAG, "SerialCom instance created");
        this.serialCom = serialCom;
        this.serialCom.open("/dev/ttyHSL1", 9600);
        SerialObservable serialObservable = new SerialObservable(serialCom);

        serialObservable.getObservable()
                .subscribeOn(Schedulers.newThread())
                .subscribe(MainActivity.this);

        Log.d(TAG, "Power on reader");
        ConePeripheral.RFID_HID_MULTIISO_GPIO.on(this);
    }

    @Override
    public void onDisposed(SerialCom serialCom) {

    }

    @Override
    public void onSubscribe(Disposable d) {

    }

    int block = 4;
    String dataRead = "";

    @Override
    public void onNext(byte[] bytes) {
        String data = new String(bytes);

        Log.d(TAG, "Main: " + data);

        // Error management (tag is out of field)
        if (handleError(data)) return;

        // Checks if reader is initialized
        if (handleInit(data)) return;

        // Handles card detection
        if (handleCardDetected(data)) return;

        // Handles continuous read stopped
        if (handleContinuousReadAborted(data)) return;

        // Handles card selection
        if (handleCardSelection(data)) return;

        // Read all remaining blocks
        if (readNextBlocks(data)) return;

        getElisIdentifier(data);
    }

    @Override
    public void onError(Throwable e) {

    }

    @Override
    public void onComplete() {
        Log.d(TAG, "onComplete");
        Log.d(TAG, "Power off reader");
        ConePeripheral.RFID_HID_MULTIISO_GPIO.off(this);
    }

    /**
     * Sends a command to the HF HID MultiISO reader
     * @param command Command to be sent
     */
    private void sendCommand(final byte[] command) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                serialCom.send(command, command.length);
            }
        }).start();
    }

    /**
     * Quits continuous read mode
     */
    private void abortContinousRead () {
        sendCommand(ABORT_CONTINUOUS_READ_COMMAND);
    }

    /**
     * Resets all vars used for the reading process
     */
    private void resetCycle() {
        cardSerialNumber = "";
        isCardDetected = false;
        isContinuousReadAborted = false;
        dataRead = "";
    }

    /**
     * Handles error from the reader, basically when the card is out of the field
     * @param data Data sent by the reader
     * @return True: an error occured, false no error
     */
    private boolean handleError(String data) {
        if (data.contains("N")) {
            Log.d(TAG, "Error!");
            resetCycle();
            clearTag();
            sendCommand(CONTINUOUS_MODE_COMMAND);
            return true;
        }

        return false;
    }

    /**
     * Handles the reader initialization. Reader is Ok when the firmware version has been returned.
     * @param data Data sent by the reader
     * @return True if the firmware version has been received, else false
     */
    private boolean handleInit(String data) {
        if (data.contains("MultiISO")) {
            isReaderInitialized = true;
            Log.d(TAG, "Reader is ready to use");
            block = 4;
            cardSerialNumber = "";
            isCardDetected = false;
            isContinuousReadAborted = false;
            dataRead = "";
            showState(true);
            return true;
        }

        return false;
    }

    /**
     * Handles a card detection
     * @param data Data sent by the reader (the card serial number in this case)
     * @return True if a card has been detected, else false
     */
    private boolean handleCardDetected(String data) {
        if (isReaderInitialized && !isCardDetected) {
            // It is a card
            cardSerialNumber = data;
            isCardDetected = true;
            Log.d(TAG, "Card detected: " + cardSerialNumber);
            abortContinousRead();
            return true;
        }

        return false;
    }

    /**
     * Handles the reader answer when an abort continuous read has been requested
     * @param data Data sent from the reader ('S' in this case)
     * @return True if the continuous read has been aborted, else false
     */
    private boolean handleContinuousReadAborted(String data) {
        if (isCardDetected && !isContinuousReadAborted) {
            if (data.contains("S")) {
                Log.d(TAG, "Continuous read aborted");
                isContinuousReadAborted = true;
                Log.d(TAG, "Select card");
                sendCommand(SELECT_COMMAND);
                return true;
            }
        }

        return false;
    }

    /**
     * Handles answer from the reader in case of a card selection (the card serial number)
     * @param data Data sent by the reader
     * @return True if the data received is the serial number of the card
     */
    private boolean handleCardSelection(String data) {
        if (isContinuousReadAborted && data.equals(cardSerialNumber)) {
            // Useful data starts at block 4
            block = 4;
            Log.d(TAG, "Card selected: " + data);
            Log.d(TAG, "Reading block" + String.format(Locale.getDefault(), "%02d", block));
            sendCommand(("rb" + String.format(Locale.getDefault(), "%02d", block)).getBytes());
            block += 4;
            return true;
        }

        return false;
    }

    /**
     * Handles the complete read of the tag
     * @param data Data sent by the reader
     * @return True if the blocks have been read correctly, else false
     */
    private boolean readNextBlocks(String data) {
        if (isContinuousReadAborted) {
            if (block <= 12) {
                Log.d(TAG, "Reading block" + String.format(Locale.getDefault(), "%02d", block));
                dataRead += data.replace("\r\n", "");
                sendCommand(("rb" + String.format(Locale.getDefault(), "%02d", block)).getBytes());
                block += 4;

                return true;
            }
        }

        return false;
    }

    /**
     * Gets ELIS identifier from data read from the tag and displays it on the screen
     * @param data Data sent by the reader
     */
    private void getElisIdentifier(String data) {
        if (isContinuousReadAborted) {
            if (block == 16) {
                // All blocks have been read
                dataRead += data.replace("\r\n", "");
                Log.d(TAG, "Data read: " + dataRead);
                // Gets all TLV (Type Length Value) blocks in the data
                // On the ELIS tag should be Lock Control TLV -> NDEF message TLV -> Terminator TLV
                byte[] dataFromTag = CpcBytes.parseHexStringToArray(dataRead);
                ArrayList<Tlv> tlvs = Tlv.parseArray(dataFromTag);

                for(Tlv tlv: tlvs) {
                    // We just want to check the NDEF message TLV, containing tag identifier (ELISxxxxxx)
                    if (tlv.getType() == Tlv.TYPE_NDEF) {
                        String elisIdentifier = parseTextTlv(tlv);
                        showTag(elisIdentifier);
                    }
                }
                // Resets vars
                resetCycle();
                // starts continuous mode to hunt for tags
                Log.d(TAG, "Continuous read enabled");
                sendCommand(CONTINUOUS_MODE_COMMAND);
            }
        }
    }

    /**
     * Parses the NDEF TLV containing Elis tag identifier
     * and returns the ELIS identifier (ELISxxxxx)
     * @param tlv TLV containing the identifier
     * @return ELIS identifier (ELISxxxxx)
     */
    private String parseTextTlv(Tlv tlv) {
        String elisIdentifier = "Unknown";
        try {
            NdefMessage message = new NdefMessage(tlv.getValue());
            NdefRecord[] records = message.getRecords();

            Log.d(TAG, "Nb record: " + records.length);
            if (records.length >= 1) {
                // Byte 0: type
                // Must be 0x54 ('T' as text)
                if (records[0].getType()[0] == 0x54) {
                    // Checks encoding
                    byte encoding = (byte) (records[0].getPayload()[0] & 0x80);
                    if (encoding == 0x00) {
                        Log.d(TAG, "Encoding UTF-8");
                    } else {
                        Log.d(TAG, "Encoding UTF-16");
                    }
                    // Gets size of language in bytes
                    byte size = (byte) (records[0].getPayload()[0] & 0x7F);
                    Log.d(TAG, "Size of language: " + size);
                    // Gets language
                    byte[] language = new byte[size];
                    System.arraycopy(records[0].getPayload(), 1, language, 0, size);
                    Log.d(TAG, "Language: " + new String(language));
                    // Gets ELIS identifier -> the only important information
                    byte[] textData = new byte[records[0].getPayload().length - 1 - size];
                    System.arraycopy(records[0].getPayload(), size + 1, textData, 0, textData.length);
                    elisIdentifier = new String(textData);
                    Log.d(TAG, "Data: " + elisIdentifier);
                }
            }

        } catch (FormatException e) {
            e.printStackTrace();
        }

        return elisIdentifier;
    }

    /**
     * Displays tag value in text view
     * @param tag Tag value
     */
    private void showTag(final String tag) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvTagValue.setText(tag);
                playBeep();
            }
        });
    }

    /**
     * Displays no tag read
     */
    private void clearTag() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvTagValue.setText(R.string.no_tag_read);
                playBeep();
            }
        });
    }

    /**
     * Plays a beep
     */
    private void playBeep() {
        try {
            if (tg == null) {
                tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            }
            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void showState(final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ivStateValue.setImageResource(state?(R.drawable.ic_check_24dp):(R.drawable.ic_clear_24dp));
            }
        });
    }
}

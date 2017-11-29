package fr.coppernic.samples.hidmifareultralight;

import java.util.ArrayList;
import java.util.Locale;

import fr.coppernic.sdk.utils.core.CpcBytes;

/**
 * Created by benoist on 28/11/17.
 */

public class Tlv {
    byte type;
    byte length;
    byte[] value;

    public static final int TYPE_NULL = 0x00;
    public static final int TYPE_LOCK_CONTROL = 0x01;
    public static final int TYPE_MEMORY_CONTROL = 0x02;
    public static final int TYPE_NDEF = 0x03;
    public static final int TYPE_PROPRIETARY = 0xFD;
    public static final int TYPE_TERMINATOR = 0xFE;

    public static ArrayList<Tlv> parseArray(byte[] data) {
        Tlv tlv = Tlv.build(data);
        ArrayList<Tlv> tlvs = new ArrayList<>();
        tlvs.add(tlv);

        int index = tlv.length + 2;

        byte[] dataTemp = new byte[data.length - index];

        while (dataTemp.length > 3 && tlv.type != (byte)TYPE_TERMINATOR) {
            System.arraycopy(data, index, dataTemp, 0, dataTemp.length);
            tlv = Tlv.build(dataTemp);
            tlvs.add(tlv);
            index += tlv.length + 2 ;
            dataTemp = new byte[data.length - index];
        }

        return tlvs;
    }

    private Tlv(byte[] data) {
        type = data[0];
        length = data[1];
        value = new byte[length];
        System.arraycopy(data, 2, value, 0, length);
    }

    public static Tlv build (byte[] data) {
        return new Tlv(data);
    }

    public String toString() {
        return "Type: " + typeToString() + "\n"
                + "Length: " + length + "\n"
                + "Value: " + CpcBytes.byteArrayToString(value);
    }

    private String typeToString() {
        switch (type) {
            case 0x00:
                return "NULL";
            case 0x01:
                return "Lock Control";
            case 0x02:
                return "Memory Control";
            case 0x03:
                return "NDEF Message";
            case (byte) 0xFD:
                return "PROPRIETARY";
            case (byte) 0xFE:
                return "TERMINATOR";
        }

        return "Unknown";
    }

    public int getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public byte[] getValue() {
        return value;
    }
}

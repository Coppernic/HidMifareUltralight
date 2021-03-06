# HidMifareUltralight
This project demonstrates how to access to ELIS ID in a NFC HF tag from ELIS.

Introduction
------------
ELIS company uses NFC tags for its applications. This specific tag is a MIFARE ULTRALIGHT tag containing data stored as NFC describes.

Read MIFARE ULTRALIGHT tag
--------------------------

The process of reading a MIFARE ULTRALIGHT tag is straight forward:

1. Enable continuous read if not enabled (command 'C')
2. Once a tag has been deiscovered, stop continuous mode (command '.')
3. Select the tag (command 'S')
4. Read blocks (command 'rbxx')

Note: HID HF MultiISO reader reads 4 blocks by 4 blocks whereas MIFARE ULTRALIGHT should read one block at a time.

Parse data read
---------------

Relevant data starts at block 4. Data is stored as a collection of TLV (Type/Length/Value) objects. 

On the ELIS tag should be Lock Control TLV -> NDEF message TLV -> Terminator TLV

The relevant message is the NDEF message:

1. Type on one byte is 0x54 ('T') as text message
2. First byte of payload contains the encoding and the size (x bytes) of the language
3. Next x bytes are the language
4. Remaining bytes are  text data

```java
// ndef is a byte array, the payload of the NDEF TLV read from the card
try {
    NdefMessage message = new NdefMessage(ndef);
    NdefRecord[] records = message.getRecords();
    
    if (records.length >= 1) {
        NdefRecord record = records[0];
        // Byte 0: type
        // Must be 0x54 ('T' as text)
        if (record.getType()[0] == 0x54) {
            // Checks encoding
            byte encoding = (byte) (record.getPayload()[0] & 0x80);
            if (encoding == 0x00) {
                Log.d(TAG, "Encoding UTF-8");
            } else {
                Log.d(TAG, "Encoding UTF-16");
            }
            // Gets size of language in bytes
            byte size = (byte) (record.getPayload()[0] & 0x7F);            
            // Gets language
            byte[] language = new byte[size];
            System.arraycopy(record.getPayload(), 1, language, 0, size);            
            // Gets ELIS identifier -> the only important information
            byte[] textData = new byte[record.getPayload().length - 1 - size];
            System.arraycopy(record.getPayload(), size + 1, textData, 0, textData.length);
            elisIdentifier = new String(textData);            
        }
    }

} catch (FormatException e) {
    e.printStackTrace();
}
```

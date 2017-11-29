# HidMifareUltralight
This project demonstrates how to access to ELIS ID in a NFC HF tag from ELIS.

Introduction
------------
ELIS company use NFC tags for their applications. This specific tag is a MIFARE ULTRALIGHT tag containing data stored as NFC describes.

Read MIFARE ULTRALIGHT tag
--------------------------

The process of reading a MIFARE ULTRALIGHT tag is straight forward:

1. Enable continuous read if not enabled (command 'C')
2. Once a tag has been deiscovered, stop continuous mode (command '.')
3. Select the tag (command 'S')
4. Read blocks (command 'rbxx')

Note: HID HF MultiISO reader reads 4 blocks by 4 blocks whereas MIFARE ULTRALIGHT shoudl read one block at a time.

Parse data read
---------------

Relevant data starts at block 4. Data is stored as a collection of TLV (Type/Length/Value) objects. 

On the ELIS tag should be Lock Control TLV -> NDEF message TLV -> Terminator TLV

The relevant message is the NDEF message:

1. Type on one byte is 0x54 ('T') as text message
2. First byte of payload contains the encoding and the size (x bytes) of the language
3. Next x bytes are the language
4. Remaining bytes are  text data

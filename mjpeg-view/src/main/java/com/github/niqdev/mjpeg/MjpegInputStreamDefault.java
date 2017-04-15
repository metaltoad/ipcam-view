package com.github.niqdev.mjpeg;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/*
 * I don't really understand and want to know what the hell it does!
 * Maybe one day I will refactor it ;-)
 * <p/>
 * https://code.google.com/archive/p/android-camera-axis
 */
public class MjpegInputStreamDefault extends MjpegInputStream {
    private static final String TAG = MjpegInputStream.class.getSimpleName();

    private final byte[] SOI_MARKER = {(byte) 0xFF, (byte) 0xD8};
    private final byte[] EOF_MARKER = {(byte) 0xFF, (byte) 0xD9};
    private final String CONTENT_LENGTH = "Content-Length";
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 40000 + HEADER_MAX_LENGTH;
    private int mContentLength = -1;

    // no more accessible
    MjpegInputStreamDefault(InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }

    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence) throws IOException {
        Log.d(TAG, "getEndOfSeq start");
        int seqIndex = 0;
        byte c;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
            Log.d(TAG, "getEndOfSeq i=" + i);
            c = (byte) in.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                seqIndex++;
                if (seqIndex == sequence.length) {
                    Log.d(TAG, "getEndOfSeq normal end");
                    return i + 1;
                } else {
                    Log.d(TAG, "getEndOfSeq fallthrough?");
                }
            } else {
                Log.d(TAG, "getEndOfSeq seqIndex=0");
                seqIndex = 0;
            }
        }
        Log.d(TAG, "getEndOfSeq alternate end");
        return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        Log.d(TAG, "getStartOfSequence start");
        int end = getEndOfSeqeunce(in, sequence);
        Log.d(TAG, "getStartOfSequence end");
        return (end < 0) ? (-1) : (end - sequence.length);
    }

    private int parseContentLength(byte[] headerBytes) throws IOException, NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }

    // no more accessible
    Bitmap readMjpegFrame() throws IOException {
        Log.d(TAG, "readMjpegFrame start");
        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();
        byte[] header = new byte[headerLen];
        Log.d(TAG, "readMjpegFrame read header");
        readFully(header);
        try {
            Log.d(TAG, "attempt to parseContentLength");
            mContentLength = parseContentLength(header);
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "attempt to getEndOfSequence");
            mContentLength = getEndOfSeqeunce(this, EOF_MARKER);
        }
        Log.d(TAG, "readMjpegFrame reset");
        reset();
        byte[] frameData = new byte[mContentLength];
        skipBytes(headerLen);
        readFully(frameData);
        Log.d(TAG, "readMjpegFrame end");
        return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData));
    }
}

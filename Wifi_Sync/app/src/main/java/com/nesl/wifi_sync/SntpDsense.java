/*
 * Original work Copyright (C) 2008 The Android Open Source Project
 * Modified: Sandeep Singh Sandha, UCLA, NESL
 */

package com.nesl.wifi_sync;

import android.os.SystemClock;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class SntpDsense {

    private static final int ORIGINATE_TIME_OFFSET = 24;
    private static final int RECEIVE_TIME_OFFSET = 32;
    private static final int TRANSMIT_TIME_OFFSET = 40;
    private static final int NTP_PACKET_SIZE = 48;

    private static final int NTP_PORT = 123;
    private static final int NTP_MODE_CLIENT = 3;
    private static final int NTP_VERSION = 3;


    // Number of seconds between Jan 1, 1900 and Jan 1, 1970, 70 years plus 17 leap days
    private static final long OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L;


    private long ntp_update_sys_time; //systime when NTP update was done

    private long ntp_update_monotonic_time;//monotonic sys time when NTP update was done

    //offset of system clock from ntp clock
    private long ntp_clockoffset;


    public boolean requestTime(String host, int timeout) {
        InetAddress address = null;
        try {
            address = InetAddress.getByName(host);
        } catch (Exception e) {
            return false;
        }
        return requestTime(address, NTP_PORT, timeout);
    }

    public boolean requestTime(InetAddress address, int port, int timeout) {


        /*
        We will do this 10 times and take the median offset
         */
        int retry = 10;
        ArrayList<Long> array_clockOffset = new ArrayList<Long>();

        while(retry>0) {
            DatagramSocket socket = null;

            try {

                socket = new DatagramSocket();
                socket.setSoTimeout(timeout);


                byte[] buffer = new byte[NTP_PACKET_SIZE];
                DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, port);

                buffer[0] = NTP_MODE_CLIENT | (NTP_VERSION << 3);

                // get current time and write it to the request packet
                final long requestTime = System.currentTimeMillis();
                final long requestTicks = SystemClock.elapsedRealtime();
                writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime);

                socket.send(request);

                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                final long responseTicks = SystemClock.elapsedRealtime();
                final long responseTime = requestTime + (responseTicks - requestTicks);

                final long originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET);
                final long receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET);
                final long transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET);


                long roundTripTime = responseTicks - requestTicks - (transmitTime - receiveTime);

                long clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2;

                if(roundTripTime<500)//200 ms is the delay based on observation in Android LTE
                {
                    array_clockOffset.add(clockOffset);
                }



                retry--;

            } catch (Exception e) {


                return false;
            } finally {
                if (socket != null) {
                    socket.close();
                }

            }


        }//end while(retry>0)


        //there was some queries which were successfull
        if(array_clockOffset.size()>0) {


            //sort the clockoffset
            Collections.sort(array_clockOffset);


            //take the median of the offset
            ntp_clockoffset=  array_clockOffset.get(array_clockOffset.size()/2);

            //at this current system which we calculated the offset
            ntp_update_sys_time = System.currentTimeMillis();

            //at this instant monotonic system time
            ntp_update_monotonic_time = SystemClock.elapsedRealtime();;

            //System.out.println("Sandeep: ntp_clockoffset:"+ntp_clockoffset+" middle_offset:"+middle_offset+"middle_roundTrip :"+middle_roundTrip);

        }

        return true;
    }

    /**
     * Returns the time computed from the NTP transaction.
     *
     * @return time value computed from NTP server response.
     */
    public long get_ntp_update_sys_time() {
        return ntp_update_sys_time;
    }


    /*
    Returns the ntp_clockoffset
     */
    public long getNtp_clockoffset()
    {
        return ntp_clockoffset;
    }

    /**
     * Returns the reference clock value (value of SystemClock.elapsedRealtime())
     * corresponding to the NTP time.
     *
     * @return reference clock corresponding to the NTP time.
     */
    public long get_ntp_update_monotonic_time() {
        return ntp_update_monotonic_time;
    }


    /**
     * Reads an unsigned 32 bit big endian number from the given offset in the buffer.
     */
    private long read32(byte[] buffer, int offset) {
        byte b0 = buffer[offset];
        byte b1 = buffer[offset+1];
        byte b2 = buffer[offset+2];
        byte b3 = buffer[offset+3];

        // convert signed bytes to unsigned values
        int i0 = ((b0 & 0x80) == 0x80 ? (b0 & 0x7F) + 0x80 : b0);
        int i1 = ((b1 & 0x80) == 0x80 ? (b1 & 0x7F) + 0x80 : b1);
        int i2 = ((b2 & 0x80) == 0x80 ? (b2 & 0x7F) + 0x80 : b2);
        int i3 = ((b3 & 0x80) == 0x80 ? (b3 & 0x7F) + 0x80 : b3);

        return ((long)i0 << 24) + ((long)i1 << 16) + ((long)i2 << 8) + (long)i3;
    }

    /**
     * Reads the NTP time stamp at the given offset in the buffer and returns
     * it as a system time (milliseconds since January 1, 1970).
     */
    private long readTimeStamp(byte[] buffer, int offset) {
        long seconds = read32(buffer, offset);
        long fraction = read32(buffer, offset + 4);
        // Special case: zero means zero.
        if (seconds == 0 && fraction == 0) {
            return 0;
        }
        return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L);
    }

    /**
     * Writes system time (milliseconds since January 1, 1970) as an NTP time stamp
     * at the given offset in the buffer.
     */
    private void writeTimeStamp(byte[] buffer, int offset, long time) {
        // Special case: zero means zero.
        if (time == 0) {
            Arrays.fill(buffer, offset, offset + 8, (byte) 0x00);
            return;
        }

        long seconds = time / 1000L;
        long milliseconds = time - seconds * 1000L;
        seconds += OFFSET_1900_TO_1970;

        // write seconds in big endian format
        buffer[offset++] = (byte)(seconds >> 24);
        buffer[offset++] = (byte)(seconds >> 16);
        buffer[offset++] = (byte)(seconds >> 8);
        buffer[offset++] = (byte)(seconds >> 0);

        long fraction = milliseconds * 0x100000000L / 1000L;
        // write fraction in big endian format
        buffer[offset++] = (byte)(fraction >> 24);
        buffer[offset++] = (byte)(fraction >> 16);
        buffer[offset++] = (byte)(fraction >> 8);
        // low order bits should be random data
        buffer[offset++] = (byte)(Math.random() * 255.0);
    }
}

/*
Developer: Sandeep Singh Sandha
Contact: sandha.iitr@gmail.com
This file contains the code which can be used to synchronize the Android smartphones.

The code uses NTP client over BLE to reach another NTP server and captues the offset of the android device monotonic time with respect to the server

BLE client and server code is present in this file
 */



package com.nesl.ble_sync;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;

    //uuid is used in server and client ble sockets
    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //used to create a buffer
    private static final int ORIGINATE_TIME_OFFSET = 0;
    private static final int RECEIVE_TIME_OFFSET = 16;
    private static final int TRANSMIT_TIME_OFFSET = 32;

    private static final int PACKET_SIZE = 48;


    //server device to which ble clients will connect to
    BluetoothDevice mmDevice;


    //we calculate 10 offset and then the median is used as the final offset
    int repeated = 10;

    long final_offset =0L;//stores the offset with respect to the server


    // Requesting permissions
    private boolean permissionToBle = false;
    private String [] permissions = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION

    };

    private static final int REQUEST_PERMISSION = 200;



    Thread ble_exp_running;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_PERMISSION:
                permissionToBle  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToBle ) finish();

    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION);



        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //check BlueTooth Capabilities
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            System.out.println("BlueTooth is not supported");
        }


        //enable blueTooth
        int REQUEST_ENABLE_BT =1;
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }



        //query the paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {

               mmDevice = device;
            }
        }//end if


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        Button start_client = (Button)findViewById(R.id.start_cli);
        start_client.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ble_exp_running = new Thread(thread_measure_ble_client);
                ble_exp_running.start();
            }
        });


        Button start_server = (Button)findViewById(R.id.start_ser);
        start_server.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try{

                    ble_exp_running = new Thread(thread_measure_ble_server);
                    ble_exp_running.start();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });


    }//end OnCreate


    Runnable thread_measure_ble_client = new Runnable() {
        @Override
        public void run() {

            for(int i=0;i<10;i++)
            {
                run_ble_client();
                System.out.println("BLE offset :"+final_offset);

            }

        }//end run
    };


    Runnable thread_measure_ble_server = new Runnable() {
        @Override
        public void run() {

                new Thread(ble_server_socket).start();


        }//end run
    };


    private BluetoothSocket createBluetoothSocket(BluetoothDevice device)
            throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, uuid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return  device.createRfcommSocketToServiceRecord(uuid);
    }



    //Thread to open a ble server socket and listen for the connection requests
    Runnable ble_server_socket = new Runnable() {
        @Override
        public void run() {


            BluetoothServerSocket mmServerSocket = null;

        //initializing server socket
                BluetoothServerSocket tmp = null;
                try {
                    tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("ble_Time", uuid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mmServerSocket = tmp;

                //server will listen forever
            while(true)
            {

               boolean done = true; //records whether communication between client and server was successfull or not


                    BluetoothSocket socket = null;

                    try {

                        try{
                            //listen until a socket is returned
                            socket = mmServerSocket.accept();

                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }

                        //this is reached only when accept succeeds, so client has connected
                        if (socket != null) {

                            long received_time = 0;//System.currentTimeMillis();


                            //get the input and output streams from the socket
                            InputStream mmInStream = socket.getInputStream();
                            OutputStream mmOutStream = socket.getOutputStream();

                            byte[] mmBuffer; // mmBuffer store for the stream

                            mmBuffer = new byte[PACKET_SIZE];

                            int numBytes = 0; // bytes returned from read()

                            try {
                                numBytes = mmInStream.read(mmBuffer);

                                received_time = SystemClock.elapsedRealtimeNanos();//System.currentTimeMillis();

                            }
                            catch (Exception e) {
                                done = false;
                                mmInStream.close();
                                mmOutStream.close();

                                e.printStackTrace();

                            }

                            if (done)//previous read from client was successfull
                            {
                                writeTimeStamp_nano(mmBuffer, RECEIVE_TIME_OFFSET, received_time);//writing to buffer the time of Receiving packet on server

                                long send_time = SystemClock.elapsedRealtimeNanos();//System.currentTimeMillis();

                                writeTimeStamp_nano(mmBuffer, TRANSMIT_TIME_OFFSET, send_time);//writing to buffer the sending time of packet from server

                                //sending to client
                                mmOutStream.write(mmBuffer);
                                mmOutStream.flush();
                                mmOutStream.close();
                                socket.close();

                            }//end if(done)//previous read from client was successfull

                        }//end  if (socket != null)


                    }//end try
                    catch (Exception e) {
                        e.printStackTrace();
                        try
                        {
                            if(socket!=null)
                                socket.close();
                        }
                        catch (Exception e2)
                        {
                            e2.printStackTrace();
                        }

                    }


            }//end for(int i=0;i<repeated;i++)



        }//end void run()
    };//end Runnable Ble_server_socket


    //runs ble client
    void run_ble_client()
    {
        ArrayList<Long> array_clockOffset = new ArrayList<Long>();

        ArrayList<Long> array_delay = new ArrayList<Long>();



        for(int i=0;i<repeated;i++)
        {

            boolean done = true;

                BluetoothSocket socket=null;
                try {

                    //connecting to the server socket
                BluetoothSocket tmp = null;
                try {
                    // Get a BluetoothSocket to connect with the given BluetoothDevice.
                    tmp = mmDevice.createRfcommSocketToServiceRecord(uuid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                socket = tmp;

                    socket = createBluetoothSocket(mmDevice);

                    // Cancel discovery because it otherwise slows down the connection
                    bluetoothAdapter.cancelDiscovery();

                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    try {
                        socket.connect();
                    } catch (Exception e) {
                        //unable to connect, close the socket
                        socket.close();
                        e.printStackTrace();
                    }

                    //get the input and output streams from the socket
                    InputStream mmInStream = socket.getInputStream();
                    OutputStream mmOutStream = socket.getOutputStream();

                    byte[] mmBuffer; // mmBuffer store for the stream

                    mmBuffer = new byte[PACKET_SIZE];

                    long requestTime = SystemClock.elapsedRealtimeNanos();

                    final long requestTicks = requestTime;

                    writeTimeStamp_nano(mmBuffer, ORIGINATE_TIME_OFFSET, requestTime);//writing to buffer the time of sending packet

                    mmOutStream.write(mmBuffer);
                    mmOutStream.flush();

                    int numBytes = 0; // bytes returned from read()
                    long responseTicks = 0;


                    try {
                        //read data from the server
                        numBytes = mmInStream.read(mmBuffer);
                        responseTicks = SystemClock.elapsedRealtimeNanos();

                    } catch (Exception e) {
                        done = false;
                        socket.close();
                        e.printStackTrace();
                    }

                    if (done) {
                        final long originateTime = readTimeStamp_nano(mmBuffer, ORIGINATE_TIME_OFFSET);
                        final long receiveTime = readTimeStamp_nano(mmBuffer, RECEIVE_TIME_OFFSET);
                        final long transmitTime = readTimeStamp_nano(mmBuffer, TRANSMIT_TIME_OFFSET);

                        final long responseTime = requestTime + (responseTicks - requestTicks);

                        long roundTripTime = responseTicks - requestTicks - (transmitTime - receiveTime);
                        //long server_time = (transmitTime - receiveTime);

                        long clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2;

                        array_clockOffset.add(clockOffset);

                        array_delay.add(roundTripTime);

                    }//if we did a successful read


                    //now close the socket
                    socket.close();



                } catch (Exception e) {
                    e.printStackTrace();

                    try{
                        if(socket!=null)
                            socket.close();
                    }
                    catch (Exception e2)
                    {
                        e2.printStackTrace();
                    }

                }

          //  } while (!done);//we will do it unless succeeded


        }//end   for(int i=0;i<repeated;i++)

        //there was some query as successfull
        if(array_clockOffset.size()>0) {

            //sort the clockoffset
            Collections.sort(array_clockOffset);

            final_offset = array_clockOffset.get(array_clockOffset.size()/2);

        }
    }



   //timestamping in the buffer code
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




    private long readTimeStamp_nano(byte[] buffer, int offset) {
        long seconds = read32(buffer, offset);
        long fraction = read32(buffer, offset + 4);

        long fraction2 = read32(buffer, offset + 8);

        long fraction3 = read32(buffer, offset + 12);


        // Special case: zero means zero.
        if (seconds == 0 && fraction == 0) {
            return 0;
        }
        return ((seconds) * 1000_000_000L) + ((fraction * 1000_000_000L) / 0x100000000L)+((fraction2 * 1000_000L) / 0x100000000L)+((fraction3 * 1000L) / 0x100000000L);
    }


    private void writeTimeStamp_nano(byte[] buffer, int offset, long time) {


        long seconds = time / 1000_000_000L;
        long milliseconds = (time - seconds * 1000_000_000L)/1000_000L;
        long microseconds = (time - seconds * 1000_000_000L - milliseconds*1000_000L)/1000L;
        long nanoseconds  =  time - seconds * 1000_000_000L - milliseconds*1000_000L + microseconds*1000L;

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
        buffer[offset++] = (byte)(fraction >> 0);

        //writing the microseconds

        long fraction2 = microseconds * 0x100000000L / 1000L;

        // write fraction in big endian format
        buffer[offset++] = (byte)(fraction2 >> 24);
        buffer[offset++] = (byte)(fraction2 >> 16);
        buffer[offset++] = (byte)(fraction2 >> 8);
        buffer[offset++] = (byte)(fraction2 >> 0);

        long fraction3 = nanoseconds * 0x100000000L / 1000L;

        // write fraction in big endian format
        buffer[offset++] = (byte)(fraction3 >> 24);
        buffer[offset++] = (byte)(fraction3 >> 16);
        buffer[offset++] = (byte)(fraction3 >> 8);
        buffer[offset++] = (byte)(fraction3 >> 0);


    }


}//end MainActivity

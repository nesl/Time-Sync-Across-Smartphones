/*
Developer: Sandeep Singh Sandha
Contact: sandha.iitr@gmail.com
This file contains the code which can be used to synchronize the Android smartphones.

The code uses NTP client to query the NTP server and captues the offset of the android device monotonic time with respect to the server
 */


package com.nesl.wifi_sync;
import android.Manifest;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;

public class MainActivity extends AppCompatActivity {

    //how many times offsets to count
    int num_of_offset=10;

    //stores the offset value
    long offset =0L;

    String ntpHost = "17.253.26.253";//"time.apple.com";

    private String [] permissions = {

            Manifest.permission.INTERNET

    };

    private static final int REQUEST_PERMISSION = 200;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION);


        setContentView(R.layout.activity_main);


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //thread to calculate offset with respect to the android device monotonic time
        Thread calculate_offsets = new Thread(Timer);
        calculate_offsets.setPriority(10);
        calculate_offsets.start();

    }//end onCreate


    void get_ntp_offset()
    {
        SntpDsense client = null;

        int timeout = 3000;
        boolean SntpSuceeded=false;

        long curr_ntp_offset = 0;
        long curr_ntp_monotonic_time=0;
        long curr_ntp_sys_time=0;

        client = new SntpDsense();

        //System.out.println("get_ntp_offset is running");
        SntpSuceeded = client.requestTime(ntpHost, timeout);

        if(SntpSuceeded)
        {

                curr_ntp_offset = client.getNtp_clockoffset();
                curr_ntp_monotonic_time = client.get_ntp_update_monotonic_time();
                curr_ntp_sys_time=client.get_ntp_update_sys_time();

                offset = curr_ntp_offset+curr_ntp_sys_time - curr_ntp_monotonic_time;

                System.out.println("offset is: "+offset);


        }




    }//end get_ntp_offset


    //    //running system time clock
    Runnable Timer = new Runnable() {
        @Override
        public void run() {

            //while (true)
            for(int i=0;i<num_of_offset;i++)
            {
                try {

                    get_ntp_offset();

                    //each offset is caculated with a difference of 1 second
                    Thread.sleep(1000);

                } catch (Exception e) {

                    e.printStackTrace();

                }
            }
        }//end  while (run_sys_time)
    };//end Timer


}//end MainActivity

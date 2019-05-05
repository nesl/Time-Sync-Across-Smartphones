/*
Developer: Sandeep Singh Sandha
Contact: sandha.iitr@gmail.com

This file has the code which can be used to synchronize the Android smartphones based on audio pipeline.

This code captures the monotonic timestamp when the audio event is observed by the phone.
Collected timestamp from all the different audio devices can be used to calculate the offset and do timesync.
 */

package com.nesl.audio_sync;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import java.util.concurrent.atomic.AtomicBoolean;



public class MainActivity extends AppCompatActivity {


    /*
  This is the highest sampling rate supported by Pixel-3 and Nexus-5X.
  44100 can also be used which is supported by a large set of Android devices.
  But with 44100, the audio timestamping precision will degrade as compared to 192000.
     */
    private static final int SAMPLING_RATE_IN_HZ = 192000;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;


    /*
    We will listen for 10000 beeps, this number can be changed depending on application/usage.
     */
    int listened_beep = 10000;


    /*
    After capturing audio event, we will not process next 60 buffers read from the audio pipeline.
    This is a developer decision. For some applications, we might not skip any buffers.
     */
    int pass_buffer = 60;

    /*
    This is event amplitude. If we read audio buffer value larger than this, then the event has happened
     */
    int max_amplitude = 8000;


    /*
    We will make the buffer size to the 5 times of the minimum allowed buffer
     */
    private static final int BUFFER_SIZE_FACTOR = 5;

    /*
    Final buffer size is the minimum allowed buffer size multiplied by the buffer size factor
     */
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;


    /**
     * Used to determined if a recording is in happening (true) or not (false).
     */
    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);



    private AudioRecord recorder = null;

    /*
    This is the thread which will run the main recording loop
     */
    private Thread recordingThread = null;

    private Button startButton;
    private Button stopButton;


    // Requesting the permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {
            Manifest.permission.RECORD_AUDIO
    };

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;


    /*
    This will record the time when the audio event happened
     */
    long event_time=0;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);


        setContentView(R.layout.activity_main);

        startButton = (Button) findViewById(R.id.btnStart);
        stopButton = (Button) findViewById(R.id.btnStop);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            }
        });


        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            }
        });


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


    }//end onCreate


    private void startRecording() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

        recorder.startRecording();
        recordingInProgress.set(true);

        recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
        /*
        Setting the highest priority for this thread
         */
        recordingThread.setPriority(10);
        recordingThread.start();
    }


    private void stopRecording() {
        if (null == recorder) {
            return;
        }

        recordingInProgress.set(false);
        recorder.stop();
        recorder.release();
        recorder = null;
        recordingThread = null;

    }//end stopRecording


    private class RecordingRunnable implements Runnable {

        @SuppressLint("NewApi")
        @Override
        public void run() {

            short buffer[] = new short[BUFFER_SIZE];
            AudioTimestamp at = new  AudioTimestamp();

            long prev_timestamp =0;
            long curr_timestamp =0;

            long prev_frame_count =0;
            long curr_frame_count =0;

            int count_listened =0;

            int local_pass=0;

            try {

                while (recordingInProgress.get() && count_listened<listened_beep) {

                    int result = recorder.read(buffer, 0, BUFFER_SIZE,AudioRecord.READ_NON_BLOCKING);

                    /**
                     * Clock monotonic including suspend time or its equivalent on the system,
                     * in the same units and timebase as {@link android.os.SystemClock#elapsedRealtimeNanos}.
                     */
                    recorder.getTimestamp (at,AudioTimestamp.TIMEBASE_BOOTTIME);


                    short max = -32768;
                    int index = 0;

                    prev_timestamp = curr_timestamp;
                    curr_timestamp = at.nanoTime;
                    prev_frame_count = curr_frame_count;
                    curr_frame_count = at.framePosition;


                    int frames_read = (int)(curr_frame_count-prev_frame_count);
                    if(frames_read>0)
                    {


                        //finding the max value of the audio event.
                        for(int i=0;i<frames_read&&i<BUFFER_SIZE;i++)
                        {
                            short val = buffer[i];

                            if (val<0)
                                val = (short)-val;

                            if(val>max)
                            {
                                max = val;
                                index = i;
                            }
                        }


                        int time_between_buffers = (int)(curr_timestamp - prev_timestamp);




                        double time_elapsed_for_the_event_double = time_between_buffers*((index*1.0)/frames_read);

                        long time_for_event = prev_timestamp + (long)time_elapsed_for_the_event_double;


                        //this is the real rate captured by the device
                        double rate = (1.0*1000*1000*1000*frames_read)/(time_between_buffers*1.0);


                        //System.out.println("Frames: "+frames_read+" : Time Difference: "+time_between_buffers+" Rate is:\t"+rate+"\t Max:"+max);


                        //We are clamping the buffer read fluctuations
                      if(rate>189000.0 && rate<195000.0) //it is a valid buffer read.
                      {

                          //An event has happened
                          if (max > max_amplitude && local_pass<=0) {

                              count_listened++;//we have listened a positive beep

                              local_pass=pass_buffer; //we are skipping next pass_buffer buffer frames

                              //This is the event time that is captured
                              event_time = time_for_event;

                              System.out.println("Frames: "+frames_read+" : Time Difference: "+time_between_buffers+" Rate is:\t"+rate+"\t Max:"+max+"\t event_time:"+event_time);


                          }//end  if(rate>189000.0 && rate<195000.0)

                      }

                        else
                      {
                          System.out.println("Rate is not correct:"+rate);
                      }

                        local_pass--;


                    }//end if(curr_frame_count-prev_frame_count>0)

                }// while (recordingInProgress.get()

            } catch (Exception e) {
                throw new RuntimeException("Not able to start the audio thread", e);
            }
        }


    }//end RecordingRunnable


}//end MainActivity

package com.pulsecam;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Vibrator;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pulsecam.R;
import com.pulsecam.RemoteSensorManager;
import com.pulsecam.data.Sensor;
import com.pulsecam.events.BusProvider;
import com.pulsecam.events.NewSensorEvent;
import com.pulsecam.events.SensorUpdatedEvent;
import com.squareup.otto.Subscribe;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends ActionBarActivity {
    private RemoteSensorManager remoteSensorManager;

    private Dialog dialogcheck;
    private Sensor hRsensor;
    private TextView viewHR;
    private TextView viewHRAverage;
    private TextView viewPicturesSecond;
    private TextView viewTotal;
    private TextView viewMissed;
    private TextView arosed;
    private SeekBar bar;
    private Date today;
    private Integer readings = 0;
    private Integer missed = 0;
    private long count = 0;
    private long sum = 0;
    private long baseInterval = 60000*5; //5 minutes base interval
    private long interval = baseInterval;
    Activity context = this;
    MyCamera camera;
    Vibrator v;
    private int percentage;

    private float HR=0;
    private static final String TAG = "PulseCam/Main";
    private Handler handler = new Handler();
    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

        }
    };
    private void showToast(String text) {
        // We show a Toast by sending request message to mMessageHandler. This makes sure that the
        // Toast is shown on the UI thread.
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    private Runnable run = new Runnable()
    {
        @Override
        public void run()
        {
            Log.d(TAG, "Taking picture");
            showToast("Taking photo");
            if (camera!=null)
            {
                //tofix
                camera.takePicture(today, 1, 2, 3, 4);
            }
            handler.postDelayed(run1, 100);
        }
    };

    private Runnable run1 = new Runnable()
    {
        @Override
        public void run()
        {
            Log.d(TAG, "resetting");
            handler.postDelayed(run, interval);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        today = new Date();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewHR = (TextView) findViewById(R.id.HR);

        viewHRAverage = (TextView) findViewById(R.id.HRAverage);
        arosed = (TextView) findViewById(R.id.arousval);
        bar = (SeekBar) findViewById(R.id.arousbar);
        percentage = bar.getProgress();
        viewPicturesSecond = (TextView) findViewById(R.id.pictures);
        viewMissed = (TextView) findViewById(R.id.missed);
        viewTotal = (TextView) findViewById(R.id.total);
        camera = new MyCamera(context, (AutoFitTextureView) findViewById(R.id.texture));
        v = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
        remoteSensorManager = RemoteSensorManager.getInstance(context);


        arosed.setText(""+percentage);
        viewHR.setText(""+HR+"  Accuracy: -");
        viewHRAverage.setText("Average: " + HR);
        viewPicturesSecond.setText("Take picture every "+ (interval/1000.00)+ " s");
        viewMissed.setText("Unreliable readings: " + missed);
        viewTotal.setText("Total readings: " + readings);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                percentage = progress;
                arosed.setText(""+progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {

            }
        });

    }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        BusProvider.getInstance().register(context);
//        List<Sensor> sensors = RemoteSensorManager.getInstance(this).getSensors();
        camera.startBackgroundThread();
//
//        // When the screen is turned off and turned back on, the SurfaceTexture is already
//        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
//        // a camera and start preview from here (otherwise, we wait until the surface is ready in
//        // the SurfaceTextureListener).
        AutoFitTextureView tmp = camera.getmTexture();
        if (tmp.isAvailable()) {
            camera.openCamera(tmp.getWidth(), tmp.getHeight());
        } else {
            tmp.setSurfaceTextureListener(camera.getListener());
        }

        remoteSensorManager.startMeasurement();
        handler.postDelayed(run, interval);

    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(run);
        handler.removeCallbacks(run1);
        BusProvider.getInstance().register(context);
        camera.closeCamera();
        camera.stopBackgroundThread();
        if(dialogcheck!=null){
            dialogcheck.dismiss();
        }
        remoteSensorManager.stopMeasurement();
    }



    private void notifyUSerForNewSensor(Sensor sensor) {
        Toast.makeText(context, "New sensor!\n" + sensor.getName(), Toast.LENGTH_SHORT).show();
    }

    @Subscribe
    public void onNewSensorEvent(final NewSensorEvent event) {
        notifyUSerForNewSensor(event.getSensor());
    }


    /**
     * show user dialog to ask for the emotional state of the user, which will be used to evaluate
     * whether the pictures create more vivid memories
     * TODO find a solution for taking picture, asking feedback, and then save it, right now if I ask feedback after the picture is taken it crashes
     */
    public void showPop(){
        handler.removeCallbacks(null);
        v.vibrate(500);
        // custom dialog
        final Dialog dialog = new Dialog(context);
        dialogcheck= dialog;
        dialog.setContentView(R.layout.wp);
        dialog.setTitle("HIgh Pulse Rate!");

        // set the custom dialog components - text, image and button
        TextView text = (TextView) dialog.findViewById(R.id.text);
        text.setText("UserFeedBack");
        ImageView image = (ImageView) dialog.findViewById(R.id.image);
        image.setImageResource(R.mipmap.ic_launcher);

        Button dialogButton = (Button) dialog.findViewById(R.id.dialogButtonOK);
        // if button is clicked, close the custom dialog
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                dialogcheck=null;
                handler.postDelayed(run, interval);
            }
        });

        dialog.show();
    }


    //HR EVENT
    @Subscribe
    public void onSensorUpdatedEvent(SensorUpdatedEvent event) {
        if(event.getSensor()!=null)
        {
            //names defined in com.pulsecam.data.SensorNames
            switch (event.getSensor().getName()){
                case "Accelerometer":
                    //do
                    break;
                case "Ambient temperatur":
                    //do
                    break;
                case "Game Rotation Vector":
                    //do
                    break;
                case "Geomagnetic Rotation Vector":
                    //do
                    break;
                case "Gravity":
                    //do
                    break;
                case "Gyroscope":
                    //do
                    break;
                case "Gyroscope (Uncalibrated)":
                    //do
                    break;
                //Relevant to PulseCam
                case "Heart Rate":
                    if (hRsensor == null)
                    {
                        hRsensor = event.getSensor();
                        handler.postDelayed(run, interval);
                        Log.d(TAG, "registering the sensor");
                    }
                    for (int i = 0; i < event.getDataPoint().getValues().length; ++i)
                    {
                        readings++;
                        if (event.getDataPoint().getAccuracy() != SensorManager.SENSOR_STATUS_UNRELIABLE && event.getDataPoint().getAccuracy() != SensorManager.SENSOR_STATUS_NO_CONTACT)
                        {
                            HR = event.getDataPoint().getValues()[i];
                            count++;
                            sum += event.getDataPoint().getValues()[i];
                            Log.d(TAG, "difference " + (HR));
                            Log.d(TAG, "difference " + (sum/count));
                            Log.d(TAG, "difference " + (HR/(sum/count)));
                            Log.d(TAG, "difference " + (HR/(sum/count)*100-100));
                            if ((HR/(sum/count)*100 -100) >= percentage)//calculate in percentage
                            {
                                Log.d(TAG, "Taking direct picture");
                                showToast("HIgh HR picture");
                                camera.takePicture(today,HR,1,1,1);
                                //ask for user input & append data to picture file
                                showPop();
                            }
                            interval = baseInterval + (((sum / count) - (((long) HR))) * 1000); // calculate new intevall
                            viewHR.setText("" + HR + "  Accuracy: " + event.getDataPoint().getAccuracy());
                            viewHRAverage.setText("Average: " + (sum / count));
                            viewPicturesSecond.setText("Take picture every " + (interval / 1000.00) + " s");

                        } else
                        {
                            Log.d(TAG, "not reliable data");
                            missed++;
                        }
                    }
                    viewMissed.setText("Unreliable readings: " + missed);
                    viewTotal.setText("Total readings: " + readings);
                    break;

                case "Light":
                    //do
                    break;
                case "Linear Acceleration":
                    //do
                    break;
                case "Magnetic Field":
                    //do
                    break;
                case "Magnetic Field (Uncalibrated)":
                    //do
                    break;
                case "Pressure":
                    //do
                    break;
                case "Proximity":
                    //do
                    break;
                case "Relative Humidity":
                    //do
                    break;
                case "Rotation Vector":
                    //do
                    break;
                case "Significant Motion":
                    //do
                    break;
                case "Step Counter":
                    //do
                    break;
                case "Step Detector":
                    //do
                    break;
                default:
                    //not a valid sensor
                    break;
            }

        }
    }
}


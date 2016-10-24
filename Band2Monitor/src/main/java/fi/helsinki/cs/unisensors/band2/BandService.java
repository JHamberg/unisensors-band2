package fi.helsinki.cs.unisensors.band2;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandAmbientLightEvent;
import com.microsoft.band.sensors.BandAmbientLightEventListener;
import com.microsoft.band.sensors.BandBarometerEvent;
import com.microsoft.band.sensors.BandBarometerEventListener;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandGyroscopeEvent;
import com.microsoft.band.sensors.BandGyroscopeEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandRRIntervalEvent;
import com.microsoft.band.sensors.BandRRIntervalEventListener;
import com.microsoft.band.sensors.HeartRateQuality;

import java.util.Locale;

import fi.helsinki.cs.unisensors.band2.io.AppendLogger;

@SuppressWarnings("FieldCanBeLocal")
public class BandService extends Service {
    private final String TAG = this.getClass().getSimpleName();
    private final String DELIM = ";";
    private final int ID = 32478611;
    private NotificationManager mNotificationManager;
    private String session = null;
    private int skinResponse, heartRate;
    private AppendLogger mGsrLogger, mHrLogger, mRrLogger, mGyroLogger,
            mAccLogger, mBaroLogger, mAmbientLogger;
    private float accX, accY, accZ;
    private float gyroaccX, gyroaccY, gyroaccZ, angvelX, angvelY, angvelZ;
    private double rrInterval;
    private double pressure, temperature;
    private int brightness;
    private boolean gsr = false;
    private boolean hr = false;
    private boolean rr = false;
    private boolean gyro = false;
    private boolean acc = false;
    private boolean baro = false;
    private boolean ambient = false;

    private BroadcastReceiver hrConsentReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            boolean consented = extras.getBoolean("consent");
            if(consented){
                Log.d(TAG, "User allowed monitoring Heart Rate");
                Band.registerHrListener(context, hrListener);
                Band.registerRriListener(context, rriListener);
            } else {
                Log.d(TAG, "User denied monitoring Heart Rate");
            }
        }
    };

    private BandGsrEventListener gsrListener = new BandGsrEventListener() {
        @Override
        public void onBandGsrChanged(final BandGsrEvent event) {
            if (event != null) {
                String t = event.getTimestamp()+"";
                skinResponse = event.getResistance();
                mGsrLogger.log(t, skinResponse+"");
                updateStatus();
            }
        }
    };
    private BandHeartRateEventListener hrListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                String t = event.getTimestamp()+"";
                int quality = 0;
                HeartRateQuality hrQuality = event.getQuality();
                if(hrQuality == HeartRateQuality.LOCKED){
                    quality = 1;
                }
                heartRate = event.getHeartRate();
                mHrLogger.log(t, heartRate+"", quality+"");
                updateStatus();
            }
        }
    };

    private BandRRIntervalEventListener rriListener = new BandRRIntervalEventListener() {
        @Override
        public void onBandRRIntervalChanged(final BandRRIntervalEvent event) {
            if (event != null) {
                String t = event.getTimestamp() + "";
                rrInterval = event.getInterval();
                mRrLogger.log(t, rrInterval+"");
                updateStatus();
            }
        }
    };

    private BandGyroscopeEventListener gyroListener = new BandGyroscopeEventListener() {
        @Override
        public void onBandGyroscopeChanged(BandGyroscopeEvent event) {
            if(event != null){
                String t = event.getTimestamp() + "";
                gyroaccX = event.getAccelerationX();
                gyroaccY = event.getAccelerationY();
                gyroaccZ = event.getAccelerationZ();
                angvelX = event.getAngularVelocityX();
                angvelY = event.getAngularVelocityY();
                angvelZ = event.getAngularVelocityZ();
                mGyroLogger.log(t, gyroaccX +"", gyroaccY +"", gyroaccZ +"",
                                angvelX+"", angvelY +"", angvelZ +"");
            }
        }
    };

    private BandAccelerometerEventListener accListener = new BandAccelerometerEventListener() {
        @Override
        public void onBandAccelerometerChanged(BandAccelerometerEvent event) {
            if(event != null){
                String t = event.getTimestamp() + "";
                accX = event.getAccelerationX();
                accY = event.getAccelerationY();
                accZ = event.getAccelerationZ();
                mAccLogger.log(t, accX+"", accY+"", accZ+"");
            }
        }
    };

    private BandBarometerEventListener baroListener = new BandBarometerEventListener() {
        @Override
        public void onBandBarometerChanged(BandBarometerEvent event) {
            if(event != null){
                String t = event.getTimestamp() + "";
                pressure = event.getAirPressure();
                temperature = event.getTemperature();
                mBaroLogger.log(t, pressure+"", temperature+"");
            }
        }
    };

    private BandAmbientLightEventListener ambientListener = new BandAmbientLightEventListener() {
        @Override
        public void onBandAmbientLightChanged(BandAmbientLightEvent event) {
            if(event != null){
                String t = event.getTimestamp() + "";
                brightness = event.getBrightness();
                mAmbientLogger.log(t, brightness+"");
                updateStatus();
            }
        }
    };

    @Override
    public void onCreate() {
        registerReceiver(hrConsentReceiver, new IntentFilter(Band.CONSENT));
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Band.disconnect();
        unregisterReceiver(hrConsentReceiver);
        mNotificationManager.cancel(ID);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(ID, getPersistentServiceNotification("Initializing.."));
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if(intent.hasExtra("session")){
            session = intent.getStringExtra("session");
        }
        if(intent.hasExtra("sensors")){
            boolean[] selection = intent.getBooleanArrayExtra("sensors");
            registerListeners(selection);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void registerListeners(boolean[] selection){
        Context baseContext = getBaseContext();
        gsr = selection[0];
        hr = selection[1];
        rr = selection[2];
        gyro = selection[3];
        acc = selection[4];
        baro = selection[5];
        ambient = selection[6];

        String name = session == null || session.isEmpty() ?
                System.currentTimeMillis() + "" : session;
        if(gsr) {
            Band.registerGsrListener(baseContext, gsrListener);
            mGsrLogger = new AppendLogger(baseContext, "gsr", name, DELIM);
        } if(hr) {
            Band.registerHrListener(baseContext, hrListener);
            mHrLogger = new AppendLogger(baseContext, "hr", name, DELIM);
        } if(rr) {
            Band.registerRriListener(baseContext, rriListener);
            mRrLogger = new AppendLogger(baseContext, "rr", name, DELIM);
        } if(gyro) {
            Band.registerGyroListener(baseContext, gyroListener);
            mGyroLogger = new AppendLogger(baseContext, "gyro", name, DELIM);
        } if(acc) {
            Band.registerAccListener(baseContext, accListener);
            mAccLogger = new AppendLogger(baseContext, "acc", name, DELIM);
        } if(baro) {
            Band.registerBaroListener(baseContext, baroListener);
            mBaroLogger = new AppendLogger(baseContext, "baro", name, DELIM);
        } if(ambient) {
            Band.registerAmbientListener(baseContext, ambientListener);
            mAmbientLogger = new AppendLogger(baseContext, "ambient", name, DELIM);
        }

        // Show something even if we are not logging live values
        if(!gsr && !hr && !rr){
            String status = "Logging...";
            mNotificationManager.notify(ID, getPersistentServiceNotification(status));
        }
    }

    public Notification getPersistentServiceNotification(String status){
        Context appContext = getApplicationContext();
        Intent notificationIntent = new Intent(appContext, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent activityIntent = PendingIntent.getActivity(appContext, 0, notificationIntent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext)
                .setSmallIcon(R.drawable.ic_watch_white_24dp)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(status)
                .setOngoing(true)
                .setContentIntent(activityIntent);
        return builder.build();
    }

    private void updateStatus(){
        String status = "Ambient : " + brightness;
                /*
                (gsr ? "GSR: " + skinResponse + " k\u2126 ": "") +
                (hr ? "HR: " + heartRate + " ": "") +
                (rr ? String.format(Locale.US, "RR: %.2f ", rrInterval) : ""); // +
                // (baro ? "B: " + pressure + " " : "");*/
        mNotificationManager.notify(ID, getPersistentServiceNotification(status));
    }

}

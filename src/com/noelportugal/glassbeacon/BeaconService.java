package com.noelportugal.glassbeacon;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.Utils;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.widget.CardBuilder;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class BeaconService extends Service implements SensorEventListener{
	private Handler handler;
	private SensorManager sensorManager;
	private Sensor stepSensor;
	private BeaconManager beaconManager;
	private Region houseRegion;
	private Beacon officeBeacon;
	private Beacon kitchenBeacon;
	private Beacon bedroomBeacon;

	private enum BeaconState {INSIDE, OUTSIDE};
	private BeaconState officeState;
	private BeaconState kitchenState;
	private BeaconState bedroomState;
	private LiveCard liveCard;
	
	private static final String TAG = "BeaconService";
	
	private static final String ESTIMOTE_PROXIMITY_UUID = "b9407f30-f5f8-466e-aff9-25556b57fe6d";

	private static final int officeMajor = 36941;
	private static final int officeMinor = 33845;

	private static final int kitchenMajor = 32789;
	private static final int kitchenMinor = 44173;

	private static final int bedroomMajor = 54060;
	private static final int bedroomMinor = 38916;

	private static final double enterThreshold = 1.5;
	private static final double exitThreshold = 2.5;

	private static Boolean listening = false;

	private static final String audioURL = "https://wearhacksmusic.blob.core.windows.net/asset-27608814-c12a-40d8-ab04-d5a631936e87/ACDC%20-%20You%20Shook%20Me%20All%20Night%20Long.mp4?sv=2012-02-12&sr=c&si=0462accd-c2f2-48ce-802a-f43c67f131f8&sig=HUqZaCAPgLm7uzovyi4FjJxG3s2TGoj1k7x1b2j3vdE%3D&st=2014-09-28T04%3A02%3A18Z&se=2016-09-27T04%3A02%3A18Z";
	
	public void playAudio(String url){
        MediaPlayer mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        Uri uri = Uri.parse(url);
        try {
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(new  MediaPlayer.OnCompletionListener() { 
            public  void  onCompletion(MediaPlayer mediaPlayer) { 
                listening = false;
            } 
        });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	private void runOnUiThread(Runnable runnable) {
		handler.post(runnable);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		handler = new Handler();


		// TODO add sensor data to stop/start beacon scanning
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
		sensorManager.registerListener(this, stepSensor,SensorManager.SENSOR_DELAY_NORMAL);

		officeState = BeaconState.OUTSIDE;
		kitchenState = BeaconState.OUTSIDE;
		bedroomState = BeaconState.OUTSIDE;

		houseRegion = new Region("regionId", ESTIMOTE_PROXIMITY_UUID, null, null);
		beaconManager = new BeaconManager(getApplicationContext());

		// Default values are 5s of scanning and 25s of waiting time to save CPU cycles.
		// In order for this demo to be more responsive and immediate we lower down those values.
		//beaconManager.setBackgroundScanPeriod(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(25));
		beaconManager.setForegroundScanPeriod(TimeUnit.SECONDS.toMillis(5), TimeUnit.SECONDS.toMillis(10));
		beaconManager.setRangingListener(new BeaconManager.RangingListener() {
			@Override
			public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						for (Beacon beacon : beacons) {
							Log.d(TAG, "MAC = " + beacon.getMacAddress() + ", RSSI = " + -beacon.getRssi() + ", UUID = " + beacon.getProximityUUID());
							if (beacon.getMajor() == officeMajor && beacon.getMinor() == officeMinor ){
								officeBeacon = beacon;
							}
							if (beacon.getMajor() == kitchenMajor && beacon.getMinor() == kitchenMinor){
								kitchenBeacon = beacon;
							}
							if (beacon.getMajor() == bedroomMajor && beacon.getMinor() == bedroomMinor){
								bedroomBeacon = beacon;
							}
						}

						
						if (officeBeacon != null){
							double officeDistance = Utils.computeAccuracy(officeBeacon);
							Log.d(TAG, "officeDistance: " + officeDistance);
							
							if(!listening) {
								//playAudio(audioURL);
							}
							
							if (officeDistance < enterThreshold && officeState == BeaconState.OUTSIDE){
								officeState = BeaconState.INSIDE;
								showNotification("You are in the office");
							}else if (officeDistance > exitThreshold && officeState == BeaconState.INSIDE){
								officeState = BeaconState.OUTSIDE;
								showNotification("You left the office");
							}
						}
						
						if (kitchenBeacon != null){
							double kitchenDistance = Utils.computeAccuracy(kitchenBeacon);
							Log.d(TAG, "kitchenDistance: " + kitchenDistance);
							if (kitchenDistance < enterThreshold && kitchenState == BeaconState.OUTSIDE){
								kitchenState = BeaconState.INSIDE;
								showNotification("You are in the kitchen");
							}else if (kitchenDistance > exitThreshold && kitchenState == BeaconState.INSIDE){
								kitchenState = BeaconState.OUTSIDE;
								showNotification("You left the kitchen");
							}
						}
						
						if (bedroomBeacon != null){
							double bedroomDistance = Utils.computeAccuracy(bedroomBeacon);
							Log.d(TAG, "bedroomDistance: " + bedroomDistance);
							if (bedroomDistance < enterThreshold && bedroomState == BeaconState.OUTSIDE){
								bedroomState = BeaconState.INSIDE;
								showNotification("You are in the bedroom");
							}else if (bedroomDistance > exitThreshold && bedroomState == BeaconState.INSIDE){
								bedroomState = BeaconState.OUTSIDE;
								showNotification("You left the bedroom");
							}
						}


					}
				});
			}
		});
	}

	private void startScanning(){
		beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
			@Override
			public void onServiceReady() {
				try {
					//beaconManager.startMonitoring(houseRegion);
					beaconManager.startRanging(houseRegion);
				} catch (RemoteException e) {
					Log.d(TAG, "Error while starting Ranging");
				}
			}
		});
	}

	private void stopScanning(){
		try {
			//beaconManager.stopMonitoring(houseRegion);
			beaconManager.stopRanging(houseRegion);
		} catch (RemoteException e) {
			Log.e(TAG, "Cannot stop but it does not matter now", e);
		}
	}

	public static Bitmap getBitmap(String url) {
	    try {
	    	Log.d(TAG, "Downloading image");
	        InputStream is = (InputStream) new URL(url).getContent();
	        Bitmap d = BitmapFactory.decodeStream(is);
	        is.close();
	        return d;
	    } catch (Exception e) {
	        return null;
	    }
	}
	
	private void showNotification(String msg) {
		Log.d(TAG, msg);
		
		Calendar c = Calendar.getInstance(); 
		int seconds = c.get(Calendar.SECOND);
		
		String imgUrl = "http://resources3.news.com.au/images/2011/05/13/1226055/070087-italy-mona-lisa.jpg";
		RemoteViews view1 = new CardBuilder(getApplication(), CardBuilder.Layout.COLUMNS)
	    .setText("This is the COLUMNS layout with dynamic text.")
	    .setFootnote("This is the footnote")
	    .addImage(getBitmap(imgUrl))
	    .getRemoteViews();
		
		
		RemoteViews views = new RemoteViews(getPackageName(), R.layout.livecard_beacon);
		views.setTextViewText(R.id.livecard_content,msg);
		liveCard = new LiveCard(getApplication(),"beacon");
		//liveCard.setViews(views);
		liveCard.setViews(view1);
		Intent intent = new Intent(getApplication(), BeaconService.class);
		liveCard.setAction(PendingIntent.getActivity(getApplication(), 0, intent, 0));
		liveCard.publish(LiveCard.PublishMode.REVEAL);
		
		
	}



	@Override
	public void onDestroy() {
		super.onDestroy();
		beaconManager.disconnect();
	}
	
	

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		ArrayList<String> voiceResults = intent.getExtras().getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
		for (String voice : voiceResults) {
			Log.d(TAG,"voiceResults:voice = " + voice);
			if (voice.contains("stop")){
				Log.d(TAG,"stopScanning");
				stopScanning();
			}else if (voice.contains("start")){
				Log.d(TAG,"startScanning");
				startScanning();
			}else{
				Log.d(TAG,"couldnt understand so lets start anyway");
				Log.d(TAG,"startScanning");
				startScanning();
			}
		}
		
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub

	}

}

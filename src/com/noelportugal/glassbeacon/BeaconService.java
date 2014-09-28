package com.noelportugal.glassbeacon;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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
	private static final String TAGWEAR = "BeaconServiceWear";
	private static final String ESTIMOTE_PROXIMITY_UUID = "b9407f30-f5f8-466e-aff9-25556b57fe6d";

	private static final int officeMajor = 55555;
	private static final int officeMinor = 11111;

	private static final int kitchenMajor = 32789;
	private static final int kitchenMinor = 44173;

	private static final int bedroomMajor = 54060;
	private static final int bedroomMinor = 38916;

	private static final double enterThreshold = 1.0;
	private static final double exitThreshold = 2.5;

	private static Boolean listening = false;
	JSONObject jObject = null;
	
	
	private static final String audioURL = "https://wearhacksmusic.blob.core.windows.net/asset-27608814-c12a-40d8-ab04-d5a631936e87/ACDC%20-%20You%20Shook%20Me%20All%20Night%20Long.mp4?sv=2012-02-12&sr=c&si=0462accd-c2f2-48ce-802a-f43c67f131f8&sig=HUqZaCAPgLm7uzovyi4FjJxG3s2TGoj1k7x1b2j3vdE%3D&st=2014-09-28T04%3A02%3A18Z&se=2016-09-27T04%3A02%3A18Z";
	MediaPlayer mediaPlayer;
	
	public void playAudio(String url){
		Log.d(TAGWEAR, "Will start audio");
		listening = true;
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        Uri uri = Uri.parse(url);
        try {
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.prepare();
            Log.d(TAGWEAR, "Will start checking for new slide");
            CheckForNewSlide(mediaPlayer);
            
            
            mediaPlayer.setOnCompletionListener(new  MediaPlayer.OnCompletionListener() { 
	            public  void  onCompletion(MediaPlayer mediaPlayer) { 
	                listening = false;
	                Log.d(TAGWEAR, "stopped audio");
	                Log.d(TAGWEAR, "removing cards");
	                for (Iterator iterator = publishedcards.iterator(); iterator
							.hasNext();) {
						LiveCard card = (LiveCard) iterator.next();
						card.unpublish();
						
					}
	            }
	        });
            Log.d(TAGWEAR, "start play");
            mediaPlayer.start();
        } catch (IOException e) {
        	Log.d(TAGWEAR,"error when trying to open media player ="+url);
            e.printStackTrace();
        }
    }
	
	String CurrentCardId;
	public void CheckForNewSlide(final MediaPlayer mediaPlayer) {
		 // SLEEP 2 SECONDS HERE ...
		
   	 Log.d(TAGWEAR, "checking for currentposition");
	 if(mediaPlayer != null) {
		 int currentPosition = mediaPlayer.getCurrentPosition();
    	 Log.d(TAGWEAR, "Current position mediaplayer : " + currentPosition);
    	 try {
    		 Log.d(TAGWEAR, "searching for new slide : " + currentPosition);
    		 JSONArray cards = jObject.getJSONArray("cards");
        	 for (int i = 0; i < 1; i++) {
        		 JSONObject card = cards.getJSONObject(i);
        		 String cardImageId = card.getString("imageUrl");
        		 if(card.getInt("time") <= currentPosition && cardImageId != CurrentCardId) {
        			 CurrentCardId = cardImageId;
        			 showNotification(card.getString("text"), cardImageId);
        		 }
        	 }
    	 } catch(Exception ex)
    	 {
    		 Log.d(TAGWEAR, "something went wrong while checking for slide.");
    	 }
    	 
	 }
		
//        Handler handler = new Handler(); 
//             handler.run(new Runnable() { 
//             public void run() { 
//            	 Log.d(TAGWEAR, "checking for currentposition");
//            	 if(mediaPlayer != null) {
//            		 int currentPosition = mediaPlayer.getCurrentPosition();
//                	 Log.d(TAGWEAR, "Current position mediaplayer : " + currentPosition);
//                	 try {
//                		 Log.d(TAGWEAR, "searching for new slide : " + currentPosition);
//                		 JSONArray cards = jObject.getJSONArray("cards");
//                    	 for (int i = 0; i < cards.length(); i++) {
//                    		 JSONObject card = cards.getJSONObject(i);
//                    		 String cardImageId = card.getString("imageUrl");
//                    		 if(card.getInt("time") <= currentPosition && cardImageId != CurrentCardId) {
//                    			 CurrentCardId = cardImageId;
//                    			 showNotification(card.getString("text"), cardImageId);
//                    		 }
//                    	 }
//                	 } catch(Exception ex)
//                	 {
//                		 Log.d(TAGWEAR, "something went wrong while checking for slide.");
//                	 }
//                	 
//            	 }
//            	 
//            	 
//            	 //TODO: check if we need to change slide.
//            	 
//            	 CheckForNewSlide(mediaPlayer); 
//             } 
//        }, 2000); 
        
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	private void runOnUiThread(Runnable runnable) {
		handler.post(runnable);
	}

	boolean ttt = true;
	
	@Override
	public void onCreate() {
		super.onCreate();

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		WakeLock wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK , TAG);
		wakeLock.acquire(1000 * 60 * 2);
		
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
		beaconManager.setForegroundScanPeriod(TimeUnit.SECONDS.toMillis(2), TimeUnit.SECONDS.toMillis(1));
		beaconManager.setRangingListener(new BeaconManager.RangingListener() {
			@Override
			public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						for (Beacon beacon : beacons) {
							Log.d(TAG, "Major = " + beacon.getMajor() + ", Minor = " + beacon.getMinor() + ", UUID = " + beacon.getProximityUUID());
							if(beacon.getProximityUUID() == ESTIMOTE_PROXIMITY_UUID) {
								Log.d(TAG, "Major = " + beacon.getMajor() + ", Minor = " + beacon.getMinor() + ", UUID = " + beacon.getProximityUUID());
							}
							if (beacon.getMajor() == officeMajor && beacon.getMinor() == officeMinor ){
								officeBeacon = beacon;
								Log.d(TAG, "Found group38 beacon");
							}
						}
						
						
						
						if (officeBeacon != null){
							double officeDistance = Utils.computeAccuracy(officeBeacon);
							Log.d(TAGWEAR, "Monalisa Distance: " + officeDistance);
							
							if (officeDistance < enterThreshold){
								officeState = BeaconState.INSIDE;
								Log.d(TAGWEAR, "Close to monalisa");
								if(!listening && ttt) {
									ttt = false;
									Log.d(TAG, "will start playing");
									new SendPostTask(ESTIMOTE_PROXIMITY_UUID).execute();
									
								}
								
								//showNotification("You are in the office");
							}else if (officeDistance > exitThreshold && officeState == BeaconState.INSIDE){
								officeState = BeaconState.OUTSIDE;
								//showNotification("You left the office");
							}
						}
						
						/*if (kitchenBeacon != null){
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
						}*/


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

	private void Dispose() {
		Log.e(TAGWEAR, "Disposing ");
		if(beaconManager != null)
			try {
				beaconManager.stopRanging(houseRegion);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		CurrentCardId = null;
		listening = false;
		
		if(mediaPlayer != null)
			mediaPlayer.stop();
		
		if(publishedcards != null) {
			for (Iterator iterator = publishedcards.iterator(); iterator.hasNext();) {
				LiveCard card = (LiveCard) iterator.next();
				card.unpublish();
			}
		}
		
	}
	
	private void stopScanning(){
		//beaconManager.stopMonitoring(houseRegion);
		Dispose();
	}
	
	private static String convertStreamToString(InputStream is) {
	    /*
	     * To convert the InputStream to String we use the BufferedReader.readLine()
	     * method. We iterate until the BufferedReader return null which means
	     * there's no more data to read. Each line will appended to a StringBuilder
	     * and returned as String.
	     */
	    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
	    StringBuilder sb = new StringBuilder();

	    String line = null;
	    try {
	        while ((line = reader.readLine()) != null) {
	            sb.append(line + "\n");
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    } finally {
	        try {
	            is.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	    return sb.toString();
	}
	
	public Bitmap getBitmapFromURL(String imageUrl) {
		try {
			URL url = new URL(imageUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);
			return myBitmap;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	List<LiveCard> publishedcards;
	private void showNotification(String msg, String imageStringId) {
		
		Log.d(TAGWEAR,"showNotification msg: " + msg);
		Log.d(TAGWEAR,"showNotification imageStringId: " + imageStringId);
		if(msg.isEmpty()) msg = "Le Louvre - Paris" +
				". All Rights Reserved.";
		
		int imageId = R.drawable.image1;
		if(imageStringId.compareToIgnoreCase("image1") == 0) {
			imageId = R.drawable.image1;
		} else  if(imageStringId.compareToIgnoreCase("image2") == 0) {
			imageId = R.drawable.image2;
		} else if(imageStringId.compareToIgnoreCase("image3") == 0) {
			imageId = R.drawable.image3;
		} else if(imageStringId.compareToIgnoreCase("image4") == 0) {
			imageId = R.drawable.image4;
		} 
		
		Log.d(TAG, msg);
		
		RemoteViews view1 = new CardBuilder(getApplication(), CardBuilder.Layout.COLUMNS)
	    .setText(msg)
	    //.setFootnote(msg)
	    .addImage(imageId)
	    .getRemoteViews();
		
		/*Log.d(TAG, "Creating view");
		RemoteViews views = new RemoteViews(getPackageName(), R.layout.livecard_beacon);
		Log.d(TAG, "setting text and image url");
		views.setTextViewText(R.id.livecard_content,msg);
		views.setImageViewUri(R.id.livecard_image, Uri.parse(imgUrl));*/
		
		liveCard = new LiveCard(getApplication(),"beacon");
		
		if(publishedcards == null) publishedcards = new ArrayList<LiveCard>();
		
		if(liveCard != null) publishedcards.add(liveCard);
		
		Log.d(TAG, "Setting view");
		//liveCard.setViews(views);
		liveCard.setViews(view1);
		Intent intent = new Intent(getApplication(), BeaconService.class);
		liveCard.setAction(PendingIntent.getActivity(getApplication(), 0, intent, 0));
		Log.d(TAG, "before publishing view");
		liveCard.publish(LiveCard.PublishMode.REVEAL);
		Log.d(TAG, "after publishing view");
		
		
		
	}



	@Override
	public void onDestroy() {
		super.onDestroy();
		beaconManager.disconnect();
		Dispose();
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
				//(new SendPostTask(ESTIMOTE_PROXIMITY_UUID)).execute();
				startScanning();
			}else{
				Log.d(TAG,"couldnt understand so lets start anyway");
				Log.d(TAG,"startScanning");
				//(new SendPostTask(ESTIMOTE_PROXIMITY_UUID)).execute();
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
	
	private class SendPostTask extends AsyncTask<Void, Void, Void> {

		String uuid;
		String BASE_URL = "http://wearhacks38.herokuapp.com/items/";
		public SendPostTask(String uuid) {
			this.uuid = uuid;
		}
		
	    @Override
	    protected Void doInBackground(Void... params) {
	            // Make your request POST here. Example:
	    	HttpClient httpclient = new DefaultHttpClient();
	    	 
	    	Log.d(TAG,"Requesting : "+ BASE_URL + uuid);
	        // Prepare a request object
	        HttpGet httpget = new HttpGet(BASE_URL + uuid);
	 
	        // Execute the request
	        HttpResponse response;
	        try {
	            response = httpclient.execute(httpget);
	            // Examine the response status
	            Log.d(TAG,response.getStatusLine().toString());
	 
	            // Get hold of the response entity
	            HttpEntity entity = response.getEntity();
	            // If the response does not enclose an entity, there is no need
	            // to worry about connection release
	 
	            if (entity != null) {
	 
	                // A Simple JSON Response Read
	                InputStream instream = entity.getContent();
	                String result= convertStreamToString(instream);
	                //result = "{\"id\":\"b9407f30-f5f8-466e-aff9-25556b57fe6d\",\"name\":\"Mona Lisa\",\"images\":[{\"url\":\"image1\",\"time\":0, \"text\":\"hello monalisa\"},{\"url\":\"image2\",\"time\":50000, \"text\":\"hello leonardo\"}, {\"url\":\"image3\",\"time\":80000, \"text\":\"hello monalisa\"}, {\"url\":\"image4\",\"time\":120000, \"text\":\"hello monalisa\"}],\"audioUrl\":\"https://wearhacksmusic.blob.core.windows.net/asset-27608814-c12a-40d8-ab04-d5a631936e87/ACDC%20-%20You%20Shook%20Me%20All%20Night%20Long.mp4?sv=2012-02-12&sr=c&si=0462accd-c2f2-48ce-802a-f43c67f131f8&sig=HUqZaCAPgLm7uzovyi4FjJxG3s2TGoj1k7x1b2j3vdE%3D&st=2014-09-28T04%3A02%3A18Z&se=2016-09-27T04%3A02%3A18Z\"}";
	                Log.d(TAGWEAR,result);
	                // now you have the string representation of the HTML request
	                instream.close();
	                jObject = new JSONObject(result);
	                if(jObject != null){
	                	Log.d(TAGWEAR,"jObject NOT null, will playAudio() : "+jObject.getString("audioUrl"));
	                	playAudio(jObject.getString("audioUrl"));
	                	
	                	JSONArray cards = jObject.getJSONArray("cards");
	                	int[] times = new int[cards.length()];
	                	String[] urls = new String[cards.length()];
	                	String[] texts = new String[cards.length()];
	                	for(int i = 0; i < cards.length(); i++){
	                		times[i] = cards.getJSONObject(i).getInt("time");
	                		urls[i] = cards.getJSONObject(i).getString("imageUrl");
	                		texts[i] = cards.getJSONObject(i).getString("text");
	                	}
	                	
	             
	                	showNotification(texts[0], urls[0]);
	                	Thread.sleep(times[1]);
	                	for(int i = 1; i < times.length - 1; i++){
	                		showNotification(texts[i], urls[i]);
	                		if(i <= times.length){
	                			Thread.sleep(times[i + 1] - times[i]);
	                		}
	                	}
	                	showNotification(texts[times.length - 1], urls[times.length - 1]);
	                	//showNotification(texts[times.length], imageStringId)
//	                	while(listening){
//	                		Log.d(TAGWEAR, "checking for currentposition");
//	   	               	 if(mediaPlayer != null) {
//	   	               		 int currentPosition = mediaPlayer.getCurrentPosition();
//	   	                   	 Log.d(TAGWEAR, "Current position mediaplayer : " + currentPosition);
//	   	                   	 try {
//	   	                   		 Log.d(TAGWEAR, "searching for new slide : " + currentPosition);
//	   	                   		 JSONArray cards = jObject.getJSONArray("cards");
//	   	                       	 for (int i = 0; i < cards.length(); i++) {
//	   	                       		 JSONObject card = cards.getJSONObject(i);
//	   	                       		 String cardImageId = card.getString("imageUrl");
//	   	                       		 if(card.getInt("time") <= currentPosition && cardImageId != CurrentCardId) {
//	   	                       			 CurrentCardId = cardImageId;
//	   	                       			 showNotification(card.getString("text"), cardImageId);
//	   	                       		 }
//	   	                       	 }
//	   	                   	 } catch(Exception ex)
//	   	                   	 {
//	   	                   		 Log.d(TAGWEAR, "something went wrong while checking for slide.");
//	   	                   	 }
//	   	                   	 
//	   	               	 }	
//	   	               	 Thread.sleep(3000);
//	                	}
	                	
	                
	                	
	                	
	                }
	                	
	                else 
	                	Log.d(TAGWEAR,"jObject is null");
	            }
	        } catch (Exception e) {Log.i("TAGWEAR","something went wrong with the http request");
	        e.printStackTrace();}
	            
	        return null;
	    }

	    protected void onPostExecute(Void result) {
	      // Do something when finished.
	    }
	}
}


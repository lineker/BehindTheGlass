package com.noelportugal.glassbeacon;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class LiveCardMenuActivity extends Activity{
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.d("FFFFF", "ucckkk");
		openOptionsMenu();
	}
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu, menu);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
	    switch (item.getItemId()) {
	        case R.id.play:
	            //gotoMain();
	            Log.d("MENUACTIVITY", "Play");
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
}

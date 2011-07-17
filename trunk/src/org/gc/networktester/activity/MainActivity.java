/*
 *
 * Copyright (C) 2011 Guillaume Cottenceau.
 *
 * Android Network Tester is licensed under the Apache 2.0 license.
 *
 */

package org.gc.networktester.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gc.networktester.R;
import org.gc.networktester.tester.Download100kbTester;
import org.gc.networktester.tester.Download10kbTester;
import org.gc.networktester.tester.Download1mbTester;
import org.gc.networktester.tester.HostResolutionTester;
import org.gc.networktester.tester.RealWebTester;
import org.gc.networktester.tester.TcpConnectionTester;
import org.gc.networktester.tester.Tester;
import org.gc.networktester.util.Log;
import org.gc.networktester.util.Util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView textNetworkType;
    private ImageView imageNetworkType;
    private Button buttonStartStop;
    
    private List<Tester> testers;
    
    private boolean running = false;
    private Integer networkType;
    private AlertDialog dialog = null;
    
    private volatile boolean wantStop = false;
    
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        
        super.onCreate( savedInstanceState );
        
        testers = new ArrayList<Tester>();
        testers.add( new HostResolutionTester() );
        testers.add( new TcpConnectionTester() );
        testers.add( new RealWebTester() );
        testers.add( new Download10kbTester() );
        testers.add( new Download100kbTester() );
        testers.add( new Download1mbTester() );

        setContentView( R.layout.main );
        setupViews();
        
        // disable java level DNS caching
        System.setProperty( "networkaddress.cache.ttl", "0" );
        System.setProperty( "networkaddress.cache.negative.ttl", "0" );
    }
    
    private void setupViews() {
        textNetworkType = (TextView) findViewById( R.id.main__text_network_type );
        imageNetworkType = (ImageView) findViewById( R.id.main__image_network_type );
        updateNetworkType();
        buttonStartStop = (Button) findViewById( R.id.main__button_startstop );
        buttonStartStop.setOnClickListener( new OnClickListener() {
            public void onClick( View v ) {
                if ( running ) {
                    wantStop = true;
                } else {
                    running = true;
                    wantStop = false;
                    launch();
                }
            }
        } );
        
        for ( Tester tester : testers ) {
            tester.setupViews( this );
        }
        
    }
    
    private void updateNetworkType() {
        NetworkInfo netinfo
            = ( (ConnectivityManager) getSystemService( Context.CONNECTIVITY_SERVICE ) ).getActiveNetworkInfo();
        String type;
        if ( netinfo == null ) {
            type = getString( R.string.network_unknown );
            networkType = null;
        } else {
            type = netinfo.getSubtypeName().length() == 0 ? netinfo.getTypeName()
                                                          : netinfo.getTypeName() + "/" + netinfo.getSubtypeName();
            networkType = netinfo.getType();
            if ( networkType == ConnectivityManager.TYPE_WIFI ) {
                imageNetworkType.setImageResource( R.drawable.wifi );
            } else if ( networkType == ConnectivityManager.TYPE_MOBILE ) {
                imageNetworkType.setImageResource( R.drawable.mobile );
            } else if ( networkType == 6 ) {  // ConnectivityManager.TYPE_WIMAX since API level 8
                imageNetworkType.setImageResource( R.drawable.wimax );
            }
        }
        textNetworkType.setText( getString( R.string.network_type, type ) );        
    }
    
    public Integer getNetworkType() {
        return networkType;
    }
    
    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        getMenuInflater().inflate( R.menu.main, menu );
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        switch ( item.getItemId()) {
        case R.id.menu_help:
            dialog = Util.createDialog( this, R.string.menu_help_content );
            break;
        case R.id.menu_about:
            dialog = Util.createDialog( this, R.string.menu_about_content ); 
            break;
        case R.id.menu_feedback:
            // Open mail composer
            final Intent sendIntent = new Intent( Intent.ACTION_VIEW );         
            sendIntent.setData( Uri.parse( "mailto:gcottenc@gmail.com" ) );
            sendIntent.putExtra( "subject", "Android Network Tester feedback" );
            try {
                startActivity( sendIntent );
            } catch ( Exception e ) {
                Toast.makeText( this, R.string.error_failed_mail_composer, Toast.LENGTH_LONG ).show();
            }
        }
        return super.onOptionsItemSelected( item );
    }
    
    private void launch() {
        updateNetworkType();  // update for in case app was launched kinda long ago and network has changed
        final Map<Tester, Boolean> areActive = new HashMap<Tester, Boolean>();
        for ( Tester tester : testers ) {
            tester.prepareTest();
            areActive.put( tester, tester.isActive() );
        }
        buttonStartStop.setText( R.string.stop_tests );
        new Thread() {
            @Override
            public void run() {
                try {
                    for ( Tester tester : testers ) {
                        if ( areActive.get( tester ) ) {
                            Log.debug( "Launch test " + tester );
                            if ( ! tester.performTest() || wantStop ) {
                                return;
                            }
                        }
                    }
                } finally {
                    runOnUiThread( new Thread() { public void run() {
                        running = false;
                        for ( Tester tester : testers ) {
                            tester.cleanupTests();
                        }
                        buttonStartStop.setText( R.string.start_tests );
                    } } );
                }
            }
        }.start();
    }
 
    public void onPause() {
        // need to detach existing popup windows before pausing/destroying activity
        // (screen orientation change, for example)
        if ( dialog != null ) {
            dialog.dismiss();
        }
        wantStop = true;
        for ( Tester tester : testers ) {
            tester.onPause();
        }
        SharedPreferences prefs = getPreferences( Context.MODE_PRIVATE );
        SharedPreferences.Editor editor = prefs.edit();
        for ( Tester tester : testers ) {
            editor.putBoolean( tester.getClass().getName() + ".isActive", tester.isActive() );
        }
        editor.commit();
        super.onPause();
    }
    
    public void onResume() {
        super.onResume();
        Map<String, ?> prefs = getPreferences( Context.MODE_PRIVATE ).getAll();
        for ( Tester tester : testers ) {
            Boolean value = (Boolean) prefs.get( tester.getClass().getName() + ".isActive" );
            if ( value != null ) {
                tester.setActive( value );
            }
        }
    }

    public boolean isWantStop() {
        return wantStop;
    }
    
}
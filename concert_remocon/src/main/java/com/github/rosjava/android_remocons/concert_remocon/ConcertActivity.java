/*
 * Copyright (C) 2013 Daniel Stonier.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.github.rosjava.android_remocons.concert_remocon;

import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import org.ros.address.InetAddressFactory;
import org.ros.android.RosActivity;
import org.ros.exception.RemoteException;
import org.ros.namespace.NameResolver;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import com.github.rosjava.android_remocons.concert_remocon.from_app_mng.ConcertAppsManager;
import com.github.rosjava.android_remocons.concert_remocon.from_app_mng.ConcertDescription;
import com.github.rosjava.android_remocons.concert_remocon.from_app_mng.ConcertNameResolver;

import rocon_app_manager_msgs.StopAppResponse;

/**
 * Design goal of this activity should be to handle almost everything
 * necessary for interaction with a concert/rocon app manager. This
 * involves direct interactions on services and topics, and also
 * necessary data transfer required for correct display of the
 * 'concert' screen in the ConcertRemocon.
 *
 * This used to be part of the old RosAppActivity, but
 * that used quite heavily a 'what am i' process to work
 * out whether it was an app or a controlling manager (appchooser
 * or remocon) with very separate workflows that didn't
 * take much advantage of code sharing.
 */
public abstract class ConcertActivity extends RosActivity {

	private String concertAppName = null;
	private String defaultConcertAppName = null;
	private String defaultConcertName = null;
    /*
      By default we assume the remocon has just launched independantly, however
      it can be launched upon the closure of one of its children applications.
     */
    protected boolean fromApplication = false;  // true if it is a remocon activity getting control from a closing application

	private int mainWindowId = 0;
	protected NodeConfiguration nodeConfiguration;
    protected NodeMainExecutor nodeMainExecutor;
	protected ConcertNameResolver concertNameResolver;
	protected ConcertDescription concertDescription;

	protected void setMainWindowResource(int resource) {
		mainWindowId = resource;
	}

	protected void setDefaultConcertName(String name) {
		defaultConcertName = name;
	}

	protected void setDefaultAppName(String name) {
        defaultConcertAppName = name;
	}

	protected ConcertActivity(String notificationTicker, String notificationTitle) {
		super(notificationTicker, notificationTitle);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(mainWindowId);

		concertNameResolver = new ConcertNameResolver();

		if (defaultConcertName != null) {
			concertNameResolver.setConcertName(defaultConcertName);
		}

		concertAppName = getIntent().getStringExtra(
				ConcertAppsManager.PACKAGE + ".concert_app_name");
		if (concertAppName == null) {
			concertAppName = defaultConcertAppName;
        } else if (concertAppName.equals("AppChooser")) { // ugly legacy identifier, it's misleading so change it sometime
            Log.i("ConcertRemocon", "reinitialising from a closing remocon application");
            fromApplication = true;
		} else {
			// DJS: do we need anything here? I think the first two cases cover everything
		}
	}

    /**
     * Start cooking! Init is run once either the master chooser has
     * finished and detected all the concert information it needs, or
     * it has returned from a remocon application. Either way, both
     * are guaranteed to return with a master uri and concert description.
     *
     * We use them here to kickstart everything else.
     *
     * @param nodeMainExecutor
     */
	@Override
	protected void init(NodeMainExecutor nodeMainExecutor) {
		this.nodeMainExecutor = nodeMainExecutor;
        nodeConfiguration = NodeConfiguration.newPublic(InetAddressFactory
                .newNonLoopback().getHostAddress(), getMasterUri());

        // concertDescription will get set by the concert master chooser as it exits
        // or passed back as an intent from a closing remocon application.
        // It should never be null!
        concertNameResolver.setConcert(concertDescription);
        nodeMainExecutor.execute(concertNameResolver,
                nodeConfiguration.setNodeName("concertNameResolver"));
        concertNameResolver.waitForResolver();
    }

	protected NameResolver getAppNameSpace() {
		return concertNameResolver.getAppNameSpace();
	}

	protected NameResolver getConcertNameSpaceResolver() {
		return concertNameResolver.getConcertNameSpace();
	}

    protected String getConcertNameSpace() {
        return concertNameResolver.getConcertNameSpace().getNamespace().toString();
    }

	protected void releaseConcertNameResolver() {
		nodeMainExecutor.shutdownNodeMain(concertNameResolver);
	}

	@Override
	protected void onDestroy() {
        Log.d("ConcertRemocon", "onDestroy()");
		super.onDestroy();
	}
}

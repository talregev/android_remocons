/*
 * Copyright (C) 2013 OSRF.
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

package com.github.rosjava.android_apps.application_management;

import android.util.Log;

import org.ros.exception.RosRuntimeException;
import org.ros.exception.ServiceNotFoundException;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseListener;
import org.ros.node.topic.Subscriber;

import rocon_app_manager_msgs.RappList;
import rocon_app_manager_msgs.GetRappList;
import rocon_app_manager_msgs.GetRappListRequest;
import rocon_app_manager_msgs.GetRappListResponse;
import rocon_app_manager_msgs.StartRapp;
import rocon_app_manager_msgs.StartRappRequest;
import rocon_app_manager_msgs.StartRappResponse;
import rocon_app_manager_msgs.StopRapp;
import rocon_app_manager_msgs.StopRappRequest;
import rocon_app_manager_msgs.StopRappResponse;

/**
 * This class implements the services and topics required to communicate
 * with the robot app manager. Typically to use this class its a three
 * step process:
 *
 * 1) provide a callback via one of the setXXX methods
 * 2) set the function type you want to call (e.g. start_app, platform_info)
 * 3) execute the app manager instance.
 *
 * INSTANCES MAY ONLY EVER BE EXECUTED ONCE!
 *
 * Essentially you are creating a node when creating an instance, and
 * rosjava isolates each service/topic to each 'node'.
 *
 * See the RosAppActivity or RobotActivity (in android_remocons) for
 * examples.
 *
 * TODO: (DJS) move these into the .rapp_manager module as separate classes.
 * Since these can only be executed once, there is no real advantage to having
 * them together and ultimately just makes it difficult to follow the code.
 */
public class AppManager extends AbstractNodeMain {

    // unique identifier to key string variables between activities.
	static public final String PACKAGE = "com.github.rosjava.android_apps.application_management.AppManager";
	private static final String startTopic = "start_app";
	private static final String stopTopic = "stop_app";
	private static final String listService = "list_apps";

	private String appName;
	private NameResolver resolver;
	private ServiceResponseListener<StartRappResponse> startServiceResponseListener;
	private ServiceResponseListener<StopRappResponse> stopServiceResponseListener;
	private ServiceResponseListener<GetRappListResponse> listServiceResponseListener;
    private MessageListener<RappList> appListListener;
	private Subscriber<RappList> subscriber;
	
	private ConnectedNode connectedNode;
	private String function = null;

	public AppManager(final String appName, NameResolver resolver) {
		this.appName = appName;
		this.resolver = resolver;
	}

	public AppManager(final String appName) {
		this.appName = appName;
	}

	public AppManager() {

	}

	public void setFunction(String function) {
		this.function = function;
	}
	
	public void setAppName(String appName) {
		this.appName = appName;
	}

    public void setAppListSubscriber(MessageListener<RappList> appListListener) {
        this.appListListener = appListListener;
    }

    public void setStartService(
			ServiceResponseListener<StartRappResponse> startServiceResponseListener) {
		this.startServiceResponseListener = startServiceResponseListener;
	}

	public void setStopService(
			ServiceResponseListener<StopRappResponse> stopServiceResponseListener) {
		this.stopServiceResponseListener = stopServiceResponseListener;
	}

	public void setListService(
			ServiceResponseListener<GetRappListResponse> listServiceResponseListener) {
		this.listServiceResponseListener = listServiceResponseListener;
	}

    public void continuouslyListApps() {
        subscriber = connectedNode.newSubscriber(resolver.resolve("app_list"),"rocon_app_manager_msgs/AppList");
        subscriber.addMessageListener(this.appListListener);
    }

    public void startApp() {
		String startTopic = resolver.resolve(this.startTopic).toString();

		ServiceClient<StartRappRequest, StartRappResponse> startAppClient;
		try {
			Log.d("ApplicationManagement", "start app service client created [" + startTopic + "]");
			startAppClient = connectedNode.newServiceClient(startTopic,
					StartRapp._TYPE);
		} catch (ServiceNotFoundException e) {
            Log.w("ApplicationManagement", "start app service not found [" + startTopic + "]");
			throw new RosRuntimeException(e);
		}
		final StartRappRequest request = startAppClient.newMessage();
		request.setName(appName);
		startAppClient.call(request, startServiceResponseListener);
		Log.d("ApplicationManagement", "start app service call done [" + startTopic + "]");
	}

	public void stopApp() {
		String stopTopic = resolver.resolve(this.stopTopic).toString();

		ServiceClient<StopRappRequest, StopRappResponse> stopAppClient;
		try {
			Log.d("ApplicationManagement", "Stop app service client created");
			stopAppClient = connectedNode.newServiceClient(stopTopic,
					StopRapp._TYPE);
		} catch (ServiceNotFoundException e) {
            Log.w("ApplicationManagement", "Stop app service not found");
            // not interested in handling this exception, just pass over it.
            return;
		}
		final StopRappRequest request = stopAppClient.newMessage();
		// request.setName(appName); // stop app name unused for now
		stopAppClient.call(request, stopServiceResponseListener);
		Log.d("ApplicationManagement", "Stop app service call done");
	}

	public void listApps() {
		String listService = resolver.resolve(this.listService).toString();
		
		ServiceClient<GetRappListRequest, GetRappListResponse> listAppsClient;
		try {
			Log.d("ApplicationManagement", "List app service client created [" + listService + "]");
			listAppsClient = connectedNode.newServiceClient(listService,
					GetRappList._TYPE);
		} catch (ServiceNotFoundException e) {
            Log.w("ApplicationManagement", "List app service not found [" + listService + "]");
			throw new RosRuntimeException(e);
		}
		final GetRappListRequest request = listAppsClient.newMessage();
		listAppsClient.call(request, listServiceResponseListener);
		Log.d("ApplicationManagement", "List apps service call done [" + listService + "]");
	}

    @Override
	public GraphName getDefaultNodeName() {
		return null;
	}

    /**
     * This provides a few ways to create and execute service/topic nodes with an app manager object.
     *
     * Note - you should only ever call (via NodeMainExecutor.execute() this once! It will fail
     * due to this instance being non-unique in the set of rosjava nodemains for this activity.
     *
     * @param connectedNode
     */
	@Override
	public void onStart(final ConnectedNode connectedNode) {
        if (this.connectedNode != null) {
            Log.e("ApplicationManagement", "app manager instances may only ever be executed once [" + function + "].");
            return;
        }
        this.connectedNode = connectedNode;
		if (function.equals("start")) {
			startApp();
		} else if (function.equals("stop")) {
			stopApp();
		} else if (function.equals("list")) {
			listApps();
		} else if (function.equals("list_apps")) {
            continuouslyListApps();
        }
	}
}

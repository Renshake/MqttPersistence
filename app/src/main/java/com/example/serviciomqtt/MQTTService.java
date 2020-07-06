package com.example.serviciomqtt;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttPingReq;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import static java.nio.charset.StandardCharsets.UTF_8;


public class MQTTService extends Service {

    private static final String TAG = "MQTTService";
    private static boolean hasWifi = false;
    private static boolean hasMmobile = false;
    private Thread thread;
    private ConnectivityManager mConnMan;
    private volatile IMqttAsyncClient mqttClient;
    private String deviceId;

    class MQTTBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            IMqttToken token;
            boolean hasConnectivity = false;
            boolean hasChanged = false;
            NetworkInfo infos[] = mConnMan.getAllNetworkInfo();

            for (int i = 0; i < infos.length; i++){
                if (infos[i].getTypeName().equalsIgnoreCase("MOBILE")){
                    if((infos[i].isConnected() != hasMmobile)){
                        hasChanged = true;
                        hasMmobile = infos[i].isConnected();
                    }
                    Log.d(TAG, infos[i].getTypeName() + " is " + infos[i].isConnected());
                } else if ( infos[i].getTypeName().equalsIgnoreCase("WIFI") ){
                    if((infos[i].isConnected() != hasWifi)){
                        hasChanged = true;
                        hasWifi = infos[i].isConnected();
                    }
                    Log.d(TAG, infos[i].getTypeName() + " is " + infos[i].isConnected());
                }
            }

            hasConnectivity = hasMmobile || hasWifi;
            Log.v(TAG, "hasConn: " + hasConnectivity + " hasChange: " + hasChanged + " - "+(mqttClient == null || !mqttClient.isConnected()));
            if (hasConnectivity && hasChanged && (mqttClient == null || !mqttClient.isConnected())) {
                doConnect();
            } else if (!hasConnectivity && mqttClient != null && mqttClient.isConnected()) {
                Log.d(TAG, "doDisconnect()");
                try {
                    token = mqttClient.disconnect();
                    token.waitForCompletion(1000);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    public class MQTTBinder extends Binder {
        public MQTTService getService(){
            return MQTTService.this;
        }
    }

    @Override
    public void onCreate() {
        IntentFilter intentf = new IntentFilter();
        setClientID();
        intentf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(new MQTTBroadcastReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        mConnMan = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged()");
        android.os.Debug.waitForDebugger();
        super.onConfigurationChanged(newConfig);

    }

    private void setClientID(){
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wInfo = wifiManager.getConnectionInfo();
        deviceId = wInfo.getMacAddress();
        if(deviceId == null){
            deviceId = MqttAsyncClient.generateClientId();
        }
    }

    private void doConnect(){

        IMqttToken token;
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);


        try {
            mqttClient = new MqttAsyncClient("tcp://192.168.0.10:1883", deviceId, new MemoryPersistence());
            token = mqttClient.connect();
            token.waitForCompletion(3500);
            mqttClient.setCallback(new MqttEventCallback());
            token = mqttClient.subscribe("test", 0);
            token.waitForCompletion(5000);
            token.setActionCallback(new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "El suscriber esta escuchando");

                    /*String mensaje = "Conchita";
                    byte[] mensajeB = mensaje.getBytes(UTF_8);
                    MqttMessage mqttMessage = new MqttMessage(mensajeB);
                    try {
                        mqttClient.publish("test", mqttMessage);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }*/
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.d(TAG, "No se pudo crear el suscriber");
                }
            });
        } catch (MqttSecurityException e) {
            e.printStackTrace();
        } catch (MqttException e) {

            switch (e.getReasonCode()) {

                case MqttException.REASON_CODE_BROKER_UNAVAILABLE:
                    Log.d(TAG, "The broker was not available to handle the request.");
                    break;
                case MqttException.REASON_CODE_CLIENT_ALREADY_DISCONNECTED:
                    Log.d(TAG, "The client is already disconnected.");
                    break;
                case MqttException.REASON_CODE_CLIENT_CLOSED:
                    Log.d(TAG, "The client is closed - no operations are permitted on the client in this state.");
                    break;
                case MqttException.REASON_CODE_CLIENT_DISCONNECT_PROHIBITED:
                    Log.d(TAG, "Thrown when an attempt to call MqttClient.disconnect() has been made from within a method on MqttCallback.");
                    break;
                case MqttException.REASON_CODE_CLIENT_DISCONNECTING:
                    Log.d(TAG, "The client is currently disconnecting and cannot accept any new work.");
                    break;
                case MqttException.REASON_CODE_CLIENT_EXCEPTION:
                    Log.d(TAG, "Client encountered an exception.");
                    System.out.println("Cause of Exception: "
                            + e.getCause());
                    break;
                case MqttException.REASON_CODE_CLIENT_NOT_CONNECTED:
                    Log.d(TAG, "The client is not connected to the server.");
                    break;
                case MqttException.REASON_CODE_CLIENT_TIMEOUT:
                    Log.d(TAG, "Client timed out while waiting for a response from the server.");
                    break;
                case MqttException.REASON_CODE_CONNECT_IN_PROGRESS:
                    Log.d(TAG, "A connect operation in already in progress, only one connect can happen at a time.");
                    break;
                case MqttException.REASON_CODE_CONNECTION_LOST:
                    Log.d(TAG, "The client has been unexpectedly disconnected from the server.");
                    break;
                case MqttException.REASON_CODE_DISCONNECTED_BUFFER_FULL:
                    Log.d(TAG, "The Client has attempted to publish a message whilst in the 'resting' / ");
                    break;
                case MqttException.REASON_CODE_FAILED_AUTHENTICATION:
                    Log.d(TAG, "Client encountered an exception.");
                    break; //
                case MqttException.REASON_CODE_INVALID_CLIENT_ID:
                    Log.d(TAG, "The server has rejected the supplied client ID");
                    break;
                case MqttException.REASON_CODE_INVALID_MESSAGE:
                    Log.d(TAG, "Protocol error: the message was not recognized as a valid MQTT packet.");
                    break;
                case MqttException.REASON_CODE_INVALID_PROTOCOL_VERSION:
                    Log.d(TAG, "The protocol version requested is not supported by the server.");
                    break;
                case MqttException.REASON_CODE_MAX_INFLIGHT:
                    Log.d(TAG, "A request has been made to send a message but the maximum number of inflight messages has already been reached.");
                    break;
                case MqttException.REASON_CODE_NO_MESSAGE_IDS_AVAILABLE:
                    Log.d(TAG, "Internal error, caused by no new message IDs being available.");
                    break;
                case MqttException.REASON_CODE_NOT_AUTHORIZED:
                    Log.d(TAG, "ot authorized to perform the requested operation");
                    break;//--
                case MqttException.REASON_CODE_SERVER_CONNECT_ERROR:
                    Log.d(TAG, "Unable to connect to server");
                    break;
                case MqttException.REASON_CODE_SOCKET_FACTORY_MISMATCH:
                    Log.d(TAG, "Server URI and supplied SocketFactory do not match.");
                    break;
                case MqttException.REASON_CODE_SSL_CONFIG_ERROR:
                    Log.d(TAG, "SSL configuration error.");
                    break;
                case MqttException.REASON_CODE_SUBSCRIBE_FAILED:
                    Log.d(TAG, "Error from subscribe - returned from the server.");
                    break;
                case MqttException.	REASON_CODE_TOKEN_INUSE:
                    Log.d(TAG, "A request has been made to use a token that is already associated with another action.");
                    break;
                case MqttException.REASON_CODE_UNEXPECTED_ERROR:
                    Log.d(TAG, "An unexpected error has occurred.");
                    break;//--
                case MqttException.REASON_CODE_WRITE_TIMEOUT:
                    Log.d(TAG, "Client timed out while waiting to write messages to the server.");
                    break;//--
                default:
                    Log.e(TAG, "a_" + e.getMessage());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand()");
        return START_STICKY; //persistencia de escucha
    }

    private class MqttEventCallback implements MqttCallback {

        @Override
        public void connectionLost(Throwable arg0) {


        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken arg0) {

        }

        @Override
        @SuppressLint("NewApi")
        public void messageArrived(String topic, final MqttMessage msg) {
            Log.i(TAG, "Message arrived from topic: " + topic);
            Handler h = new Handler(getMainLooper());

            h.post(new Runnable() {
                @Override
                public void run() { //notificación emergente en Android
                    Intent launchA = new Intent(MQTTService.this, MainActivity.class);
                    launchA.putExtra("message", msg.getPayload());
                    startActivity(launchA);
                    Toast.makeText(getApplicationContext(), "MQTT Message:\n" + new String(msg.getPayload()), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Mensaje del cliente: "+ new String(msg.getPayload()));

                }
            });
        }
    }

    public String getThread(){
        return Long.valueOf(thread.getId()).toString();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind called");
        return null;
    }

}
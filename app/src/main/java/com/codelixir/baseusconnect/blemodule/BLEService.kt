package com.codelixir.baseusconnect.blemodule

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN
import com.codelixir.baseusconnect.MainActivity
import com.codelixir.baseusconnect.R
import com.codelixir.baseusconnect.util.toast
import java.util.*
import kotlin.experimental.and


@SuppressLint("MissingPermission")
class BLEService : Service() {

    private val TAG = "BLE"
    private val mBinder = LocalBinder()//Binder for Activity that binds to this Service
    private var mBluetoothManager: BluetoothManager? =
        null//BluetoothManager used to get the BluetoothAdapter
    private var mBluetoothAdapter: BluetoothAdapter? =
        null//The BluetoothAdapter controls the BLE radio in the phone/tablet
    private var mBluetoothGatt: BluetoothGatt? =
        null//BluetoothGatt controls the Bluetooth communication link
    private var mBluetoothDeviceAddress: String? = null//Address of the connected BLE device
    private val mCompleResponseByte = ByteArray(100)

    private var mGattConnectionState = -1

    private lateinit var ringtone: Ringtone

    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {//Change in connection state

            mGattConnectionState = newState

            broadcastUpdate(BLEConstants.ACTION_GATT_CONNECTION_STATE_CHANGE, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {//See if we are connected
                Log.i(TAG, "**ACTION_SERVICE_CONNECTED**$status")
                broadcastUpdate(BLEConstants.ACTION_GATT_CONNECTED)//Go broadcast an intent to say we are connected
                gatt.discoverServices()
                mBluetoothGatt?.discoverServices()//Discover services on connected BLE device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {//See if we are not connectedLog.i(TAG, "**ACTION_SERVICE_DISCONNECTED**" + status);
                broadcastUpdate(BLEConstants.ACTION_GATT_DISCONNECTED)//Go broadcast an intent to say we are disconnected
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {              //BLE service discovery complete
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the service discovery was successful
                Log.i(TAG, "**ACTION_SERVICE_DISCOVERED**$status")
                broadcastUpdate(BLEConstants.ACTION_GATT_SERVICES_DISCOVERED)                       //Go broadcast an intent to say we have discovered services
            } else {                                                                      //Service discovery failed so log a warning
                Log.i(TAG, "onServicesDiscovered received: $status")
            }
        }

        //For information only. This application uses Indication to receive updated characteristic data, not Read
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) { //A request to Read has completed
            //String value = characteristic.getStringValue(0);
            //int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
            val values = characteristic.value
            val clearValue = byteArrayOf(255.toByte())
            var value = 0

            if (null != values) {
                Log.i(TAG, "ACTION_DATA_READ VALUE: " + values.size)
                Log.i(TAG, "ACTION_DATA_READ VALUE: " + (values[0] and 0xFF.toByte()))

                value = (values[0] and 0xFF.toByte()).toInt()
            }

            BLEConnectionManager.writeEmergencyGatt(clearValue)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                //See if the read was successful
                Log.i(TAG, "**ACTION_DATA_READ**$characteristic")
                broadcastUpdate(
                    BLEConstants.ACTION_DATA_AVAILABLE,
                    characteristic
                )                 //Go broadcast an intent with the characteristic data
            } else {
                Log.i(TAG, "ACTION_DATA_READ: Error$status")
            }

        }

        //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) { //A request to Write has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the write was successful
                Log.e(TAG, "**ACTION_DATA_WRITTEN**$characteristic")
                broadcastUpdate(
                    BLEConstants.ACTION_DATA_WRITTEN,
                    characteristic
                )                   //Go broadcast an intent to say we have have written data
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic != null && characteristic.properties == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
                Log.e(TAG, "**THIS IS A NOTIFY MESSAGE")
            }
            if (characteristic != null) {
                broadcastUpdate(BLEConstants.ACTION_DATA_AVAILABLE, characteristic)
            }                     //Go broadcast an intent with the characteristic data

            if (characteristic != null) {
                val uuid: String = characteristic.uuid.toString()

                if (uuid.equals(
                        BLEConstants.LE_SERVICE_NOTIFICATION_CHARACTERISTIC_UUID, ignoreCase = true
                    )
                ) {
                    when (characteristic.value.contentToString()) {
                        "[-86, 8]" -> playAlarm()
                        "[-86, 3, 1]" -> {
                            Handler(Looper.getMainLooper()).post {
                                toast("Command successful!")
                            }
                        }

                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                toast("Unknown notification!")
                            }
                        }
                    }

                }

            }
        }
    }

    fun getState(): Int {
        return mGattConnectionState
    }

    fun playAlarm() {
        try {
            if (::ringtone.isInitialized) {
                ringtone.stop()
            }

            val notification =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone.play()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun stopAlarm() {
        if (::ringtone.isInitialized) {
            ringtone.stop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    // An activity has bound to this service
    override fun onBind(intent: Intent): IBinder? {
        return mBinder                                                                 //Return LocalBinder when an Activity binds to this Service
    }

    // An activity has unbound from this service
    override fun onUnbind(intent: Intent): Boolean {

//        if (mBluetoothGatt != null) {                                                   //Check for existing BluetoothGatt connection
//            mBluetoothGatt!!.close()                                                     //Close BluetoothGatt coonection for proper cleanup
//            mBluetoothGatt =
//                null                                                      //No longer have a BluetoothGatt connection
//        }

        return super.onUnbind(intent)
    }

    private fun startForeground() {
        val channelId =
            createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopActionIntent = Intent(this, MainActivity::class.java)
        stopActionIntent.action = "stop"
        val stopActionPendingIntent = PendingIntent.getActivity(
            this, 0,
            stopActionIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = notificationBuilder
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Keep runnning for constant connection")
            .setPriority(PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopActionPendingIntent
            )
            .build()

        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)

        //connect
        //connectDevice(myRepository.deviceToConnect)

    }

    private fun createNotificationChannel(): String {
        val channelId = "background_service"
        val channelName = "Background Service"
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_HIGH
        )
        channel.lightColor = Color.BLUE
        channel.importance = NotificationManager.IMPORTANCE_NONE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }


    fun connect(address: String?): Boolean {
        try {

            if (mBluetoothAdapter == null || address == null) {                             //Check that we have a Bluetooth adappter and device address
                Log.i(
                    TAG,
                    "BluetoothAdapter not initialized or unspecified address."
                )     //Log a warning that something went wrong
                return false                                                               //Failed to connect
            }

            // Previously connected device.  Try to reconnect.
            if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress && mBluetoothGatt != null) { //See if there was previous connection to the device
                Log.i(TAG, "Trying to use an existing mBluetoothGatt for connection.")
                //See if we can connect with the existing BluetoothGatt to connect
                //Success
                //Were not able to connect
                return mBluetoothGatt!!.connect()
            }

            val device = mBluetoothAdapter!!.getRemoteDevice(address)
                ?: //Check whether a device was returned
                return false                                                               //Failed to find the device
            //No previous device so get the Bluetooth device by referencing its address

            mBluetoothGatt = device.connectGatt(
                this,
                true,
                mGattCallback
            )                //Directly connect to the device so autoConnect is false
            mBluetoothDeviceAddress =
                address                                              //Record the address in case we need to reconnect with the existing BluetoothGatt

            return true
        } catch (e: Exception) {
            Log.i(TAG, e.message.toString())
        }

        return false
    }

    // Broadcast an intent with a string representing an action
    private fun broadcastUpdate(action: String) {
        val intent =
            Intent(action)                                       //Create new intent to broadcast the action
        sendBroadcast(intent)                                                          //Broadcast the intent
    }

    private fun broadcastUpdate(action: String, state: Int) {
        val intent =
            Intent(action).apply { putExtra(BLEConstants.EXTRA_STATE, state) }
        sendBroadcast(intent)
    }

    // Broadcast an intent with a string representing an action an extra string with the data
    // Modify this code for data that is not in a string format
    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        //Create new intent to broadcast the action
        val intent = Intent(action)
        var dataValueHex = ""
        var dataValueNew = ""

        val value = characteristic.value
        if (value != null) {
            for (singleData in value) {
                dataValueHex =
                    dataValueHex + "\t" + "0x" + String.format(Locale.ENGLISH, "%02X", singleData)
                dataValueHex = dataValueHex.replace(",", ".")
                dataValueNew = "$dataValueNew $singleData"
            }
            Log.i(
                TAG,
                "MESSAGE Response Array Normal===> " + dataValueNew + " UUID " + characteristic.uuid
            )
            intent.putExtra(BLEConstants.EXTRA_DATA, dataValueNew)
            intent.putExtra(BLEConstants.EXTRA_UUID, characteristic.uuid.toString())
            sendBroadcast(intent)
        }
    }

    // A Binder to return to an activity to let it bind to this service
    inner class LocalBinder : Binder() {
        internal fun getService(): BLEService {
            return this@BLEService//Return this instance of BluetoothLeService so clients can call its public methods
        }
    }

    // Enable indication on a characteristic

    fun setCharacteristicIndication(characteristic: BluetoothGattCharacteristic, enabled: Boolean) {
        try {
            if (mBluetoothAdapter == null || mBluetoothGatt == null) { //Check that we have a GATT connection
                Log.i(TAG, "BluetoothAdapter not initialized")
                return
            }

            mBluetoothGatt!!.setCharacteristicNotification(
                characteristic,
                enabled
            )//Enable notification and indication for the characteristic

            for (des in characteristic.descriptors) {
                if (null != des) {
                    des.value =
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE//Set the value of the descriptor to enable indication
                    mBluetoothGatt!!.writeDescriptor(des)
                    Log.i(
                        TAG,
                        "***********************SET CHARACTERISTIC INDICATION SUCCESS**"
                    )//Write the descriptor
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, e.message.toString())
        }

    }

    // Write to a given characteristic. The completion of the write is reported asynchronously through the
    // BluetoothGattCallback onCharacteristic Wire callback method.

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic) {
        try {

            if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio

                return
            }
            val test =
                characteristic.properties                                      //Get the properties of the characteristic

            if (test and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 && test and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0) { //Check that the property is writable

                return
            }
            if (mBluetoothGatt!!.writeCharacteristic(characteristic)) {                       //Request the BluetoothGatt to do the Write
                Log.i(
                    TAG,
                    "****************WRITE CHARACTERISTIC SUCCESSFUL**$characteristic"
                )//The request was accepted, this does not mean the write completed
                /*  if(characteristic.getUuid().toString().equalsIgnoreCase(getString(R.string.char_uuid_missed_connection))){

                }*/
            } else {
                Log.i(
                    TAG,
                    "writeCharacteristic failed"
                )                                   //Write request was not accepted by the BluetoothGatt
            }

        } catch (e: Exception) {
            Log.i(TAG, e.message.toString())
        }

    }

    // Retrieve and return a list of supported GATT services on the connected device
    fun getSupportedGattServices(): List<BluetoothGattService>? {

        return if (mBluetoothGatt == null) {                                                   //Check that we have a valid GATT connection

            null
        } else mBluetoothGatt!!.services

    }


    // Disconnects an existing connection or cancel a pending connection
    // BluetoothGattCallback.onConnectionStateChange() will get the result

    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have a GATT connection to disconnect
            return
        }
        mBluetoothGatt?.disconnect()                                                    //Disconnect GATT connection
    }
    // Request a read of a given BluetoothGattCharacteristic. The Read result is reported asynchronously through the
    // BluetoothGattCallback onCharacteristicRead callback method.
    // For information only. This application uses Indication to receive updated characteristic data, not Read

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have access to a Bluetooth radio
            return
        }
        val status =
            mBluetoothGatt!!.readCharacteristic(characteristic)                              //Request the BluetoothGatt to Read the characteristic
        Log.i(TAG, "READ STATUS $status")
    }

    /**
     * The remaining Data need to Update to the First Position Onwards
     *
     * @param byteValue
     * @param currentPos
     */
    private fun processData(byteValue: ByteArray, currentPos: Int) {
        var i = currentPos
        var j = 0
        while (i < byteValue.size) {
            mCompleResponseByte[j] = byteValue[i]
            i++
            j++
        }
    }

    private fun processIntent(intent: Intent?, isNeedToSend: Boolean) {
        if (isNeedToSend) {
            val value =
                String(mCompleResponseByte).trim { it <= ' ' } //new String(mCompleResponseByte, "UTF-8");
            Log.i(TAG, "MESSAGE Resp Complete message ===> $value")
            intent!!.putExtra(BLEConstants.EXTRA_DATA, value)
            sendBroadcast(intent)
        }
        for (i in mCompleResponseByte.indices) {
            mCompleResponseByte[i] = 0
        }
    }

    // Initialize by getting the BluetoothManager and BluetoothAdapter
    fun initialize(): Boolean {
        if (mBluetoothManager == null) {                                                //See if we do not already have the BluetoothManager
            mBluetoothManager =
                getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager //Get the BluetoothManager

            if (mBluetoothManager == null) {                                            //See if we failed
                Log.i(TAG, "Unable to initialize BluetoothManager.")
                return false                                                           //Report the error
            }
        }
        mBluetoothAdapter =
            mBluetoothManager!!.adapter                             //Ask the BluetoothManager to get the BluetoothAdapter

        if (mBluetoothAdapter == null) {                                                //See if we failed
            Log.i(TAG, "Unable to obtain a BluetoothAdapter.")

            return false                                                               //Report the error
        }

        return true                                                                    //Success, we have a BluetoothAdapter to control the radio
    }

    // Enable notification on a characteristic
    // For information only. This application uses Indication, not Notification

    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean
    ) {
        try {

            if (mBluetoothAdapter == null || mBluetoothGatt == null) {                      //Check that we have a GATT connection
                Log.i(TAG, "BluetoothAdapter not initialized")

                return
            }
            mBluetoothGatt!!.setCharacteristicNotification(
                characteristic,
                enabled
            )          //Enable notification and indication for the characteristic

            for (des in characteristic.descriptors) {

                if (null != des) {
                    des.value =
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE         //Set the value of the descriptor to enable notification
                    mBluetoothGatt!!.writeDescriptor(des)
                }

            }
        } catch (e: Exception) {
            Log.i(TAG, e.message.toString())
        }
    }

}
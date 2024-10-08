package com.codelixir.baseusconnect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.codelixir.baseusconnect.blemodule.BLEConnectionManager
import com.codelixir.baseusconnect.blemodule.BLEConstants
import com.codelixir.baseusconnect.blemodule.BLEDeviceManager
import com.codelixir.baseusconnect.blemodule.BLEService
import com.codelixir.baseusconnect.blemodule.BleDeviceData
import com.codelixir.baseusconnect.blemodule.OnConnectionStateListener
import com.codelixir.baseusconnect.blemodule.OnDeviceScanListener
import com.codelixir.baseusconnect.ui.theme.BaseusConnectTheme
import com.codelixir.baseusconnect.util.toast
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : BaseActivity(), OnDeviceScanListener, OnConnectionStateListener {
    private var mBleDeviceData: MutableState<BleDeviceData> = mutableStateOf(BleDeviceData())
    private lateinit var launcher: ActivityResultLauncher<Intent>

    private var mGattConnectionState = mutableIntStateOf(-1)

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "stop") {
            shutDown()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            BaseusConnectTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(mBleDeviceData, mGattConnectionState)
                }
            }
        }

        registerActivityResult()
        requestPermissionsAndProceed()
    }

    override fun onDestroy() {
        super.onDestroy()
        //BLEConnectionManager.disconnect()
        BLEConnectionManager.unBindBLEService(this@MainActivity)
        unRegisterServiceReceiver()
    }

    override fun onScanCompleted(deviceData: BleDeviceData) {
        mBleDeviceData.value = deviceData
        //BLEConnectionManager.connect(deviceDataList.mBleDeviceData)
    }

    private fun requestPermissionsAndProceed() {
        XXPermissions.with(this)
            .permission(
                Permission.POST_NOTIFICATIONS,
                Permission.ACCESS_COARSE_LOCATION,
                Permission.BLUETOOTH_SCAN,
                Permission.BLUETOOTH_CONNECT,
            )
            .request(object : OnPermissionCallback {

                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (!allGranted) {
                        toast("Some permissions were obtained, but some permissions were not granted normally")
                        return
                    }
                    toast("Acquired all permissions successfully")

                    toast("initBLEModule")
                    initBLEModule()
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain) {
                        toast("Authorization denied permanently, please grant all permissions manually")
                        // If it is permanently denied, jump to the application permission system settings page
                        XXPermissions.startPermissionActivity(this@MainActivity, permissions)
                    } else {
                        toast("Failed to get permissions")
                    }
                }
            })
    }

    /**
     *After receive the Location Permission, the Application need to initialize the
     * BLE Module and BLE Service
     */
    private fun initBLEModule() {
        // BLE initialization
        if (!BLEDeviceManager.init(this)) {
            toast("BLE NOT SUPPORTED")
            return
        }
        registerServiceReceiver()
        BLEDeviceManager.setListener(this)

        BLEConnectionManager.setListener(this)

        if (!BLEDeviceManager.isEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            launcher.launch(enableBtIntent)
        }

        startService()
    }

    private fun startService() {
        startForegroundService(Intent(this, BLEService::class.java))
        BLEConnectionManager.initBLEService(this)
    }

    private fun stopService() {
        stopService(Intent(this, BLEService::class.java))
    }

    private fun shutDown() {
        disconnectDevice()
        stopService()
        finish()
    }

    private fun registerActivityResult() {
        launcher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {

        }
    }

    /**
     * Register GATT update receiver
     */
    private fun registerServiceReceiver() {
        ContextCompat.registerReceiver(
            this,
            mGattUpdateReceiver, makeGattUpdateIntentFilter(),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            toast(action)

            when (action) {
                BLEConstants.ACTION_GATT_CONNECTION_STATE_CHANGE -> {
                    mGattConnectionState.intValue = intent.getIntExtra(BLEConstants.EXTRA_STATE, -1)
                }

                BLEConstants.ACTION_GATT_CONNECTED -> {
                    Log.i(TAG, "ACTION_GATT_CONNECTED ")
                    BLEConnectionManager.findBLEGattService(this@MainActivity)
                }

                BLEConstants.ACTION_GATT_DISCONNECTED -> {
                    Log.i(TAG, "ACTION_GATT_DISCONNECTED ")
                }

                BLEConstants.ACTION_GATT_SERVICES_DISCOVERED -> {
                    Log.i(TAG, "ACTION_GATT_SERVICES_DISCOVERED ")
                    try {
                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    BLEConnectionManager.findBLEGattService(this@MainActivity)
                }

                BLEConstants.ACTION_DATA_AVAILABLE -> {
                    val data = intent.getStringExtra(BLEConstants.EXTRA_DATA)
                    val uuId = intent.getStringExtra(BLEConstants.EXTRA_UUID)
                    Log.i(TAG, "ACTION_DATA_AVAILABLE $data")
                    toast("Notification: $data")
                }

                BLEConstants.ACTION_DATA_WRITTEN -> {
                    val data = intent.getStringExtra(BLEConstants.EXTRA_DATA)
                    Log.i(TAG, "ACTION_DATA_WRITTEN ")
                }
            }
        }
    }

    /**
     * Intent filter for Handling BLEService broadcast.
     */
    private fun makeGattUpdateIntentFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BLEConstants.ACTION_GATT_CONNECTION_STATE_CHANGE)
        intentFilter.addAction(BLEConstants.ACTION_GATT_CONNECTED)
        intentFilter.addAction(BLEConstants.ACTION_GATT_DISCONNECTED)
        intentFilter.addAction(BLEConstants.ACTION_GATT_SERVICES_DISCOVERED)
        intentFilter.addAction(BLEConstants.ACTION_DATA_AVAILABLE)
        intentFilter.addAction(BLEConstants.ACTION_DATA_WRITTEN)

        return intentFilter
    }


    /**
     * Unregister GATT update receiver
     */
    private fun unRegisterServiceReceiver() {
        try {
            this.unregisterReceiver(mGattUpdateReceiver)
        } catch (e: Exception) {
            //May get an exception while user denies the permission and user exists the app
            Log.e(TAG, e.message.toString())
        }

    }

    /*    private fun writeEmergency() {
            BLEConnectionManager.writeEmergencyGatt("0xfe");
        }

        private fun writeBattery() {
            BLEConnectionManager.writeBatteryLevel("100")
        }

        private fun writeMissedConnection() {
            BLEConnectionManager.writeMissedConnection("0x00")
        }

        private fun readMissedConnection() {
            BLEConnectionManager.readMissedConnection(getString(R.string.char_uuid_missed_calls))
        }

        private fun readBatteryLevel() {
            BLEConnectionManager.readBatteryLevel(getString(R.string.char_uuid_emergency))
        }

        private fun readEmergencyGatt() {
            BLEConnectionManager.readEmergencyGatt(getString(R.string.char_uuid_emergency))
        }*/

    /**
     * Scan the BLE device
     */
    private fun scanDevice(isContinuesScan: Boolean) {
        BLEDeviceManager.scanBLEDevice(isContinuesScan)
    }

    /**
     * Connect the application with BLE device with selected device address.
     */
    private fun connectDevice() {
        lifecycleScope.launch {
            delay(100)
            BLEConnectionManager.initBLEService(this@MainActivity)
            if (!BLEConnectionManager.connect(mBleDeviceData.value.mDeviceAddress)) {
                toast("DEVICE CONNECTION FAILED")
            }
        }
    }

    private fun disconnectDevice() {
        BLEConnectionManager.disconnect()
    }

    private fun callDevice(status: Int) {
        BLEConnectionManager.writeCallDevice(status)
    }

    @Composable
    fun HomeScreen(
        mBleDeviceData: MutableState<BleDeviceData>,
        mGattConnectionState: MutableState<Int>,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier.padding(all = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    modifier = Modifier
                        .weight(1f),
                    value = mBleDeviceData.value.mDeviceAddress,
                    onValueChange = {
                        mBleDeviceData.value = BleDeviceData(mDeviceAddress = it)
                    },
                    label = { Text("Device: " + mBleDeviceData.value.mDeviceName) }
                )
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                    onClick = {
                        scanDevice(false)
                    }) {
                    Text(text = "Scan")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .width(32.dp)
                        .clip(shape = RoundedCornerShape(32.dp))
                        .background(
                            color = when (mGattConnectionState.value) {
                                BluetoothProfile.STATE_CONNECTED -> Color.Green
                                BluetoothProfile.STATE_DISCONNECTED -> Color.Red
                                BluetoothProfile.STATE_CONNECTING -> Color.Yellow
                                BluetoothProfile.STATE_DISCONNECTING -> Color.Yellow
                                else -> Color.White
                            }
                        )
                ) {

                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                    onClick = {
                        connectDevice()
                    }) {
                    Text(text = "Connect")
                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    onClick = {
                        disconnectDevice()
                    }) {
                    Text(text = "Disconnect")
                }
            }
            Row (verticalAlignment = Alignment.CenterVertically){
                Button(
                    modifier = Modifier
                        .weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                    onClick = {
                        callDevice(1)
                    }) {
                    Text(text = "Call")
                }
                Button(
                    modifier = Modifier
                        .weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    onClick = {
                        callDevice(0)
                    }) {
                    Text(text = "Stop Call")
                }
            }
        }

    }

    @Preview(showBackground = true)
    @Composable
    fun Preview() {
        BaseusConnectTheme {
            HomeScreen(mBleDeviceData, mGattConnectionState)
        }
    }

    override fun onStateChanged(state: Int) {
        mGattConnectionState.value = BLEConnectionManager.getState()
    }

}


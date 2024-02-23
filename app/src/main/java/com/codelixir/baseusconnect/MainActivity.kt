package com.codelixir.baseusconnect

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.codelixir.baseusconnect.blemodule.BLEConnectionManager
import com.codelixir.baseusconnect.blemodule.BLEConstants
import com.codelixir.baseusconnect.blemodule.BLEDeviceManager
import com.codelixir.baseusconnect.blemodule.BLEService
import com.codelixir.baseusconnect.blemodule.BleDeviceData
import com.codelixir.baseusconnect.blemodule.OnDeviceScanListener
import com.codelixir.baseusconnect.ui.theme.BaseusConnectTheme


class MainActivity : ComponentActivity(), OnDeviceScanListener {
    private var mDeviceAddress: String = "D4:3D:39:6C:BC:85"


    override fun onScanCompleted(deviceDataList: BleDeviceData) {
        mDeviceAddress = deviceDataList.mDeviceAddress
        BLEConnectionManager.connect(deviceDataList.mDeviceAddress)
    }

    private val REQUEST_LOCATION_PERMISSION = 2018
    private val TAG = "MainActivity"

    /**
     * Check the Location Permission before calling the BLE API's
     */
    private fun checkLocationPermission() {
        when {
            isLocationPermissionEnabled() -> initBLEModule()
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> displayRationale()

            else -> requestLocationPermission()
        }
    }

    /**
     * Request Location API
     * If the request go to Android system and the System will throw a dialog message
     * user can accept or decline the permission from there
     */
    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }

    /**
     * If the user decline the Permission request and tick the never ask again message
     * Then the application can't proceed further steps
     * In such situation- App need to prompt the user to do the change form Settings Manually
     */
    private fun displayRationale() {

    }

    /**
     * If the user either accept or reject the Permission- The requested App will get a callback
     * Form the call back we can filter the user response with the help of request key
     * If the user accept the same- We can proceed further steps
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (permissions.size != 1 || grantResults.size != 1) {
                    throw RuntimeException("Error on requesting location permission.")
                }
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initBLEModule()
                } else {
                    Toast.makeText(
                        this, "R.string.location_permission_not_granted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Check with the system- If the permission already enabled or not
     */
    private fun isLocationPermissionEnabled(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     *After receive the Location Permission, the Application need to initialize the
     * BLE Module and BLE Service
     */
    private fun initBLEModule() {
        // BLE initialization
        if (!BLEDeviceManager.init(this)) {
            Toast.makeText(this, "BLE NOT SUPPORTED", Toast.LENGTH_SHORT).show()
            return
        }
        registerServiceReceiver()
        BLEDeviceManager.setListener(this)

        if (!BLEDeviceManager.isEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val launcher: ActivityResultLauncher<Intent> =
                registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {

                }
            launcher.launch(enableBtIntent)
        }

        startForegroundService(Intent(this@MainActivity, BLEService::class.java))
        BLEConnectionManager.initBLEService(this@MainActivity)
    }


    /**
     * Register GATT update receiver
     */
    private fun registerServiceReceiver() {
        this.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter())
    }


    private val mGattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when {
                BLEConstants.ACTION_GATT_CONNECTED.equals(action) -> {
                    Log.i(TAG, "ACTION_GATT_CONNECTED ")
                    BLEConnectionManager.findBLEGattService(this@MainActivity)
                }

                BLEConstants.ACTION_GATT_DISCONNECTED.equals(action) -> {
                    Log.i(TAG, "ACTION_GATT_DISCONNECTED ")
                }

                BLEConstants.ACTION_GATT_SERVICES_DISCOVERED.equals(action) -> {
                    Log.i(TAG, "ACTION_GATT_SERVICES_DISCOVERED ")
                    try {
                        Thread.sleep(500)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    BLEConnectionManager.findBLEGattService(this@MainActivity)
                }

                BLEConstants.ACTION_DATA_AVAILABLE.equals(action) -> {
                    val data = intent.getStringExtra(BLEConstants.EXTRA_DATA)
                    val uuId = intent.getStringExtra(BLEConstants.EXTRA_UUID)
                    Log.i(TAG, "ACTION_DATA_AVAILABLE $data")

                }

                BLEConstants.ACTION_DATA_WRITTEN.equals(action) -> {
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

    override fun onDestroy() {
        super.onDestroy()
        //BLEConnectionManager.disconnect()
        BLEConnectionManager.unBindBLEService(this@MainActivity)
        unRegisterServiceReceiver()
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
     * Scan the BLE device if the device address is null
     * else the app will try to connect with device with existing device address.
     */
    private fun scanDevice(isContinuesScan: Boolean) {
        if (mDeviceAddress.isNotEmpty()) {
            connectDevice()
        } else {
            BLEDeviceManager.scanBLEDevice(isContinuesScan)
        }
    }

    /**
     * Connect the application with BLE device with selected device address.
     */
    private fun connectDevice() {
        Handler().postDelayed({
            BLEConnectionManager.initBLEService(this@MainActivity)
            if (BLEConnectionManager.connect(mDeviceAddress)) {
                Toast.makeText(this@MainActivity, "DEVICE CONNECTED", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "DEVICE CONNECTION FAILED", Toast.LENGTH_SHORT)
                    .show()
            }
        }, 100)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                100
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        setContent {
            BaseusConnectTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }

        checkLocationPermission()
    }

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Column(modifier = modifier) {

            Text(
                text = "Hello $name!",
            )
            Button(
                modifier = Modifier.size(width = 80.dp, height = 80.dp),
                onClick = {
                    connectDevice()
                }) {
                Text(text = "Connect")
            }
        }

    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        BaseusConnectTheme {
            Greeting("Android")
        }
    }


}


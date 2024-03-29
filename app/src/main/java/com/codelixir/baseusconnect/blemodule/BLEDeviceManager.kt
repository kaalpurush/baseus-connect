package com.codelixir.baseusconnect.blemodule

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import java.util.ArrayList

@SuppressLint("MissingPermission")
object BLEDeviceManager {

    private val TAG = "BLEDeviceManager"
    private val scanCallback by lazy { createScanCallBack() }
    private var mDeviceObject: BleDeviceData? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mHandler: Handler? = null
    private var mOnDeviceScanListener: OnDeviceScanListener? = null
    private var mIsContinuesScan: Boolean = false

    init {
        mHandler = Handler()
        createScanCallBack()
    }

    /**
     * ScanCallback for Lollipop and above
     * The Callback will trigger the Nearest available BLE devices
     * Search the BLE device in Range and pull the Name and Mac Address from it
     */
    private fun createScanCallBack(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)

                if (null != mOnDeviceScanListener && result != null &&
                    result.device != null && result.device.address != null
                ) {
                    val data = BleDeviceData()
                    data.mDeviceName = if (result.device.name != null)
                        result.device.name else "Unknown"
                    // Some case the Device Name will return as Null from BLE
                    // because of Swathing from one device to another
                    data.mDeviceAddress = (result.device.address)
                    /**
                     * Save the Valid Device info into a list
                     * The List will display to the UI as a popup
                     * User has an option to select one BLE from the popup
                     * After selecting one BLE, the connection will establish and
                     * communication channel will create if its valid device.
                     */

                    if (data.mDeviceName.contains("Baseus", true)) {
                        mDeviceObject = data
                        stopScan(mDeviceObject)
                    }
                }
            }
        }
    }

    /**
     * Initialize BluetoothAdapter
     * Check the device has the hardware feature BLE
     * Then enable the hardware,
     */
    fun init(context: Context): Boolean {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter

        return mBluetoothAdapter != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * Check bluetooth is enabled or not.
     */
    fun isEnabled(): Boolean {
        return mBluetoothAdapter != null && mBluetoothAdapter!!.isEnabled
    }

    /**
     * setListener
     */
    fun setListener(onDeviceScanListener: OnDeviceScanListener) {
        mOnDeviceScanListener = onDeviceScanListener

        mDeviceObject?.let {
            onDeviceScanListener.onScanCompleted(it)
        }

    }

    /**
     * Scan The BLE Device
     * Check the available BLE devices in the Surrounding
     * If the device is Already scanning then stop Scanning
     * Else start Scanning and check 10 seconds
     * Send the available devices as a callback to the system
     * Finish Scanning after 10 Seconds
     */
    fun scanBLEDevice(isContinuesScan: Boolean) {
        try {
            mIsContinuesScan = isContinuesScan

            if (mBluetoothAdapter != null && mBluetoothAdapter!!.isEnabled) {
                scan()
            }
            /**
             * Stop Scanning after a Period of Time
             * Set a 10 Sec delay time and Stop Scanning
             * collect all the available devices in the 10 Second
             */
            /* if (!isContinuesScan) {
                 mHandler?.postDelayed({
                     // Set a delay time to Scanning
                     stopScan(mDeviceObject)
                 }, BLEConstants.SCAN_PERIOD) // Delay Period
             }*/
        } catch (e: Exception) {
            Log.e(TAG, e.message.toString())
        }

    }


    private fun scan() {
        mBluetoothAdapter?.bluetoothLeScanner?.startScan(
            null,
            scanSettings(),
            scanCallback
        ) // Start BLE device Scanning in a separate thread
    }

    private fun scanFilters(): List<ScanFilter> {
        val missedConnectionUUID = ""// Your UUID
        val emergencyUDID = ""// Your UUID
        val catchUDID = ""// Your UUID
        val catchAllUDID = ""// Your UUID
        val filter =
            ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(emergencyUDID)).build()
        val list = ArrayList<ScanFilter>(1)
        list.add(filter)
        return list
    }

    private fun scanSettings(): ScanSettings {
        return ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    }

    private fun stopScan(data: BleDeviceData?) {
        if (mBluetoothAdapter != null && mBluetoothAdapter!!.isEnabled
        ) {
            if (mBluetoothAdapter != null && mBluetoothAdapter!!.isEnabled) { // check if its Already available
                mBluetoothAdapter!!.bluetoothLeScanner.stopScan(scanCallback)
            }
            if (data != null) {
                mOnDeviceScanListener?.onScanCompleted(data)
            }
        }
    }
}

interface OnDeviceScanListener {

    /**
     * Scan Completed -
     *
     * @param deviceDataList - Send available devices as a list to the init Activity
     * The List Contain, device name and mac address,
     */
    fun onScanCompleted(deviceData: BleDeviceData)
}
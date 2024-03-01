package com.codelixir.baseusconnect.blemodule

class BLEConstants {

    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.codelixir.baseusconnec.ACTION_GATT_CONNECTED" //Strings representing actions to broadcast to activities
        const val ACTION_GATT_DISCONNECTED =
            "com.codelixir.baseusconnec.ACTION_GATT_DISCONNECTED" // Old com.zco.ble
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "ccom.codelixir.baseusconnec.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.codelixir.baseusconnec.ACTION_DATA_AVAILABLE"
        const val ACTION_DATA_WRITTEN = "ccom.codelixir.baseusconnec.ACTION_DATA_WRITTEN"
        const val EXTRA_DATA = "com.codelixir.baseusconnec.EXTRA_DATA"
        const val EXTRA_UUID = "com.codelixir.baseusconnec.EXTRA_UUID"


        const val LE_DATA_PRIVATE_CHAR =
            "75748f1d-daef-4fc1-b602-51c17c9d49c2" //Characteristic for MLDP Data, properties - notify, write

        const val CHARACTERISTIC_DEVICE_NAME = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_APPEARANCE = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_DEVICE_INFORMATION = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_MANUFACTURE_NAME = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_MODEL_NUMBER = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_SYSTEM_ID = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_BATTERY_SERVICE = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_BATTERY_LEVEL = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_EMERGENCY_ALERT = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_EMERGENCY_GATT = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_MISSED_CONNECTION = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_CATCH_ALL = ""    //Special UUID for get the device Name
        const val CHARACTERISTIC_CATCH = ""    //Special UUID for get the device Name
        const val SCAN_PERIOD: Long = 10000

        const val LE_SERVICE_UUID = "edfec62e-9910-0bac-5241-d8bda6932a2f"
        const val LE_SERVICE_NOTIFICATION_CHARACTERISTIC_UUID = "15005991-b131-3396-014c-664c9867b917"
        const val LE_SERVICE_CALL_CHARACTERISTIC_UUID = "2d86686a-53dc-25b3-0c4a-f0e10c8aec21"
    }
}
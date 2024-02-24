package com.codelixir.baseusconnect

import androidx.activity.ComponentActivity

abstract class BaseActivity: ComponentActivity() {
    val TAG: String by lazy {
        this::class.java.simpleName
    }
}
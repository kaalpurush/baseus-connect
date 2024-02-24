package com.codelixir.baseusconnect.util

import android.app.Activity
import android.content.Context
import android.widget.Toast

fun Context.toast(text: String?, duration: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(this, "$text", duration).show()
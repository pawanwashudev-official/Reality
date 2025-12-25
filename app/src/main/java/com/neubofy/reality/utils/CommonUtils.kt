package com.neubofy.reality.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager


fun getDefaultLauncherPackageName(packageManager: PackageManager): String? {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }

    val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo?.activityInfo?.packageName
}


fun getCurrentKeyboardPackageName(context: Context): String? {
    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val currentInputMethodId = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
    )
    return currentInputMethodId?.substringBefore('/')
}

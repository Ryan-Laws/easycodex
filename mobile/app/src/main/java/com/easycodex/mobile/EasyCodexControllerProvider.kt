package com.easycodex.mobile

import android.content.Context

object EasyCodexControllerProvider {
    @Volatile
    private var controller: EasyCodexController? = null

    fun get(context: Context): EasyCodexController {
        return controller ?: synchronized(this) {
            controller ?: EasyCodexController(context.applicationContext).also { controller = it }
        }
    }
}

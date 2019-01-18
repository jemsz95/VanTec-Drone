package mx.tec.vanttec.dron

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.secneo.sdk.Helper

class DJIApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
        Helper.install(this)
        System.loadLibrary("opencv_java3")
    }
}

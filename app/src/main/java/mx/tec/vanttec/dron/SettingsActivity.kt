package mx.tec.vanttec.dron

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        setContentView(R.layout.activity_settings)

        fragmentManager.beginTransaction()
                .replace(R.id.settingsFragment, SettingsFragment())
                .commit()
    }

}
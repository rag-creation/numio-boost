package com.rr.numio.boost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rr.numio.boost.ui.BoostScreen
import com.rr.numio.boost.ui.NumioBoostTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NumioBoostTheme {
                BoostScreen()
            }
        }
    }
}

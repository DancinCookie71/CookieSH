package com.cookie.sh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cookie.sh.navigation.CookieShNavGraph
import com.cookie.sh.ui.components.LockedDownBanner
import com.cookie.sh.ui.theme.CookieShTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CookieShTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column {
                        LockedDownBanner()
                        CookieShNavGraph()
                    }
                }
            }
        }
    }
}

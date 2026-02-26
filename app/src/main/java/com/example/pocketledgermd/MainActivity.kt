package com.example.pocketledgermd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pocketledgermd.ui.LedgerScreen
import com.example.pocketledgermd.ui.LedgerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: LedgerViewModel = viewModel()
            MaterialTheme {
                Surface {
                    LedgerScreen(vm)
                }
            }
        }
    }
}

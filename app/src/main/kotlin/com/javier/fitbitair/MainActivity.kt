package com.javier.fitbitair

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.javier.fitbitair.data.SampleHealthRepository
import com.javier.fitbitair.data.StepGoalStore
import com.javier.fitbitair.ui.FitbitAirApp
import com.javier.fitbitair.ui.theme.FitbitAirTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SampleHealthRepository()
        val stepGoalStore = StepGoalStore(applicationContext)

        setContent {
            FitbitAirTheme {
                FitbitAirApp(
                    repository = repository,
                    stepGoalStore = stepGoalStore,
                )
            }
        }
    }
}

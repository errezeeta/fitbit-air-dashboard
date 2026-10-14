package com.javier.fitbitair.data

import android.content.Context

/** Stores only the user's display preference; never caches health measurements. */
class StepGoalStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun getGoal(): Int = preferences.getInt(KEY_STEP_GOAL, DEFAULT_STEP_GOAL).coerceAtLeast(1)

    fun saveGoal(goal: Int) {
        if (goal > 0) preferences.edit().putInt(KEY_STEP_GOAL, goal).apply()
    }

    private companion object {
        const val KEY_STEP_GOAL = "manual_step_goal"
        const val DEFAULT_STEP_GOAL = 8_000
    }
}

package app.personal.workouttracker.wear

import android.app.Application
import app.personal.workouttracker.wear.ongoing.WorkoutOngoingActivity

class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkoutOngoingActivity.start(this)
    }
}

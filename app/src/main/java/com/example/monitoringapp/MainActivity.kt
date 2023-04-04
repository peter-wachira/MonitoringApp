package com.example.monitoringapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.* // ktlint-disable no-wildcard-imports
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Step 1: Display a list of installed apps
        val appList = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val listView = findViewById<ListView>(R.id.list_view)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            appList.map { appInfo -> appInfo.loadLabel(packageManager) },
        )
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        // Step 2: Allow user to select apps to monitor
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = listView.adapter.getItem(position)
            Toast.makeText(this, "$item selected", Toast.LENGTH_SHORT).show()
        }

        // Step 4: Track the actions of the selected apps

        val selectedAppsButton = findViewById<Button>(R.id.selected_apps_button)
        selectedAppsButton.setOnClickListener {
            val selectedAppPositions = listView.checkedItemPositions
            val selectedAppList = mutableListOf<String>()
            for (i in 0 until selectedAppPositions.size()) {
                val position = selectedAppPositions.keyAt(i)
                val appInfo = appList[position]
                val appName = appInfo.loadLabel(packageManager).toString()
                selectedAppList.add(appName)
                // Log the selected app's actions
                Log.d(TAG, "$appName: accessed personal folders")
                Log.d(TAG, "$appName: accessed location data")
                // ...
            }
            // Start the LogActivity and pass the selected app list
            val intent = Intent(this, LogActivity::class.java).apply {
                putStringArrayListExtra("appList", ArrayList(selectedAppList))
            }
            startActivity(intent)
        }
    }
}

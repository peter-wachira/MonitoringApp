package com.example.monitoringapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class AppListActivity : AppCompatActivity() {

    private lateinit var listView: ListView

    @SuppressLint("MissingInflatedId", "QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        listView = findViewById(R.id.list_view)

        // Get a list of installed apps
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val appNames = mutableListOf<String>()

        // Filter the list to only include apps with the required permissions
        for (app in installedApps) {
            if (packageManager.checkPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    app.packageName,
                ) == PackageManager.PERMISSION_GRANTED ||
                packageManager.checkPermission(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    app.packageName,
                ) == PackageManager.PERMISSION_GRANTED ||
                packageManager.checkPermission(
                    android.Manifest.permission.READ_CONTACTS,
                    app.packageName,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                appNames.add(app.loadLabel(packageManager).toString())
            }
        }

        // Create an ArrayAdapter and set it to the ListView
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, appNames)
        listView.adapter = adapter

        // Set an OnItemClickListener to handle clicks on app names
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val packageName = installedApps[position].packageName
            val intent = Intent(this, LogActivity::class.java)
            intent.putExtra("packageName", packageName) // Add the package name as an extra
            startActivity(intent)
        }
    }
}

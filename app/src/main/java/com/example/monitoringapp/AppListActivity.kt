package com.example.monitoringapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView

class AppListActivity : AppCompatActivity() {

    private lateinit var systemListView: ListView
    private lateinit var otherListView: ListView
    private lateinit var otherAdapter: ArrayAdapter<String>
    private lateinit var searchView: SearchView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)

        systemListView = findViewById(R.id.system_list_view)
        otherListView = findViewById(R.id.other_list_view)
        searchView = findViewById(R.id.search_view)

        // Get a list of installed apps
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val systemApps = mutableListOf<String>()
        val otherApps = mutableListOf<String>()

        // Filter the list to separate system and other apps
        for (app in installedApps) {
            if (app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                systemApps.add(app.loadLabel(packageManager).toString())
            } else {
                otherApps.add(app.loadLabel(packageManager).toString())
            }
        }

        // Create an ArrayAdapter for system apps and set it to the ListView
        val systemAdapter = ArrayAdapter(this, R.layout.list_item_system, systemApps)
        val systemHeader = layoutInflater.inflate(R.layout.list_header, systemListView, false)
        systemHeader.findViewById<TextView>(R.id.header_text).text = getString(R.string.system_apps)
        systemListView.addHeaderView(systemHeader)
        systemListView.adapter = systemAdapter

        // Create an ArrayAdapter for other apps and set it to the ListView
        otherAdapter = ArrayAdapter(this, R.layout.list_item_other, otherApps)
        val otherHeader = layoutInflater.inflate(R.layout.list_header, otherListView, false)
        otherHeader.findViewById<TextView>(R.id.header_text).text = getString(R.string.other_apps)
        otherListView.addHeaderView(otherHeader)
        otherListView.adapter = otherAdapter

        // Set an OnItemClickListener to handle clicks on app names
        systemListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position > 0) { // Skip the header row
                val appName = systemListView.getItemAtPosition(position) as String
                val packageName = getPackageName(appName)
                val intent = Intent(this, LogActivity::class.java)
                intent.putExtra("packageName", packageName) // Add the package name as an extra
                startActivity(intent)
            }
        }

        // Set an OnItemClickListener to handle clicks on app names
        otherListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position > 0) { // Skip the header row
                val appName = otherListView.getItemAtPosition(position) as String
                val packageName = getPackageName(appName)
                val intent = Intent(this, LogActivity::class.java)
                intent.putExtra("packageName", packageName) // Add the package name as an extra
                startActivity(intent)
            }
        }

        // Set up the SearchView to filter the list of apps
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                otherAdapter.filter.filter(newText)
                systemAdapter.filter.filter(newText)

                return false
            }
        })

        searchView.setOnCloseListener {
            searchView.setQuery("", false)
            systemListView.clearTextFilter()
            otherListView.clearTextFilter()
            false
        }
    }

    // Function to get the package name for a given app name
    private fun getPackageName(appName: String): String {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            if (app.loadLabel(packageManager).toString() == appName) {
                return app.packageName
            }
        }
        throw RuntimeException("Package not found for app name: $appName")
    }
}

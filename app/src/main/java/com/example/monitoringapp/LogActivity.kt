package com.example.monitoringapp

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri

import android.os.* // ktlint-disable no-wildcard-imports
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.* // ktlint-disable no-wildcard-imports
import androidx.appcompat.app.AppCompatActivity
import com.example.monitoringapp.service.FileMonitorService
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

/**
 **LogActivity**
- The code is an implementation of an app that monitors other apps file access.
 The main class is LogActivity that extends AppCompatActivity.
 The code declares some properties, such as TAG, packageInfo, permissions, accessedFolders, logMessages, logListAdapter, fileMonitorService, and fileObserver.
 It also creates a service connection object to bind to the FileMonitorService and store the result in the bound variable.
 If binding failed, it logs an error message and shows a toast message.

- The code sets up the back button to clear the app name and call onBackPressed().
 It gets the package name from the intent extra and sets the app name.
 It gets the permissions for the selected app and creates a TextView for each permission and adds it to the log container.
 If there are no permissions, it shows a message in an empty view.

- The code then monitors the app's file access in the background.
 It gets all installed applications, creates a single FileObserver that watches the data folder of all installed apps, and overrides the onEvent method to log file access.
 If the modified file belongs to an installed app, the code gets the folder path from the log message and adds it to the accessedFolders set.
 It also updates the UI to display the folder paths in a list view.

- In summary, the code declares some properties and sets up a service connection object to bind to the FileMonitorService.
 It also sets up the UI components and logs the file access of installed apps in the background.
 It updates the UI to display the folder paths in a list view.
*/

class LogActivity : AppCompatActivity() {

    // Declare properties
    private val TAG = LogActivity::class.java.simpleName
    private lateinit var packageInfo: PackageInfo
    private lateinit var permissions: Array<String>
    private var accessedFolders = mutableSetOf<String>()
    private val logMessages = mutableListOf<String>()
    private lateinit var logListAdapter: ArrayAdapter<String>
    private lateinit var fileMonitorService: FileMonitorService
    private lateinit var fileObserver: FileObserver

    // Create a service connection object to bind to the FileMonitorService
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FileMonitorService.LocalBinder
            fileMonitorService = binder.getService()
            // Get the accessed folders from the service and add them to the accessedFolders set
            accessedFolders.addAll(fileMonitorService.getAccessedFolders())
            Log.d(TAG, "accessedFolders: $accessedFolders")
            // Create an array adapter for the accessed folders list
            logListAdapter = object : ArrayAdapter<String>(
                this@LogActivity,
                android.R.layout.simple_list_item_1,
                ArrayList(accessedFolders),
            ) {
                // Override the getView method to display the folder path
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context)
                        .inflate(android.R.layout.simple_list_item_1, parent, false)
                    val folderPath = getItem(position)
                    (view as TextView).text = folderPath
                    return view
                }

                override fun getCount(): Int {
                    return accessedFolders.size
                }

                override fun getItem(position: Int): String? {
                    return accessedFolders.elementAt(position)
                }
            }
            // Set the adapter for the accessed folders list view
            val logList = findViewById<ListView>(R.id.folders_accessed_list)
            logList.adapter = logListAdapter
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Do nothing
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        // Create an intent to start the FileMonitorService
        val serviceIntent = Intent(this, FileMonitorService::class.java)
        // Bind to the service and store the result in the bound variable
        val bound = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        // If binding failed, log an error message and show a toast message
        if (!bound) {
            Log.e(TAG, "Failed to bind to FileMonitorService")
            Toast.makeText(this, "Failed to bind to FileMonitorService", Toast.LENGTH_SHORT).show()
        }

        // Set up the back button to clear the app name and call onBackPressed()
        val backButton = findViewById<ImageButton>(R.id.back_button)
        val title = findViewById<TextView>(R.id.app_name)
        backButton.setOnClickListener {
            title.text = ""
            onBackPressed()
        }

        // Get the package name from the intent extra and set the app name
        val packageName = intent.getStringExtra("packageName")
        title.text = packageName?.let { getAppName(it, applicationContext) }
        // Get the permissions for the selected app
        packageInfo = packageName?.let {
            packageManager.getPackageInfo(it, PackageManager.GET_PERMISSIONS)
        } ?: throw IllegalArgumentException("Package name cannot be null")

        permissions = packageInfo.requestedPermissions ?: emptyArray()

        // Create a TextView for each permission and add it to the log container
        val logContainer = findViewById<LinearLayout>(R.id.log_container)
        if (permissions.isNotEmpty()) {
            for (permission in permissions) {
                val permissionStatus = packageManager.checkPermission(permission, packageName)
                logMessage("$permission: \n ${getPermissionStatus(permissionStatus)}", logContainer)
            }
        } else {
            // If there are no permissions, show a message in an empty view
            val emptyView = TextView(this)
            val textColor = Color.BLACK
            emptyView.setTextColor(textColor)
            emptyView.text = getString(R.string.no_permissions_found)
            emptyView.setPadding(16, 16, 16, 16)
            emptyView.gravity = Gravity.CENTER
            logContainer.addView(emptyView)
        }

        // Monitor the app's file access in the background
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Create a single FileObserver that watches the data folder of all installed apps
        fileObserver =
            object : FileObserver("/data/data", CREATE or DELETE or MODIFY or OPEN or CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) {
                    if (event and (CREATE or DELETE or MODIFY or OPEN or CLOSE_WRITE) != 0) {
                        // Iterate over all installed apps to find the app that the modified file belongs to
                        for (packageInfo in packages) {
                            val dataDir = packageInfo.dataDir
                            if (dataDir != null && path?.startsWith(dataDir) == true) {
                                val fullPath = "$dataDir/$path"
                                Log.e(TAG, "File access: $fullPath")
                                runOnUiThread {
                                    logMessage("File access: $fullPath", logContainer)
                                }
                                val folderPath = getFolderPathFromMessage("File access: $fullPath")
                                if (folderPath != null) {
                                    accessedFolders.add(fullPath)
                                    runOnUiThread {
                                        logListAdapter.clear()
                                        logListAdapter.addAll(accessedFolders)
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            }.apply { startWatching() }

        startMonitoring()

        val logList = findViewById<ListView>(R.id.folders_accessed_list)
        // Set up empty view when there are no folders accessed yet
        val emptyView = findViewById<View>(R.id.empty_view)
        logList.emptyView = emptyView
        if (accessedFolders.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            logList.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            logList.visibility = View.VISIBLE
        }

        logListAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            ArrayList(accessedFolders),
        )

        logList.adapter = logListAdapter

        // Set up the log button to save log messages
        val logButton = findViewById<Button>(R.id.save_app_logs)
        logButton.setOnClickListener {
            saveLog()
        }
    }

    private fun startMonitoring() {
        // Get a list of all installed apps
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // Monitor the data directories of all installed apps
        for (packageInfo in packages) {
            val packageName = packageInfo.packageName
            val dataDir = packageInfo.dataDir

            if (packageName != applicationContext.packageName && dataDir != null) {
                fileObserver = object : FileObserver(dataDir, ALL_EVENTS) {
                    override fun onEvent(event: Int, path: String?) {
                        if (event and (CREATE or OPEN or CLOSE_WRITE) != 0) {
                            val folderPath = "$dataDir/$path"
                            Log.d("FileMonitor", "$folderPath has been accessed")
                            // Add the accessed folder to the list
                            accessedFolders.add(folderPath)
                        }
                    }
                }

                fileObserver.startWatching()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        } else {
            // Permission already granted or running on a lower version of Android
            // Get the list of external storage directories
            val externalDirs = applicationContext.getExternalFilesDirs(null)

            // Monitor the external storage directories
            for (externalDir in externalDirs) {
                if (externalDir != null) {
                    val rootDir = externalDir.parentFile
                    if (rootDir != null) {
                        fileObserver = object : FileObserver(rootDir.path, ALL_EVENTS) {
                            override fun onEvent(event: Int, path: String?) {
                                if (event and (CREATE or OPEN or CLOSE_WRITE) != 0) {
                                    val folderPath = "$rootDir$path"
                                    Log.d("FileMonitor", "$folderPath has been accessed")
                                    // Add the accessed folder to the list
                                    accessedFolders.add(folderPath)
                                }
                            }
                        }
                        fileObserver.startWatching()
                    }
                }
            }
        }
    }

    private fun getPermissionStatus(permissionStatus: Int): String {
        return when (permissionStatus) {
            PackageManager.PERMISSION_GRANTED -> "Status: Granted"
            PackageManager.PERMISSION_DENIED -> "Status: Denied"
            else -> "Unknown"
        }
    }

    /* In this implementation, the saveLog() method writes each log message to a file named "log.txt" in the app's external files directory.
    It also displays a toast message indicating whether the log was successfully saved or if there was an error.
    Note that this implementation uses a FileWriter to write the log messages to the file,
    and catches any IOException that might occur while writing.
    */

    private fun saveLog() {
        val file = File(getExternalFilesDir(null), "log.txt")
        try {
            val writer = FileWriter(file)
            for (message in logMessages) {
                writer.append(message).append("\n")
            }
            for (folderPath in accessedFolders) {
                writer.append("Folder accessed: $folderPath").append("\n")
            }
            writer.flush()
            writer.close()
            Toast.makeText(this, "Log saved to ${file.absolutePath}", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving log", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logMessage(message: String, logContainer: LinearLayout) {
        logMessages.add(message)

        // Set text color based on the type of message
        val textColor = when {
            message.endsWith("Status: Granted") -> Color.BLACK
            message.endsWith("Status: Denied") -> Color.RED
            else -> Color.BLACK
        }

        // Check if the message is related to accessing files
        if (message.startsWith("File access: ")) {
            // If it is, set the background color to yellow
            val logTextView = TextView(this)
            logTextView.setTextColor(textColor)

            // Extract the full path of the file accessed
            val fullPath = message.substring(13) // 14 is the length of "File access: "
            logTextView.text = "File access: $fullPath"

            // Check if the message contains a folder path
            val folderPath = getFolderPathFromMessage(message)
            if (folderPath != null) {
                // If it does, set the text as a clickable link
                val clickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        // Handle click event
                        // Open folder path in file explorer app
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = true
                        ds.color = textColor
                    }
                }
                val clickableText = SpannableString(fullPath)
                clickableText.setSpan(
                    clickableSpan,
                    0,
                    fullPath.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                logTextView.text = clickableText
                logTextView.movementMethod = LinkMovementMethod.getInstance()
            }

            logContainer.addView(logTextView)
        } else {
            // If it is not related to accessing files, set the background color to white
            val logTextView = TextView(this)
            logTextView.setTextColor(textColor)
            logTextView.text = message
            logContainer.addView(logTextView)
        }

        // Scroll to the bottom of the log
        val logScrollView = findViewById<ScrollView>(R.id.logScrollView)
        logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun getFolderPathFromMessage(message: String): String? {
        val regex = Regex("at ([^()]+)[(][^()]+[)]")
        val matchResult = regex.find(message)
        return matchResult?.groups?.get(1)?.value
    }

    private fun getAppName(packageName: String, context: Context): String {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        return packageManager.getApplicationLabel(appInfo).toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }

    override fun onStop() {
        super.onStop()
        stopWatchingFileObservers()
    }

    // Stop watching all FileObservers
    private fun stopWatchingFileObservers() {
        fileObserver.stopWatching()
    }
}

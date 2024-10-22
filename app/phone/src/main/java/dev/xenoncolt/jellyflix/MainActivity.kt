package dev.xenoncolt.jellyflix

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUiSaveStateControl
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.navigation.NavigationBarView
import dagger.hilt.android.AndroidEntryPoint
import dev.xenoncolt.jellyflix.database.ServerDatabaseDao
import dev.xenoncolt.jellyflix.databinding.ActivityMainBinding
import dev.xenoncolt.jellyflix.update.UpdateManager
import dev.xenoncolt.jellyflix.viewmodels.MainViewModel
import dev.xenoncolt.jellyflix.work.SyncWorker
import javax.inject.Inject
import dev.xenoncolt.jellyflix.core.R as CoreR
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var database: ServerDatabaseDao

    @Inject
    lateinit var appPreferences: AppPreferences

    private lateinit var navController: NavController

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleUserDataSync()
        applyTheme()
        setupActivity()

        // Update Checker
        lifecycleScope.launch {
            val abi = UpdateManager.getDeviceAbi()
            val apkUrl = UpdateManager.getApkUrl(abi)

            if (UpdateManager.isUpdateAvailable()) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Update Available")
                    .setMessage("An update is available for Jellyflix. Would you like to download it?")
                    .setPositiveButton("Yes") { _, _ ->
//                        val author = "xenoncolt"
//                        val repo = "Jellyflix_Android"
//                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$author/$repo/releases/latest"))
//                        startActivity(intent)
//                        if (apkUrl != null) {
//                            UpdateManager.downloadAndInstallApk(this@MainActivity, apkUrl)
//                        }
                        if (apkUrl != null) {
                            UpdateManager.downloadAndInstallApk(this@MainActivity, apkUrl)
                        }
//                        if (packageManager.canRequestPackageInstalls()) {
//                            apkUrl.let {
//                                if (apkUrl != null) {
//                                    UpdateManager.downloadAndInstallApk(this@MainActivity, apkUrl)
//                                }
//                            }
//                        } else {
//                            if (apkUrl != null) {
//                                UpdateManager.requestInstallPermission(this@MainActivity, apkUrl)
//                            }
//                        }
                    }
                    .setNegativeButton("Not Now", null)
                    .show()
            }
        }
    }

    @OptIn(NavigationUiSaveStateControl::class)
    private fun setupActivity() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        val inflater = navController.navInflater
        val graph = inflater.inflate(R.navigation.app_navigation)

        checkServersEmpty(graph) {
            navController.setGraph(graph, intent.extras)
        }
        checkUser(graph) {
            navController.setGraph(graph, intent.extras)
        }

        val navView: NavigationBarView = binding.navView as NavigationBarView

        if (appPreferences.offlineMode) {
            navView.menu.clear()
            navView.inflateMenu(CoreR.menu.bottom_nav_menu_offline)
        }

        setSupportActionBar(binding.mainToolbar)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.mediaFragment,
                R.id.favoriteFragment,
                R.id.downloadsFragment,
            ),
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        // navView.setupWithNavController(navController)
        // Don't save the state of other main navigation items, only this experimental function allows turning off this behavior
        NavigationUI.setupWithNavController(navView, navController, false)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.navView.visibility = when (destination.id) {
                R.id.twoPaneSettingsFragment, R.id.serverSelectFragment, R.id.addServerFragment, R.id.loginFragment, com.mikepenz.aboutlibraries.R.id.about_libraries_dest, R.id.usersFragment, R.id.serverAddressesFragment, R.id.WebViewFragment -> View.GONE
                else -> View.VISIBLE
            }
            if (destination.id == R.id.WebViewFragment) {
//                supportActionBar?.title = "" // Remove title
//                supportActionBar?.setDisplayHomeAsUpEnabled(false) // Remove back icon
                supportActionBar?.hide() // hide the entire bar
            } else {
                supportActionBar?.show() // show the toolbars for others
            }
            if (destination.id == com.mikepenz.aboutlibraries.R.id.about_libraries_dest) {
                binding.mainToolbar.title =
                    getString(CoreR.string.app_info)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp()
    }

    private fun checkServersEmpty(graph: NavGraph, onServersEmpty: () -> Unit = {}) {
        if (!viewModel.startDestinationChanged) {
            val numOfServers = database.getServersCount()
            if (numOfServers < 1) {
                graph.setStartDestination(R.id.addServerFragment)
                viewModel.startDestinationChanged = true
                onServersEmpty()
            }
        }
    }

    private fun checkUser(graph: NavGraph, onNoUser: () -> Unit = {}) {
        if (!viewModel.startDestinationChanged) {
            appPreferences.currentServer?.let {
                val currentUser = database.getServerCurrentUser(it)
                if (currentUser == null) {
                    graph.setStartDestination(R.id.serverSelectFragment)
                    viewModel.startDestinationChanged = true
                    onNoUser()
                }
            }
        }
    }

    private fun scheduleUserDataSync() {
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED,
                    )
                    .build()
            )
            .build()

        val workManager = WorkManager.getInstance(applicationContext)

        workManager.beginUniqueWork("syncUserData", ExistingWorkPolicy.KEEP, syncWorkRequest)
            .enqueue()
    }

    private fun applyTheme() {
        if (appPreferences.amoledTheme) {
            setTheme(CoreR.style.Theme_FindroidAMOLED)
        }
    }
}

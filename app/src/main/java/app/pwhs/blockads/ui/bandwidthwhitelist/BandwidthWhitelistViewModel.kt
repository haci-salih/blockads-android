package app.pwhs.blockads.ui.bandwidthwhitelist

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.service.ServiceController
import app.pwhs.blockads.ui.whitelist.data.AppInfoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Apps exempt from the global bandwidth limiter (Settings → Protection
 * → Bant Genişliği Sınırı). This is a separate list from the ad-block
 * whitelist ([app.pwhs.blockads.ui.whitelist.AppWhitelistViewModel]) —
 * an app can be throttle-exempt while still having its ads blocked,
 * and vice versa.
 *
 * Reuses [AppInfoData] / the installed-apps loading logic from the
 * ad-block whitelist feature since the UI need (pick apps from a
 * searchable list, toggle a switch) is identical.
 */
class BandwidthWhitelistViewModel(
    private val appPrefs: AppPreferences,
    application: Application
) : AndroidViewModel(application) {

    val whitelistedApps: StateFlow<Set<String>> = appPrefs.bandwidthWhitelistedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _installedApps = MutableStateFlow<List<AppInfoData>>(emptyList())
    val installedApps: StateFlow<List<AppInfoData>> = _installedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = application.applicationContext.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                    .filter { it.packageName != application.applicationContext.packageName }
                    .map { appInfo ->
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        AppInfoData(
                            packageName = appInfo.packageName,
                            label = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                            isSystemApp = isSystem
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _installedApps.value = apps
            _isLoading.value = false
        }
    }

    fun refreshApps() {
        loadApps()
    }

    // Toggling requires a VPN restart (not just a live engine call) so
    // the newly (un)whitelisted app's package name can be re-resolved to
    // a UID and re-synced to the Go engine via
    // GoTunnelAdapter.start()/AdBlockVpnService — same pattern already
    // used for the down/up KB/s limit itself.
    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            appPrefs.toggleBandwidthWhitelistedApp(packageName)
            ServiceController.requestRestart(getApplication<Application>().applicationContext)
        }
    }
}

package pl.snowdog.kiosk

import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.Snackbar
import pl.snowdog.kiosk.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var mAdminComponentName: ComponentName
    private lateinit var mDevicePolicyManager: DevicePolicyManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPref: SharedPreferences

    companion object {
        const val LOCK_ACTIVITY_KEY = "pl.snowdog.kiosk.MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAdminComponentName = MyDeviceAdminReceiver.getComponentName(this)
        mDevicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        mDevicePolicyManager.removeActiveAdmin(mAdminComponentName)
        sharedPref =
            getSharedPreferences(getString(R.string.storage_key), Context.MODE_PRIVATE) ?: return

        val url = sharedPref.getString(getString(R.string.url_key), "")
        binding.txtUrl.editText?.setText(url)
        val pin = sharedPref.getString(getString(R.string.pin_key), "")
        binding.txtPin.editText?.setText(pin)

        val edit = sharedPref.getBoolean(getString(R.string.edit_key), false)

        if (!edit && url != null && url != "" && pin != null && pin != "") {
            val intent = Intent(applicationContext, WebviewActivity::class.java)
            startActivity(intent)
            return
        } else if (url == null || url == "") {
            showValidationError(R.string.url_wrong, R.id.txtUrl)
        } else if (pin == null || pin == "") {
            showValidationError(R.string.pin_wrong, R.id.txtPin)
        }

        val isAdmin = isAdmin()
        if (isAdmin) {
            Snackbar.make(binding.content, R.string.device_owner, Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(binding.content, R.string.not_device_owner, Snackbar.LENGTH_SHORT).show()
        }

        initButtons(isAdmin)
    }

    private fun initButtons(isAdmin: Boolean) {
        binding.btStartLockTask.setOnClickListener {
            setKioskPolicies(true, isAdmin)
        }

        binding.btStopLockTask.setOnClickListener {
            setKioskPolicies(false, isAdmin)
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            intent.putExtra(LOCK_ACTIVITY_KEY, false)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        initButtons(isAdmin())
    }

    private fun isAdmin() = mDevicePolicyManager.isDeviceOwnerApp(packageName)

    private fun showValidationError(message: Int, anchorId: Int) {
        val snack = Snackbar.make(binding.content, message, Snackbar.LENGTH_SHORT)
        val layoutParams = snack.view.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.anchorId = anchorId //Id for your bottomNavBar or TabLayout
        layoutParams.anchorGravity = Gravity.BOTTOM
        layoutParams.gravity = Gravity.BOTTOM
        layoutParams.topMargin = 10
        snack.view.layoutParams = layoutParams
        snack.show()
    }

    private fun setKioskPolicies(enable: Boolean, isAdmin: Boolean) {
        var url = binding.txtUrl.editText?.text.toString()
        val pin = binding.txtPin.editText?.text.toString()

        if (enable) {
            if (url.trim() == "") {
                showValidationError(R.string.url_wrong, R.id.txtUrl)
                return
            } else if (pin.trim() == "") {
                showValidationError(R.string.pin_wrong, R.id.txtPin)
                return
            }

            //if (!url.startsWith("http")) {
            //    url = "https://$url"
            //} else if (!url.startsWith("https")) {
            //    url = url.replace("http", "https")
            //}
        }

        if (isAdmin) {
            setRestrictions(enable)
            enableStayOnWhilePluggedIn(enable)
            setUpdatePolicy(enable)
            setAsHomeApp(enable)
            setKeyGuardEnabled(enable)
        }
        setLockTask(enable, isAdmin)
        setImmersiveMode(enable)


        // save URL on storage
        with(sharedPref.edit()) {
            putString(getString(R.string.url_key), url)
            putString(getString(R.string.pin_key), pin)
            commit()
        }

        if (enable) {
            with(sharedPref.edit()) {
                putBoolean(getString(R.string.edit_key), false)
                commit()
            }
            val intent = Intent(applicationContext, WebviewActivity::class.java)
            startActivity(intent)
        }
    }

    // region restrictions
    private fun setRestrictions(disallow: Boolean) {
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disallow)
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disallow)
        setUserRestriction(UserManager.DISALLOW_ADD_USER, disallow)
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, disallow)
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, disallow)
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, disallow)
    }

    private fun setUserRestriction(restriction: String, disallow: Boolean) = if (disallow) {
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, restriction)
    } else {
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, restriction)
    }
    // endregion

    private fun enableStayOnWhilePluggedIn(active: Boolean) = if (active) {
        mDevicePolicyManager.setGlobalSetting(
            mAdminComponentName,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            (BatteryManager.BATTERY_PLUGGED_AC
                    or BatteryManager.BATTERY_PLUGGED_USB
                    or BatteryManager.BATTERY_PLUGGED_WIRELESS).toString()
        )
    } else {
        mDevicePolicyManager.setGlobalSetting(
            mAdminComponentName,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            "0"
        )
    }

    private fun setLockTask(start: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            mDevicePolicyManager.setLockTaskPackages(
                mAdminComponentName, if (start) arrayOf(packageName) else arrayOf()
            )
        }
        if (start) {
            startLockTask()
        } else {
            stopLockTask()
        }
    }

    private fun setUpdatePolicy(enable: Boolean) {
        if (enable) {
            mDevicePolicyManager.setSystemUpdatePolicy(
                mAdminComponentName,
                SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
            )
        } else {
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, null)
        }
    }

    private fun setAsHomeApp(enable: Boolean) {
        if (enable) {
            val intentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            mDevicePolicyManager.addPersistentPreferredActivity(
                mAdminComponentName,
                intentFilter,
                ComponentName(packageName, MainActivity::class.java.name)
            )
        } else {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(
                mAdminComponentName, packageName
            )
        }
    }

    private fun setKeyGuardEnabled(enable: Boolean) {
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, !enable)
    }

    private fun setImmersiveMode(enable: Boolean) {
        if (enable) {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.decorView.systemUiVisibility = flags
        } else {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            window.decorView.systemUiVisibility = flags
        }
    }
}

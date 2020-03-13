package net.imknown.android.forefrontinfo.ui.others

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.imknown.android.forefrontinfo.MyApplication
import net.imknown.android.forefrontinfo.R
import net.imknown.android.forefrontinfo.base.Event
import net.imknown.android.forefrontinfo.base.booleanEventLiveData
import net.imknown.android.forefrontinfo.ui.base.BaseListViewModel
import net.imknown.android.forefrontinfo.ui.base.MyModel
import java.text.SimpleDateFormat
import java.util.*

class OthersViewModel : BaseListViewModel() {

    companion object {
        private const val CMD_GETPROP = "getprop"

        private const val ERRNO_NO_SUCH_FILE_OR_DIRECTORY = 2
        private const val ERRNO_PERMISSION_DENIED = 13
        private const val BINDER32_PROTOCOL_VERSION = 7
        private const val BINDER64_PROTOCOL_VERSION = 8
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            System.loadLibrary("BinderDetector")
        }
    }

    private val _rawProp by lazy {
        MyApplication.sharedPreferences.booleanEventLiveData(
            viewModelScope,
            MyApplication.getMyString(R.string.function_raw_build_prop_key),
            false
        )
    }
    val rawProp: LiveData<Event<Boolean>> by lazy { _rawProp }

    @SuppressLint("DiscouragedPrivateApi")
    private fun getProcessBit(): String {
        val isProcess64Bit = if (isAtLeastAndroid6()) {
            android.os.Process.is64Bit()
        } else {
            val vmRuntimeInstance = Class.forName("dalvik.system.VMRuntime")
                .getDeclaredMethod("getRuntime")
                .invoke(null)

            Class.forName("dalvik.system.VMRuntime")
                .getDeclaredMethod("is64Bit")
                .invoke(vmRuntimeInstance) as Boolean
        }

        return MyApplication.getMyString(
            if (isProcess64Bit) {
                R.string.process_bit_64
            } else {
                R.string.process_bit_32
            }
        )
    }

    @ExperimentalStdlibApi
    override fun collectModels() = viewModelScope.launch(Dispatchers.IO) {
        val tempModels = ArrayList<MyModel>()

        // region [Basic]
        add(tempModels, MyApplication.getMyString(R.string.build_brand), Build.BRAND)
        add(tempModels, MyApplication.getMyString(R.string.build_manufacturer), Build.MANUFACTURER)
        add(tempModels, MyApplication.getMyString(R.string.build_model), Build.MODEL)
        add(tempModels, MyApplication.getMyString(R.string.build_device), Build.DEVICE)
        add(tempModels, MyApplication.getMyString(R.string.build_product), Build.PRODUCT)
        add(tempModels, MyApplication.getMyString(R.string.build_hardware), Build.HARDWARE)
        add(tempModels, MyApplication.getMyString(R.string.build_board), Build.BOARD)
        // endregion [Basic]

        // region [Arch & ABI]
        add(tempModels, MyApplication.getMyString(R.string.current_process_bit), getProcessBit())
        add(tempModels, MyApplication.getMyString(R.string.os_arch), System.getProperty("os.arch"))
        @Suppress("DEPRECATION")
        add(tempModels, MyApplication.getMyString(R.string.build_cpu_abi), Build.CPU_ABI)
        add(
            tempModels,
            MyApplication.getMyString(R.string.build_supported_32_bit_abis),
            Build.SUPPORTED_32_BIT_ABIS.joinToString()
        )
        add(tempModels,
            MyApplication.getMyString(R.string.build_supported_64_bit_abis),
            Build.SUPPORTED_64_BIT_ABIS.joinToString().takeIf { it.isNotEmpty() }
                ?: MyApplication.getMyString(R.string.result_not_supported)
        )

        detectBinderStatus(tempModels, "/dev/binder", R.string.binder_status)
        detectBinderStatus(tempModels, "/dev/hwbinder", R.string.hw_binder_status)
        detectBinderStatus(tempModels, "/dev/vndbinder", R.string.vnd_binder_status)
        // endregion [Arch & ABI]

        // endregion [ROM]
        add(tempModels, MyApplication.getMyString(R.string.build_user), Build.USER)
        add(tempModels, MyApplication.getMyString(R.string.build_HOST), Build.HOST)
        val time =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(Build.TIME))
        add(tempModels, MyApplication.getMyString(R.string.build_time), time)
        if (isAtLeastAndroid6()) {
            add(
                tempModels,
                MyApplication.getMyString(R.string.build_base_os),
                Build.VERSION.BASE_OS
            )
        }
        addFingerprints(tempModels)
        add(tempModels, MyApplication.getMyString(R.string.build_display), Build.DISPLAY)
        add(
            tempModels,
            MyApplication.getMyString(R.string.build_incremental),
            Build.VERSION.INCREMENTAL
        )
        add(tempModels, MyApplication.getMyString(R.string.build_type), Build.TYPE)
        add(tempModels, MyApplication.getMyString(R.string.build_tags), Build.TAGS)
        add(tempModels, MyApplication.getMyString(R.string.build_codename), Build.VERSION.CODENAME)
        // endregion [ROM]

        // endregion [Others]
        add(tempModels, MyApplication.getMyString(R.string.build_bootloader), Build.BOOTLOADER)
        add(tempModels, MyApplication.getMyString(R.string.build_radio), Build.getRadioVersion())
        // endregion [Others]

        getProp(tempModels)

        withContext(Dispatchers.Main) {
            _models.value = tempModels
        }
    }

    @ExperimentalStdlibApi
    private fun addFingerprints(tempModels: ArrayList<MyModel>) {
        if (isAtLeastAndroid10()) {
            Build.getFingerprintedPartitions().forEach {
                add(
                    tempModels,
                    MyApplication.getMyString(
                        R.string.build_certain_fingerprint,
                        it.name.capitalize(Locale.US)
                    ),
                    it.fingerprint
                )
            }
        } else {
            add(
                tempModels,
                MyApplication.getMyString(R.string.build_stock_fingerprint),
                Build.FINGERPRINT
            )
        }
    }

    private external fun getBinderVersion(driver: String): Int

    private fun detectBinderStatus(
        tempModels: ArrayList<MyModel>,
        driver: String,
        @StringRes titleId: Int
    ) {
        val binderVersion = getBinderVersion(driver)

        @StringRes val binderStatusId = if (binderVersion == -ERRNO_NO_SUCH_FILE_OR_DIRECTORY) {
            R.string.result_not_supported
        } else if (binderVersion == -ERRNO_PERMISSION_DENIED) {
            android.R.string.unknownName
        } else if (binderVersion == BINDER64_PROTOCOL_VERSION) {
            if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                R.string.abi64_binder64
            } else {
                R.string.abi32_binder64
            }
        } else if (binderVersion == BINDER32_PROTOCOL_VERSION) {
            R.string.abi32_binder32
        } else {
            android.R.string.unknownName
        }

        add(
            tempModels,
            MyApplication.getMyString(titleId),
            MyApplication.getMyString(binderStatusId)
        )
    }

    private suspend fun getProp(tempModels: ArrayList<MyModel>) {
        val rawBuildProp = MyApplication.sharedPreferences.getBoolean(
            MyApplication.getMyString(R.string.function_raw_build_prop_key), false
        )

        if (!rawBuildProp) {
            return
        }

        var temp = ""
        shAsync(CMD_GETPROP).await().forEach {
            if (it.startsWith("[") && it.endsWith("]")) {
                addRawProp(tempModels, it)
            } else {
                temp += "$it\n"

                if (it.endsWith("]")) {
                    addRawProp(tempModels, temp)

                    temp = ""
                }
            }
        }
    }

    private fun addRawProp(tempModels: ArrayList<MyModel>, text: String) {
        val result = text.split(": ")
        add(tempModels, removeSquareBrackets(result[0]), removeSquareBrackets(result[1]))
    }

    private fun removeSquareBrackets(text: String) =
        text.substringAfter("[").substringBefore(']').trimIndent()

    private fun add(tempModels: ArrayList<MyModel>, title: String, detail: String?) {
        val translatedDetail = if (detail.isNullOrEmpty()) {
            MyApplication.getMyString(R.string.build_not_filled)
        } else {
            detail.toString()
        }

        tempModels.add(MyModel(title, translatedDetail))
    }
}
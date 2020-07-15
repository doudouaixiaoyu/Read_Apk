package com.ghtn.update

import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.FileSystem
import com.github.mjdev.libaums.fs.UsbFile
import com.github.mjdev.libaums.fs.UsbFileInputStream
import java.io.*
import java.util.*

/**
 * Author: GeHaoRan
 * Date: 2020/7/14 10:56 PM
 * Doc: 从U盘更新Apk
 */
class UpdateApkForU : AppCompatActivity() {

    private var storageDevices: Array<UsbMassStorageDevice>? = null
    private val fileApkList: MutableList<UsbFile> = mutableListOf()
    private var uFileSystem: FileSystem? = null

    private var installApk: File? = null

    companion object {
        const val ACTION_USB_PERMISSION = "com.android.usb.USB_PERMISSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /// 注册广播
        val usbDeviceStateFilter = IntentFilter()
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        usbDeviceStateFilter.addAction(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, usbDeviceStateFilter)
    }

    /// 广播注册器
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            when (p1?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Toast.makeText(p0, "优盘已插入", Toast.LENGTH_SHORT).show()
                    checkUPermission()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Toast.makeText(p0, "优盘已拔出", Toast.LENGTH_SHORT).show()
                }

                ACTION_USB_PERMISSION -> {
                    val usbDevice: UsbDevice? = p1.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (p1.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null) {
                            // 不为空,则执行let 里的代码
                            getUsbMass(usbDevice)?.let { readUFile(it) }
                        }
                    } else {
                        Toast.makeText(p0, "未授权U盘设备", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 检查u盘权限
     */
    private fun checkUPermission() {
        val usbManager =
            applicationContext?.getSystemService(Context.USB_SERVICE) as UsbManager

        storageDevices = UsbMassStorageDevice.getMassStorageDevices(this)

        val mPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent(ACTION_USB_PERMISSION),
            0
        )

        for (device in storageDevices!!) {
            if (usbManager.hasPermission(device.usbDevice)) {
                Toast.makeText(applicationContext, "有权限", Toast.LENGTH_SHORT).show()
                readUFile(device)
            } else {
                // 没有权限，申请U盘权限，并指定广播接受
                usbManager.requestPermission(device.usbDevice, mPendingIntent)
            }
        }
    }

    /**
     * 得到U盘Mass设备
     */
    private fun getUsbMass(usbDevice: UsbDevice): UsbMassStorageDevice? {
        for (device in storageDevices!!) {
            if (usbDevice == device.usbDevice) {
                return device
            }
        }
        return null
    }

    /**
     * 读取U盘文件
     */
    private fun readUFile(usbMassStorageDevice: UsbMassStorageDevice) {
        usbMassStorageDevice.init()
        uFileSystem = usbMassStorageDevice.partitions[0].fileSystem

        val root = uFileSystem!!.rootDirectory

        val files = root.listFiles()

        getApkFileList(files)
    }

    /**
     * 得到U盘中所有的Apk文件
     */
    private fun getApkFileList(root: Array<UsbFile>) {
        for (file in root) {
            if (file.isDirectory) {
                getApkFileList(file.listFiles())
            } else {
                if (file.name.trim().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    fileApkList.add(file)
                }
            }
        }
    }

    /**
     * 开始复制文件
     */
    private fun copyUsbFileToLocal() {
        if (fileApkList.isNotEmpty()) {
            val param = CopyTaskParam()
            val uFile = fileApkList[0]

            param.from = uFile
            val f = Environment.getExternalStoragePublicDirectory("Download")
            f.mkdirs()

            val file = File("${f.path}/${uFile.name}")
            if (!file.exists()) {
                file.createNewFile()
            }

            param.to = file

            // 复制文件
            CopyTask().execute(param)
        }
    }


    /**
     * 内部类，复制文件参数
     */
    private class CopyTaskParam {
        var from: UsbFile? = null
        var to: File? = null
    }

    /**
     * 内部类，复制文件
     */
    private inner class CopyTask :
        AsyncTask<CopyTaskParam, Int, Void>() {

        private var dialog: ProgressDialog = ProgressDialog(this@UpdateApkForU)
        private var param: CopyTaskParam = CopyTaskParam()

        init {
            dialog = ProgressDialog(this@UpdateApkForU)
            dialog.setTitle("复制文件")
            dialog.setMessage("复制u盘文件到本地文件下载文件夹中")
            dialog.isIndeterminate = false
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            dialog.setCancelable(false)
        }

        override fun onPreExecute() {
            dialog.show()
        }

        override fun doInBackground(vararg p0: CopyTaskParam?): Void? {
            val time = System.currentTimeMillis()
            param = p0[0]!!
            try {
                val out: OutputStream = BufferedOutputStream(FileOutputStream(param.to))
                val inputStream: InputStream = UsbFileInputStream(param.from!!)
                val bytes = ByteArray(this@UpdateApkForU.uFileSystem!!.chunkSize)

                var count: Int
                var total = 0
                do {
                    count = inputStream.read(bytes)
                    if (count != -1) {
                        out.write(bytes, 0, count)
                        total += count
                        var progress = total
                        if (param.from!!.length > Int.MAX_VALUE) {
                            progress = total / 1024
                        }
                        publishProgress(progress)
                    } else {
                        break
                    }
                } while (true)
                out.close()
                inputStream.close()
            } catch (e: IOException) {
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            dialog.dismiss()
            param.to?.delete()
            getNeedApkFile()
        }

        override fun onProgressUpdate(vararg values: Int?) {
            var max = param.from!!.length
            if (param.from?.length!! > Int.MAX_VALUE) {
                max = (param.from!!.length / 1024)
            }
            dialog.max = max.toInt()
            dialog.progress = values[0]!!
        }
    }


    /**
     * 获得符合条件的Apk
     */
    private fun getNeedApkFile() {
        val download = Environment.getExternalStoragePublicDirectory("Download")

        val apkArray: MutableList<File> = mutableListOf()

        // 获取报名相同的
        download.listFiles()?.let {
            for (apkPackage in it) {
                val apkPackageName = getPackageNameFromApk(apkPackage.path)
                if (apkPackageName == packageName) {
                    apkArray.add(apkPackage)
                }
            }
        }

        // 获取版本号比当前应用版本号高的
        if (apkArray.isNotEmpty()) {
            for (needApk in apkArray) {
                val needApkVersion = getVersionCodeFromApk(needApk.path)
                val nowApkVersion = getNowVersionCode()
                if (needApkVersion.toDouble() > nowApkVersion.toDouble()) {
                    installApk = needApk
                }
            }
        }
    }


    /**
     * 获得指定位置Apk 版本号
     */
    private fun getVersionCodeFromApk(path: String): String {
        val pm: PackageManager = this.packageManager
        return pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)?.versionName
            ?: "1.0"
    }

    /**
     * 获得指定位置Apk 包名
     */
    private fun getPackageNameFromApk(path: String): String? {
        val pm: PackageManager = this.packageManager
        return pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)?.packageName
    }

    /**
     * 安装指定位置Apk
     */
    private fun installApk(path: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val apkUri: Uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", path)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            apkUri = Uri.fromFile(path)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        intent.setDataAndType(
            apkUri,
            "application/vnd.android.package-archive"
        )

        startActivity(intent)
    }

    /**
     * 安装符合条件的Apk
     */
    private fun installNeedApkFile() {
        installApk?.let { installApk(it) }
    }

    /**
     * 获得当前应用的版本号
     */
    private fun getNowVersionCode(): String {
        return packageManager.getPackageInfo(packageName, 0).versionName
    }

    /**
     * 获得符合条件的Apk版本号
     */
    private fun getNeedApkVersionCode(): String {
        return if (installApk != null) {
            getVersionCodeFromApk(installApk!!.path)
        } else {
            "1.0"
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

}
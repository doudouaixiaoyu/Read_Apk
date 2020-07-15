package com.read_apk


import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.github.mjdev.libaums.UsbMassStorageDevice
import com.github.mjdev.libaums.fs.FileSystem
import com.github.mjdev.libaums.fs.UsbFile
import com.github.mjdev.libaums.fs.UsbFileInputStream
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*


class MainActivity : AppCompatActivity(), UsbReceiver.Message {

    //    private val usbReceiver = UsbReceiver()

    private var storageDevices: Array<UsbMassStorageDevice>? = null

    private val fileApkList: MutableList<UsbFile> = mutableListOf()

    private var uFileSystem: FileSystem? = null

    companion object {
        const val ACTION_USB_PERMISSION = "com.android.usb.USB_PERMISSION"
    }

    // 广播接受实现
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
                    val usbDevice: UsbDevice = p1.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (p1.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Toast.makeText(p0, "已授权${usbDevice.deviceId}", Toast.LENGTH_SHORT).show()

                        // 不为空,则执行let 里的代码
                        getUsbMass(usbDevice)?.let { readUFile(it) }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (supportActionBar != null) {
            supportActionBar?.hide()
        }
        setContentView(R.layout.activity_main)


        /// 注册广播
        val usbDeviceStateFilter = IntentFilter()
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        usbDeviceStateFilter.addAction(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, usbDeviceStateFilter)

/*        val usbDeviceStateFilter = IntentFilter()
        usbDeviceStateFilter.addAction(Intent.ACTION_MEDIA_MOUNTED)
        usbDeviceStateFilter.addAction(Intent.ACTION_MEDIA_EJECT)
        usbDeviceStateFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        usbDeviceStateFilter.addAction(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, usbDeviceStateFilter)
        usbReceiver.setMessage(this)*/

        checkPermission()

        read_button.setOnClickListener {
            readApkByU()
        }

        read_Local_button.setOnClickListener {
            readApkByLocal()
        }

        copy_file_button.setOnClickListener {
            copyUsbToLocal()
        }
    }


    /**
     * 检查文件读取权限
     */
    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                // 提醒用户授权
                Toast.makeText(applicationContext, "您需要此权限来更新APK", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
            }
        }
    }

    /**
     * 申请权限返回结果
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 用户同意
                } else {
                    // 用户没同意
                    Toast.makeText(applicationContext, "您没有同意", Toast.LENGTH_SHORT).show()
                }
                return
            }
            else -> {

            }
        }

    }


    /**
     * 检查是否有U盘权限
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


/*        if (mDevices.isNotEmpty() && mDevices.size != 0) {

            for (usb: UsbDevice in mDevices.values) {
                if (usbManager!!.hasPermission(usb)) {
                    Toast.makeText(applicationContext, "有权限", Toast.LENGTH_SHORT).show()
                } else {
                    usbManager!!.requestPermission(usb, mPendingIntent)
                }
            }
        }*/
        // 循环迭代器
/*            val iterator: Iterator<UsbDevice> = mDevices.values.iterator()
            while (iterator.hasNext()) {
                val usb: UsbDevice = iterator.next()
                if (!usbManager.hasPermission(usb)) {
                    Toast.makeText(applicationContext, "${usb.deviceName}没有权限", Toast.LENGTH_SHORT)
                        .show()
                    usbManager.requestPermission(usb, mPendingIntent)
                } else {
                    Toast.makeText(applicationContext, "有权限", Toast.LENGTH_SHORT).show()
                }
            }*/

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

//        usbMassStorageDevice.close()
    }

    /**
     * 复制U盘的指定文件到本地文件
     */
    private fun copyUsbToLocal() {
        val param = CopyTaskParam()
        val uFile = fileApkList[0]

        param.from = uFile
        val f = Environment.getExternalStoragePublicDirectory("Download")
        f.mkdirs()

        val file = File("${f.path}/${uFile.name}")
        if (!file.exists()) {
            file.createNewFile()
        }


        Toast.makeText(applicationContext, file.path, Toast.LENGTH_LONG).show()

        /*     val index =
                 if (uFile.name.lastIndexOf(".") > 0) uFile.name.lastIndexOf(".") else uFile.name.length

             var prefix = uFile.name.substring(0, index)
             val ext = uFile.name.substring(index)

             if (prefix.length < 3) {
                 prefix += "pad"
             }



             param.to = File.createTempFile(prefix, ext, f)*/

        param.to = file

        // 复制文件
        CopyTask().execute(param)
    }

    private class CopyTaskParam {
        var from: UsbFile? = null
        var to: File? = null
    }

    private inner class CopyTask :
        AsyncTask<CopyTaskParam, Int, Void>() {

        private var dialog: ProgressDialog = ProgressDialog(this@MainActivity)
        private var param: CopyTaskParam = CopyTaskParam()

        init {
            dialog = ProgressDialog(this@MainActivity)
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
                val bytes = ByteArray(this@MainActivity.uFileSystem!!.chunkSize)

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
            // 获得刚刚复制的文件的版本信息
            val packageInfo = getVersionCodeFromApk(param.to!!.path)
            Toast.makeText(
                applicationContext,
                "${packageInfo.packageName} | ${packageInfo.versionName}",
                Toast.LENGTH_LONG
            ).show()
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
     * 获得指定位置Apk 版本信息
     */
    private fun getVersionCodeFromApk(path: String): PackageInfo {
        val pm: PackageManager = this.packageManager
        return pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
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
     * 获得U盘所有Apk 列表
     */
    private fun readApkByU() {
/*
        val savePath = Environment.getExternalStoragePublicDirectory("Download").absolutePath

        if (fileApkList.size == 0) {
            Toast.makeText(applicationContext, "当前U盘里没有Apk 安装文件", Toast.LENGTH_SHORT).show()
            return
        }
*/


/*        object : Thread() {
            override fun run() {
                super.run()
                result = copyUsbToLocal(fileApkList[0], savePath, object : DownLoadProgress {
                    override fun fileDownLoadProgress(progress: Int) {
                        val text = "下载进度为$progress"
                        down.text = text
                    }
                })
            }
        }.start()*/

        if (fileApkList.size == 0) {
            Toast.makeText(applicationContext, "当前U盘里没有Apk 安装文件", Toast.LENGTH_SHORT).show()
            return
        }
        val adapter =
            ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, fileApkList)
        list.adapter = adapter
    }

    /**
     * 获得本地下载中所有Apk文件
     */
    private fun readApkByLocal() {
        val savePath = Environment.getExternalStoragePublicDirectory("Download")
        if (savePath.listFiles().isEmpty()) {
            Toast.makeText(applicationContext, "当前下载文件夹里没有Apk 安装文件", Toast.LENGTH_SHORT).show()
            return
        }
        val adapter =
            ArrayAdapter(
                applicationContext,
                android.R.layout.simple_list_item_1,
                savePath.listFiles()
            )
        list.adapter = adapter
    }

    override fun getMsg(string: String) {
        Toast.makeText(applicationContext, string, Toast.LENGTH_SHORT).show()
    }

    override fun getUsbDevices() {

        val usbManager: UsbManager =
            applicationContext?.getSystemService(Context.USB_SERVICE) as UsbManager

        val mDevices: HashMap<String, UsbDevice> = usbManager.deviceList

        val mPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent(ACTION_USB_PERMISSION),
            0
        )

        if (mDevices.isNotEmpty() && mDevices.size != 0) {
            val iterator: Iterator<UsbDevice> = mDevices.values.iterator()
            while (iterator.hasNext()) {
                val usb: UsbDevice = iterator.next()
                if (!usbManager.hasPermission(usb)) {
                    Toast.makeText(applicationContext, "${usb.deviceName}没有权限", Toast.LENGTH_SHORT)
                        .show()
                    usbManager.requestPermission(usb, mPendingIntent)
                } else {
                    Toast.makeText(applicationContext, "有权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}

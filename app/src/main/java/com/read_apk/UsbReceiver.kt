package com.read_apk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.widget.Toast

/**
 * Author: GeHaoRan
 * Date: 2020/7/9 4:21 PM
 * Doc:
 */
class UsbReceiver : BroadcastReceiver() {

    private lateinit var message: Message

    override fun onReceive(p0: Context?, p1: Intent?) {
        when (p1?.action) {
            Intent.ACTION_MEDIA_CHECKING -> {
                Toast.makeText(p0, "ACTION_MEDIA_CHECKING", Toast.LENGTH_SHORT).show()
            }

            Intent.ACTION_MEDIA_MOUNTED -> {
                val uri = p1.data
                if (uri != null) {
                    val filePath = uri.path
                    Toast.makeText(p0, filePath, Toast.LENGTH_SHORT).show()
                }
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
//                val deviceAdd: UsbManager =
//                    p1.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

//                val usbManager: UsbManager = p0?.getSystemService(Context.USB_SERVICE) as UsbManager

                message.getUsbDevices()

/*                if (deviceAdd != null) {
//                    message.getMsg(deviceAdd.deviceName)
                    val mDevice = deviceAdd.getde
                }*/
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Toast.makeText(p0, "优盘已拔出", Toast.LENGTH_SHORT).show()
            }
        }
    }


    interface Message {
        fun getMsg(string: String)

        fun getUsbDevices()
    }

    fun setMessage(msg: Message) {
        this.message = msg
    }

}
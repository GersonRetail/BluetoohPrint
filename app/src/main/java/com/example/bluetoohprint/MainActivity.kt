package com.example.bluetoohprint

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.bluetoohprint.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.StringBuilder
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.util.UUID
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    val connectionClass = ConnectionClass()

    var value = ""

    private lateinit var binding: ActivityMainBinding
    private var btPermission = false
    var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var bluetoohDevice: BluetoothDevice? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var workerThread: Thread? = null
    private lateinit var readBuffer: ByteArray
    private var readBufferPosition = 0
    private var stopWorker by Delegates.notNull<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Bluetooth Printer Example Kotlin"
    }

    fun scanBt(view: View) {
        checkPermission()
        print_inv()
    }

    fun print(view: View) {
        if (btPermission) {
            print_inv()
        } else {
            checkPermission()
        }


    }

    fun checkPermission() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            //Device doesnt support Bluetooh
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    private val blueToothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            btPermission = true
            if (bluetoothAdapter?.isEnabled == false) {
                val enabledBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                btActivityResultLauncher.launch(enabledBtIntent)
            } else {
                btScan()
            }
        }
    }
    private val btActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            btScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun btScan() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoohAdapter: BluetoothAdapter? = bluetoothManager.adapter
        val builder = AlertDialog.Builder(this@MainActivity)
        val inflater = layoutInflater
        val dialogView: View = inflater.inflate(R.layout.scan_bt, null)
        builder.setCancelable(false)
        builder.setView(dialogView)
        val btlst = dialogView.findViewById<ListView>(R.id.bt_lst)
        val dialog = builder.create()
        val pairedDevices: Set<BluetoothDevice> =
            bluetoohAdapter?.bondedDevices as Set<BluetoothDevice>
        val ADhere: SimpleAdapter
        var data: MutableList<Map<String?, Any?>?>? = null
        data = ArrayList()
        if (pairedDevices.isNotEmpty()) {
            val datanum1: MutableMap<String?, Any?> = HashMap()
            datanum1["A"] = ""
            datanum1["B"] = ""
            data.add(datanum1)
            for (device in pairedDevices) {
                val datanum: MutableMap<String?, Any?> = HashMap()
                datanum["A"] = device.name
                datanum["B"] = device.address
                data.add(datanum)
            }
            val fromwhere = arrayOf("A")
            val viewswhere = intArrayOf(R.id.item_name)
            ADhere =
                SimpleAdapter(this@MainActivity, data, R.layout.item_list, fromwhere, viewswhere)
            btlst.adapter = ADhere
            ADhere.notifyDataSetChanged()
            btlst.onItemClickListener =
                AdapterView.OnItemClickListener { adapterView, view, position, l ->
                    val string = ADhere.getItem(position) as HashMap<String, String>
                    val prnName = string["A"]
                    //devicename no esta especificado
                    binding.deviceName.setText(prnName)
                    connectionClass.privater_name = prnName.toString()
                    dialog.dismiss()
                }
        } else {
            val value = "No Devices found"
            Toast.makeText(this, value, Toast.LENGTH_LONG).show()
            return
        }
        dialog.show()
    }

    fun beginListenForData() {
        try {
            val handler = Handler()
            val delimiter: Byte = 10
            stopWorker = false
            readBufferPosition = 0
            readBuffer = ByteArray(1024)
            workerThread = Thread {
                while (!Thread.currentThread().isInterrupted && !stopWorker) {
                    try {
                        val bytesAvailable = inputStream!!.available()
                        if (bytesAvailable > 0) {
                            val packetBytes = ByteArray(bytesAvailable)
                            inputStream!!.read(packetBytes)
                            for (i in 0 until bytesAvailable) {
                                val b = packetBytes[i]
                                if (b == delimiter) {
                                    val encodedBytes = ByteArray(readBufferPosition)
                                    System.arraycopy(
                                        readBuffer, 0,
                                        encodedBytes, 0,
                                        encodedBytes.size
                                    )
                                    val data = String(encodedBytes, Charset.forName("US-ASCII"))
                                    readBufferPosition = 0
                                    handler.post { Log.d("e", data) }
                                } else {
                                    readBuffer[readBufferPosition++] = b
                                }
                            }

                        }

                    } catch (ex: IOException) {
                        stopWorker = true
                    }
                }
            }
            workerThread!!.start()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun InitPrinter() {
        var prname: String = ""
        prname = connectionClass.privater_name.toString()
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        try {
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled) {
                    val enableBluetooh = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    btActivityResultLauncher.launch(enableBluetooh)
                }
            }
            val pairedDevices = bluetoothAdapter?.bondedDevices
            if (pairedDevices != null) {
                if (pairedDevices.size > 0) {
                    if (pairedDevices != null) {
                        for (device in pairedDevices) {
                            if (device.name == prname) {
                                bluetoohDevice = device
                                val uuid =
                                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                                val m = bluetoohDevice!!.javaClass.getMethod(
                                    "createRfcommSocket", *arrayOf<Class<*>?>(
                                        Int::class.javaPrimitiveType
                                    )
                                )
                                socket = m.invoke(bluetoohDevice, 1) as BluetoothSocket
                                bluetoothAdapter?.cancelDiscovery()
                                socket!!.connect()
                                outputStream = socket!!.outputStream
                                inputStream = socket!!.inputStream
                                beginListenForData()
                                break
                            }
                        }
                    }
                } else {
                    val value = "No Devices found"
                    Toast.makeText(this, value, Toast.LENGTH_LONG).show()
                }
            }
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, "BlueTooh Printer Not Connected", Toast.LENGTH_LONG).show()
            socket = null
        }
    }

    //version Antigua
    /*fun print_inv() {
         try {
             var str: String
             var invhdr: String = "Retail Custom Solution"
             var addr: String = "Deccon Tech"
             var mo: String = ""
             var gstin: String = ""
             var billno: String = ""
             var billdt: String = ""
             var tblno: String = ""
             var stw: String = ""
             var msg: String = ""
             var amtwd: String = ""

             val wnm = "Self"
             val logName = "Admin"
             var amt = 0.0
             var gst = 0.0
             var gamt = 0.0
             var cmpname: String = "Sunmi V2 PRO LA"
             mo = "901624110"
             gstin = "Gst no"
             billno = "1001"
             billdt = "RUC: 205158484"
             tblno = "5"

             msg = "Gracias por su compra"
             amtwd = "Son ciento y cinco con 00 / 100 SOLES"
             amt = 100.00
             gst = 5.0
             gamt = 105.00
             val textData = StringBuilder()
             val textData1 = StringBuilder()
             val textData2 = StringBuilder()
             val textData3 = StringBuilder()
             val textData4 = StringBuilder()
             if (invhdr.isNotEmpty()) {
                 textData.append("""$invhdr"""".trimIndent())
             }
             textData.append(
                 """"$cmpname"""".trimIndent()
             )
             if (mo.isNotEmpty()) {
                 textData.append("""$mo""")
             }
             if (gstin.isNotEmpty()) {
                 textData.append("""$gstin""".trimIndent())
             }
             str = ""
             str = String.format("%-14s %17s", "Inv#$billno", "Table#:$tblno")

             textData.append("""$str""".trimIndent())
             textData.append("Date time: $billdt\n")

             //method = "ALIGN_LEFT";
             textData1.append("-----------------------------\n")
             textData1.append(
                 """
                     Item Description
                     """.trimIndent()
             )
             str = ""
             str = String.format("%-11s %9s %10s", "Qty", "Rate", "Amount")
             textData1.append(
                 """
                     $str
                     """.trimIndent()
             )
             textData1.append("-------------------------------\n")
             val df = DecimalFormat("0.00")
             var itmname: String
             var rt: String?
             var qty: String
             var amount: String?
             for (i in 0 until 10) {
                 val price = 10
                 itmname = "Item $i"
                 rt = df.format(price)
                 qty = "1 pc"
                 amount = "10.0"
                 textData1.append(itmname + "\n")
                 str = ""
                 str = String.format("%-11s %9s %10s", qty, rt, amount)
                 textData1.append(str + "\n")
             }
             textData1.append("---------------------------------\n")
             str = ""

             str = String.format("%-9s %-11s %10s", wnm, "Total:", amt)
             textData1.append(str + "\n")
             str = ""

             str = String.format("%-9s %-11s %10s", logName, "Gst:", gst)
             textData1.append(str + "\n")
             str = ""
             str = String.format("%-7s %8s", "Total:", gamt)
             textData2.append(str + "\n")
             textData3.append(amtwd + "\n")

             if (msg.isNotEmpty()) {
                 textData4.append(msg + "\n")
             }
             textData4.append("Android App\n\n\n\n")
             IntentPrint(
                 textData.toString(),
                 textData1.toString(),
                 textData2.toString(),
                 textData3.toString(),
                 textData4.toString()
             )


         } catch (ex: java.lang.Exception) {
             value += "$ex\nExcep IntentPrint \n"
             Toast.makeText(this, value, Toast.LENGTH_LONG).show()
         }
     }*/

    //Refactorizado
    fun print_inv() {
        try {
            val invhdr = "Retail Custom Solution S.A.C"

            val addr = "Deccon Tech"
            val mo = "901624110"
            val gstin = "Gst no"
            val billno = "1001"
            val billdt = "RUC: 205158484"
            val tblno = "5"
            val msg = "Gracias por su compra"
            val amtwd = "Son ciento y cinco con 00 / 100 SOLES"

            val wnm = "Self"
            val logName = "Admin"
            val cmpname = "Sunmi V2 PRO LA"

            val textData = StringBuilder()
            val textData1 = StringBuilder()
            val textData2 = StringBuilder()
            val textData3 = StringBuilder()
            val textData4 = StringBuilder()

            if (invhdr.isNotEmpty()) {
                textData.append(invhdr).append("\n")
            }
            textData.append(cmpname).append("\n")
            if (mo.isNotEmpty()) {
                textData.append(mo)
            }
            if (gstin.isNotEmpty()) {
                textData.append(gstin).append("\n")
            }
            textData.append("Inv#$billno Table#:$tblno\n")
            textData.append("Date time: $billdt\n")

            textData1.append("-------------------------------\n")
            textData1.append("Item Description\n")
            textData1.append(String.format("%-11s %9s %10s\n", "Qty", "Rate", "Amount"))
            textData1.append("-------------------------------\n")

            val df = DecimalFormat("0.00")

            for (i in 0 until 10) {
                val price = 10.0
                val itmname = "Item $i"
                val rt = df.format(price)
                val qty = "1 pc"
                val amount = "10.0"
                textData1.append("$itmname\n")
                textData1.append(String.format("%-11s %9s %10s\n", qty, rt, amount))
            }
            textData1.append("-------------------------------\n")
            textData1.append(String.format("%-9s %-11s %10s\n", wnm, "Total:", 100.0))
            textData1.append(String.format("%-9s %-11s %10s\n", logName, "Gst:", 5.0))
            textData2.append(String.format("%-7s %8s\n", "Total:", 105.0))
            textData3.append("$amtwd\n")

            if (msg.isNotEmpty()) {
                textData4.append("$msg\n")
            }
            textData4.append("Android App\n\n\n\n")

            IntentPrint(
                textData.toString(),
                textData1.toString(),
                textData2.toString(),
                textData3.toString(),
                textData4.toString()
            )

        } catch (ex: Exception) {
            value += "$ex\nExcep IntentPrint \n"
            Toast.makeText(this, value, Toast.LENGTH_LONG).show()
        }
    }


    fun IntentPrint(
        txtValue: String,
        txtValue1: String,
        txtValue2: String,
        txtValue3: String,
        txtValue4: String,
    ) {
        if (connectionClass.privater_name.trim().isNotEmpty()) {
            val buffer = txtValue1.toByteArray()
            val PrintHeader = byteArrayOf(0xAA.toByte(), 0x55, 2, 0)
            PrintHeader[3] = buffer.size.toByte()
            InitPrinter()
            if (PrintHeader.size > 128) {
                value += "\nValue is more than 128 size\n"
                Toast.makeText(this, value, Toast.LENGTH_LONG).show()
            } else {
                try {
                    if (socket != null) {
                        try {
                            val SP = byteArrayOf(0x1B, 0x40)
                            outputStream!!.write(SP)
                            Thread.sleep(1000)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        val FONT_1X = byteArrayOf(0x1B, 0x21, 0x00)
                        outputStream!!.write(FONT_1X)
                        val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 1)
                        outputStream!!.write(ALIGN_CENTER)
                        outputStream!!.write(txtValue.toByteArray())
                        val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0)
                        outputStream!!.write(ALIGN_LEFT)
                        outputStream!!.write(txtValue1.toByteArray())
                        val FONT_2X = byteArrayOf(0x1B, 0x21, 0x30)
                        outputStream!!.write(FONT_2X)
                        outputStream!!.write(txtValue2.toByteArray())
                        outputStream!!.write(FONT_1X)
                        outputStream!!.write(ALIGN_LEFT)
                        outputStream!!.write(txtValue3.toByteArray())
                        outputStream!!.write(ALIGN_CENTER)
                        outputStream!!.write(txtValue4.toByteArray())
                        val FEED_PAPER_AND_CUT = byteArrayOf(0x1D, 0x56, 66, 0x00)
                        outputStream!!.write(FEED_PAPER_AND_CUT)
                        outputStream!!.flush()
                        outputStream!!.close()
                        socket!!.close()
                    }
                } catch (ex: java.lang.Exception) {
                    Toast.makeText(this, ex.message.toString(), Toast.LENGTH_LONG).show()
                }
            }

        }
    }
}
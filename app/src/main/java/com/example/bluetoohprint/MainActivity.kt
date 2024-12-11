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

    // Clase para manejar conexiones
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
        // Configura el binding con la vista
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Bluetooth Printer Example Kotlin"
    }
    // Escanea dispositivos Bluetooth disponibles
    fun scanBt(view: View) {
        checkPermission()// Verifica los permisos de Bluetooth
        print_inv()// Imprime datos de ejemplo
    }

    // Imprime datos utilizando una conexión Bluetooth
    fun print(view: View) {
        if (btPermission) {
            print_inv()
        } else {
            checkPermission() // Solicita los permisos si no están otorgados
        }
    }

    // Verifica y solicita permisos necesarios para Bluetooth
    fun checkPermission() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            // El dispositivo no soporta Bluetooth
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    // Solicita permiso para Bluetooth y configura la acción posterior
    private val blueToothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            btPermission = true
            if (bluetoothAdapter?.isEnabled == false) {
                // Solicita al usuario habilitar Bluetooth
                val enabledBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                btActivityResultLauncher.launch(enabledBtIntent)
            } else {
                btScan() // Inicia el escaneo de dispositivos
            }
        }
    }

    // Maneja el resultado de la solicitud de habilitación de Bluetooth
    private val btActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            btScan() // Si el usuario habilita Bluetooth, inicia el escaneo
        }
    }

    @SuppressLint("MissingPermission")
    // Escanea dispositivos Bluetooth emparejados y los muestra en un cuadro de diálogo
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
            // Agrega los dispositivos emparejados a la lista
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
            // Configura el adaptador para mostrar los dispositivos en el ListView
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
                    binding.deviceName.setText(prnName) // Muestra el nombre del dispositivo seleccionado
                    connectionClass.privater_name = prnName.toString()
                    dialog.dismiss()
                }
        } else {
            // Si no se encuentran dispositivos, muestra un mensaje
            val value = "No Devices found"
            Toast.makeText(this, value, Toast.LENGTH_LONG).show()
            return
        }
        dialog.show() // Muestra el diálogo
    }


    // Inicia la escucha de datos entrantes desde el dispositivo Bluetooth
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
    // Inicializa la conexión con la impresora Bluetooth seleccionada
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



    // Prepara los datos para imprimir y los envía a la impresora
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


      //Imprime un conjunto de datos en una impresora Bluetooth utilizando comandos específicos de impresión.
      //txtValue Encabezado o título principal a imprimir.
      //txtValue1 Detalle adicional a imprimir (por ejemplo, descripción o información de cliente).
      //txtValue2 Texto en fuente grande (por ejemplo, total a pagar).
      //txtValue3 Texto adicional en fuente normal (por ejemplo, detalles del recibo).
      //txtValue4 Pie de página o texto centrado final (por ejemplo, mensaje de agradecimiento).

    fun IntentPrint(
        txtValue: String,
        txtValue1: String,
        txtValue2: String,
        txtValue3: String,
        txtValue4: String,
    ) {
        // Verifica si hay un dispositivo seleccionado para la impresión
        if (connectionClass.privater_name.trim().isNotEmpty()) {
            val buffer = txtValue1.toByteArray()
            val PrintHeader = byteArrayOf(0xAA.toByte(), 0x55, 2, 0)
            PrintHeader[3] = buffer.size.toByte() // Ajusta el tamaño de la cabecera de impresión

            // Inicializa la impresora antes de enviar datos
            InitPrinter()

            // Verifica si el tamaño de la cabecera supera el límite permitido
            if (PrintHeader.size > 128) {
                value += "\nValue is more than 128 size\n"
                Toast.makeText(this, value, Toast.LENGTH_LONG).show()
            } else {
                try {
                    if (socket != null) {
                        try {
                            // Comando para inicializar la impresora
                            val SP = byteArrayOf(0x1B, 0x40)
                            outputStream!!.write(SP)
                            Thread.sleep(1000) // Espera breve para garantizar la inicialización
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }

                        // Configura la fuente y alineación para el encabezado
                        val FONT_1X = byteArrayOf(0x1B, 0x21, 0x00)// Fuente normal
                        outputStream!!.write(FONT_1X)
                        val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 1)// Alineación centrada
                        outputStream!!.write(ALIGN_CENTER)
                        outputStream!!.write(txtValue.toByteArray()) // Imprime el encabezado


                        // Configura la fuente y alineación para el texto del detalle
                        val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0) // Alineación izquierda
                        outputStream!!.write(ALIGN_LEFT)
                        outputStream!!.write(txtValue1.toByteArray())


                        // Configura la fuente grande para el texto principal
                        val FONT_2X = byteArrayOf(0x1B, 0x21, 0x30)// Fuente grande
                        outputStream!!.write(FONT_2X)
                        outputStream!!.write(txtValue2.toByteArray())


                        // Imprime el texto adicional con fuente normal y alineación izquierda
                        outputStream!!.write(FONT_1X)
                        outputStream!!.write(ALIGN_LEFT)
                        outputStream!!.write(txtValue3.toByteArray())

                        // Imprime el texto final centrado
                        outputStream!!.write(ALIGN_CENTER)
                        outputStream!!.write(txtValue4.toByteArray())

                        // Comando para alimentar el papel y cortar
                        val FEED_PAPER_AND_CUT = byteArrayOf(0x1D, 0x56, 66, 0x00)
                        outputStream!!.write(FEED_PAPER_AND_CUT)

                        // Limpia el flujo de salida y cierra la conexión
                        outputStream!!.flush()
                        outputStream!!.close()
                        socket!!.close()
                    }
                } catch (ex: java.lang.Exception) {
                    // Manejo de errores al imprimir
                    Toast.makeText(this, ex.message.toString(), Toast.LENGTH_LONG).show()
                }
            }

        }
    }
}
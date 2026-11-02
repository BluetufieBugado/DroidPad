package com.phonepad.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat

/** Tela de conexão: busca o receptor e abre o controle (Wi-Fi ou Bluetooth HID). */
class MainActivity : AppCompatActivity() {
    private val found = mutableListOf<UdpDiscovery.Found>()
    private lateinit var adapter: ArrayAdapter<String>
    private var hid: HidGamepad? = null

    private val btPermLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) startBtFlow() else toast(getString(R.string.bt_no_perm))
    }

    private val btEnableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) startBtFlow()
        else toast(getString(R.string.bt_enable))
    }

    private val discoverableLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Resultado irrelevante: segue o jogo (se já pareado, conecta mesmo sem visível)
        registerHid()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(ControllerProfileStore.exportAll(this).toByteArray()) }
            Toast.makeText(this, R.string.profiles_exported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_fail, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@registerForActivityResult
            val n = ControllerProfileStore.importAll(this, json)
            Toast.makeText(this, getString(R.string.imported_n, n), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_fail, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etIp = findViewById<EditText>(R.id.etIp)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnDiscover = findViewById<Button>(R.id.btnDiscover)
        val spDevices = findViewById<Spinner>(R.id.spDevices)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf(getString(R.string.tap_find_hint)))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDevices.adapter = adapter
        spDevices.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos < found.size) etIp.setText(found[pos].ip)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnDiscover.setOnClickListener {
            tvStatus.text = getString(R.string.searching)
            btnDiscover.isEnabled = false
            Thread({
                val list = UdpDiscovery.discover { f ->
                    runOnUiThread {
                        if (found.none { it.ip == f.ip && it.port == f.port }) {
                            found.add(f)
                            refreshSpinner()
                        }
                    }
                }
                runOnUiThread {
                    btnDiscover.isEnabled = true
                    found.clear(); found.addAll(list)
                    refreshSpinner()
                    if (found.isEmpty()) {
                        tvStatus.text = getString(R.string.nothing_found)
                    } else {
                        etIp.setText(found[0].ip)
                        spDevices.setSelection(0)
                        tvStatus.text = getString(R.string.found_n, found.size)
                    }
                }
            }).start()
        }

        btnSend.setOnClickListener {
            val ip = etIp.text.toString().trim().ifEmpty { "192.168.0.100" }
            Session.start(ip)
            startActivity(Intent(this, ControllerActivity::class.java))
        }

        val btnBt = findViewById<Button>(R.id.btnBt)
        val swLegacy = findViewById<Switch>(R.id.swBtLegacy)
        swLegacy.isChecked = getSharedPreferences(HidGamepad.PREF_BT, MODE_PRIVATE)
            .getBoolean(HidGamepad.KEY_LEGACY, false)
        swLegacy.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences(HidGamepad.PREF_BT, MODE_PRIVATE).edit()
                .putBoolean(HidGamepad.KEY_LEGACY, checked).apply()
            // Descritor muda -> registra de novo (e despareia/pareia no receptor)
            if (hid?.running == true) {
                hid?.stop(); hid = null
                tvStatus.text = getString(R.string.bt_mode_changed)
                btnBt.setText(R.string.control_bt)
            }
        }
        btnBt.setOnClickListener {
            if (hid?.running == true) {
                hid?.stop(); hid = null
                btnBt.setText(R.string.control_bt)
                tvStatus.text = getString(R.string.bt_off)
            } else {
                if (!HidGamepad.supported()) {
                    toast(getString(R.string.bt_unsupported))
                    return@setOnClickListener
                }
                Session.stop()
                startBtFlow()
            }
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportLauncher.launch("droidpad-profiles.json")
        }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }

        findViewById<Button>(R.id.btnLang).setOnClickListener {
            showLanguageDialog()
        }
        val swImmersive = findViewById<Switch>(R.id.swImmersive)
        swImmersive.isChecked = getSharedPreferences("phonepad_ui", MODE_PRIVATE)
            .getBoolean("immersive", true)
        swImmersive.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences("phonepad_ui", MODE_PRIVATE).edit()
                .putBoolean("immersive", checked).apply()
        }
    }

    private fun showLanguageDialog() {
        val tags = arrayOf("", "pt", "en", "es")
        val labels = arrayOf(getString(R.string.lang_system), "Português", "English", "Español")
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checked = tags.indexOf(current).takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle(R.string.lang_title)
            .setSingleChoiceItems(labels, checked) { d, which ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshSpinner() {
        val labels = if (found.isEmpty()) listOf(getString(R.string.spinner_empty)) else found.map { "${it.name} (${it.ip})" }
        adapter.clear(); adapter.addAll(labels); adapter.notifyDataSetChanged()
    }

    // ---------- Bluetooth HID ----------

    private fun startBtFlow() {
        if (Build.VERSION.SDK_INT >= 31) {
            val need = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
                .filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }.toTypedArray()
            if (need.isNotEmpty()) {
                btPermLauncher.launch(need)
                return
            }
        }
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) {
            toast(getString(R.string.bt_none))
            return
        }
        if (!adapter.isEnabled) {
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        // Fica visível p/ parear (300s). Se recusar, segue mesmo assim (vale p/ já pareados).
        try {
            discoverableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            })
        } catch (_: Exception) {
            registerHid()
        }
    }

    private fun registerHid() {
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnBt = findViewById<Button>(R.id.btnBt)
        if (hid?.running == true) return
        val legacy = getSharedPreferences(HidGamepad.PREF_BT, MODE_PRIVATE)
            .getBoolean(HidGamepad.KEY_LEGACY, false)
        hid = HidGamepad(applicationContext, legacy, object : HidGamepad.Listener {
            override fun onRegistered(ok: Boolean) {
                runOnUiThread {
                    if (ok) {
                        tvStatus.text = getString(R.string.bt_visible)
                        btnBt.setText(R.string.bt_stop)
                        startActivity(Intent(this@MainActivity, ControllerActivity::class.java))
                    } else {
                        tvStatus.text = getString(R.string.bt_reg_fail)
                        hid = null
                    }
                }
            }

            override fun onDevice(connected: Boolean, name: String?) {
                runOnUiThread {
                    tvStatus.text = if (connected) getString(R.string.bt_connected, name ?: "?")
                    else getString(R.string.bt_disconnected)
                }
            }
        })
        hid?.start()
        tvStatus.text = getString(R.string.bt_registering)
    }

    override fun onDestroy() {
        if (isFinishing) { hid?.stop(); hid = null }
        super.onDestroy()
    }
}

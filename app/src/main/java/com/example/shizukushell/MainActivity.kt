package com.example.shizukushell

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.example.shizukushell.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val outputBuilder = StringBuilder()

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                appendOutput("[+] Shizuku permission granted\n")
            } else {
                appendOutput("[-] Permission denied\n")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        binding.btnCheckShizuku.setOnClickListener { checkShizuku() }
        binding.btnClear.setOnClickListener {
            outputBuilder.clear()
            binding.tvOutput.text = ""
        }

        binding.etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO) {
                runCommand()
                true
            } else false
        }

        binding.btnRun.setOnClickListener { runCommand() }
    }

    private fun checkShizuku() {
        appendOutput("[*] Checking Shizuku...\n")
        try {
            if (!Shizuku.pingBinder()) {
                appendOutput("[-] Shizuku is NOT running\n")
                return
            }
            appendOutput("[+] Shizuku is running\n")
            appendOutput("[*] Shizuku version: ${Shizuku.getVersion()}\n")
            appendOutput("[*] UID: ${Shizuku.getUid()}\n")

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                appendOutput("[+] Permission already granted\n")
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                appendOutput("[-] Permission denied (user said no)\n")
            } else {
                appendOutput("[*] Requesting permission...\n")
                Shizuku.requestPermission(1)
            }
        } catch (e: Exception) {
            appendOutput("[-] Error: ${e.message}\n")
        }
    }

    private fun runCommand() {
        val cmd = binding.etCommand.text.toString().trim()
        if (cmd.isEmpty()) return

        binding.etCommand.setText("")
        appendOutput("\n$ $cmd\n")

        if (!Shizuku.pingBinder()) {
            appendOutput("[-] Shizuku not running\n")
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            appendOutput("[-] No Shizuku permission\n")
            return
        }

        Thread {
            try {
                val process = Shizuku.newProcess(
                    arrayOf("sh", "-c", cmd),
                    null,
                    null
                )

                val stdout = BufferedReader(InputStreamReader(process.inputStream))
                val stderr = BufferedReader(InputStreamReader(process.errorStream))

                val outText = stdout.readText()
                val errText = stderr.readText()

                process.waitFor()

                runOnUiThread {
                    if (outText.isNotEmpty()) appendOutput(outText)
                    if (errText.isNotEmpty()) appendOutput("[stderr] $errText")
                    appendOutput("[exit: ${process.exitValue()}]\n")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    appendOutput("[-] Exception: ${e.message}\n")
                }
            }
        }.start()
    }

    private fun appendOutput(text: String) {
        outputBuilder.append(text)
        binding.tvOutput.text = outputBuilder.toString()
        binding.scrollView.post {
            binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }
}

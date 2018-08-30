package co.fadaojia.skyeye

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val REQUEST_PERMISSIONS = 0
        const val API_KEY: String = "ba2d70f1a5542ec2b2bbdc8da915d013"
        const val API_SECRET: String = "a31aa14ef574b4bc4befccabfcd78fbd"
        val PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private lateinit var btnStart: Button
    private lateinit var etStore: EditText
    private lateinit var etGroup: EditText
    private lateinit var rgDoors: RadioGroup

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        val deniedPermissions = PERMISSIONS.filterNot { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (deniedPermissions.isNotEmpty()) {
            requestPermissions(deniedPermissions.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            initSKFace()
        }
    }

    override fun onDestroy() {
        LibCenter.getInstance(applicationContext).skFace.SKFaceReleaseSDK()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            val deniedPermissions = permissions.filterIndexed { i, _ -> grantResults[i] != PackageManager.PERMISSION_GRANTED }
            if (deniedPermissions.isNotEmpty()) {
                requestPermissions(deniedPermissions.toTypedArray(), REQUEST_PERMISSIONS)
            } else {
                initSKFace()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun initView() {
        etStore = findViewById(R.id.store_id)
        etGroup = findViewById(R.id.group_id)
        rgDoors = findViewById(R.id.doors)
        btnStart = findViewById(R.id.button)
        btnStart.setOnClickListener { _ ->
            when {
                etStore.text.isNullOrBlank() -> Toast.makeText(applicationContext, "店铺编号不能为空", Toast.LENGTH_SHORT).show()
                etGroup.text.isNullOrBlank() -> Toast.makeText(applicationContext, "分组ID不能为空", Toast.LENGTH_SHORT).show()
                rgDoors.checkedRadioButtonId == -1 -> Toast.makeText(applicationContext, "请选择入口或出口", Toast.LENGTH_SHORT).show()
                else -> {
                    val intent = Intent(this, FaceJackActivity::class.java)
                    intent.putExtra("store", etStore.text.toString())
                    intent.putExtra("group", etGroup.text.toString())
                    val checked = rgDoors.indexOfChild(findViewById(rgDoors.checkedRadioButtonId))
                    intent.putExtra("door", if (checked == 0) "entrance" else "exit")
                    startActivity(intent)
                }
            }
        }
    }

    private fun initSKFace() {
        val libCenter = LibCenter.getInstance(applicationContext)
        libCenter.skFace.SKFaceInitSDK(API_KEY, API_SECRET) { code, msg ->
            Log.d(TAG, "$code:$msg")
            if (code == 0) {
                Log.d(TAG, "sdk version: ${libCenter.skFace.SKFaceGetSDKVersion()}")
                Log.d(TAG, "model version: ${libCenter.skFace.SKFaceGetModelVersion()}")
                runOnUiThread { btnStart.isEnabled = true }
            }
        }
    }
}

package co.fadaojia.skyeye

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.drawable.BitmapDrawable
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.interjoy.skface.FaceStruct
import com.interjoy.skface.SKFaceEnum
import com.interjoy.skface.callbackinterface.*
import kotlinx.serialization.internal.ArrayListSerializer
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.json.JSON
import java.lang.ref.WeakReference
import java.util.*


class FaceActivity : AppCompatActivity() {

    companion object {
        const val TAG = "FaceActivity"
        const val WIDTH = 640
        const val HEIGHT = 480
        const val MSG_REGISTERED = 1
        const val SERVER_URL: String = "http://skfrm.sk-ai.com"
        const val SIDE_LEN = 256
    }

    private var isTracking: Boolean = false
    private var processing: Boolean = false
    private var registering: Boolean = false

    private lateinit var storeId: String
    private lateinit var groupId: String
    private lateinit var doorKey: String

    private lateinit var svPreview: SurfaceView
    private lateinit var tvTips: TextView
    private lateinit var ivQRCode: ImageView
    private lateinit var msgHandler: MessageHandler

    private var camera: Camera? = null
    private var previewWidth: Int? = null
    private var previewHeight: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face)

        storeId = intent.getStringExtra("store")
        groupId = intent.getStringExtra("group")
        doorKey = intent.getStringExtra("door")
        msgHandler = MessageHandler(this)

        initView()
        startListening()
    }

    override fun onDestroy() {
        val skFace = LibCenter.getInstance(applicationContext).skFace
        skFace.SKFaceStopRegPerson()

        registering = false
        processing = false
        isTracking = false
        msgHandler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }


    private fun displayQRCode(shopId: String, personId: String, deviceId: String) {
        val redirect = "https://m.fadaojia.co/transfer?shop=$shopId&person=$personId&device=$deviceId"
        val bitmap = encodeAsBitmap("https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx0a94f6df1a12a685&redirect_uri=${Uri.encode(redirect)}&response_type=code&scope=snsapi_base&state=1&connect_redirect=1#wechat_redirect")
        ivQRCode.setImageBitmap(bitmap)
        ivQRCode.visibility = View.VISIBLE
    }

    private fun clearQRCode() {
        (ivQRCode.drawable as BitmapDrawable).bitmap?.recycle()
        tvTips.text = "欢迎光临"
        ivQRCode.setImageBitmap(null)
        ivQRCode.visibility = View.GONE
    }

    private fun encodeAsBitmap(content: String): Bitmap {
        val hintMap = Hashtable<EncodeHintType, ErrorCorrectionLevel>()
        hintMap[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
        val qrCodeEncoder = QRCodeWriter()
        val bitMatrix = qrCodeEncoder.encode(content, BarcodeFormat.QR_CODE, SIDE_LEN, SIDE_LEN, hintMap)
        val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun initView() {
        svPreview = findViewById(R.id.preview)
        svPreview.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        svPreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
                Log.d(TAG, "surface changed: format($format), width($width), height($height)")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                closeCamera()
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                startCamera()
            }
        })
        tvTips = findViewById(R.id.tips)
        ivQRCode = findViewById(R.id.qrcode)
    }

    private fun startListening() {
        val libCenter = LibCenter.getInstance(applicationContext)
        val skFace = libCenter.skFace
        val ready: Boolean = skFace.SKFaceSetServer(SERVER_URL)
        Log.d(TAG, "server is ready: $ready")
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_REGISTERD_PERSON_NUM, NetRegisteredPersonNumCallbackInterface { Num ->
            Log.d(TAG, "NETWORK_DB_REGISTERD_PERSON_NUM: $Num")
        })
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_SYNC_PROGRESS, NetDBSyncProCallbackInterface { SyncPersonNum, PersonTotal ->
            Log.d(TAG, "NETWORK_DB_SYNC_PROGRESS: $SyncPersonNum / $PersonTotal")
        })
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.PERSON_LEAVE, PersonLeaveCallbackInterface { PersonLeaveInfo ->
            Log.d(TAG, "PERSON_LEAVE: $PersonLeaveInfo")
            runOnUiThread { clearQRCode() }
        })
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.PERSON_ENTER, PersonEnterCallbackInterface { PersonEnterInfo ->
            Log.d(TAG, "PERSON_ENTER: $PersonEnterInfo")
            val person = JSON.parse<PersonInfo>(PersonEnterInfo)
            if (person.Person_Name?.startsWith(storeId)!!) {
                runOnUiThread {
                    tvTips.text = "请扫描屏幕上的二维码"
                    displayQRCode(storeId, person.Person_Name.toString(), doorKey)
                }
            } else if (person.Person_id != -1 && !person.Person_Name.isNullOrBlank()) {
                Log.d(TAG, "opening $doorKey of $storeId")
                runOnUiThread {
                    tvTips.text = person.Person_Name
                    clearQRCode()
                }
                "http://wx.fadaojia.co/command/$storeId/$doorKey/switch".httpPost().responseString { _, _, result ->
                    when (result) {
                        is Result.Failure -> {
                            Log.d(TAG, "open failure: ${result.getException()}")
                            runOnUiThread { tvTips.text = "开门失败" }
                        }
                        is Result.Success -> {
                            Log.d(TAG, "open success: ${result.get()}")
                            runOnUiThread { tvTips.text = "门已打开" }
                        }
                    }
                }
            } else {
                Log.d(TAG, "person id: ${person.Person_id}")
            }
        })
    }

    private fun startCamera() {
        camera = Camera.open(if (Camera.getNumberOfCameras() > 1) Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK)
        initCamera()
        camera!!.setPreviewDisplay(svPreview.holder)
        camera!!.setPreviewCallback { bytes, camera ->
            val libCenter = LibCenter.getInstance(applicationContext)
            if (!isTracking) {
                val previewSize = camera.parameters.previewSize
                Log.d(TAG, "camera preview size: ${previewSize.width} * ${previewSize.height}")
                isTracking = libCenter.skFace.SKFaceVideoTrackInit(previewSize.width, previewSize.height, 3, true, 0)
            } else if (!processing && !registering) {
                processing = true
                libCenter.skFace.SKFaceVideoTrackYUV(bytes) { FaceStructs ->
                    checkFaces(FaceStructs)
                    processing = false
                }
            }
        }
    }

    private fun checkFaces(structs: Array<out FaceStruct>?) {
        if (structs == null) {
            return
        }
        val libCenter = LibCenter.getInstance(applicationContext)
        val skFace = libCenter.skFace
        val faces = structs.filterNot { it.Name.isNullOrBlank() }
        if (faces.isEmpty()) {
            return
        }
        if (faces.size > 1) {
            Log.d(TAG, "注意尾随：${faces.size}")
            return
        }
        val face = faces.first()
        Log.d(TAG, "${face.PersonID}, ${face.Name}, ${face.Age}, ${face.Sex}, ${face.Glasses}")
        if (face.Name == "陌生人") {
            if (registering) {
                return
            }
            registering = true
            val registerName = "222" //"$storeId${System.currentTimeMillis() / 1000}"
            Log.d(TAG, "start registering: $registerName")
            skFace.SKFaceStartRegPerson(0, registerName, face.Sex, Calendar.getInstance().get(Calendar.YEAR) - face.Age, 10, 1, "", "", 0, 0, 1344, 1008, object : RegPersonCallbackInterface {
                override fun RegPersonStateCallback(code: Int, msg: String, personInfo: String) {
                    Log.d(TAG, "$code: $msg")
                    runOnUiThread { tvTips.text = msg }
                    when (code) {
                        5001 -> {
                            Log.d(TAG, personInfo)
                            val person = JSON.parse<PersonInfo>(personInfo)
                            val message = Message.obtain()
                            message.what = MSG_REGISTERED
                            message.obj = person.Person_id
                            msgHandler.sendMessageDelayed(message, 2000)
                        }
                        4001 -> {
                            registering = false
                        }
                    }
                }

                override fun RegPersonProgressInfoCallback(progress: String) {
                    Log.d(TAG, "progress: $progress")
                    val result = JSON.parse(ArrayListSerializer(IntSerializer), progress)
                    val ready = "已注册：${if (result[36] == 1) "正" else ""}${if (result[9] == 1) "右" else ""}${if (result[27] == 1) "左" else ""}${if (result[0] == 1) "上" else ""}${if (result[18] == 1) "下" else ""}"
                    val template = resources.getString(R.string.please)
                    val text: String = when {
                        result[36] == 0 -> template.format("正视前方", ready)
                        result[9] == 0 -> template.format("向右转头", ready)
                        result[27] == 0 -> template.format("向左转头", ready)
                        result[0] == 0 -> template.format("向上仰头", ready)
                        result[18] == 0 -> template.format("向下低头", ready)
                        else -> "已完成注册"
                    }
                    runOnUiThread {
                        tvTips.text = text
                    }
                }
            })
        }
    }

    private fun initCamera() {
        val params = camera!!.parameters
        params.exposureCompensation = 0
        params.set("orientation", "landscape")
        params.setRotation(0)
        params.setPreviewSize(WIDTH, HEIGHT)
        if (params.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }
        params.previewFormat = ImageFormat.NV21
        Log.d(TAG, "initCamera: ${params.previewSize.width} * ${params.previewSize.height}")
        previewWidth = params.previewSize.width
        previewHeight = params.previewSize.height

        camera!!.parameters = params
        camera!!.setDisplayOrientation(0)
        camera!!.startPreview()
    }

    private fun closeCamera() {
        camera?.setPreviewCallback(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    class MessageHandler(activity: FaceActivity) : Handler() {
        private val ref: WeakReference<FaceActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            val activity = ref.get()
            if (activity != null) {
                when (msg?.what) {
                    MSG_REGISTERED -> {
                        activity.registering = false
                    }
                }
            }
        }
    }
}

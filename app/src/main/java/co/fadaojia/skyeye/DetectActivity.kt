package co.fadaojia.skyeye

import android.content.Context
import android.graphics.*
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.text.TextPaint
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import com.interjoy.skface.FaceStruct
import com.interjoy.skface.SKFaceEnum
import com.interjoy.skface.callbackinterface.*
import java.lang.ref.WeakReference


class DetectActivity : AppCompatActivity() {

    companion object {
        const val TAG = "DetectActivity"
        const val WIDTH = 640
        const val HEIGHT = 480
        const val MSG_REGISTERED = 1
        const val SERVER_URL: String = "http://skfrm.sk-ai.com"
    }

    private val paints = arrayOf(Paint(), Paint(), Paint())//画人脸框的画笔
    private val paint = Paint() //画72个关键点的画笔
    private var textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)// 设置字体画笔
    private var canvas: Canvas? = null//画布
    private var path: Path? = null
    private var faceName: String? = null//人脸名称
    private var keyPointArr: FloatArray? = null//人脸72个关键点数组
    private var leftTopX: Float = 0.toFloat()
    private var leftTopY: Float = 0.toFloat()
    private var rightTopX: Float = 0.toFloat()
    private var rightTopY: Float = 0.toFloat()
    private var rightBottomX: Float = 0.toFloat()
    private var rightBottomY: Float = 0.toFloat()
    private var leftBottomX: Float = 0.toFloat()
    private var leftBottomY: Float = 0.toFloat()//人脸框四个角点坐标
    private var age: Int = 0//年龄
    private var sex: Int = 0//0：未知，1:女，2:男
    private var glass: Int = 0//<0:未知，0:没戴眼镜，1:普通眼镜，2:墨镜
    private var lineLen: Int = 0//人脸框边角长度
    private var faceWidth: Int = 0// 人脸框大概宽度

    private var currFaceStructs: Array<FaceStruct>? = null
    private var bmpFaceInfo: Bitmap? = null

    private var mCamera: Camera? = null
    private var cameraSize: Camera.Size? = null

    private lateinit var mSurfaceView: SurfaceView
    private lateinit var ivFaceBorder: ImageView

    private lateinit var previewCallback: CameraPreview
    private lateinit var mHandler: MessageHandler

    private var isTracking = false
    private var processing = false
    private var registering = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_face)
        initPaints()
        initView()
        initListeners()
    }

    private fun initPaints() {
        for (i in paints.indices) {
            paints[i].style = Paint.Style.STROKE
            paints[i].strokeWidth = 3f
        }
        paint.color = Color.GREEN
        paint.style = Paint.Style.FILL
        textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)
        textPaint.textSize = 16f
        textPaint.color = Color.WHITE
        paints[0].color = Color.rgb(255, 77, 185) // 女生边框颜色
        paints[1].color = Color.rgb(0, 188, 255) // 男生边框颜色
        paints[2].color = Color.rgb(238, 238, 238) // 未知性别边框颜色
    }

    private fun initView() {
        mHandler = MessageHandler(this)
        previewCallback = CameraPreview()

        ivFaceBorder = findViewById(R.id.iv_face_border)

        mSurfaceView = findViewById(R.id.preview)
        mSurfaceView.setOnClickListener { _ ->
            if (registering) {
                registering = false
                LibCenter.getInstance(this).skFace.SKFaceStopRegPerson()
            } else {
                registering = true
                startRegPerson(0, "111", 1, 1988, 2, 18, "", "", 0, 0, 640, 480)
            }
            Log.d(TAG, "registering: $registering")
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(outMetrics)
        var previewWidth = outMetrics.widthPixels
        var previewHeight = outMetrics.heightPixels
        val ratio = 1.0f * outMetrics.widthPixels / outMetrics.heightPixels
        val f1 = 1.0f * HEIGHT / WIDTH
        val f2 = 1.0f * WIDTH / HEIGHT
        if (ratio >= f1) {
            previewHeight = (f1 * previewWidth + 0.5f).toInt()
        } else {
            previewWidth = (f2 * previewHeight + 0.5f).toInt()
        }
        val fp = FrameLayout.LayoutParams(previewWidth, previewHeight)
        fp.gravity = Gravity.START
        mSurfaceView.layoutParams = fp
        mSurfaceView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        mSurfaceView.holder.addCallback(CameraCallback())
    }

    private fun initListeners() {
        val skFace = LibCenter.getInstance(this).skFace
        val setServerState = skFace.SKFaceSetServer(SERVER_URL)
        Log.d(TAG, "setServerState: $setServerState")
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_REGISTERD_PERSON_NUM, NetRegisteredPersonNumCallbackInterface { Num ->
            Log.d(TAG, "NetRegisteredPersonNum: $Num")
        })
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.PERSON_ENTER, PersonEnterCallbackInterface { personEnterInfo ->
            Log.d(TAG, "PersonEnterCallback: $personEnterInfo")
        })
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.PERSON_LEAVE, PersonLeaveCallbackInterface { personLeaveInfo ->
            Log.d(TAG, "PersonLeaveCallback: $personLeaveInfo")
        })
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_SYNC_PROGRESS, NetDBSyncProCallbackInterface { SyncPersonNum, PersonTotal ->
            Log.d(TAG, "NetDBSyncProgress: $SyncPersonNum / $PersonTotal")
        })

    }

    private fun startRegPerson(RegModel: Int, PersonName: String?, PersonSex: Int, BirthYear: Int, BirthMonth: Int, BirthDay: Int, DetailsInfo: String, GroupsID: String, StartX: Int, StartY: Int, Width: Int, Height: Int) {
        LibCenter.getInstance(this).skFace.SKFaceStartRegPerson(RegModel, PersonName, PersonSex, BirthYear, BirthMonth, BirthDay, DetailsInfo, GroupsID, StartX, StartY, Width, Height, object : RegPersonCallbackInterface {
            override fun RegPersonStateCallback(code: Int, msg: String, personInfo: String) {
                Log.d(TAG, "$code, $msg")
                when (code) {
                    5001 -> {
                        Log.d(TAG, "注册成功: $personInfo")
                        registering = false
                    }
                    4001 -> {
                        Log.d(TAG, "注册失败: $personInfo")
                        registering = false
                    }
                }
            }

            override fun RegPersonProgressInfoCallback(progress: String) {
                Log.d(TAG, "RegisterProgressInfo: $progress")
            }
        })
    }

//    private fun updateRegPersonPro(regPersonProgress: String?) {
//        hintInfo = ""
//        val jsonArray: JSONArray
//        val num: Int
//        val proArr: IntArray
//        try {
//            jsonArray = JSONArray(regPersonProgress)
//            num = jsonArray.length()
//            proArr = IntArray(num)
//            for (i in 0 until num) {
//                proArr[i] = jsonArray.getInt(i)
//            }
//        } catch (e: JSONException) {
//            return
//        }
//        if (proArr[0] == 0) {
//            hintInfo = application.getString(R.string.right)
//        } else if (proArr[18] == 0) {
//            hintInfo = application.getString(R.string.left)
//        }
//        if (!TextUtils.isEmpty(hintInfo))
//            tv_hint_info!!.text = hintInfo//更新提示信息
//        tv_reg_pro!!.text = "注册进度：" + getRegPro(proArr, RegPersonModel) + "%"//更新进度
//    }

//    /**
//     * 获取注册进度
//     *
//     * @return
//     */
//    private fun getRegPro(regRes: IntArray, regModel: Int): Float {
//        var regPro = 0f
//        var oneNum = 0//注册结果为1的个数
//        for (i in regRes.indices) {
//            if (regRes[i] == 1) oneNum++
//        }
//        if (oneNum > 0 && oneNum <= 18)
//            regPro = 30f
//        else if (oneNum > 18 && oneNum <= 35)
//            regPro = 60f
//        else if (oneNum > 35) regPro = 100f
//        return regPro
//    }

    private fun updateImage(faceStructs: Array<FaceStruct>?) {
        bmpFaceInfo!!.eraseColor(Color.TRANSPARENT)
        if (registering) {
            return
        }
        if (null == faceStructs) {
            ivFaceBorder.setImageBitmap(bmpFaceInfo)
            return
        }

        for (i in faceStructs.indices) {
            val currFaceStruct = faceStructs[i]
            faceName = currFaceStruct.Name
            age = currFaceStruct.Age
            sex = currFaceStruct.Sex
            glass = currFaceStruct.Glasses
            keyPointArr = currFaceStruct.KeyPoint

            //创建画布绘图类
            if (path != null)
                path = null
            path = Path()

            //人脸框大概宽度
            faceWidth = Math.abs(currFaceStruct.FaceSize)
            lineLen = (faceWidth * 0.2f).toInt()

            leftTopX = currFaceStruct.FaceRX1.toFloat()
            leftTopY = currFaceStruct.FaceRY1.toFloat()
            rightTopX = currFaceStruct.FaceRX2.toFloat()
            rightTopY = currFaceStruct.FaceRY2.toFloat()
            rightBottomX = currFaceStruct.FaceRX3.toFloat()
            rightBottomY = currFaceStruct.FaceRY3.toFloat()
            leftBottomX = currFaceStruct.FaceRX4.toFloat()
            leftBottomY = currFaceStruct.FaceRY4.toFloat()

            val leftTopY1: Float = leftTopY + lineLen
            val leftTopX3: Float = leftTopX + lineLen
            val rightTopX1: Float = rightTopX - lineLen
            val rightTopY3: Float = rightTopY + lineLen
            val rightBottomY1: Float = rightBottomY - lineLen
            val rightBottomX3: Float = rightBottomX - lineLen
            val leftBottomX1: Float = leftBottomX + lineLen
            val leftBottomY3: Float = leftBottomY - lineLen

            path!!.moveTo(leftTopX, leftTopY1)
            path!!.lineTo(leftTopX, leftTopY)
            path!!.lineTo(leftTopX3, leftTopY)

            path!!.moveTo(rightTopX1, rightTopY)
            path!!.lineTo(rightTopX, rightTopY)
            path!!.lineTo(rightTopX, rightTopY3)


            path!!.moveTo(rightBottomX, rightBottomY1)
            path!!.lineTo(rightBottomX, rightBottomY)
            path!!.lineTo(rightBottomX3, rightBottomY)

            path!!.moveTo(leftBottomX1, leftBottomY)
            path!!.lineTo(leftBottomX, leftBottomY)
            path!!.lineTo(leftBottomX, leftBottomY3)

            //要显示的文字
            var str = ""
            //姓名
            if (!TextUtils.isEmpty(faceName)) {//认识
                str = faceName!! + " "
            }

            var paintType = 0//画笔类型（根据性别决定使用哪种颜色的画笔）
            // 性别
            when (sex) {
                1 -> {
                    str += "女"
                    paintType = 0
                }
                2 -> {
                    str += "男"
                    paintType = 1
                }
                else -> paintType = 2
            }

            // 年龄
            if (age > 0)
                str = "$str/$age"
            //眼镜
            if (glass == 1)
            //普通眼镜
                str = "$str○-○"
            else if (glass == 2)
            //墨镜
                str = "$str●-●"


            //设置矩形框属性并画框
            canvas!!.drawPath(path!!, paints[paintType])

            canvas!!.drawText(str, 0, str.length, leftTopX, leftTopY - 20, textPaint)
        }
        ivFaceBorder.setImageBitmap(bmpFaceInfo)
    }

    private fun startCamera(holder: SurfaceHolder) {
        try {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
            initCamera()
            mCamera!!.setPreviewDisplay(holder)
            mCamera!!.setPreviewCallback(previewCallback)
        } catch (exception: Exception) {
            exception.printStackTrace()
            if (mCamera == null) {
                Toast.makeText(this, "获取Camera权限失败了！！！", Toast.LENGTH_SHORT)
                        .show()
                Log.e(TAG, "open fail NullPointerException！！！")
                return
            } else {
                mCamera!!.release()
                mCamera = null
            }
        }
    }

    private fun initCamera() {
        try {
            val params = mCamera!!.parameters
            params.exposureCompensation = 0
            params.set("orientation", "landscape")
            mCamera!!.setDisplayOrientation(0)
            params.setRotation(0)
            params.setPreviewSize(WIDTH, HEIGHT)
            val focusModes = params.supportedFocusModes
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            }
            mCamera!!.parameters = params
            mCamera!!.parameters.previewFormat = ImageFormat.NV21
            cameraSize = mCamera!!.parameters.previewSize
            bmpFaceInfo = Bitmap.createBitmap(cameraSize!!.width, cameraSize!!.height, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bmpFaceInfo!!)
            mCamera!!.startPreview()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (mCamera != null) {
            return
        }
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
        try {
            initCamera()
            mCamera!!.setPreviewDisplay(mSurfaceView.holder)
            mCamera!!.startPreview()
            mCamera!!.setPreviewCallback(previewCallback)
            processing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public override fun onPause() {
        super.onPause()
        processing = false
        mCamera!!.setPreviewCallback(null)
        mCamera!!.stopPreview()
        mCamera!!.release()
        mCamera = null
    }

    override fun onDestroy() {
        super.onDestroy()
        processing = false
    }

    private inner class CameraPreview : Camera.PreviewCallback {

        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            val skFace = LibCenter.getInstance(applicationContext).skFace

            if (!isTracking) {
                isTracking = skFace.SKFaceVideoTrackInit(cameraSize!!.width, cameraSize!!.height, 3, true, 0)
            }

            if (!processing) {
                processing = true
                skFace.SKFaceVideoTrackYUV(data) { faceStructs ->
                    processing = false
                    currFaceStructs = faceStructs
                    mHandler.sendEmptyMessage(MSG_REGISTERED)
                }
            }
        }
    }

    private inner class CameraCallback : SurfaceHolder.Callback {

        override fun surfaceCreated(holder: SurfaceHolder) {
            startCamera(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int,
                                    height: Int) {

        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (null != mCamera) {
                mCamera!!.stopPreview()
                mCamera!!.release()
                mCamera = null
            }
        }
    }

    class MessageHandler(activity: DetectActivity) : Handler() {
        private val ref: WeakReference<DetectActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message?) {
            super.handleMessage(msg)
            val activity = ref.get()
            if (activity != null) {
                when (msg?.what) {
                    MSG_REGISTERED -> {
                        activity.updateImage(activity.currFaceStructs)
                    }
                }
            }
        }
    }
}

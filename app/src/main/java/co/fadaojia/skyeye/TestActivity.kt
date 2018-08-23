package co.fadaojia.skyeye

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import com.interjoy.skface.FaceStruct
import com.interjoy.skface.SKFace
import com.interjoy.skface.SKFaceEnum
import kotlinx.serialization.json.JSON


//@Serializable
//internal data class PersonInfo(
//        val Person_id: Int = 0,
//        var Person_Name: String? = null,
//        @Optional
//        var Person_HeadUrl: String? = null,
//        @Optional
//        val Age: Int = 0,
//        @Optional
//        val Gender: Int = 0,
//        @Optional
//        val Glasses: Int = 0,
//        @Optional
//        val AgeConfidence: Double = 0.toDouble(),
//        @Optional
//        val GenderConfidence: Double = 0.toDouble(),
//        @Optional
//        val GlassesConfidence: Double = 0.toDouble()
//)

class TestActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val REQUEST_PERMISSIONS = 0
        const val API_KEY: String = "ba2d70f1a5542ec2b2bbdc8da915d013"
        const val API_SECRET: String = "a31aa14ef574b4bc4befccabfcd78fbd"
        const val SERVER_URL: String = "http://skfrm.sk-ai.com"
        val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private var mIsTracking: Boolean = false
    private var mProcessing: Boolean = false

    private var mSurfaceWidth: Int = 0
    private var mSurfaceHeight: Int = 0

    private var mSKFace: SKFace? = null

    private lateinit var mPreview: SurfaceView

    private var mCamera: Camera? = null
//    private var mPreviewCallback: Camera.PreviewCallback? = null

//    private var mCameraThread: HandlerThread? = null
//    private var mCameraHandler: Handler? = null
//    private var mImageHandler: Handler? = null
//
//    private lateinit var mPreviewRequestBuilder: CaptureRequest.Builder
//    private var mCameraDevice: CameraDevice? = null
//    private var mCaptureSession: CameraCaptureSession? = null
//    private var mImageReader: ImageReader? = null
//    private val mCameraOpenCloseLock = Semaphore(1)

    private val mPreviewCallback = Camera.PreviewCallback { bytes, camera ->
        if (mSKFace != null) {
            if (!mIsTracking) {
                mIsTracking = mSKFace!!.SKFaceVideoTrackInit(mSurfaceWidth, mSurfaceHeight, 1)
                Log.d(TAG, "isTracking: $mIsTracking")
            }
            if (mIsTracking && !mProcessing) {
                mProcessing = true
//                mSKFace!!.SKFaceVideoTrackYUV(bytes, false) { faceStructs ->
//                    markFaces(faceStructs)
//                    mProcessing = false
//                }
            }
        }
    }

//    private val mStateCallback = object : CameraDevice.StateCallback() {
//
//        override fun onOpened(cameraDevice: CameraDevice) {
//            mCameraOpenCloseLock.release()
//            mCameraDevice = cameraDevice
//            startPreview()
//        }
//
//        override fun onDisconnected(cameraDevice: CameraDevice) {
//            mCameraOpenCloseLock.release()
//            cameraDevice.close()
//            mCameraDevice = null
//        }
//
//        override fun onError(cameraDevice: CameraDevice, error: Int) {
//            mCameraOpenCloseLock.release()
//            cameraDevice.close()
//            mCameraDevice = null
//        }
//
//    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!permissionsGranted(PERMISSIONS)) {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
        }
        initView()
        initSKFace()
    }

    override fun onDestroy() {
        releaseSKFace()
        closeCamera()
        super.onDestroy()
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (permissionsGranted(PERMISSIONS)) {
                initSKFace()
                openCamera()
            } else {
                requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun initView() {
        mPreview = findViewById(R.id.preview)
        mPreview.holder.setKeepScreenOn(true)
        mPreview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
                Log.d(TAG, "surfaceChanged: format($format), width($width), height($height)")
                mSurfaceWidth = width
                mSurfaceHeight = height
                openCamera()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                closeCamera()
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                Log.d(TAG, "surface created")
            }
        })
    }

    @TargetApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        Log.d(TAG, "init camera")
        mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
        mCamera!!.parameters.exposureCompensation = 0
        mCamera!!.setDisplayOrientation(90)
        mCamera!!.parameters.setRotation(90)
        mCamera!!.parameters.setPreviewSize(640, 480)
//        mCamera!!.parameters.setPreviewSize(mSurfaceWidth, mSurfaceHeight)
        if (mCamera!!.parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            mCamera!!.parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }
        mCamera!!.parameters.previewFormat = ImageFormat.NV21
        mCamera!!.setPreviewDisplay(mPreview.holder)
        mCamera!!.setPreviewCallback(mPreviewCallback)
        mCamera!!.startPreview()
//        if (mImageHandler == null) {
//            mImageHandler = Handler(Looper.getMainLooper())
//        }
//        if (mCameraThread == null) {
//            mCameraThread = HandlerThread("CameraBackground")
//            mCameraThread!!.start()
//        }
//        if (mCameraHandler == null) {
//            mCameraHandler = Handler(mCameraThread!!.looper)
//        }
//        mImageReader = ImageReader.newInstance(mSurfaceWidth, mSurfaceHeight, ImageFormat.YUV_420_888, 1)
//        mImageReader!!.setOnImageAvailableListener({ reader ->
//            if (mSKFace != null) {
//                if (!mIsTracking) {
//                    mIsTracking = mSKFace!!.SKFaceVideoTrackInit(mSurfaceWidth, mSurfaceHeight, 3)
//                    Log.d(TAG, "isTracking: $mIsTracking")
//                } else {
//                    val image = reader.acquireNextImage()
//                    mSKFace!!.SKFaceVideoTrackYUV(getBytes(image), false) { faceStructs ->
//                        markFaces(faceStructs)
//                    }
//                    image.close()
//                }
//            }
//        }, mImageHandler)
//        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
//        if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
//            throw RuntimeException("Time out waiting to lock camera opening.")
//        }
//        for ((index, cameraId) in manager.cameraIdList.withIndex()) {
//            Log.d(TAG, "camera[$index]: $cameraId")
//        }
//        val cameraId = manager.cameraIdList.last() as String
//        manager.openCamera(cameraId, mStateCallback, mImageHandler)
    }

    private fun closeCamera() {
        mCamera?.setPreviewCallback(null)
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera = null
//        mCameraOpenCloseLock.acquire()
//        closePreviewSession()
//        mCameraDevice?.close()
//        mCameraDevice = null
//        mCameraOpenCloseLock.release()
//        mCameraThread?.quitSafely()
//        mCameraThread?.join()
//        mCameraThread = null
//        mCameraHandler = null
//        mImageHandler = null
        Log.d(TAG, "camera closed")
    }

//    private fun startPreview() {
//        if (mCameraDevice == null) {
//            return
//        }
//        closePreviewSession()
//        mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
//        mPreviewRequestBuilder.addTarget(mPreview.holder.surface)
//        mPreviewRequestBuilder.addTarget(mImageReader!!.surface)
//        mCameraDevice!!.createCaptureSession(listOf(mPreview.holder.surface, mImageReader!!.surface), object : CameraCaptureSession.StateCallback() {
//            override fun onConfigured(cameraCaptureSession: CameraCaptureSession?) {
//                mCaptureSession = cameraCaptureSession
//                updatePreview()
//            }
//
//            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession?) {
//                Toast.makeText(applicationContext, "onConfigureFailed", Toast.LENGTH_SHORT).show()
//            }
//        }, mCameraHandler)
//    }
//
//    private fun updatePreview() {
//        if (mCameraDevice == null) {
//            return
//        }
//        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
//        mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mCameraHandler)
//    }
//
//    private fun closePreviewSession() {
//        mCaptureSession?.close()
//        mCaptureSession = null
//    }

    private fun markFaces(structs: Array<out FaceStruct>?) {
        if (structs == null) {
            return
        }
        for ((i, struct) in structs.withIndex()) {
            Log.d(TAG, "face[$i]: ${struct.Name}, ${struct.Age}, ${struct.Sex}")
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun initSKFace() {
        Log.d(TAG, "init SKFaceOne")
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (mSKFace == null) {
            mSKFace = SKFace(applicationContext)
        }
        mSKFace!!.SKFaceInitSDK(API_KEY, API_SECRET) { code, msg ->
            Toast.makeText(applicationContext, "$code:$msg", Toast.LENGTH_SHORT).show()
            mSKFace!!.SKFaceSetServer(SERVER_URL)
            bindSKFaceListeners()
        }
    }

    private fun releaseSKFace() {
        mSKFace!!.SKFaceReleaseSDK()
        mSKFace = null
        mIsTracking = false
        Log.d(TAG, "SKFaceOne released")
    }

    private fun bindSKFaceListeners() {
        Log.d(TAG, "bindSKFaceListeners")
        mSKFace!!.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.PERSON_ENTER) { personEnterInfo: String ->
            Log.d(TAG, "PersonEnterCallback: $personEnterInfo")
            val personInfo = JSON.parse<PersonInfo>(personEnterInfo)
            Toast.makeText(applicationContext, personInfo.Person_Name, Toast.LENGTH_SHORT).show()
        }
        mSKFace!!.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.PERSON_LEAVE) { personLeaveInfo: String ->
            Log.d(TAG, "PersonLeaveCallback: $personLeaveInfo")
            val personInfo = JSON.parse<PersonInfo>(personLeaveInfo)
            Toast.makeText(applicationContext, personInfo.Person_Name, Toast.LENGTH_SHORT).show()
        }
        mSKFace!!.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_SYNC_PROGRESS) { syncPersonNum: Int, personTotal: Int ->
            Log.d(TAG, "NetDBSyncProCallback: $syncPersonNum / $personTotal")
            if (syncPersonNum == personTotal) {
                Toast.makeText(applicationContext, "synced: $personTotal", Toast.LENGTH_SHORT).show()
            }
        }
        mSKFace!!.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_REGISTERD_PERSON_NUM) { num: Int ->
            Log.d(TAG, "NetRegisteredPersonNumCallback: $num")
            Toast.makeText(applicationContext, "registered: $num", Toast.LENGTH_SHORT).show()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun permissionsGranted(permissions: Array<out String>): Boolean {
        return permissions.firstOrNull { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED } == null
    }

//    private fun parseInfo(image: Image) {
//        Log.d(TAG, "image format: ${image.format}")
//        Log.d(TAG, "image width: ${image.width}")
//        Log.d(TAG, "image height: ${image.height}")
//        Log.d(TAG, "get data from ${image.planes.size} planes")
//        for ((i, plane) in image.planes.withIndex()) {
//            Log.d(TAG, "plane $i")
//            Log.d(TAG, " pixelStride: ${plane.pixelStride}")
//            Log.d(TAG, " rowStride: ${plane.rowStride}")
//            Log.d(TAG, " buffer size: ${plane.buffer.remaining()}")
//            Log.d(TAG, " colStride: ${plane.buffer.remaining() / plane.rowStride}")
//        }
//    }
//
//    private fun getBytes(image: Image): ByteArray {
//        assert(image.format == ImageFormat.YUV_420_888)
//
//        val width = image.width
//        val height = image.height
//        val planes = image.planes
//
//        val result = ByteArray(width * height * 3 / 2)
//
//        var stride = planes[0].rowStride
//        if (stride == width) {
//            planes[0].buffer.get(result, 0, width)
//        } else {
//            for (row in 0 until height) {
//                planes[0].buffer.position(row * stride)
//                planes[0].buffer.get(result, row * width, width)
//            }
//        }
//
//        stride = planes[1].rowStride
//
//        assert(stride == planes[2].rowStride)
//
//        val rowBytesCb = ByteArray(stride)
//        val rowBytesCr = ByteArray(stride)
//
//        for (row in 0 until height / 2) {
//            val rowOffset = width * height + width / 2 * row
//            planes[1].buffer.position(row * stride)
//            planes[1].buffer.get(rowBytesCb, 0, width / 2)
//            planes[2].buffer.position(row * stride)
//            planes[2].buffer.get(rowBytesCr, 0, width / 2)
//
//            for (col in 0 until width / 2) {
//                result[rowOffset + col * 2] = rowBytesCr[col]
//                result[rowOffset + col * 2 + 1] = rowBytesCb[col]
//            }
//        }
//        return result
//    }

//    private fun getNV21Bytes(image: Image): ByteArray {
//        val data = ByteArray(image.width * image.height * ImageFormat.getBitsPerPixel(image.format) / 8)
//        val rowStride = ByteArray(image.planes[0].rowStride)
//        var channelOffset = 0
//        var outputStride = 1
//        for ((i, plane) in image.planes.withIndex()) {
//            when (i) {
//                0 -> {
//                    channelOffset = 0
//                    outputStride = 1
//                }
//                1 -> {
//                    channelOffset = image.width * image.height + 1
//                    outputStride = 2
//                }
//                2 -> {
//                    channelOffset = image.width * image.height
//                    outputStride = 2
//                }
//            }
//            val buffer = plane.buffer
//            val shift = if (i == 0) 0 else 1
//            val w = image.width shr shift
//            val h = image.height shr shift
//            buffer.position(plane.rowStride * (image.cropRect.top shr shift) + plane.pixelStride * (image.cropRect.left shr shift))
//            for (row in 0 until h) {
//                val length: Int
//                if (plane.pixelStride == 1 && outputStride == 1) {
//                    length = w
//                    buffer.get(data, channelOffset, length)
//                    channelOffset += length
//                } else {
//                    length = (w - 1) * plane.pixelStride + 1
//                    buffer.get(rowStride, 0, length)
//                    for (col in 0 until w) {
//                        data[channelOffset] = rowStride[col * plane.pixelStride]
//                        channelOffset += outputStride
//                    }
//                }
//                if (row < h - 1) {
//                    buffer.position(buffer.position() + plane.rowStride - length)
//                }
//            }
//        }
//        return data
//    }
}

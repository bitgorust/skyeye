package co.fadaojia.skyeye;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.kittinunf.fuel.FuelKt;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.interjoy.skface.FaceStruct;
import com.interjoy.skface.SKFace;
import com.interjoy.skface.SKFaceEnum;
import com.interjoy.skface.SKFaceEnum.PersonEvent;
import com.interjoy.skface.callbackinterface.FaceDetectCallbackInterface;
import com.interjoy.skface.callbackinterface.NetDBSyncProCallbackInterface;
import com.interjoy.skface.callbackinterface.NetRegisteredPersonNumCallbackInterface;
import com.interjoy.skface.callbackinterface.PersonEnterCallbackInterface;
import com.interjoy.skface.callbackinterface.PersonLeaveCallbackInterface;
import com.interjoy.skface.callbackinterface.RegPersonCallbackInterface;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Reflection;
import kotlinx.serialization.KSerialLoader;
import kotlinx.serialization.SerializationKt;
import kotlinx.serialization.internal.ArrayListSerializer;
import kotlinx.serialization.internal.IntSerializer;
import kotlinx.serialization.json.JSON;

public final class FaceJackActivity extends AppCompatActivity {

    public static final String TAG = "FaceJackActivity";
    public static final int WIDTH = 640;
    public static final int HEIGHT = 480;
    public static final int MSG_REGISTERED = 1;
    public static final String SERVER_URL = "http://skfrm.sk-ai.com";
    public static final int SIDE_LEN = 256;

    private boolean isTracking;
    private boolean processing;
    private boolean registering;
    private String storeId;
    private String groupId;
    private String doorKey;
    private SurfaceView svPreview;
    private TextView tvTips;
    private ImageView ivQRCode;
    private MessageHandler msgHandler;
    private Camera camera;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face);

        Intent intent = getIntent();
        storeId = getIntent().getStringExtra("store");
        groupId = intent.getStringExtra("group");
        doorKey = getIntent().getStringExtra("door");
        msgHandler = new MessageHandler(this);

        initView();
        startListening();
    }

    protected void onDestroy() {
        SKFace skFace = LibCenter.Companion.getInstance(getApplicationContext()).getSkFace();
        skFace.SKFaceStopRegPerson();

        registering = false;
        processing = false;
        isTracking = false;
        msgHandler.removeCallbacksAndMessages((Object)null);

        super.onDestroy();
    }

    private void displayQRCode(String shopId, String personId, String deviceId) {
        String redirect = "https://m.fadaojia.co/transfer?shop=" + shopId + "&person=" + personId + "&device=" + deviceId;
        Bitmap bitmap = this.encodeAsBitmap("https://open.weixin.qq.com/connect/oauth2/authorize?appid=wx0a94f6df1a12a685&redirect_uri=" + Uri.encode(redirect) + "&response_type=code&scope=snsapi_base&state=1&connect_redirect=1#wechat_redirect");
        ivQRCode.setImageBitmap(bitmap);
        ivQRCode.setVisibility(View.VISIBLE);
    }

    private void clearQRCode() {
        BitmapDrawable bitmapDrawable = (BitmapDrawable) ivQRCode.getDrawable();
        bitmapDrawable.getBitmap().recycle();
        tvTips.setText("欢迎光临");
        ivQRCode.setImageBitmap(null);
        ivQRCode.setVisibility(View.GONE);
    }

    private Bitmap encodeAsBitmap(String content) {
        Hashtable hintMap = new Hashtable<EncodeHintType, ErrorCorrectionLevel>();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        QRCodeWriter qrCodeEncoder = new QRCodeWriter();
        Bitmap bitmap = null;
        try {
            BitMatrix bitMatrix = qrCodeEncoder.encode(content, BarcodeFormat.QR_CODE, 256, 256, hintMap);
            bitmap = Bitmap.createBitmap(bitMatrix.getWidth(), bitMatrix.getHeight(), Config.RGB_565);
            for (int x = 0; x < bitMatrix.getWidth(); x++) {
                for (int y = 0; y < bitMatrix.getHeight(); y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
        } catch (WriterException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private void initView() {
        svPreview = findViewById(R.id.preview);
        SurfaceHolder holder = svPreview.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder.addCallback(new Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                startCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surface changed: format(" + format + "), width(" + width + "), height(" + height + ')');
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                closeCamera();
            }
        });
        tvTips = findViewById(R.id.tips);
        ivQRCode = findViewById(R.id.qrcode);
    }

    private void startListening() {
        LibCenter libCenter = LibCenter.Companion.getInstance(getApplicationContext());
        SKFace skFace = libCenter.getSkFace();
        boolean ready = skFace.SKFaceSetServer(SERVER_URL);
        Log.d(TAG, "server is ready: " + ready);
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_REGISTERD_PERSON_NUM, new NetRegisteredPersonNumCallbackInterface() {
            @Override
            public void NetRegisteredPersonNumCallback(int Num) {
                Log.d(TAG, "NETWORK_DB_REGISTERD_PERSON_NUM: " + Num);
            }
        });
        skFace.SKFaceRegisterEvent(SKFaceEnum.PersonEvent.NETWORK_DB_SYNC_PROGRESS, new NetDBSyncProCallbackInterface() {
            @Override
            public void NetDBSyncProCallback(int SyncPersonNum, int PersonTotal) {
                Log.d(TAG, "NETWORK_DB_SYNC_PROGRESS: " + SyncPersonNum + " / " + PersonTotal);
            }
        });
        skFace.SKFaceRegisterEvent(PersonEvent.PERSON_LEAVE, new PersonLeaveCallbackInterface() {
            @Override
            public final void PersonLeaveCallback(String PersonLeaveInfo) {
                Log.d(TAG, "PERSON_LEAVE: " + PersonLeaveInfo);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        clearQRCode();
                    }
                });
            }
        });
        skFace.SKFaceRegisterEvent(PersonEvent.PERSON_ENTER, new PersonEnterCallbackInterface() {
            @Override
            public final void PersonEnterCallback(String PersonEnterInfo) {
                Log.d(TAG, "PERSON_ENTER: " + PersonEnterInfo);
                final PersonInfo person = (PersonInfo) JSON.Companion.parse((KSerialLoader)SerializationKt.serializer(Reflection.getOrCreateKotlinClass(PersonInfo.class)), PersonEnterInfo);
                if (Objects.requireNonNull(person.getPerson_Name()).startsWith(storeId)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvTips.setText((CharSequence)"请扫描屏幕上的二维码");
                            displayQRCode(storeId, person.getPerson_Name(), doorKey);
                        }
                    });
                } else if (person.getPerson_id() != -1 && !TextUtils.isEmpty(person.getPerson_Name())) {
                    Log.d(TAG, "opening " + doorKey + " of " + storeId);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvTips.setText(person.getPerson_Name());
                            clearQRCode();
                        }
                    });
                    FuelKt.httpPost("http://wx.fadaojia.co/command/" + storeId + "/" + doorKey + "/switch").responseString(new com.github.kittinunf.fuel.core.Handler<String>() {
                        @Override
                        public void success(Request request, Response response, String s) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvTips.setText("门已打开");
                                }
                            });
                        }

                        @Override
                        public void failure(Request request, Response response, FuelError fuelError) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvTips.setText("开门失败");
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    private void startCamera() {
        camera = Camera.open(Camera.getNumberOfCameras() > 1 ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK);
        initCamera();
        try {
            camera.setPreviewDisplay(svPreview.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
        camera.setPreviewCallback(new PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                LibCenter libCenter = LibCenter.Companion.getInstance(getApplicationContext());
                if (!isTracking) {
                    Parameters parameters = camera.getParameters();
                    Size previewSize = parameters.getPreviewSize();
                    Log.d(TAG, "camera preview size: " + previewSize.width + " * " + previewSize.height);
                    isTracking = libCenter.getSkFace().SKFaceVideoTrackInit(previewSize.width, previewSize.height, 3, true, 0);
                } else if (!processing && !registering) {
                    processing = true;
                    libCenter.getSkFace().SKFaceVideoTrackYUV(data, new FaceDetectCallbackInterface() {
                        @Override
                        public void FaceDetectCallback(FaceStruct[] faceStructs) {
                            checkFaces(faceStructs);
                            processing = false;
                        }
                    });
                }
            }
        });
    }

    private void checkFaces(FaceStruct[] structs) {
        if (structs == null) {
            return;
        }
        LibCenter libCenter = LibCenter.Companion.getInstance(getApplicationContext());
        SKFace skFace = libCenter.getSkFace();
        ArrayList<FaceStruct> faces = new ArrayList<>();
        for (int i = 0; i < structs.length; i++) {
            if (!TextUtils.isEmpty(structs[i].Name)) {
                faces.add(structs[i]);
            }
        }
        if (faces.isEmpty()) {
            return;
        }
        if (faces.size() > 1) {
            Log.d(TAG, "注意尾随：" + faces.size());
            return;
        }
        FaceStruct face = CollectionsKt.first(faces);
        if (face.Name.equals("陌生人")) {
            if (registering) {
                return;
            }
            registering = true;
            String registerName = storeId + (System.currentTimeMillis() / 1000);
            Log.d(TAG, "start registering: " + registerName);
            skFace.SKFaceStartRegPerson(0, registerName, face.Sex, Calendar.getInstance().get(Calendar.YEAR) - face.Age, 10, 1, "", "",0, 0, WIDTH, HEIGHT, new RegPersonCallbackInterface() {

                @Override
                public void RegPersonStateCallback(int code, final String msg, String personInfo) {
                    Log.d(TAG, code + ": " + msg);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvTips.setText(msg);
                        }
                    });
                    if (code == 5001) {
                        Log.d(TAG, personInfo);
                        PersonInfo person = (PersonInfo) JSON.Companion.parse((KSerialLoader)SerializationKt.serializer(Reflection.getOrCreateKotlinClass(PersonInfo.class)), personInfo);
                        Message message = Message.obtain();
                        message.what = MSG_REGISTERED;
                        message.obj = person.getPerson_id();
                        msgHandler.sendMessageDelayed(message, 2000);
                    } else if (code == 4001) {
                        registering = false;
                    }
                }

                @Override
                public void RegPersonProgressInfoCallback(String progress) {
                    Log.d("FaceActivity", "progress: " + progress);
                    final List result = (List)JSON.Companion.parse(new ArrayListSerializer(IntSerializer.INSTANCE), progress);
                    final String ready = "已注册：" + (((Number)result.get(36)).intValue() == 1 ? "正" : "") + (((Number)result.get(9)).intValue() == 1 ? "右" : "") + (((Number)result.get(27)).intValue() == 1 ? "左" : "") + (((Number)result.get(0)).intValue() == 1 ? "上" : "") + (((Number)result.get(18)).intValue() == 1 ? "下" : "");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String text = "已完成注册";
                            String template = getResources().getString(R.string.please);
                            if (((Number) result.get(36)).intValue() == 0) {
                                text = String.format(template, "正视前方", ready);
                            } else if (((Number) result.get(9)).intValue() == 0) {
                                text = String.format(template, "向右转头", ready);
                            } else if (((Number) result.get(27)).intValue() == 0) {
                                text = String.format(template, "向左转头", ready);
                            } else if (((Number) result.get(0)).intValue() == 0) {
                                text = String.format(template, "向上仰头", ready);
                            } else if (((Number) result.get(18)).intValue() == 0) {
                                text = String.format(template, "向下低头", ready);
                            }
                            tvTips.setText(text);
                        }
                    });
                }
            });
        }
    }

    private void initCamera() {
        Parameters params = camera.getParameters();
        params.setExposureCompensation(0);
        params.set("orientation", "landscape");
        params.setRotation(0);
        params.setPreviewSize(WIDTH, HEIGHT);
        if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            params.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        params.setPreviewFormat(ImageFormat.NV21);
        Log.d(TAG, "initCamera: " + params.getPreviewSize().width + " * " + params.getPreviewSize().height);
        camera.setParameters(params);
        camera.setDisplayOrientation(0);
        camera.startPreview();
    }

    private void closeCamera() {
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    public static final class MessageHandler extends Handler {
        private final WeakReference ref;

        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            FaceJackActivity activity = (FaceJackActivity) ref.get();
            if (activity != null) {
                int what = msg != null ? msg.what : -1;
                if (what == MSG_REGISTERED) {
                    activity.registering = false;
                }
            }

        }

        MessageHandler(FaceJackActivity activity) {
            super();
            ref = new WeakReference(activity);
        }
    }
}

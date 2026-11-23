package com.example.robotcontrollerbola;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.zeromq.ZMQ;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {
    private static final float STICK_THRESHOLD = 0.1f;
    private static final long SEND_INTERVAL_MS = 30L;
    private static final int PORT_ZMQ = 5555;
    private static final int PORT_UDP_CMD = 5556;
    private static final int PORT_UDP_CAM = 5005;
    private static final int TIMEOUT_HANDSHAKE_ZMQ_MS = 4000;
    private static final int TIMEOUT_ZMQ_MS = 2500;
    private static final int MAX_UDP_BYTES = 65507;
    private static final int MAX_CAM_DIMEN = 1920;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService commExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isCommandPending = new AtomicBoolean();
    private final AtomicBoolean isFrameRenderScheduled = new AtomicBoolean();
    private final AtomicReference<Bitmap> pendingFrame = new AtomicReference<>();

    private EditText inputIPaddress;
    private EditText portInput;
    private Button connectButton;
    private Button protocolButton;
    private Button stopBtnPhysical;
    private Button stopBtnVirtual;

    private MaterialButton speed30Btn;
    private MaterialButton speed70Btn;
    private MaterialButton speed100Btn;
    private MaterialButton physSpeed30Btn;
    private MaterialButton physSpeed70Btn;
    private MaterialButton physSpeed100Btn;
    private MaterialButton gripButton;

    private SwitchMaterial gamepadCameraSwitch;
    private SwitchMaterial joystickCameraSwitch;
    private Button backBtn;
    private Button backToModesBtn;
    private TextView statusText;
    private TextView gamepadStatus;
    private TextView virtualGamepadStatus;
    private TextView physicalCamPlaceholder;
    private TextView virtualCamPlaceholder;

    private TextView xValText;
    private TextView yValText;
    private TextView wzValText;
    private TextView gripValText;
    private TextView gamepadXValText;
    private TextView gamepadYValText;
    private TextView gamepadWzValText;
    private TextView gamepadGripValText;

    private View dashboardPage;
    private View gamepadPage;
    private LinearLayout modeSelectionPage;
    private ImageView physicalCamPreview;
    private ImageView virtualCamPreview;
    private Gamepad posisiJoystick;
    private Gamepad rotasiJoystick;
    private OnBackPressedCallback backPressedCallback;

    private ZMQ.Context zmqContext;
    private ZMQ.Socket socketZMQ;
    private DatagramSocket udpSocket;
    private InetAddress udpAddress;
    private volatile DatagramSocket socketCamera;
    private int udpPort;

    private volatile boolean Connecting;
    private volatile boolean Connected;
    private volatile boolean useUDP;
    private volatile boolean isUdpConn;
    private volatile int cameraSession;
    private volatile int connSession;
    private boolean isPhysicalMode;
    private boolean isVirtualGamepadMode;
    private boolean isCameraEnabled = true;
    private boolean isUpdatingCamSwitch;
    private int speedLimit = 100;
    private boolean isTurnLeft;
    private boolean isTurnRight;

    private volatile float majuMundur = 0.0f;
    private volatile float kiriKanan = 0.0f;
    private volatile float rotasi = 0.0f;
    private volatile int grip = 0;

    private final Runnable periodicSender = new Runnable() {
        @Override
        public void run() {
            if (Connected) {
                sendCommand();
            }
            uiHandler.postDelayed(this, SEND_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);

        inputIPaddress = findViewById(R.id.inputIPaddress);
        portInput = findViewById(R.id.portInput);
        connectButton = findViewById(R.id.connectButton);
        protocolButton = findViewById(R.id.protocolButton);
        stopBtnPhysical = findViewById(R.id.physicalStopButton);
        stopBtnVirtual = findViewById(R.id.virtualStopButton);

        speed30Btn = findViewById(R.id.speed30Button);
        speed70Btn = findViewById(R.id.speed70Button);
        speed100Btn = findViewById(R.id.speed100Button);
        physSpeed30Btn = findViewById(R.id.physicalSpeed30Button);
        physSpeed70Btn = findViewById(R.id.physicalSpeed70Button);
        physSpeed100Btn = findViewById(R.id.physicalSpeed100Button);
        gripButton = findViewById(R.id.gripButton);

        gamepadCameraSwitch = findViewById(R.id.gamepadCameraSwitch);
        joystickCameraSwitch = findViewById(R.id.joystickCameraSwitch);
        backBtn = findViewById(R.id.backButton);
        backToModesBtn = findViewById(R.id.backToModesButton);
        statusText = findViewById(R.id.statusText);
        gamepadStatus = findViewById(R.id.gamepadConnectionStatus);
        virtualGamepadStatus = findViewById(R.id.virtualConnectionStatus);
        physicalCamPlaceholder = findViewById(R.id.physicalCameraPlaceholder);
        virtualCamPlaceholder = findViewById(R.id.virtualCameraPlaceholder);

        xValText = findViewById(R.id.xValueText);
        yValText = findViewById(R.id.yValueText);
        wzValText = findViewById(R.id.wzValueText);
        gripValText = findViewById(R.id.gripValueText);
        gamepadXValText = findViewById(R.id.gamepadXValueText);
        gamepadYValText = findViewById(R.id.gamepadYValueText);
        gamepadWzValText = findViewById(R.id.gamepadWzValueText);
        gamepadGripValText = findViewById(R.id.gamepadGripValueText);

        dashboardPage = findViewById(R.id.dashboardPage);
        gamepadPage = findViewById(R.id.gamepadPage);
        modeSelectionPage = findViewById(R.id.modeSelectionPage);
        physicalCamPreview = findViewById(R.id.physicalCameraPreview);
        virtualCamPreview = findViewById(R.id.virtualCameraPreview);
        posisiJoystick = findViewById(R.id.posisiJoystick);
        rotasiJoystick = findViewById(R.id.rotasiJoystick);

        String initialPort = portInput.getText().toString().trim();
        if (initialPort.equals(Integer.toString(PORT_UDP_CMD))) {
            useUDP = true;
        } else {
            useUDP = false;
            if (initialPort.isEmpty()) {
                portInput.setText(Integer.toString(PORT_ZMQ));
            }
        }
        protocolButton.setText(useUDP ? "UDP  ▼" : "ZMQ  ▼");

        setupVirtualGamepad();
        if (posisiJoystick != null) posisiJoystick.setOpacity(0.5f);
        if (rotasiJoystick != null) rotasiJoystick.setOpacity(0.5f);

        connectButton.setOnClickListener(v -> {
            if (Connecting || Connected) {
                disconnect();
            } else {
                connect();
            }
        });

        protocolButton.setOnClickListener(this::showProtocolMenu);
        findViewById(R.id.physicalModeButton).setOnClickListener(v -> openPhysicalMode());
        findViewById(R.id.virtualModeButton).setOnClickListener(v -> openVirtualJoystickMode());

        backBtn.setOnClickListener(v -> backToModeSelection());
        backToModesBtn.setOnClickListener(v -> backToModeSelection());
        stopBtnPhysical.setOnClickListener(v -> emergencyStop());
        stopBtnVirtual.setOnClickListener(v -> emergencyStop());

        View.OnClickListener speed30Listener = v -> setSpeedLimit(30);
        View.OnClickListener speed70Listener = v -> setSpeedLimit(70);
        View.OnClickListener speed100Listener = v -> setSpeedLimit(100);

        if (speed30Btn != null) speed30Btn.setOnClickListener(speed30Listener);
        if (speed70Btn != null) speed70Btn.setOnClickListener(speed70Listener);
        if (speed100Btn != null) speed100Btn.setOnClickListener(speed100Listener);

        if (physSpeed30Btn != null) physSpeed30Btn.setOnClickListener(speed30Listener);
        if (physSpeed70Btn != null) physSpeed70Btn.setOnClickListener(speed70Listener);
        if (physSpeed100Btn != null) physSpeed100Btn.setOnClickListener(speed100Listener);

        if (gripButton != null) {
            gripButton.setOnClickListener(v -> toggleGrip());
        }

        gamepadCameraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingCamSwitch) {
                setCameraEnabled(isChecked);
            }
        });
        joystickCameraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isUpdatingCamSwitch) {
                setCameraEnabled(isChecked);
            }
        });

        setupEdgeToEdgeInsets();
        setupBackPressedHandling();

        setupPhysicalGamepad();
        updateDisplayValues();
        updateSpeedButtons();
        updateFormState();
        uiHandler.post(periodicSender);

        updateGamepadConnectionStatus();
    }

    private void setupEdgeToEdgeInsets() {
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void setupBackPressedHandling() {
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                backToModeSelection();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private void setupVirtualGamepad() {
        if (rotasiJoystick != null) rotasiJoystick.setHorizontalOnly(true);
        if (posisiJoystick != null) {
            posisiJoystick.setOnPositionChangedListener((joyX, joyY) -> {
                majuMundur = scaleToFloat(-joyY);
                kiriKanan = scaleToFloat(joyX);
                updateDisplayValues();
                sendCommand();
            });
        }
        if (rotasiJoystick != null) {
            rotasiJoystick.setOnPositionChangedListener((joyX, joyY) -> {
                rotasi = scaleToFloat(joyX);
                updateDisplayValues();
                sendCommand();
            });
        }
    }

    private float scaleToFloat(float rawVal) {
        float absVal = Math.abs(rawVal);
        if (absVal < STICK_THRESHOLD) {
            return 0.0f;
        }
        float speedRatio = speedLimit / 100.0f;
        float scaled = rawVal * speedRatio;
        return Math.round(scaled * 100.0f) / 100.0f;
    }

    private void openPhysicalMode() {
        isPhysicalMode = true;
        isVirtualGamepadMode = false;
        modeSelectionPage.setVisibility(View.GONE);
        dashboardPage.setVisibility(View.VISIBLE);
        gamepadPage.setVisibility(View.GONE);
        backPressedCallback.setEnabled(true);
        updateDisplayValues();
        updateCameraDisplay();
        updateGamepadConnectionStatus();
    }

    private void openVirtualJoystickMode() {
        isPhysicalMode = false;
        isVirtualGamepadMode = true;
        modeSelectionPage.setVisibility(View.GONE);
        dashboardPage.setVisibility(View.GONE);
        gamepadPage.setVisibility(View.VISIBLE);
        backPressedCallback.setEnabled(true);
        updateDisplayValues();
        updateCameraDisplay();
        updateGamepadConnectionStatus();
    }

    private void backToModeSelection() {
        isPhysicalMode = false;
        isVirtualGamepadMode = false;
        dashboardPage.setVisibility(View.GONE);
        gamepadPage.setVisibility(View.GONE);
        modeSelectionPage.setVisibility(View.VISIBLE);
        backPressedCallback.setEnabled(false);
        updateCameraDisplay();
    }

    private void setCameraEnabled(boolean enabled) {
        isCameraEnabled = enabled;
        isUpdatingCamSwitch = true;
        if (gamepadCameraSwitch.isChecked() != enabled) gamepadCameraSwitch.setChecked(enabled);
        if (joystickCameraSwitch.isChecked() != enabled) joystickCameraSwitch.setChecked(enabled);
        isUpdatingCamSwitch = false;
        updateCameraDisplay();
    }

    private void updateCameraDisplay() {
        physicalCamPreview.setVisibility(isCameraEnabled ? View.VISIBLE : View.GONE);
        virtualCamPreview.setVisibility(isCameraEnabled ? View.VISIBLE : View.GONE);

        if (!isCameraEnabled) {
            physicalCamPlaceholder.setVisibility(View.GONE);
            virtualCamPlaceholder.setVisibility(View.GONE);
            stopCamera();
            physicalCamPreview.setImageDrawable(null);
            virtualCamPreview.setImageDrawable(null);
            return;
        }

        if (dashboardPage.getVisibility() == View.VISIBLE || gamepadPage.getVisibility() == View.VISIBLE) {
            startCamera();
        } else {
            stopCamera();
        }
    }

    private void showProtocolMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("ZMQ");
        menu.getMenu().add("UDP");
        menu.setOnMenuItemClickListener(item -> {
            boolean selectUdp = "UDP".contentEquals(item.getTitle());
            if (useUDP != selectUdp) {
                int prevPort = useUDP ? PORT_UDP_CMD : PORT_ZMQ;
                int newPort = selectUdp ? PORT_UDP_CMD : PORT_ZMQ;
                if (portInput.getText().toString().trim().equals(Integer.toString(prevPort))) {
                    portInput.setText(Integer.toString(newPort));
                }
                useUDP = selectUdp;
                protocolButton.setText(useUDP ? "UDP  ▼" : "ZMQ  ▼");
            }
            return true;
        });
        menu.show();
    }

    private void setSpeedLimit(int limit) {
        speedLimit = limit;
        updateSpeedButtons();
        if (isVirtualGamepadMode && posisiJoystick != null && rotasiJoystick != null) {
            majuMundur = scaleToFloat(posisiJoystick.getPositionX() == 0 ? 0 : -posisiJoystick.getPositionY());
            kiriKanan = scaleToFloat(posisiJoystick.getPositionX());
            rotasi = scaleToFloat(rotasiJoystick.getPositionX());
        }
        updateDisplayValues();
        sendCommand();
    }

    private void updateSpeedButtons() {
        int activeBg = 0xFF000000;
        int inactiveBg = 0x80000000;
        int activeBgVirt = 0xFF000000;
        int inactiveBgVirt = 0xBFFFFFFF;

        if (speed30Btn != null) updateButtonAppearance(speed30Btn, speedLimit == 30, activeBgVirt, inactiveBgVirt);
        if (speed70Btn != null) updateButtonAppearance(speed70Btn, speedLimit == 70, activeBgVirt, inactiveBgVirt);
        if (speed100Btn != null) updateButtonAppearance(speed100Btn, speedLimit == 100, activeBgVirt, inactiveBgVirt);

        if (physSpeed30Btn != null) updateButtonAppearance(physSpeed30Btn, speedLimit == 30, activeBg, inactiveBg);
        if (physSpeed70Btn != null) updateButtonAppearance(physSpeed70Btn, speedLimit == 70, activeBg, inactiveBg);
        if (physSpeed100Btn != null) updateButtonAppearance(physSpeed100Btn, speedLimit == 100, activeBg, inactiveBg);
    }

    private void updateButtonAppearance(MaterialButton btn, boolean isActive, int activeBg, int inactiveBg) {
        btn.setBackgroundTintList(ColorStateList.valueOf(isActive ? activeBg : inactiveBg));
        btn.setTextColor(isActive ? 0xFFFFFFFF : 0xFF000000);
    }

    private void toggleGrip() {
        grip = (grip == 0) ? 1 : 0;
        updateDisplayValues();
        sendCommand();
    }

    private void emergencyStop() {
        resetControlValues();
        updateDisplayValues();
        sendCommand();
    }

    private void resetControlValues() {
        majuMundur = 0.0f;
        kiriKanan = 0.0f;
        rotasi = 0.0f;
        isTurnLeft = false;
        isTurnRight = false;
        if (posisiJoystick != null) posisiJoystick.setPosition(0, 0);
        if (rotasiJoystick != null) rotasiJoystick.setPosition(0, 0);
    }

    private void updateDisplayValues() {
        String xStr = String.format(Locale.US, "%.2f", majuMundur);
        String yStr = String.format(Locale.US, "%.2f", kiriKanan);
        String zStr = String.format(Locale.US, "%.2f", rotasi);
        String wStr = String.valueOf(grip);

        xValText.setText(xStr);
        yValText.setText(yStr);
        wzValText.setText(zStr);
        gripValText.setText(wStr);

        gamepadXValText.setText(xStr);
        gamepadYValText.setText(yStr);
        gamepadWzValText.setText(zStr);
        gamepadGripValText.setText(wStr);
    }

    private void updateStatusText(String statusStr, int colorHex) {
        statusText.setText(statusStr);
        statusText.setTextColor(colorHex);
    }

    private static String cleanHostAddress(String rawHost) {
        if (rawHost == null) return "";
        String host = rawHost.trim();
        if (host.startsWith("http://")) host = host.substring(7);
        else if (host.startsWith("https://")) host = host.substring(8);

        int slashIndex = host.indexOf('/');
        if (slashIndex != -1) host = host.substring(0, slashIndex);

        int colonIndex = host.indexOf(':');
        if (colonIndex != -1) host = host.substring(0, colonIndex);

        return host.trim();
    }

    private static boolean isLocalAddress(String host) {
        if (host.equalsIgnoreCase("localhost")) return true;
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
        } catch (IOException ignored) {
            return false;
        }
    }

    private Network findLocalNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;

            Network ethernetNet = null;
            for (Network net : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return net;
                    }
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        ethernetNet = net;
                    }
                }
            }
            return ethernetNet;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void bindProcessNetwork(Network net) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || net == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.bindProcessToNetwork(net);
            }
        } catch (RuntimeException ignored) {}
    }

    private void connect() {
        String address = cleanHostAddress(inputIPaddress.getText().toString());
        inputIPaddress.setText(address);

        final int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            updateStatusText("Invalid IP/Port", 0xFFD32F2F);
            return;
        }
        if (address.isEmpty() || port < 1 || port > 65535) {
            updateStatusText("Invalid IP/Port", 0xFFD32F2F);
            return;
        }

        resetControlValues();
        updateDisplayValues();

        final boolean tryUdp = useUDP;
        final String targetUrl = "tcp://" + address + ":" + port;
        final int session = ++connSession;
        Connected = false;
        Connecting = true;
        connectButton.setText("Cancel");
        updateFormState();
        updateStatusText("Connecting.", 0xFFF57C00);

        commExecutor.execute(() -> {
            if (session != connSession) return;
            closeComm();
            try {
                Network localNet = isLocalAddress(address) ? findLocalNetwork() : null;
                bindProcessNetwork(localNet);

                isUdpConn = tryUdp;
                if (tryUdp) {
                    udpAddress = (localNet != null) ? localNet.getByName(address) : InetAddress.getByName(address);
                    udpPort = port;
                    udpSocket = new DatagramSocket();
                    udpSocket.setSoTimeout(TIMEOUT_ZMQ_MS);
                    if (localNet != null) {
                        localNet.bindSocket(udpSocket);
                    }

                    byte[] initPacket = "0.00,0.00,0.00,0".getBytes(StandardCharsets.UTF_8);
                    udpSocket.send(new DatagramPacket(initPacket, initPacket.length, udpAddress, udpPort));
                } else {
                    zmqContext = ZMQ.context(1);
                    socketZMQ = zmqContext.socket(ZMQ.REQ);
                    socketZMQ.setLinger(0);
                    socketZMQ.setReconnectIVL(200);
                    socketZMQ.setReconnectIVLMax(1000);
                    socketZMQ.setSendTimeOut(TIMEOUT_HANDSHAKE_ZMQ_MS);
                    socketZMQ.setReceiveTimeOut(TIMEOUT_HANDSHAKE_ZMQ_MS);
                    socketZMQ.connect(targetUrl);

                    if (!socketZMQ.send("0.00,0.00,0.00,0")) {
                        throw new IOException("Handshake failed");
                    }
                    String reply = socketZMQ.recvStr();
                    if (reply == null) {
                        throw new IOException("No respon");
                    }

                    socketZMQ.setSendTimeOut(TIMEOUT_ZMQ_MS);
                    socketZMQ.setReceiveTimeOut(TIMEOUT_ZMQ_MS);
                }

                if (session != connSession) {
                    closeComm();
                    return;
                }

                uiHandler.post(() -> {
                    if (session == connSession) {
                        Connecting = false;
                        Connected = true;
                        connectButton.setText("Disconnect");
                        updateFormState();
                        updateStatusText("Connected", 0xFF388E3C);
                        sendCommand();
                    }
                });
            } catch (IOException | RuntimeException e) {
                if (session == connSession) {
                    closeComm();
                    Connected = false;
                    uiHandler.post(() -> {
                        if (session == connSession) {
                            Connecting = false;
                            Connected = false;
                            connectButton.setText("CONNECT");
                            updateFormState();
                            updateStatusText("No respon", 0xFFD32F2F);
                        }
                    });
                }
            }
        });
    }

    private void disconnect() {
        ++connSession;
        Connecting = false;
        Connected = false;
        isCommandPending.set(false);
        resetControlValues();
        updateDisplayValues();

        connectButton.setText("CONNECT");
        updateFormState();
        updateStatusText("Disconnected", 0xFFE53935);
        commExecutor.execute(this::closeComm);
    }

    private void updateFormState() {
        boolean editable = !Connected && !Connecting;
        inputIPaddress.setEnabled(editable);
        portInput.setEnabled(editable);
        protocolButton.setEnabled(editable);
    }

    private void sendCommand() {
        updateDisplayValues();
        if (!Connected) return;

        isCommandPending.set(true);
        if (!isSending.compareAndSet(false, true)) return;

        final int session = connSession;
        commExecutor.execute(() -> {
            boolean ownsSending = true;
            try {
                while (Connected && session == connSession) {
                    isCommandPending.set(false);
                    String cmdStr = String.format(Locale.US, "%.2f,%.2f,%.2f,%d", majuMundur, kiriKanan, rotasi, grip);

                    if (isUdpConn) {
                        if (udpSocket == null || udpAddress == null) break;
                        byte[] payload = cmdStr.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket packet = new DatagramPacket(payload, payload.length, udpAddress, udpPort);
                        udpSocket.send(packet);
                    } else {
                        if (socketZMQ == null) break;
                        boolean sent = socketZMQ.send(cmdStr);
                        if (!sent) throw new IllegalStateException("Send failed");

                        String reply = socketZMQ.recvStr();
                        if (reply == null) throw new IllegalStateException("No reply");
                    }

                    if (isCommandPending.getAndSet(false)) continue;

                    isSending.set(false);
                    ownsSending = false;
                    if (!isCommandPending.get() || !isSending.compareAndSet(false, true)) return;
                    ownsSending = true;
                }
            } catch (IOException | RuntimeException e) {
                isCommandPending.set(false);
                if (session == connSession) {
                    closeComm();
                    Connected = false;
                    uiHandler.post(() -> {
                        if (session == connSession) {
                            Connecting = false;
                            connectButton.setText("CONNECT");
                            updateFormState();
                            updateStatusText("Lost connection", 0xFFD32F2F);
                        }
                    });
                }
            } finally {
                if (ownsSending) {
                    isSending.set(false);
                }
            }
        });
    }

    private void startCamera() {
        if (!isCameraEnabled) return;
        stopCamera();
        final int session = ++cameraSession;
        setPlaceholderText("Connecting camera", View.VISIBLE);
        physicalCamPreview.setImageDrawable(null);
        virtualCamPreview.setImageDrawable(null);

        cameraExecutor.execute(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.setReceiveBufferSize(1048576);
                socket.bind(new InetSocketAddress(PORT_UDP_CAM));
                if (session != cameraSession) return;

                socketCamera = socket;
                CameraFrameAssembler frameAssembler = new CameraFrameAssembler();
                byte[] buffer = new byte[MAX_UDP_BYTES];

                while (session == cameraSession) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byte[] frameData = frameAssembler.accept(packet.getData(), packet.getLength());
                    if (frameData == null) continue;

                    Bitmap frame = decodeCameraFrame(frameData);
                    if (frame != null) {
                        enqueueFrame(frame, session);
                    }
                }
            } catch (IOException e) {
                if (session == cameraSession) {
                    uiHandler.post(() -> {
                        if (session == cameraSession) {
                            setPlaceholderText("Camera Error", View.VISIBLE);
                        }
                    });
                }
            } finally {
                if (socket != null) socket.close();
                if (socketCamera == socket) socketCamera = null;
            }
        });
    }

    private void stopCamera() {
        ++cameraSession;
        DatagramSocket socket = socketCamera;
        socketCamera = null;
        if (socket != null) socket.close();

        Bitmap frame = pendingFrame.getAndSet(null);
        if (frame != null) frame.recycle();
    }

    private void setPlaceholderText(String text, int visibility) {
        physicalCamPlaceholder.setText(text);
        virtualCamPlaceholder.setText(text);
        physicalCamPlaceholder.setVisibility(visibility);
        virtualCamPlaceholder.setVisibility(visibility);
    }

    private void enqueueFrame(Bitmap frame, int session) {
        if (session != cameraSession) {
            frame.recycle();
            return;
        }
        Bitmap oldFrame = pendingFrame.getAndSet(frame);
        if (oldFrame != null) oldFrame.recycle();

        if (isFrameRenderScheduled.compareAndSet(false, true)) {
            uiHandler.post(this::renderCameraFrame);
        }
    }

    private void renderCameraFrame() {
        isFrameRenderScheduled.set(false);
        Bitmap frame = pendingFrame.getAndSet(null);
        if (frame == null) return;

        ImageView preview = isPhysicalMode ? physicalCamPreview : (isVirtualGamepadMode ? virtualCamPreview : null);
        if (preview == null) {
            frame.recycle();
            return;
        }

        if (preview == physicalCamPreview) {
            physicalCamPlaceholder.setVisibility(View.GONE);
        } else {
            virtualCamPlaceholder.setVisibility(View.GONE);
        }

        preview.setImageBitmap(frame);
    }

    private static Bitmap decodeCameraFrame(byte[] frameData) {
        BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
        boundsOpts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(frameData, 0, frameData.length, boundsOpts);

        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null;

        int sampleSize = 1;
        while (boundsOpts.outWidth / sampleSize > MAX_CAM_DIMEN || boundsOpts.outHeight / sampleSize > MAX_CAM_DIMEN) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(frameData, 0, frameData.length, decodeOpts);
    }

    private void setupPhysicalGamepad() {
        View decorView = getWindow().getDecorView();
        decorView.setOnGenericMotionListener((v, event) -> {
            if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                    && event.getAction() == MotionEvent.ACTION_MOVE
                    && isPhysicalMode) {
                float rawY = getCenteredAxis(event, MotionEvent.AXIS_Y);
                float rawX = getCenteredAxis(event, MotionEvent.AXIS_X);
                float rawZ = getCenteredAxis(event, MotionEvent.AXIS_Z);
                if (rawZ == 0.0f) rawZ = getCenteredAxis(event, MotionEvent.AXIS_RZ);

                majuMundur = scaleToFloat(-rawY);
                kiriKanan = scaleToFloat(rawX);
                rotasi = scaleToFloat(rawZ);
                updateDisplayValues();
                sendCommand();
                return true;
            }
            return false;
        });

        decorView.setOnKeyListener((v, keyCode, event) -> {
            if (isPhysicalMode && isGamepadEvent(event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
                        toggleGrip();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        isTurnLeft = true;
                        rotasi = scaleToFloat(-1.0f);
                        updateDisplayValues();
                        sendCommand();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        isTurnRight = true;
                        rotasi = scaleToFloat(1.0f);
                        updateDisplayValues();
                        sendCommand();
                        return true;
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        isTurnLeft = false;
                        if (!isTurnRight) rotasi = 0.0f;
                        updateDisplayValues();
                        sendCommand();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        isTurnRight = false;
                        if (!isTurnLeft) rotasi = 0.0f;
                        updateDisplayValues();
                        sendCommand();
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private static float getCenteredAxis(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        if (device == null) return 0.0f;
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range == null) return 0.0f;
        float value = event.getAxisValue(axis);
        if (Math.abs(value) <= range.getFlat()) return 0.0f;
        return value;
    }

    private static boolean isGamepadEvent(KeyEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private void updateGamepadConnectionStatus() {
        int[] deviceIds = InputDevice.getDeviceIds();
        boolean hasGamepad = false;
        for (int id : deviceIds) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual()) {
                int sources = device.getSources();
                if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                        || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                    hasGamepad = true;
                    break;
                }
            }
        }
        String label = hasGamepad ? "Gamepad Aktif" : "No Gamepad";
        gamepadStatus.setText(label);
        virtualGamepadStatus.setText(label);
    }

    private void closeComm() {
        isUdpConn = false;
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
        udpAddress = null;

        if (socketZMQ != null) {
            socketZMQ.close();
            socketZMQ = null;
        }
        if (zmqContext != null) {
            zmqContext.close();
            zmqContext = null;
        }
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(periodicSender);
        disconnect();
        stopCamera();
        commExecutor.shutdownNow();
        cameraExecutor.shutdownNow();
        super.onDestroy();
    }
}

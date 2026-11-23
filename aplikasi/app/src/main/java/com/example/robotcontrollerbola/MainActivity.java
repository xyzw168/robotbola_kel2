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
    private static final float BATAS_STIK = 0.1f;
    private static final long JEDA_KIRIM_PERINTAH_MS = 30L;
    private static final int PORT_ZMQ = 5555;
    private static final int PORT_UDP_PERINTAH = 5556;
    private static final int PORT_KAMERA_UDP = 5005;
    private static final int TIMEOUT_HANDSHAKE_ZMQ_MS = 4000;
    private static final int TIMEOUT_ZMQ_MS = 2500;
    private static final int BATAS_UDP_BYTES = 65507;
    private static final int BATAS_DIMENSI_KAMERA = 1920;

    private final Handler antreanLayar = new Handler(Looper.getMainLooper());
    private final ExecutorService jalurKomunikasi = Executors.newSingleThreadExecutor();
    private final ExecutorService jalurKamera = Executors.newSingleThreadExecutor();
    private final AtomicBoolean pengirimanSedangBerjalan = new AtomicBoolean();
    private final AtomicBoolean adaPerintahMenunggu = new AtomicBoolean();
    private final AtomicBoolean renderFrameTerjadwal = new AtomicBoolean();
    private final AtomicReference<Bitmap> frameMenunggu = new AtomicReference<>();

    private EditText alamatRobotInput;
    private EditText portRobotInput;
    private Button tombolConnect;
    private Button tombolProtokol;
    private Button tombolStopFisik;
    private Button tombolStopVirtual;
    private Button tombolKecepatan30;
    private Button tombolKecepatan70;
    private Button tombolKecepatan100;
    private Button tombolJepitBola;
    private SwitchMaterial sakelarKameraFisik;
    private SwitchMaterial sakelarKameraVirtual;
    private Button tombolKembali;
    private Button tombolKembaliKeMode;
    private TextView teksStatus;
    private TextView statusGamepad;
    private TextView statusGamepadVirtual;
    private TextView placeholderKameraFisik;
    private TextView placeholderKameraVirtual;
    private TextView nilaiX;
    private TextView nilaiY;
    private TextView nilaiWz;
    private TextView nilaiGrip;
    private TextView nilaiXGamepad;
    private TextView nilaiYGamepad;
    private TextView nilaiWzGamepad;
    private TextView nilaiGripGamepad;
    private View halamanDashboard;
    private View halamanGamepad;
    private LinearLayout halamanPilihanMode;
    private View panelGamepadVirtual;
    private ImageView kameraFisik;
    private ImageView kameraVirtual;
    private Gamepad stikGerak;
    private Gamepad stikPutaran;
    private OnBackPressedCallback penangananTombolKembali;

    private ZMQ.Context jalurRobot;
    private ZMQ.Socket pengirimPerintah;
    private DatagramSocket soketUdp;
    private InetAddress alamatUdp;
    private volatile DatagramSocket penerimaKameraUdp;
    private int portUdp;

    private volatile boolean sedangMenghubungkan;
    private volatile boolean sudahTerhubung;
    private volatile boolean memakaiUdp;
    private volatile boolean koneksiUdp;
    private volatile int sesiKamera;
    private volatile int nomorPercobaanSambungan;
    private boolean memakaiModeFisik;
    private boolean memakaiGamepadVirtual;
    private boolean kameraDiaktifkan = true;
    private boolean memperbaruiSakelarKamera;
    private int batasKecepatan = 100;
    private boolean tombolPutarKiriDitekan;
    private boolean tombolPutarKananDitekan;
    private volatile int majuMundur;
    private volatile int kiriKanan;
    private volatile int putaran;
    private volatile int penjepitBola;

    private final Runnable pengirimBerkala = new Runnable() {
        @Override
        public void run() {
            if (sudahTerhubung) {
                kirimPerintahRobot();
            }
            antreanLayar.postDelayed(this, JEDA_KIRIM_PERINTAH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);

        alamatRobotInput = findViewById(R.id.ipAddressInput);
        portRobotInput = findViewById(R.id.portInput);
        tombolConnect = findViewById(R.id.connectButton);
        tombolProtokol = findViewById(R.id.protocolButton);
        tombolStopFisik = findViewById(R.id.physicalStopButton);
        tombolStopVirtual = findViewById(R.id.virtualStopButton);
        tombolKecepatan30 = findViewById(R.id.speed30Button);
        tombolKecepatan70 = findViewById(R.id.speed70Button);
        tombolKecepatan100 = findViewById(R.id.speed100Button);
        tombolJepitBola = findViewById(R.id.gripButton);
        sakelarKameraFisik = findViewById(R.id.physicalCameraSwitch);
        sakelarKameraVirtual = findViewById(R.id.virtualCameraSwitch);
        tombolKembali = findViewById(R.id.backButton);
        tombolKembaliKeMode = findViewById(R.id.backToModesButton);
        teksStatus = findViewById(R.id.statusText);
        statusGamepad = findViewById(R.id.gamepadConnectionStatus);
        statusGamepadVirtual = findViewById(R.id.virtualConnectionStatus);
        placeholderKameraFisik = findViewById(R.id.physicalCameraPlaceholder);
        placeholderKameraVirtual = findViewById(R.id.virtualCameraPlaceholder);
        nilaiX = findViewById(R.id.xValueText);
        nilaiY = findViewById(R.id.yValueText);
        nilaiWz = findViewById(R.id.wzValueText);
        nilaiGrip = findViewById(R.id.gripValueText);
        nilaiXGamepad = findViewById(R.id.gamepadXValueText);
        nilaiYGamepad = findViewById(R.id.gamepadYValueText);
        nilaiWzGamepad = findViewById(R.id.gamepadWzValueText);
        nilaiGripGamepad = findViewById(R.id.gamepadGripValueText);
        halamanDashboard = findViewById(R.id.dashboardPage);
        halamanGamepad = findViewById(R.id.gamepadPage);
        halamanPilihanMode = findViewById(R.id.modeSelectionPage);
        panelGamepadVirtual = findViewById(R.id.virtualGamepadPanel);
        kameraFisik = findViewById(R.id.physicalCameraPreview);
        kameraVirtual = findViewById(R.id.virtualCameraPreview);
        stikGerak = findViewById(R.id.movementJoystick);
        stikPutaran = findViewById(R.id.rotationJoystick);

        // Sinkronkan tampilan protokol & port default saat pertama dibuka
        String portAwal = portRobotInput.getText().toString().trim();
        if (portAwal.equals(Integer.toString(PORT_UDP_PERINTAH))) {
            memakaiUdp = true;
        } else {
            memakaiUdp = false;
            if (portAwal.isEmpty()) {
                portRobotInput.setText(Integer.toString(PORT_ZMQ));
            }
        }
        tombolProtokol.setText(memakaiUdp ? "UDP  ▼" : "ZMQ  ▼");

        pasangGamepadVirtual();
        stikGerak.setOpacity(0.5f);
        stikPutaran.setOpacity(0.5f);
        tombolConnect.setOnClickListener(tampilan -> {
            if (sedangMenghubungkan || sudahTerhubung) {
                putuskanDariRobot();
            } else {
                hubungkanKeRobot();
            }
        });
        tombolProtokol.setOnClickListener(tampilan -> tampilkanPilihanProtokol());
        sakelarKameraFisik.setOnCheckedChangeListener(
                (tampilan, aktif) -> {
                    if (!memperbaruiSakelarKamera) {
                        aturKameraDiaktifkan(aktif);
                    }
                }
        );
        sakelarKameraVirtual.setOnCheckedChangeListener(
                (tampilan, aktif) -> {
                    if (!memperbaruiSakelarKamera) {
                        aturKameraDiaktifkan(aktif);
                    }
                }
        );
        findViewById(R.id.physicalModeButton).setOnClickListener(
                tampilan -> bukaHalamanFisik()
        );
        findViewById(R.id.virtualModeButton).setOnClickListener(
                tampilan -> bukaHalamanGamepad()
        );
        tombolKembali.setOnClickListener(tampilan -> kembaliKePilihanMode());
        tombolKembaliKeMode.setOnClickListener(tampilan -> kembaliKePilihanMode());
        tombolStopFisik.setOnClickListener(tampilan -> hentikanRobotDarurat());
        tombolStopVirtual.setOnClickListener(tampilan -> hentikanRobotDarurat());
        tombolKecepatan30.setOnClickListener(tampilan -> aturBatasKecepatan(30));
        tombolKecepatan70.setOnClickListener(tampilan -> aturBatasKecepatan(70));
        tombolKecepatan100.setOnClickListener(tampilan -> aturBatasKecepatan(100));
        tombolJepitBola.setOnClickListener(
                tampilan -> aturPenjepitBola(penjepitBola == 0)
        );

        penangananTombolKembali = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                kembaliKePilihanMode();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, penangananTombolKembali);

        aturKendaliGamepad();
        perbaruiTampilanNilai();
        tampilkanPilihanKecepatan();
        aturFormSambungan();
        antreanLayar.post(pengirimBerkala);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (tampilan, insets) -> {
            Insets bilahSistem = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            tampilan.setPadding(
                    bilahSistem.left,
                    bilahSistem.top,
                    bilahSistem.right,
                    bilahSistem.bottom
            );
            return insets;
        });
    }

    private void pasangGamepadVirtual() {
        stikPutaran.setHorizontalOnly(true);
        stikGerak.setOnPositionChangedListener((x, y) -> {
            majuMundur = ubahKePersenKecepatan(-y);
            kiriKanan = ubahKePersenKecepatan(x);
            perbaruiTampilanNilai();
            kirimPerintahRobot();
        });
        stikPutaran.setOnPositionChangedListener((x, y) -> {
            putaran = ubahKePersenKecepatan(x);
            perbaruiTampilanNilai();
            kirimPerintahRobot();
        });
    }

    private void bukaHalamanFisik() {
        aturBatasKecepatan(100);
        memakaiModeFisik = true;
        memakaiGamepadVirtual = false;
        berhenti();
        halamanPilihanMode.setVisibility(View.GONE);
        halamanGamepad.setVisibility(View.GONE);
        halamanDashboard.setVisibility(View.VISIBLE);
        penangananTombolKembali.setEnabled(true);
        aturKendaliGamepad();
        aturBilahSistemTampil(false);
        aturTampilanKamera();
    }

    private void bukaHalamanGamepad() {
        memakaiModeFisik = false;
        memakaiGamepadVirtual = true;
        berhenti();
        halamanPilihanMode.setVisibility(View.GONE);
        halamanDashboard.setVisibility(View.GONE);
        halamanGamepad.setVisibility(View.VISIBLE);
        penangananTombolKembali.setEnabled(true);
        aturKendaliGamepad();
        aturBilahSistemTampil(false);
        aturTampilanKamera();
    }

    private void aturKameraDiaktifkan(boolean aktif) {
        kameraDiaktifkan = aktif;
        memperbaruiSakelarKamera = true;
        try {
            if (sakelarKameraFisik.isChecked() != aktif) {
                sakelarKameraFisik.setChecked(aktif);
            }
            if (sakelarKameraVirtual.isChecked() != aktif) {
                sakelarKameraVirtual.setChecked(aktif);
            }
        } finally {
            memperbaruiSakelarKamera = false;
        }
        aturTampilanKamera();
    }

    private void aturTampilanKamera() {
        kameraFisik.setVisibility(kameraDiaktifkan ? View.VISIBLE : View.GONE);
        kameraVirtual.setVisibility(kameraDiaktifkan ? View.VISIBLE : View.GONE);
        if (!kameraDiaktifkan) {
            placeholderKameraFisik.setVisibility(View.GONE);
            placeholderKameraVirtual.setVisibility(View.GONE);
            hentikanPenerimaKamera();
            kameraFisik.setImageDrawable(null);
            kameraVirtual.setImageDrawable(null);
            return;
        }

        if (halamanDashboard.getVisibility() == View.VISIBLE
                || halamanGamepad.getVisibility() == View.VISIBLE) {
            mulaiPenerimaKamera();
        }
    }

    private void kembaliKePilihanMode() {
        berhenti();
        memakaiModeFisik = false;
        memakaiGamepadVirtual = false;
        halamanGamepad.setVisibility(View.GONE);
        halamanDashboard.setVisibility(View.GONE);
        halamanPilihanMode.setVisibility(View.VISIBLE);
        penangananTombolKembali.setEnabled(false);
        aturKendaliGamepad();
        hentikanPenerimaKamera();
        aturBilahSistemTampil(true);
    }

    private void tampilkanPilihanProtokol() {
        PopupMenu menu = new PopupMenu(this, tombolProtokol);
        menu.getMenu().add("ZMQ");
        menu.getMenu().add("UDP");
        menu.setOnMenuItemClickListener(item -> {
            boolean pilihUdp = "UDP".contentEquals(item.getTitle());
            if (memakaiUdp != pilihUdp) {
                int portDefaultSebelumnya = memakaiUdp ? PORT_UDP_PERINTAH : PORT_ZMQ;
                int portDefaultBaru = pilihUdp ? PORT_UDP_PERINTAH : PORT_ZMQ;
                String portSaatIni = portRobotInput.getText().toString().trim();
                if (portSaatIni.isEmpty() || portSaatIni.equals(Integer.toString(portDefaultSebelumnya))) {
                    portRobotInput.setText(Integer.toString(portDefaultBaru));
                }
                memakaiUdp = pilihUdp;
            }
            tombolProtokol.setText(memakaiUdp ? "UDP  ▼" : "ZMQ  ▼");
            return true;
        });
        menu.show();
    }

    private void aturKendaliGamepad() {
        boolean halamanVirtualTerbuka = memakaiGamepadVirtual
                && halamanGamepad.getVisibility() == View.VISIBLE;
        panelGamepadVirtual.setVisibility(halamanVirtualTerbuka ? View.VISIBLE : View.GONE);
        stikGerak.setEnabled(halamanVirtualTerbuka);
        stikPutaran.setEnabled(halamanVirtualTerbuka);
        tombolJepitBola.setEnabled(halamanVirtualTerbuka);
    }

    private void aturBatasKecepatan(int batasBaru) {
        int batasLama = batasKecepatan;
        if (batasLama != 0 && batasBaru != batasLama) {
            majuMundur = Math.round(majuMundur * batasBaru / (float) batasLama);
            kiriKanan = Math.round(kiriKanan * batasBaru / (float) batasLama);
            putaran = Math.round(putaran * batasBaru / (float) batasLama);
        }
        batasKecepatan = batasBaru;
        tampilkanPilihanKecepatan();
        perbaruiTampilanNilai();
        kirimPerintahRobot();
    }

    private void tampilkanPilihanKecepatan() {
        aturTampilanTombolKecepatan(tombolKecepatan30, batasKecepatan == 30);
        aturTampilanTombolKecepatan(tombolKecepatan70, batasKecepatan == 70);
        aturTampilanTombolKecepatan(tombolKecepatan100, batasKecepatan == 100);
    }

    private static void aturTampilanTombolKecepatan(Button tombol, boolean aktif) {
        tombol.setBackgroundTintList(ColorStateList.valueOf(
                aktif ? 0xFF000000 : 0xBFFFFFFF
        ));
        tombol.setTextColor(aktif ? 0xFFFFFFFF : 0xFF000000);
    }

    private void hentikanRobotDarurat() {
        resetNilaiKendali();
        penjepitBola = 0;
        perbaruiTampilanNilai();
        kirimPerintahRobot();
    }

    private int ubahKePersenKecepatan(float arah) {
        return Math.round(ubahKePersen(arah) * batasKecepatan / 100f);
    }

    private void aturBilahSistemTampil(boolean tampil) {
        WindowInsetsControllerCompat pengendali = WindowCompat.getInsetsController(
                getWindow(),
                findViewById(R.id.main)
        );
        if (tampil) {
            pengendali.show(WindowInsetsCompat.Type.systemBars());
        } else {
            pengendali.hide(WindowInsetsCompat.Type.systemBars());
            pengendali.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }

    private void aturPenjepitBola(boolean jepit) {
        penjepitBola = jepit ? 1 : 0;
        perbaruiTampilanNilai();
        kirimPerintahRobot();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent gerakan) {
        boolean memakaiGamepad = gerakan.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || gerakan.isFromSource(InputDevice.SOURCE_GAMEPAD);
        if (!memakaiGamepad || !memakaiModeFisik) {
            return super.dispatchGenericMotionEvent(gerakan);
        }

        if (gerakan.getAction() == MotionEvent.ACTION_MOVE) {
            majuMundur = ubahKePersenKecepatan(-gerakan.getAxisValue(MotionEvent.AXIS_Y));
            kiriKanan = ubahKePersenKecepatan(gerakan.getAxisValue(MotionEvent.AXIS_X));
            putaran = ubahKePersenKecepatan(bacaPutaranGamepad(gerakan));
            perbaruiTampilanNilai();
            kirimPerintahRobot();
            return true;
        }
        return super.dispatchGenericMotionEvent(gerakan);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent tombol) {
        if (memakaiModeFisik) {
            if (tombol.getAction() == KeyEvent.ACTION_DOWN && tombol.getRepeatCount() == 0) {
                if (tombol.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
                    aturPenjepitBola(true);
                    return true;
                }
                if (tombol.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
                    aturPenjepitBola(false);
                    return true;
                }
                if (tombol.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                        || tombol.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    setelTombolPutar(tombol.getKeyCode(), true);
                    return true;
                }
            }
            if (tombol.getAction() == KeyEvent.ACTION_UP
                    && (tombol.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                    || tombol.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                setelTombolPutar(tombol.getKeyCode(), false);
                return true;
            }
        }
        return super.dispatchKeyEvent(tombol);
    }

    private void setelTombolPutar(int tombol, boolean ditekan) {
        if (tombol == KeyEvent.KEYCODE_DPAD_LEFT) {
            tombolPutarKiriDitekan = ditekan;
        } else {
            tombolPutarKananDitekan = ditekan;
        }

        putaran = tombolPutarKiriDitekan == tombolPutarKananDitekan
                ? 0
                : tombolPutarKiriDitekan ? -batasKecepatan : batasKecepatan;
        perbaruiTampilanNilai();
        kirimPerintahRobot();
    }

    private static int ubahKePersen(float arah) {
        if (Math.abs(arah) < BATAS_STIK) {
            return 0;
        }
        return Math.max(-100, Math.min(100, Math.round(arah * 100)));
    }

    static float sumbuPutaran(float mendatar, float tegak) {
        return Math.abs(mendatar) >= Math.abs(tegak) ? mendatar : -tegak;
    }

    static float sumbuPutaranTambahan(float kiri, float kanan) {
        return Math.max(-1, Math.min(1, (kanan - kiri) * 2));
    }

    private static float bacaPutaranGamepad(MotionEvent gerakan) {
        float sumbuUtama = sumbuPutaran(
                gerakan.getAxisValue(MotionEvent.AXIS_RX),
                gerakan.getAxisValue(MotionEvent.AXIS_RY)
        );
        if (Math.abs(sumbuUtama) >= BATAS_STIK) {
            return sumbuUtama;
        }

        InputDevice perangkat = gerakan.getDevice();
        if (perangkat == null) {
            return sumbuUtama;
        }

        InputDevice.MotionRange sumbuKiri = perangkat.getMotionRange(MotionEvent.AXIS_Z);
        InputDevice.MotionRange sumbuKanan = perangkat.getMotionRange(MotionEvent.AXIS_RZ);
        if (sumbuKiri == null && sumbuKanan == null) {
            return sumbuUtama;
        }

        float kiri = sumbuKiri == null
                ? posisiNetralSumbu(sumbuKanan, gerakan.getAxisValue(MotionEvent.AXIS_RZ))
                : ubahKeRentangNormal(
                gerakan.getAxisValue(MotionEvent.AXIS_Z),
                sumbuKiri
        );
        float kanan = sumbuKanan == null
                ? posisiNetralSumbu(sumbuKiri, gerakan.getAxisValue(MotionEvent.AXIS_Z))
                : ubahKeRentangNormal(
                gerakan.getAxisValue(MotionEvent.AXIS_RZ),
                sumbuKanan
        );
        float sumbuTambahan = sumbuPutaranTambahan(kiri, kanan);
        return Math.abs(sumbuTambahan) >= BATAS_STIK ? sumbuTambahan : sumbuUtama;
    }

    private static float ubahKeRentangNormal(
            float nilaiSumbu,
            InputDevice.MotionRange rentang
    ) {
        float lebarRentang = rentang.getMax() - rentang.getMin();
        if (lebarRentang <= 0) {
            return 0;
        }
        float nilaiNormal = (nilaiSumbu - rentang.getMin()) / lebarRentang;
        return Math.max(0, Math.min(1, nilaiNormal));
    }

    private static float posisiNetralSumbu(
            InputDevice.MotionRange rentang,
            float nilaiSumbu
    ) {
        if (rentang.getMin() < 0 && rentang.getMax() > 0) {
            float jarakDatar = Math.max(rentang.getFlat(), 0.05f);
            if (nilaiSumbu <= rentang.getMin() + jarakDatar) {
                return 0;
            }
            return 0.5f;
        }
        return 0;
    }

    private static String tulisNilai(int nilai) {
        return nilai > 0 ? "+" + nilai : Integer.toString(nilai);
    }

    private static String bersihkanAlamatHost(String raw) {
        String bersih = raw.trim()
                .replaceFirst("^(?i)(tcp|udp|http|https)://", "")
                .replaceAll("/+$", "");
        int posisiTitikDua = bersih.indexOf(':');
        if (posisiTitikDua > 0 && bersih.indexOf(':', posisiTitikDua + 1) == -1) {
            bersih = bersih.substring(0, posisiTitikDua).trim();
        }
        return bersih;
    }

    private static boolean adalahAlamatLokal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.endsWith(".local")
                || h.startsWith("10.")
                || h.startsWith("192.168.")
                || h.startsWith("127.")
                || h.startsWith("169.254.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] bagian = h.split("\\.");
            if (bagian.length >= 2) {
                try {
                    int oktetKedua = Integer.parseInt(bagian[1]);
                    return oktetKedua >= 16 && oktetKedua <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private Network cariJaringanLokal() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return null;
            }
            Network jaringanEthernet = null;
            for (Network net : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) {
                    continue;
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return net;
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    jaringanEthernet = net;
                }
            }
            return jaringanEthernet;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void aturPengikatanJaringan(Network jaringan) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.bindProcessToNetwork(jaringan);
            }
        } catch (RuntimeException ignored) {
            // Abaikan jika izin ACCESS_NETWORK_STATE belum ditambahkan
        }
    }

    private static String ambilPesanKesalahan(Throwable kesalahan) {
        if (kesalahan instanceof SecurityException) {
            return "Izin INTERNET ditolak. Cek AndroidManifest.xml.";
        }
        String pesan = kesalahan.getMessage();
        if (pesan == null || pesan.trim().isEmpty()) {
            return kesalahan.getClass().getSimpleName();
        }
        return pesan;
    }

    private void hubungkanKeRobot() {
        String alamat = bersihkanAlamatHost(alamatRobotInput.getText().toString());
        alamatRobotInput.setText(alamat);

        final int port;
        try {
            port = Integer.parseInt(portRobotInput.getText().toString().trim());
        } catch (NumberFormatException kesalahan) {
            tampilkanStatus("Port belum diisi dengan benar.");
            return;
        }
        if (alamat.isEmpty() || port < 1 || port > 65535) {
            tampilkanStatus("Periksa kembali alamat robot dan port.");
            return;
        }

        resetNilaiKendali();
        perbaruiTampilanNilai();

        final boolean gunakanUdp = memakaiUdp;
        final String tujuan = "tcp://" + alamat + ":" + port;
        final int percobaan = ++nomorPercobaanSambungan;
        sudahTerhubung = false;
        sedangMenghubungkan = true;
        tombolConnect.setText("Batalkan");
        aturFormSambungan();
        tampilkanStatus("Menyambungkan ke " + alamat + ":" + port + " (" + (gunakanUdp ? "UDP" : "ZMQ") + ")...");

        jalurKomunikasi.execute(() -> {
            if (percobaan != nomorPercobaanSambungan) {
                return;
            }
            tutupJalurKomunikasi();
            try {
                Network jaringanLokal = adalahAlamatLokal(alamat) ? cariJaringanLokal() : null;
                aturPengikatanJaringan(jaringanLokal);

                koneksiUdp = gunakanUdp;
                if (gunakanUdp) {
                    alamatUdp = jaringanLokal != null
                            ? jaringanLokal.getByName(alamat)
                            : InetAddress.getByName(alamat);
                    portUdp = port;
                    soketUdp = new DatagramSocket();
                    soketUdp.setSoTimeout(TIMEOUT_ZMQ_MS);
                    if (jaringanLokal != null) {
                        jaringanLokal.bindSocket(soketUdp);
                    }

                    // Kirim paket awal 0,0,0,0 untuk tes kirim UDP
                    byte[] isiAwal = "0,0,0,0".getBytes(StandardCharsets.UTF_8);
                    soketUdp.send(new DatagramPacket(isiAwal, isiAwal.length, alamatUdp, portUdp));
                } else {
                    jalurRobot = ZMQ.context(1);
                    pengirimPerintah = jalurRobot.socket(ZMQ.REQ);
                    pengirimPerintah.setLinger(0);
                    pengirimPerintah.setReconnectIVL(200);
                    pengirimPerintah.setReconnectIVLMax(1000);
                    pengirimPerintah.setSendTimeOut(TIMEOUT_HANDSHAKE_ZMQ_MS);
                    pengirimPerintah.setReceiveTimeOut(TIMEOUT_HANDSHAKE_ZMQ_MS);
                    pengirimPerintah.connect(tujuan);

                    // Handshake awal: pastikan server wifi_node di Mini PC merespons
                    if (!pengirimPerintah.send("0,0,0,0")) {
                        throw new IOException("Gagal mengirim paket awal ke robot.");
                    }
                    String balasanAwal = pengirimPerintah.recvStr();
                    if (balasanAwal == null) {
                        throw new IOException("Robot tidak merespons (cek IP, port, dan wifi_node).");
                    }

                    pengirimPerintah.setSendTimeOut(TIMEOUT_ZMQ_MS);
                    pengirimPerintah.setReceiveTimeOut(TIMEOUT_ZMQ_MS);
                }

                if (percobaan != nomorPercobaanSambungan) {
                    tutupJalurKomunikasi();
                    return;
                }

                antreanLayar.post(() -> {
                    if (percobaan == nomorPercobaanSambungan) {
                        sedangMenghubungkan = false;
                        sudahTerhubung = true;
                        tombolConnect.setText("Putuskan");
                        aturFormSambungan();
                        tampilkanStatus(gunakanUdp
                                ? "UDP aktif ke " + alamat + ":" + port
                                : "Terhubung (ZMQ) ke " + alamat + ":" + port);
                        kirimPerintahRobot();
                    }
                });
            } catch (IOException | RuntimeException kesalahan) {
                if (percobaan == nomorPercobaanSambungan) {
                    tutupJalurKomunikasi();
                    sudahTerhubung = false;
                    final String pesanError = ambilPesanKesalahan(kesalahan);
                    antreanLayar.post(() -> {
                        if (percobaan == nomorPercobaanSambungan) {
                            sedangMenghubungkan = false;
                            sudahTerhubung = false;
                            tombolConnect.setText("Hubungkan");
                            aturFormSambungan();
                            tampilkanStatus("Gagal konek: " + pesanError);
                        }
                    });
                }
            }
        });
    }

    private void putuskanDariRobot() {
        boolean sambunganSedangDicoba = sedangMenghubungkan;
        ++nomorPercobaanSambungan;
        sedangMenghubungkan = false;
        sudahTerhubung = false;
        adaPerintahMenunggu.set(false);
        resetNilaiKendali();
        perbaruiTampilanNilai();

        tombolConnect.setText("Hubungkan");
        aturFormSambungan();
        tampilkanStatus(sambunganSedangDicoba ? "Sambungan dibatalkan." : "Koneksi terputus.");
        jalurKomunikasi.execute(this::tutupJalurKomunikasi);
    }

    private void resetNilaiKendali() {
        majuMundur = 0;
        kiriKanan = 0;
        putaran = 0;
        tombolPutarKiriDitekan = false;
        tombolPutarKananDitekan = false;
        if (stikGerak != null) {
            stikGerak.setPosition(0, 0);
        }
        if (stikPutaran != null) {
            stikPutaran.setPosition(0, 0);
        }
    }

    private void berhenti() {
        resetNilaiKendali();
        perbaruiTampilanNilai();
        kirimPerintahRobot();
    }

    private void aturFormSambungan() {
        boolean bisaMengubahAlamat = !sudahTerhubung && !sedangMenghubungkan;
        alamatRobotInput.setEnabled(bisaMengubahAlamat);
        portRobotInput.setEnabled(bisaMengubahAlamat);
        tombolProtokol.setEnabled(bisaMengubahAlamat);
    }

    private void kirimPerintahRobot() {
        perbaruiTampilanNilai();
        if (!sudahTerhubung) {
            return;
        }

        adaPerintahMenunggu.set(true);
        if (!pengirimanSedangBerjalan.compareAndSet(false, true)) {
            return;
        }

        final int percobaan = nomorPercobaanSambungan;
        jalurKomunikasi.execute(() -> {
            boolean pemilikPengiriman = true;
            try {
                while (sudahTerhubung && percobaan == nomorPercobaanSambungan) {
                    adaPerintahMenunggu.set(false);
                    String isiPerintah = majuMundur + "," + kiriKanan + ","
                            + putaran + "," + penjepitBola;
                    if (koneksiUdp) {
                        if (soketUdp == null || alamatUdp == null) {
                            break;
                        }
                        byte[] isi = isiPerintah.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket paket = new DatagramPacket(
                                isi,
                                isi.length,
                                alamatUdp,
                                portUdp
                        );
                        soketUdp.send(paket);
                    } else {
                        if (pengirimPerintah == null) {
                            break;
                        }
                        boolean terkirim = pengirimPerintah.send(isiPerintah);
                        if (!terkirim) {
                            throw new IllegalStateException("Permintaan tidak terkirim.");
                        }

                        String balasan = pengirimPerintah.recvStr();
                        if (balasan == null) {
                            throw new IllegalStateException("Tidak ada balasan dari robot.");
                        }
                    }

                    if (adaPerintahMenunggu.getAndSet(false)) {
                        continue;
                    }

                    pengirimanSedangBerjalan.set(false);
                    pemilikPengiriman = false;
                    if (!adaPerintahMenunggu.get()
                            || !pengirimanSedangBerjalan.compareAndSet(false, true)) {
                        return;
                    }
                    pemilikPengiriman = true;
                }
            } catch (IOException | RuntimeException kesalahan) {
                adaPerintahMenunggu.set(false);
                if (percobaan == nomorPercobaanSambungan) {
                    tutupJalurKomunikasi();
                    sudahTerhubung = false;
                    final String pesanError = ambilPesanKesalahan(kesalahan);
                    antreanLayar.post(() -> {
                        if (percobaan == nomorPercobaanSambungan) {
                            sedangMenghubungkan = false;
                            tombolConnect.setText("Hubungkan");
                            aturFormSambungan();
                            tampilkanStatus("Komunikasi terputus: " + pesanError);
                        }
                    });
                }
            } finally {
                if (pemilikPengiriman) {
                    pengirimanSedangBerjalan.set(false);
                }
            }
        });
    }

    private void mulaiPenerimaKamera() {
        if (!kameraDiaktifkan) {
            return;
        }
        hentikanPenerimaKamera();
        final int sesi = ++sesiKamera;
        aturPlaceholderKamera("menunggu camera", View.VISIBLE);
        kameraFisik.setImageDrawable(null);
        kameraVirtual.setImageDrawable(null);

        jalurKamera.execute(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.setReceiveBufferSize(1_048_576);
                socket.bind(new InetSocketAddress(PORT_KAMERA_UDP));
                if (sesi != sesiKamera) {
                    return;
                }
                penerimaKameraUdp = socket;

                CameraFrameAssembler penyusunFrame = new CameraFrameAssembler();
                byte[] buffer = new byte[BATAS_UDP_BYTES];
                while (sesi == sesiKamera) {
                    DatagramPacket paket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(paket);
                    byte[] dataFrame = penyusunFrame.accept(
                            paket.getData(),
                            paket.getLength()
                    );
                    if (dataFrame == null) {
                        continue;
                    }

                    Bitmap frame = decodeFrameKamera(dataFrame);
                    if (frame != null) {
                        antrekanFrameTerbaru(frame, sesi);
                    }
                }
            } catch (IOException kesalahan) {
                if (sesi == sesiKamera) {
                    antreanLayar.post(() -> {
                        if (sesi == sesiKamera) {
                            aturPlaceholderKamera("KAMERA UDP ERROR", View.VISIBLE);
                        }
                    });
                }
            } finally {
                if (socket != null) {
                    socket.close();
                }
                if (penerimaKameraUdp == socket) {
                    penerimaKameraUdp = null;
                }
            }
        });
    }

    private void hentikanPenerimaKamera() {
        ++sesiKamera;
        DatagramSocket socket = penerimaKameraUdp;
        penerimaKameraUdp = null;
        if (socket != null) {
            socket.close();
        }
        Bitmap frame = frameMenunggu.getAndSet(null);
        if (frame != null) {
            frame.recycle();
        }
    }

    private void aturPlaceholderKamera(String teks, int visibilitas) {
        placeholderKameraFisik.setText(teks);
        placeholderKameraVirtual.setText(teks);
        placeholderKameraFisik.setVisibility(visibilitas);
        placeholderKameraVirtual.setVisibility(visibilitas);
    }

    private void antrekanFrameTerbaru(Bitmap frame, int sesi) {
        if (sesi != sesiKamera) {
            frame.recycle();
            return;
        }
        Bitmap frameLama = frameMenunggu.getAndSet(frame);
        if (frameLama != null) {
            frameLama.recycle();
        }
        if (renderFrameTerjadwal.compareAndSet(false, true)) {
            antreanLayar.post(this::renderFrameKamera);
        }
    }

    private void renderFrameKamera() {
        Bitmap frame = frameMenunggu.getAndSet(null);
        if (frame != null) {
            ImageView preview = kameraDiaktifkan
                    && halamanDashboard.getVisibility() == View.VISIBLE
                    ? kameraFisik
                    : kameraDiaktifkan && halamanGamepad.getVisibility() == View.VISIBLE
                    ? kameraVirtual : null;
            if (preview == null || isFinishing()) {
                frame.recycle();
            } else {
                preview.setImageBitmap(frame);
                if (preview == kameraFisik) {
                    placeholderKameraFisik.setVisibility(View.GONE);
                } else {
                    placeholderKameraVirtual.setVisibility(View.GONE);
                }
            }
        }
        renderFrameTerjadwal.set(false);
        if (frameMenunggu.get() != null
                && renderFrameTerjadwal.compareAndSet(false, true)) {
            antreanLayar.post(this::renderFrameKamera);
        }
    }

    private static Bitmap decodeFrameKamera(byte[] dataFrame) {
        BitmapFactory.Options batas = new BitmapFactory.Options();
        batas.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(dataFrame, 0, dataFrame.length, batas);
        if (batas.outWidth <= 0
                || batas.outHeight <= 0
                || batas.outWidth > 8192
                || batas.outHeight > 8192) {
            return null;
        }

        int ukuranSampel = 1;
        while (batas.outWidth / ukuranSampel > BATAS_DIMENSI_KAMERA
                || batas.outHeight / ukuranSampel > BATAS_DIMENSI_KAMERA) {
            ukuranSampel *= 2;
        }
        BitmapFactory.Options opsi = new BitmapFactory.Options();
        opsi.inSampleSize = ukuranSampel;
        return BitmapFactory.decodeByteArray(dataFrame, 0, dataFrame.length, opsi);
    }

    private void perbaruiTampilanNilai() {
        if (nilaiX == null) {
            return;
        }
        String nilaiXText = tulisNilai(kiriKanan);
        String nilaiYText = tulisNilai(majuMundur);
        String nilaiWzText = tulisNilai(putaran);
        String nilaiGripText = Integer.toString(penjepitBola);

        nilaiX.setText(nilaiXText);
        nilaiY.setText(nilaiYText);
        nilaiWz.setText(nilaiWzText);
        nilaiGrip.setText(nilaiGripText);
        nilaiXGamepad.setText(nilaiXText);
        nilaiYGamepad.setText(nilaiYText);
        nilaiWzGamepad.setText(nilaiWzText);
        nilaiGripGamepad.setText(nilaiGripText);
        tombolJepitBola.setText(penjepitBola == 1 ? "LEPAS" : "AMBIL");
    }

    private void tampilkanStatus(String pesan) {
        teksStatus.setText(pesan);
        teksStatus.setTextColor(sudahTerhubung ? 0xFF16803C : 0xFFE53935);
        statusGamepad.setText(sudahTerhubung ? "Terhubung" : pesan);
        statusGamepadVirtual.setText(sudahTerhubung ? "Terhubung" : pesan);
    }

    private void tutupJalurKomunikasi() {
        koneksiUdp = false;
        if (soketUdp != null) {
            try {
                soketUdp.close();
            } catch (Exception ignored) {
            }
            soketUdp = null;
        }
        alamatUdp = null;
        if (pengirimPerintah != null) {
            try {
                pengirimPerintah.close();
            } catch (Exception ignored) {
            }
            pengirimPerintah = null;
        }
        if (jalurRobot != null) {
            try {
                jalurRobot.close();
            } catch (Exception ignored) {
            }
            jalurRobot = null;
        }
        aturPengikatanJaringan(null);
    }

    @Override
    protected void onDestroy() {
        antreanLayar.removeCallbacks(pengirimBerkala);
        hentikanPenerimaKamera();
        jalurKamera.shutdownNow();
        ++nomorPercobaanSambungan;
        sudahTerhubung = false;
        resetNilaiKendali();
        jalurKomunikasi.execute(this::tutupJalurKomunikasi);
        jalurKomunikasi.shutdown();
        super.onDestroy();
    }
}
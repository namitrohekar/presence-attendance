package com.presence.attendance.scan.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.presence.attendance.PresenceApp;
import com.presence.attendance.R;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.location.LocationSource;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentScanBinding;
import com.presence.attendance.scan.ui.ScanViewModel.Stage;
import com.presence.attendance.session.ui.HandlerScheduler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Student: full-screen scanner. Location updates start the moment the screen opens, so a fix is
 * usually ready by the time the code is read. Camera and location both stop in onStop.
 */
public final class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final String[] PERMISSIONS = {
        Manifest.permission.CAMERA,
        // Fine and coarse together: Android may then offer "precise", which the geofence needs.
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private ScanViewModel viewModel;
    private LocationSource location;
    @Nullable private FragmentScanBinding binding;
    @Nullable private BarcodeScanner scanner;
    @Nullable private ImageAnalysis analysis;
    @Nullable private Camera camera;
    @Nullable private Runnable stopLocation;
    private boolean torchOn;

    private final ActivityResultLauncher<String[]> requestPermissions =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(), granted -> begin());

    public ScanFragment() {
        super(R.layout.fragment_scan);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentScanBinding bound = FragmentScanBinding.bind(view);
        binding = bound;
        WindowInsetsPadding.apply(bound.scanControls);
        WindowInsetsPadding.apply(bound.scanPermission);
        location =
                ((PresenceApp) requireContext().getApplicationContext()).getContainer().location();

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new ScanViewModel(
                                                c.auth(),
                                                c.sessions(),
                                                c.attendance(),
                                                new HandlerScheduler(),
                                                SystemClock::elapsedRealtimeNanos,
                                                System::currentTimeMillis)))
                        .get(ScanViewModel.class);

        scanner =
                BarcodeScanning.getClient(
                        new BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build());
        bound.scanClose.setOnClickListener(v -> close());
        bound.scanPermissionClose.setOnClickListener(v -> close());
        bound.scanTorch.setOnClickListener(v -> toggleTorch());
        bound.scanPermissionAllow.setOnClickListener(v -> requestPermissions.launch(PERMISSIONS));
        bound.scanPermissionSettings.setOnClickListener(v -> openSettings());
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onStart() {
        super.onStart();
        begin();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopLocationUpdates();
        stopCamera();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
        binding = null;
    }

    /** Starts the camera and location, or explains what is missing. */
    private void begin() {
        final FragmentScanBinding bound = binding;
        if (bound == null || !isAdded()) {
            return;
        }
        final boolean cameraGranted = granted(Manifest.permission.CAMERA);
        final boolean ready = cameraGranted && location.hasPrecisePermission();
        final boolean locationOn = location.isLocationEnabled();
        bound.scanPermission.setVisibility(ready && locationOn ? View.GONE : View.VISIBLE);
        if (!ready) {
            bound.scanPermissionTitle.setText(R.string.scan_permission_title);
            bound.scanPermissionBody.setText(R.string.scan_permission_body);
            bound.scanPermissionAllow.setVisibility(View.VISIBLE);
            return;
        }
        if (!locationOn) {
            bound.scanPermissionTitle.setText(R.string.scan_location_off_title);
            bound.scanPermissionBody.setText(R.string.scan_location_off_body);
            // Nothing to grant: only the system setting helps.
            bound.scanPermissionAllow.setVisibility(View.GONE);
            return;
        }
        if (stopLocation == null) {
            stopLocation = location.observe(viewModel::onFix);
        }
        startCamera(bound);
    }

    private void startCamera(@NonNull FragmentScanBinding bound) {
        final Executor mainExecutor = ContextCompat.getMainExecutor(requireContext());
        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(
                () -> {
                    if (binding == null || !isStarted()) {
                        return;
                    }
                    try {
                        bind(future.get(), bound, mainExecutor);
                    } catch (ExecutionException
                            | InterruptedException
                            | IllegalArgumentException e) {
                        Log.w(TAG, "The camera could not be started", e);
                        bound.scanStatus.setText(R.string.scan_camera_error);
                    }
                },
                mainExecutor);
    }

    private void bind(
            @NonNull ProcessCameraProvider provider,
            @NonNull FragmentScanBinding bound,
            @NonNull Executor mainExecutor) {
        final Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(bound.scanPreview.getSurfaceProvider());
        // Only the newest frame is kept, so a slow decode never builds a backlog.
        final ImageAnalysis frames =
                new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
        frames.setAnalyzer(mainExecutor, this::analyze);
        analysis = frames;
        provider.unbindAll();
        camera =
                provider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        frames);
        final boolean hasTorch = camera.getCameraInfo().hasFlashUnit();
        bound.scanTorch.setVisibility(hasTorch ? View.VISIBLE : View.GONE);
    }

    /** Hands one frame to ML Kit. The frame is closed when ML Kit has finished with it. */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(@NonNull ImageProxy frame) {
        final Image image = frame.getImage();
        final BarcodeScanner active = scanner;
        if (image == null || active == null) {
            frame.close();
            return;
        }
        active.process(InputImage.fromMediaImage(image, frame.getImageInfo().getRotationDegrees()))
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        viewModel.onQrText(barcode.getRawValue());
                    }
                })
                .addOnCompleteListener(done -> frame.close());
    }

    private void render(@NonNull ScanViewModel.State state) {
        final FragmentScanBinding bound = binding;
        if (bound == null) {
            return;
        }
        if (state.stage != Stage.SCANNING && analysis != null) {
            // The first valid code ends the scan: no more frames are decoded.
            analysis.clearAnalyzer();
        }
        if (state.stage == Stage.DONE) {
            openResult(state.rejection != null ? state.rejection.name() : "");
            return;
        }
        if (state.stage == Stage.CHECKING) {
            bound.scanStatus.setText(R.string.scan_checking);
        } else {
            bound.scanStatus.setText(
                    state.locationReady ? R.string.scan_location_ready : R.string.scan_locating);
        }
    }

    private void toggleTorch() {
        final FragmentScanBinding bound = binding;
        if (camera == null || bound == null) {
            return;
        }
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        bound.scanTorch.setContentDescription(
                getString(torchOn ? R.string.scan_torch_off : R.string.scan_torch_on));
    }

    private void stopCamera() {
        if (analysis != null) {
            analysis.clearAnalyzer();
            analysis = null;
        }
        camera = null;
        torchOn = false;
        // The preview and analysis are bound to the view's lifecycle and unbind with it.
    }

    private void stopLocationUpdates() {
        if (stopLocation != null) {
            stopLocation.run();
            stopLocation = null;
        }
    }

    private void openResult(@NonNull String rejection) {
        final NavController navController = NavHostFragment.findNavController(this);
        final NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.scanFragment) {
            navController.navigate(ScanFragmentDirections.actionScanToResult(rejection));
        }
    }

    /** The app's page in system settings, where a denied or approximate permission is changed. */
    private void openSettings() {
        final Intent intent =
                location.hasPrecisePermission()
                                && granted(Manifest.permission.CAMERA)
                                && !location.isLocationEnabled()
                        ? new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        : new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    private void close() {
        NavHostFragment.findNavController(this).popBackStack();
    }

    private boolean granted(@NonNull String permission) {
        return ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isStarted() {
        return getLifecycle()
                .getCurrentState()
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);
    }
}

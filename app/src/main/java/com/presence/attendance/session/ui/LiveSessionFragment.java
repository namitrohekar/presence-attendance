package com.presence.attendance.session.ui;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.databinding.FragmentLiveSessionBinding;
import com.presence.attendance.session.domain.Session;
import com.presence.attendance.session.domain.Tokens;
import com.presence.attendance.session.ui.LiveSessionViewModel.Phase;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Faculty: the rotating attendance QR, the countdown to the next code, and who has marked so far.
 * The screen is kept on at full brightness while the code is showing.
 */
public final class LiveSessionFragment extends Fragment {

    /** Fragment result the confirmation sheet sends. */
    static final String RESULT_END_CONFIRMED = "end_confirmed";

    private static final String TAG_SHEET = "live_sheet";
    private static final int LATEST_MARKS = 5;
    private static final int RING_MAX = 1000;
    private static final long QR_FADE_MS = 120;
    private static final float QR_FADE_FROM = 0.4f;

    private LiveSessionViewModel viewModel;
    @Nullable private String shownQrText;
    private long shownRotatesAt;
    @Nullable private ObjectAnimator ringAnimator;
    private boolean bright;

    public LiveSessionFragment() {
        super(R.layout.fragment_live_session);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentLiveSessionBinding binding = FragmentLiveSessionBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.liveContent);
        final LiveSessionFragmentArgs args = LiveSessionFragmentArgs.fromBundle(requireArguments());
        final String sessionId = args.getSessionId();
        final SecureRandom random = new SecureRandom();

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new LiveSessionViewModel(
                                                c.auth(),
                                                c.sessions(),
                                                c.attendance(),
                                                c.courses(),
                                                sessionId,
                                                new HandlerScheduler(),
                                                SystemClock::elapsedRealtime,
                                                System::currentTimeMillis,
                                                () -> Tokens.generate(random))))
                        .get(LiveSessionViewModel.class);

        // A new view starts blank, so the first state always redraws the code and the ring.
        shownQrText = null;
        shownRotatesAt = 0;
        binding.liveToolbar.setTitle(args.getCourseName());
        binding.liveToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(null);
        binding.liveRecycler.setAdapter(adapter);
        binding.liveSummary.setVisibility(View.GONE);
        binding.livePrimary.setText(R.string.live_end);
        binding.livePrimary.setOnClickListener(v -> confirmEnd());
        binding.liveMessageAction.setOnClickListener(v -> onMessageAction());
        getChildFragmentManager()
                .setFragmentResultListener(
                        RESULT_END_CONFIRMED,
                        getViewLifecycleOwner(),
                        (key, result) -> viewModel.end());

        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    @Override
    public void onStart() {
        super.onStart();
        viewModel.onVisible();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Nobody can see the code now: stop writing tokens and give the screen back.
        viewModel.onHidden();
        setBright(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ringAnimator != null) {
            ringAnimator.cancel();
            ringAnimator = null;
        }
    }

    private void render(
            @NonNull FragmentLiveSessionBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull LiveSessionViewModel.State state) {
        final boolean live = state.phase == Phase.LIVE;
        binding.liveProgress.setVisibility(visibleIf(state.phase == Phase.LOADING));
        binding.liveScroll.setVisibility(visibleIf(live));
        binding.liveFooter.setVisibility(visibleIf(live));
        binding.liveMessage.setVisibility(
                visibleIf(state.phase == Phase.ENDED || state.phase == Phase.ERROR));
        setBright(live && isStarted());

        if (state.phase == Phase.ENDED && state.session != null) {
            openSummary(state.session);
            return;
        }
        if (state.phase == Phase.ENDED) {
            binding.liveMessageTitle.setText(R.string.live_ended_title);
            binding.liveMessageBody.setText(R.string.live_ended_body);
            binding.liveMessageAction.setText(R.string.action_done);
        } else if (state.phase == Phase.ERROR) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.liveMessageTitle.setText(R.string.live_error_title);
            binding.liveMessageBody.setText(AppErrorMessages.of(cause));
            binding.liveMessageAction.setText(R.string.action_try_again);
        }
        if (!live) {
            return;
        }
        renderQr(binding, state);
        renderStatus(binding, state);
        final int present = state.presentCount();
        // Out of everyone enrolled, once the course list has loaded.
        binding.liveCount.setText(
                state.members.isEmpty()
                        ? getResources().getQuantityString(R.plurals.live_present, present, present)
                        : getString(
                                R.string.session_roster_summary, present, state.members.size()));
        adapter.submitList(latestMarks(state));
        binding.liveNone.setVisibility(visibleIf(state.records.isEmpty()));
        binding.livePrimary.setEnabled(!state.ending);
        binding.livePrimary.setText(state.ending ? R.string.end_ending : R.string.live_end);
    }

    /** Redraws the code only when it changes, with a short fade so the swap does not flash. */
    private void renderQr(
            @NonNull FragmentLiveSessionBinding binding,
            @NonNull LiveSessionViewModel.State state) {
        if (!Objects.equals(state.qrText, shownQrText)) {
            shownQrText = state.qrText;
            if (state.qrText == null) {
                binding.liveQr.setImageDrawable(null);
                binding.liveQr.setContentDescription(getString(R.string.live_qr_blank));
            } else {
                binding.liveQr.setImageDrawable(
                        QrBitmap.render(
                                getResources(),
                                state.qrText,
                                requireContext().getColor(R.color.qr_dark),
                                requireContext().getColor(R.color.qr_light)));
                binding.liveQr.setContentDescription(getString(R.string.live_qr_description));
                binding.liveQr.setAlpha(QR_FADE_FROM);
                binding.liveQr.animate().alpha(1f).setDuration(QR_FADE_MS).start();
            }
        }
        if (state.rotatesAtElapsedMs != shownRotatesAt) {
            shownRotatesAt = state.rotatesAtElapsedMs;
            animateRing(binding, state.rotatesAtElapsedMs);
        }
    }

    /** Empties the ring over the time left until the next code. */
    private void animateRing(@NonNull FragmentLiveSessionBinding binding, long rotatesAtElapsedMs) {
        if (ringAnimator != null) {
            ringAnimator.cancel();
        }
        final long remaining = rotatesAtElapsedMs - SystemClock.elapsedRealtime();
        if (rotatesAtElapsedMs == 0 || remaining <= 0) {
            binding.liveRing.setProgress(0);
            return;
        }
        final long rotationMs = Constants.ROTATION_S * DateUtils.SECOND_IN_MILLIS;
        final int from = (int) Math.min(RING_MAX, remaining * RING_MAX / rotationMs);
        ringAnimator = ObjectAnimator.ofInt(binding.liveRing, "progress", from, 0);
        ringAnimator.setDuration(remaining);
        ringAnimator.setInterpolator(new LinearInterpolator());
        ringAnimator.start();
    }

    private void renderStatus(
            @NonNull FragmentLiveSessionBinding binding,
            @NonNull LiveSessionViewModel.State state) {
        if (state.error != null) {
            binding.liveStatus.setText(AppErrorMessages.of(state.error));
        } else if (state.reconnecting) {
            binding.liveStatus.setText(R.string.live_reconnecting);
        } else if (state.qrText == null) {
            binding.liveStatus.setText(R.string.live_preparing);
        } else if (state.session != null) {
            binding.liveStatus.setText(
                    getString(
                            R.string.live_ends_at,
                            DateUtils.formatDateTime(
                                    requireContext(),
                                    state.session.getEndsAtMillis(),
                                    DateUtils.FORMAT_SHOW_TIME)));
        }
    }

    /** The most recent few marks: name, roll number and time, with how each was recorded. */
    @NonNull
    private List<Line> latestMarks(@NonNull LiveSessionViewModel.State state) {
        final List<Line> lines = new ArrayList<>();
        final int count = Math.min(LATEST_MARKS, state.records.size());
        for (int i = 0; i < count; i++) {
            final AttendanceRecord record = state.records.get(i);
            final Member member = state.members.get(record.getStudentId());
            final String time =
                    DateUtils.formatDateTime(
                            requireContext(),
                            record.getMarkedAtMillis(),
                            DateUtils.FORMAT_SHOW_TIME);
            final String detail =
                    member != null && !member.getRollNo().isEmpty()
                            ? getString(R.string.import_row_detail, member.getRollNo(), time)
                            : time;
            lines.add(
                    Line.row(
                                    record.getStudentId(),
                                    member != null
                                            ? member.getName()
                                            : getString(R.string.live_unknown_student),
                                    detail,
                                    badge(record.getStatus()),
                                    record.getStatus() == AttendanceRecord.Status.LATE_SYNC
                                            ? Line.Tone.WARNING
                                            : Line.Tone.NEUTRAL)
                            .withAvatar(member != null ? member.shownEmoji().glyph() : null));
        }
        return Line.grouped(lines);
    }

    @Nullable
    private String badge(@NonNull AttendanceRecord.Status status) {
        return switch (status) {
            case PRESENT -> null;
            case LATE_SYNC -> getString(R.string.live_late_sync);
            case MANUAL_PRESENT, MANUAL_ABSENT -> getString(R.string.live_manual);
            case REVOKED -> getString(R.string.status_revoked);
        };
    }

    private void confirmEnd() {
        if (getChildFragmentManager().findFragmentByTag(TAG_SHEET) == null) {
            new EndSessionSheet().show(getChildFragmentManager(), TAG_SHEET);
        }
    }

    /** Replaces this screen with the session's summary, where the marks are reviewed. */
    private void openSummary(@NonNull Session session) {
        final NavController navController = NavHostFragment.findNavController(this);
        final NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.liveSessionFragment) {
            navController.navigate(
                    LiveSessionFragmentDirections.actionLiveToSessionRoster(
                            session.getCourseId(),
                            session.getId(),
                            DateUtils.formatDateTime(
                                    requireContext(),
                                    session.getStartedAtMillis(),
                                    DateUtils.FORMAT_SHOW_DATE
                                            | DateUtils.FORMAT_SHOW_TIME
                                            | DateUtils.FORMAT_SHOW_WEEKDAY
                                            | DateUtils.FORMAT_ABBREV_ALL)));
        }
    }

    private void onMessageAction() {
        final LiveSessionViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.phase == Phase.ERROR) {
            viewModel.retry();
        } else {
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private boolean isStarted() {
        return getLifecycle()
                .getCurrentState()
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);
    }

    /** Full brightness and no screen timeout while the code is showing; restored otherwise. */
    private void setBright(boolean on) {
        if (bright == on) {
            return;
        }
        bright = on;
        final Window window = requireActivity().getWindow();
        final WindowManager.LayoutParams params = window.getAttributes();
        params.screenBrightness =
                on
                        ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                        : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        window.setAttributes(params);
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}

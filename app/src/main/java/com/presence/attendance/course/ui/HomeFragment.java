package com.presence.attendance.course.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.attendance.ui.SavedScansViewModel;
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.auth.ui.GoogleIdTokenRequester;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.databinding.FragmentHomeBinding;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.TimetableSlot;
import com.presence.attendance.department.ui.BatchSheet;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The home screen for every role. Faculty see the courses they teach, which one is live, and can
 * create one; the HOD also sees the department, its batches and the faculty list; students see the
 * courses they joined and can join one.
 */
public final class HomeFragment extends Fragment {

    /** Fragment result a sheet sends when the course list has changed. */
    public static final String RESULT_COURSES_CHANGED = "courses_changed";

    private static final String TAG_SHEET = "home_sheet";
    private static final String BATCH = "batch:";
    private static final String KEY_FACULTY = "faculty";
    private static final String KEY_ADD_BATCH = "addBatch";
    private static final String TODAY = "today";

    /** How early a teacher may start a class from "Today". */
    private static final int EARLY_START_MIN = 15;

    private HomeViewModel viewModel;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentHomeBinding binding = FragmentHomeBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.homeContent);
        // The content is laid out taller than the screen by however far the title can still
        // collapse, so the footer anchored to its bottom is lifted back into view.
        binding.homeAppBar.addOnOffsetChangedListener((bar, offset) ->
                binding.homeFooter.setTranslationY(-(bar.getTotalScrollRange() + offset)));

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new HomeViewModel(
                                                c.auth(),
                                                c.courses(),
                                                c.sessions(),
                                                c.batches(),
                                                System::currentTimeMillis,
                                                ZoneId.systemDefault())))
                        .get(HomeViewModel.class);

        final LineAdapter adapter = new LineAdapter(this::onRowClicked);
        binding.homeList.setAdapter(adapter);
        binding.homeToolbar.setOnMenuItemClickListener(item -> onMenuItem(binding, item));
        binding.homeMessageAction.setOnClickListener(v -> onMessageAction());
        binding.homeScan.setOnClickListener(v -> leave(HomeFragmentDirections.actionHomeToScan()));
        getChildFragmentManager()
                .setFragmentResultListener(
                        RESULT_COURSES_CHANGED,
                        getViewLifecycleOwner(),
                        (key, result) -> viewModel.refresh());

        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    @Override
    public void onStart() {
        super.onStart();
        // Coming back from a course or a session: a session may have started or ended.
        final HomeViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.status == HomeViewModel.Status.CONTENT) {
            viewModel.refresh();
        }
    }

    private void render(
            @NonNull FragmentHomeBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull HomeViewModel.State state) {
        if (state.status == HomeViewModel.Status.SIGNED_OUT) {
            GoogleIdTokenRequester.clearState(requireContext());
            leave(HomeFragmentDirections.actionHomeToSignIn());
            return;
        }
        if (state.status == HomeViewModel.Status.REGISTRATION_REQUIRED) {
            leave(HomeFragmentDirections.actionHomeToRegistration());
            return;
        }
        renderPrimaryAction(
                binding.homeToolbar.getMenu().findItem(R.id.action_primary), state.user);

        final boolean isFaculty = state.user != null && state.user.getRole() == Role.FACULTY;
        adapter.submitList(lines(state, isFaculty));
        // The student's one primary action. Faculty show the code instead of scanning it.
        binding.homeFooter.setVisibility(visibleIf(state.user != null && !isFaculty));
        if (state.user != null && !isFaculty) {
            showSavedScans(binding, state.user.getUid());
        }

        binding.homeProgress.setVisibility(visibleIf(state.status == HomeViewModel.Status.LOADING));
        binding.homeList.setVisibility(visibleIf(state.status == HomeViewModel.Status.CONTENT));
        final boolean empty = state.status == HomeViewModel.Status.EMPTY;
        final boolean error = state.status == HomeViewModel.Status.ERROR;
        binding.homeMessage.setVisibility(visibleIf(empty || error));
        if (empty) {
            binding.homeMessageTitle.setText(R.string.home_empty_title);
            binding.homeMessageBody.setText(
                    isFaculty ? R.string.home_empty_faculty : R.string.home_empty_student);
            binding.homeMessageAction.setText(
                    isFaculty ? R.string.home_create_course : R.string.home_join_course);
        } else if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.homeMessageTitle.setText(R.string.home_error_title);
            binding.homeMessageBody.setText(AppErrorMessages.of(cause));
            binding.homeMessageAction.setText(R.string.action_try_again);
        }
    }

    /**
     * For the HOD, the department first: each batch with its semester, the faculty list, and a way
     * to add a batch. Then one row per course. Faculty rows show the join code, a "Live" badge
     * while a session is running, and open the course; a student has already used the code.
     */
    @NonNull
    private List<Line> lines(@NonNull HomeViewModel.State state, boolean isFaculty) {
        final List<Line> lines = new ArrayList<>();
        addToday(lines, state);
        final boolean hod = state.user != null && state.user.isHod();
        if (hod) {
            lines.add(Line.header("department", getString(R.string.home_department)));
            for (Batch batch : state.batches) {
                lines.add(
                        Line.row(
                                        BATCH + batch.getId(),
                                        batch.displayName(),
                                        getString(
                                                R.string.home_batch_detail,
                                                batch.getSemester(),
                                                batch.semesterCount(),
                                                batch.getIntake()))
                                .navigates());
            }
            lines.add(
                    Line.row(
                                    KEY_FACULTY,
                                    getString(R.string.home_faculty),
                                    getString(R.string.home_faculty_detail))
                            .navigates());
            lines.add(
                    Line.row(
                            KEY_ADD_BATCH,
                            getString(R.string.home_add_batch),
                            getString(R.string.home_add_batch_detail)));
        }
        if ((hod || !state.today.isEmpty()) && !state.courses.isEmpty()) {
            lines.add(Line.header("courses", getString(R.string.home_your_courses)));
        }
        for (Course course : state.courses) {
            final boolean live = state.liveSessionByCourse.containsKey(course.getId());
            final String detail;
            if (course.isSubject()) {
                detail = subjectDetail(course, isFaculty);
            } else {
                detail =
                        isFaculty
                                ? getString(
                                        R.string.course_row_faculty,
                                        course.getCode(),
                                        course.getJoinCode())
                                : course.getCode();
            }
            final Line row =
                    Line.row(
                            course.getId(),
                            course.getName(),
                            detail,
                            live ? getString(R.string.home_live) : null,
                            Line.Tone.SUCCESS);
            lines.add(row.navigates());
        }
        return Line.grouped(lines);
    }

    /**
     * Today's classes from the timetable, the one on now marked: time, lab group and room. Tapping
     * one opens its course, where the teacher starts the session.
     */
    private void addToday(@NonNull List<Line> lines, @NonNull HomeViewModel.State state) {
        if (state.today.isEmpty()) {
            return;
        }
        lines.add(Line.header("today", getString(R.string.home_today)));
        final int now = viewModel.minutesNow();
        for (int i = 0; i < state.today.size(); i++) {
            final TimetableSlot slot = state.today.get(i);
            final Course course = state.courseOf(slot);
            final List<String> parts = new ArrayList<>();
            parts.add(getString(R.string.today_time, slot.getStart(), slot.getEnd()));
            if (course != null && course.getLabGroup() > 0) {
                parts.add(getString(R.string.roster_group, course.getLabGroup()));
            } else if (slot.getType() == TimetableSlot.Type.TUTORIAL) {
                parts.add(getString(R.string.today_tutorial));
            }
            if (!slot.getRoom().isEmpty()) {
                parts.add(slot.getRoom());
            }
            final boolean on = now >= slot.startMinutes() && now < slot.endMinutes();
            lines.add(
                    Line.row(
                                    TODAY + i + ":" + slot.getCourseId(),
                                    course != null ? course.getName() : slot.getCourseId(),
                                    TextUtils.join(" · ", parts),
                                    on ? getString(R.string.today_now) : null,
                                    Line.Tone.SUCCESS)
                            .navigates());
        }
    }

    /** "R5MC5013T · MCA26 · Lab group 1": a subject has no join code to show. */
    @NonNull
    private String subjectDetail(@NonNull Course course, boolean isFaculty) {
        final List<String> parts = new ArrayList<>();
        parts.add(course.getCode());
        if (isFaculty && course.getBatchId() != null) {
            parts.add(course.getBatchId());
        }
        if (course.getLabGroup() > 0) {
            parts.add(getString(R.string.roster_group, course.getLabGroup()));
        }
        return TextUtils.join(" · ", parts);
    }

    /**
     * A link to the student's saved scans, shown while there are any. Set up once the student is
     * known; watching the list also sends any scan that is still waiting.
     */
    private void showSavedScans(@NonNull FragmentHomeBinding binding, @NonNull String studentId) {
        final SavedScansViewModel saved =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new SavedScansViewModel(c.pendingMarks(), c::requestSync)))
                        .get(SavedScansViewModel.class);
        saved.setStudent(studentId);
        if (saved.getMarks().hasObservers()) {
            return;
        }
        binding.homeSaved.setOnClickListener(
                v -> leave(HomeFragmentDirections.actionHomeToSaved(studentId)));
        saved.getMarks()
                .observe(
                        getViewLifecycleOwner(),
                        marks -> {
                            binding.homeSaved.setVisibility(visibleIf(!marks.isEmpty()));
                            binding.homeSaved.setText(
                                    getResources()
                                            .getQuantityString(
                                                    R.plurals.home_saved_scans,
                                                    marks.size(),
                                                    marks.size()));
                        });
    }

    private void onRowClicked(@NonNull String key) {
        final HomeViewModel.State state = viewModel.getState().getValue();
        if (state == null || state.user == null) {
            return;
        }
        if (key.startsWith(BATCH)) {
            final String batchId = key.substring(BATCH.length());
            for (Batch batch : state.batches) {
                if (batch.getId().equals(batchId)) {
                    leave(HomeFragmentDirections.actionHomeToRoster(batchId, batch.displayName()));
                    return;
                }
            }
        } else if (KEY_FACULTY.equals(key)) {
            leave(HomeFragmentDirections.actionHomeToFaculty());
        } else if (KEY_ADD_BATCH.equals(key)) {
            if (getChildFragmentManager().findFragmentByTag(TAG_SHEET) == null) {
                BatchSheet.create(RESULT_COURSES_CHANGED)
                        .show(getChildFragmentManager(), TAG_SHEET);
            }
        } else if (key.startsWith(TODAY)) {
            openToday(state, Integer.parseInt(key.substring(TODAY.length(), key.indexOf(':'))));
        } else {
            openCourse(state, key);
        }
    }

    /**
     * A class in "Today". For its teacher, from a few minutes before it begins until it ends, the
     * course opens with the start sheet set to its room and the minutes it has left.
     */
    private void openToday(@NonNull HomeViewModel.State state, int index) {
        if (index < 0 || index >= state.today.size() || state.user == null) {
            return;
        }
        final TimetableSlot slot = state.today.get(index);
        final Course course = state.courseOf(slot);
        final int now = viewModel.minutesNow();
        final boolean teaching =
                state.user.getRole() == Role.FACULTY
                        && course != null
                        && course.getTeacherId().equals(state.user.getUid());
        if (!teaching || now < slot.startMinutes() - EARLY_START_MIN || now >= slot.endMinutes()) {
            openCourse(state, slot.getCourseId());
            return;
        }
        final int minutes = slot.endMinutes() - Math.max(now, slot.startMinutes());
        final HomeFragmentDirections.ActionHomeToCourse directions =
                HomeFragmentDirections.actionHomeToCourse(
                        course.getId(),
                        course.getName(),
                        course.getJoinCode(),
                        course.getThreshold());
        directions.setStartRoom(slot.getRoom());
        directions.setStartMinutes(
                Math.max(Constants.SESSION_MIN_MIN, Math.min(Constants.SESSION_MAX_MIN, minutes)));
        leave(directions);
    }

    private void openCourse(@NonNull HomeViewModel.State state, @NonNull String courseId) {
        if (state.user == null) {
            return;
        }
        for (Course course : state.courses) {
            if (!course.getId().equals(courseId)) {
                continue;
            }
            // Faculty manage the course; a student sees their own attendance in it.
            leave(
                    state.user.getRole() == Role.FACULTY
                            ? HomeFragmentDirections.actionHomeToCourse(
                                    course.getId(),
                                    course.getName(),
                                    course.getJoinCode(),
                                    course.getThreshold())
                            : HomeFragmentDirections.actionHomeToHistory(
                                    course.getId(), course.getName(), course.getThreshold()));
            return;
        }
    }

    /** "+" for faculty, "Join" for students, hidden until the role is known. */
    private static void renderPrimaryAction(@NonNull MenuItem action, @Nullable User user) {
        action.setVisible(user != null);
        if (user == null) {
            return;
        }
        if (user.getRole() == Role.FACULTY) {
            action.setIcon(R.drawable.ic_add);
            action.setTitle(R.string.home_create_course);
        } else {
            action.setIcon(null);
            action.setTitle(R.string.home_join);
        }
    }

    private boolean onMenuItem(@NonNull FragmentHomeBinding binding, @NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_primary) {
            openSheet();
            return true;
        }
        if (item.getItemId() == R.id.action_account) {
            final View anchor = binding.homeToolbar.findViewById(R.id.action_account);
            showAccountMenu(anchor != null ? anchor : binding.homeToolbar);
            return true;
        }
        return false;
    }

    private void onMessageAction() {
        final HomeViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.status == HomeViewModel.Status.ERROR) {
            viewModel.refresh();
        } else {
            openSheet();
        }
    }

    private void openSheet() {
        final HomeViewModel.State state = viewModel.getState().getValue();
        final boolean alreadyOpen = getChildFragmentManager().findFragmentByTag(TAG_SHEET) != null;
        if (state == null || state.user == null || alreadyOpen) {
            return;
        }
        if (state.user.getRole() == Role.FACULTY) {
            new CreateCourseSheet().show(getChildFragmentManager(), TAG_SHEET);
        } else {
            new JoinCourseSheet().show(getChildFragmentManager(), TAG_SHEET);
        }
    }

    private void showAccountMenu(@NonNull View anchor) {
        final HomeViewModel.State state = viewModel.getState().getValue();
        final User user = state != null ? state.user : null;
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.menu_account);
        final MenuItem email = menu.getMenu().findItem(R.id.account_email);
        if (user != null) {
            email.setTitle(user.getEmail());
        } else {
            email.setVisible(false);
        }
        // The alerts are a faculty tool; the rules refuse them to students anyway.
        final boolean isFaculty = user != null && user.getRole() == Role.FACULTY;
        menu.getMenu().findItem(R.id.action_alerts).setVisible(isFaculty);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_sign_out) {
                viewModel.signOut();
            } else if (item.getItemId() == R.id.action_alerts) {
                leave(HomeFragmentDirections.actionHomeToAlerts());
            } else {
                return false;
            }
            return true;
        });
        menu.show();
    }

    private void leave(@NonNull NavDirections directions) {
        final NavController navController = NavHostFragment.findNavController(this);
        final NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.homeFragment) {
            navController.navigate(directions);
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}

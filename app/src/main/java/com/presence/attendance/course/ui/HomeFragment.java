package com.presence.attendance.course.ui;

import android.os.Bundle;
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
import com.presence.attendance.auth.domain.Role;
import com.presence.attendance.auth.domain.User;
import com.presence.attendance.auth.ui.GoogleIdTokenRequester;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.databinding.FragmentHomeBinding;
import java.util.ArrayList;
import java.util.List;

/**
 * The home screen for both roles. Faculty see the courses they teach, which one is live, and can
 * create one; students see the courses they joined and can join one.
 */
public final class HomeFragment extends Fragment {

    /** Fragment result a sheet sends when the course list has changed. */
    static final String RESULT_COURSES_CHANGED = "courses_changed";

    private static final String TAG_SHEET = "home_sheet";

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
        // collapse, so the button anchored to its bottom is lifted back into view.
        binding.homeAppBar.addOnOffsetChangedListener((bar, offset) ->
                binding.homeScan.setTranslationY(-(bar.getTotalScrollRange() + offset)));

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new HomeViewModel(
                                                c.auth(),
                                                c.courses(),
                                                c.sessions(),
                                                System::currentTimeMillis)))
                        .get(HomeViewModel.class);

        final LineAdapter adapter = new LineAdapter(this::openCourse);
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
        binding.homeScan.setVisibility(visibleIf(state.user != null && !isFaculty));

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
     * One row per course. Faculty rows show the join code, a "Live" badge while a session is
     * running, and open the course; a student has already used the code.
     */
    @NonNull
    private List<Line> lines(@NonNull HomeViewModel.State state, boolean isFaculty) {
        final List<Line> lines = new ArrayList<>();
        for (Course course : state.courses) {
            final boolean live = state.liveSessionByCourse.containsKey(course.getId());
            final String detail =
                    isFaculty
                            ? getString(
                                    R.string.course_row_faculty,
                                    course.getCode(),
                                    course.getJoinCode())
                            : course.getCode();
            final Line row =
                    Line.row(
                            course.getId(),
                            course.getName(),
                            detail,
                            live ? getString(R.string.home_live) : null,
                            Line.Tone.SUCCESS);
            lines.add(isFaculty ? row.navigates() : row);
        }
        return Line.grouped(lines);
    }

    private void openCourse(@NonNull String courseId) {
        final HomeViewModel.State state = viewModel.getState().getValue();
        if (state == null || state.user == null || state.user.getRole() != Role.FACULTY) {
            return;
        }
        for (Course course : state.courses) {
            if (course.getId().equals(courseId)) {
                leave(
                        HomeFragmentDirections.actionHomeToCourse(
                                course.getId(), course.getName(), course.getJoinCode()));
                return;
            }
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
        // The roster and the alerts are faculty tools; the rules refuse them to students anyway.
        final boolean isFaculty = user != null && user.getRole() == Role.FACULTY;
        menu.getMenu().findItem(R.id.action_roster).setVisible(isFaculty);
        menu.getMenu().findItem(R.id.action_alerts).setVisible(isFaculty);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_sign_out) {
                viewModel.signOut();
            } else if (item.getItemId() == R.id.action_roster) {
                leave(HomeFragmentDirections.actionHomeToRoster());
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

package com.presence.attendance.course.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.presence.attendance.R;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.databinding.ItemCourseBinding;

/** Rows of the home screen's course list. */
final class CourseAdapter extends ListAdapter<Course, CourseAdapter.Holder> {

    private static final DiffUtil.ItemCallback<Course> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull Course a, @NonNull Course b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Course a, @NonNull Course b) {
                    return a.getName().equals(b.getName())
                            && a.getCode().equals(b.getCode())
                            && a.getJoinCode().equals(b.getJoinCode());
                }
            };

    // Only the teacher sees the join code; a student has already used it.
    private boolean showJoinCode;

    CourseAdapter() {
        super(DIFF);
    }

    void setShowJoinCode(boolean showJoinCode) {
        this.showJoinCode = showJoinCode;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new Holder(ItemCourseBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final Course course = getItem(position);
        holder.binding.courseName.setText(course.getName());
        holder.binding.courseDetail.setText(
                showJoinCode
                        ? holder.itemView
                                .getContext()
                                .getString(
                                        R.string.course_row_faculty,
                                        course.getCode(),
                                        course.getJoinCode())
                        : course.getCode());
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemCourseBinding binding;

        Holder(@NonNull ItemCourseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

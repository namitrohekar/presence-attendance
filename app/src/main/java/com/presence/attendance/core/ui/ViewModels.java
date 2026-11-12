package com.presence.attendance.core.ui;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import com.presence.attendance.PresenceApp;
import com.presence.attendance.core.AppContainer;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds ViewModels from the {@link AppContainer}, so ViewModels take plain constructor arguments.
 */
public final class ViewModels {

    private ViewModels() {}

    @NonNull
    public static ViewModelProvider.Factory factory(
            @NonNull Function<AppContainer, ? extends ViewModel> creator) {
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(
                    @NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
                final Application application =
                        extras.get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY);
                final AppContainer container =
                        ((PresenceApp) Objects.requireNonNull(application)).getContainer();
                return Objects.requireNonNull(modelClass.cast(creator.apply(container)));
            }
        };
    }
}

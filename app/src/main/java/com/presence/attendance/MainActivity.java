package com.presence.attendance;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.databinding.ActivityMainBinding;
import java.util.Objects;

/** The single activity; every screen is a fragment in the navigation graph. */
public final class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(ActivityMainBinding.inflate(getLayoutInflater()).getRoot());

        final NavHostFragment host =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        final NavController navController = Objects.requireNonNull(host).getNavController();
        final NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph);
        // A returning user goes straight to their courses; sign-in is never shown and popped.
        graph.setStartDestination(isSignedIn() ? R.id.homeFragment : R.id.signInFragment);
        navController.setGraph(graph);
    }

    private boolean isSignedIn() {
        // Firebase cannot start in a build without google-services.json.
        return BuildConfig.FIREBASE_CONFIGURED
                && ((PresenceApp) getApplication()).getContainer().auth().isSignedIn();
    }
}

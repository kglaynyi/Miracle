package com.miracle.kglaynyi;

import static com.miracle.kglaynyi.utils.UpdateUtils.checkForUpdates;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.miracle.kglaynyi.database.AppDatabase;
import com.miracle.kglaynyi.fragments.HomeFragment;
import com.miracle.kglaynyi.fragments.LibraryFragment;
import com.miracle.kglaynyi.fragments.SearchFragment;
import com.miracle.kglaynyi.fragments.SettingsFragment;
import com.miracle.kglaynyi.utils.IndexUtils;
import com.miracle.kglaynyi.utils.RefreshWorker;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    BottomNavigationView bottomNavigationView;
    HomeFragment homeFragment = new HomeFragment();
    SearchFragment searchFragment = new SearchFragment();
    LibraryFragment libraryFragment = new LibraryFragment();
    SettingsFragment settingsFragment = new SettingsFragment();

    public static Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(1);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        setUpBottomNavigationView();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, homeFragment)
                    .commit();

            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> IndexUtils.refreshEnabledIndexesOnStartup(getApplicationContext()),
                    750L);
        }

        checkForUpdates(this);

        Room.databaseBuilder(getApplicationContext(),
                AppDatabase.class, "MyToDos").build();

        SharedPreferences sharedPreferences =
                getSharedPreferences("Settings", Context.MODE_PRIVATE);
        boolean savedREF = sharedPreferences.getBoolean("REFRESH_SETTING", false);
        int savedTime = sharedPreferences.getInt("REFRESH_TIME", 0);
        if (savedREF) scheduleWork(savedTime, 0);
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStackImmediate();
        } else {
            super.onBackPressed();
        }
    }

    private void setUpBottomNavigationView() {
        if (bottomNavigationView == null) {
            Log.w(TAG, "Bottom navigation view is missing from layout");
            return;
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.homeFragment) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, homeFragment)
                        .commit();
                return true;
            }
            if (item.getItemId() == R.id.searchFragment) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, searchFragment)
                        .commit();
                return true;
            }
            if (item.getItemId() == R.id.libraryFragment) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, libraryFragment)
                        .commit();
                return true;
            }
            if (item.getItemId() == R.id.settingsFragment) {
                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .replace(R.id.container, settingsFragment)
                        .commit();
                return true;
            }
            return false;
        });
    }

    private void scheduleWork(int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        long nowMillis = calendar.getTimeInMillis();

        if (calendar.get(Calendar.HOUR_OF_DAY) > hour ||
                (calendar.get(Calendar.HOUR_OF_DAY) == hour
                        && calendar.get(Calendar.MINUTE) + 1 >= minute)) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long diff = calendar.getTimeInMillis() - nowMillis;

        WorkManager workManager = WorkManager.getInstance(context);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        workManager.cancelAllWork();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RefreshWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(diff, TimeUnit.MILLISECONDS)
                .build();
        workManager.enqueue(request);
    }
}

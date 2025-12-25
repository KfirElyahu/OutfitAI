package com.kfir.outfitai;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.lang.reflect.Field;

public class GenerateActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.generate_screen_host);

        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.nav_view);

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        reduceDragSensitivity(viewPager);

        viewPager.setUserInputEnabled(true);

        viewPager.setCurrentItem(1, false);
        bottomNavigationView.setSelectedItemId(R.id.navigation_generate);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.navigation_settings) {
                    viewPager.setCurrentItem(0);
                    return true;
                } else if (itemId == R.id.navigation_generate) {
                    viewPager.setCurrentItem(1);
                    return true;
                } else if (itemId == R.id.navigation_history) {
                    viewPager.setCurrentItem(2);
                    return true;
                }
                return false;
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        bottomNavigationView.getMenu().findItem(R.id.navigation_settings).setChecked(true);
                        break;
                    case 1:
                        bottomNavigationView.getMenu().findItem(R.id.navigation_generate).setChecked(true);
                        break;
                    case 2:
                        bottomNavigationView.getMenu().findItem(R.id.navigation_history).setChecked(true);
                        break;
                }
            }
        });
    }

    private void reduceDragSensitivity(ViewPager2 viewPager) {
        try {
            View child = viewPager.getChildAt(0);
            if (child instanceof RecyclerView) {
                RecyclerView recyclerView = (RecyclerView) child;
                Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
                touchSlopField.setAccessible(true);
                int touchSlop = (int) touchSlopField.get(recyclerView);
                touchSlopField.set(recyclerView, touchSlop * 4);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
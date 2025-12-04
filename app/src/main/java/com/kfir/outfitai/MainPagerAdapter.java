package com.kfir.outfitai;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new SettingsFragment();
            case 1:
                return new GenerateFragment();
            case 2:
                return new HistoryFragment();
            default:
                return new GenerateFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
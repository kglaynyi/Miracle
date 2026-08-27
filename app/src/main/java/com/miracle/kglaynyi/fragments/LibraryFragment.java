package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.FragmentViewPagerAdapter;

public class LibraryFragment extends BaseFragment {
    private static final String ARG_TAB = "initial_tab";

    private TabLayout tabLayout;
    private ViewPager2 viewPagerLibrary;

    public static LibraryFragment newInstance(int tab) {
        LibraryFragment fragment = new LibraryFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TAB, Math.max(0, Math.min(3, tab)));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tabLayout = view.findViewById(R.id.tabLayout);
        viewPagerLibrary = view.findViewById(R.id.viewPagerLibrary);
        viewPagerLibrary.setSaveEnabled(false);
        viewPagerLibrary.setAdapter(new FragmentViewPagerAdapter(this));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                viewPagerLibrary.setCurrentItem(tab.getPosition(), true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        viewPagerLibrary.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                TabLayout.Tab tab = tabLayout.getTabAt(position);
                if (tab != null && !tab.isSelected()) tabLayout.selectTab(tab);
            }
        });

        int initialTab = getArguments() == null ? 0 : getArguments().getInt(ARG_TAB, 0);
        initialTab = Math.max(0, Math.min(3, initialTab));
        viewPagerLibrary.setCurrentItem(initialTab, false);
        TabLayout.Tab tab = tabLayout.getTabAt(initialTab);
        if (tab != null) tabLayout.selectTab(tab);
    }
}

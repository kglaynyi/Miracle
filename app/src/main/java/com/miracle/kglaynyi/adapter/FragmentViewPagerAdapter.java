package com.miracle.kglaynyi.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.miracle.kglaynyi.fragments.FilesLibraryFragment;
import com.miracle.kglaynyi.fragments.MovieLibraryFragment;
import com.miracle.kglaynyi.fragments.TvShowsLibraryFragment;

public class FragmentViewPagerAdapter extends FragmentStateAdapter {


    public FragmentViewPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if(position==0){
                return new MovieLibraryFragment();
        }else if(position==1){
                return new TvShowsLibraryFragment();
        }else {
                return new FilesLibraryFragment();
        }
    }



    @Override
    public int getItemCount() {
        return 3;
    }


}

package com.vincestyling.ixiaoshuo.view.finder;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.vincestyling.ixiaoshuo.R;
import com.vincestyling.ixiaoshuo.ui.TopTabIndicator;
import com.vincestyling.ixiaoshuo.view.BaseFragment;
import com.vincestyling.ixiaoshuo.view.FragmentCreator;
import com.vincestyling.ixiaoshuo.view.PageIndicator;

public class FinderView extends BaseFragment {
	public static final int PAGER_INDEX = 1;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.finder, null);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		ViewPager finderPager = (ViewPager) view.findViewById(R.id.finderPager);
		finderPager.setAdapter(new MyAdapter());

		PageIndicator indicator = (TopTabIndicator) view.findViewById(R.id.pageIndicator);
		indicator.setViewPager(finderPager);
	}

	private FragmentCreator[] mMenus = {
			new FragmentCreator(R.string.finder_tab_type_simply, FinderSimplyView.class),
			new FragmentCreator(R.string.finder_tab_type_amply, FinderSimplyView.class),
	};

	private class MyAdapter extends FragmentStatePagerAdapter {
		public MyAdapter() {
			super(getChildFragmentManager());
		}

		@Override
		public int getCount() {
			return mMenus.length;
		}

		@Override
		public Fragment getItem(int position) {
			return mMenus[position].newInstance();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			int resId = mMenus[position].getTitleResId();
			return resId > 0 ? getResources().getString(resId) : null;
		}
	}

}
